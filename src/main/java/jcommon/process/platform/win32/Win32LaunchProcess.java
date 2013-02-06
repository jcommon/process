package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jcommon.process.api.MemoryBufferPool;
import jcommon.process.api.PinnableMemory;
import jcommon.process.platform.ILaunchProcess;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static jcommon.process.api.JNAUtils.fromSeq;
import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.api.win32.Win32.INVALID_HANDLE_VALUE;

public class Win32LaunchProcess implements ILaunchProcess {
  private static final Object io_port_lock = new Object();
  private static final MemoryBufferPool memory_pool = new MemoryBufferPool(4096, 2, 100 /* Max # of concurrent processes effectively since it won't give any more slices than that. */);
  private static HANDLE io_completion_port = INVALID_HANDLE_VALUE;
  private static final AtomicInteger running_process_count = new AtomicInteger(0);
  private static final AtomicInteger running_thread_count = new AtomicInteger(0);
  private static final ThreadGroup thread_group = new ThreadGroup("external processes");
  private static final int number_of_processors = Runtime.getRuntime().availableProcessors();
  private static final CyclicBarrier thread_pool_barrier_start = new CyclicBarrier(number_of_processors + 1);
  private static final CyclicBarrier thread_pool_barrier_stop = new CyclicBarrier(number_of_processors + 1);
  private static final ThreadInformation[] thread_pool = new ThreadInformation[number_of_processors];
  private static final Map<HANDLE, IOCompletionPortInformation> completion_ports = new HashMap<HANDLE, IOCompletionPortInformation>(2);

  private static class ThreadInformation {
    public Thread thread;
    public boolean please_stop;
    public int id;

    public ThreadInformation(int id) {
      this.id = id;
      this.please_stop = false;
    }

    public void start() {
      thread.start();
    }

    public void stop() {
      please_stop = true;
      postThreadStop(id);
    }
  }

  private static class IOCompletionPortInformation {
    OVERLAPPEDEX oOverlap;
    HANDLE port;
    HANDLE hPipeInst;

    public IOCompletionPortInformation(int index, HANDLE hPipeInst, HANDLE port) {
      this.hPipeInst = hPipeInst;
      this.port = port;
      this.oOverlap = new OVERLAPPEDEX();
      this.oOverlap.op = OVERLAPPEDEX.OP_READ;
      this.oOverlap.buffer = memory_pool.requestSlice();
      this.oOverlap.bufferSize = memory_pool.getSliceSize();
    }

    public void dispose() {
      PinnableMemory.unpin(oOverlap.buffer);
      CloseHandle(hPipeInst);
    }
  }

  private static IOCompletionPortInformation initSharedIOCompletionPort(HANDLE associate) {
    synchronized (io_port_lock) {
      if (running_process_count.getAndIncrement() == 0 && io_completion_port == INVALID_HANDLE_VALUE) {
        io_completion_port = CreateUnassociatedIoCompletionPort(Runtime.getRuntime().availableProcessors());

        //Create thread pool
        running_thread_count.set(0);
        thread_pool_barrier_start.reset();
        for(int i = 0; i < thread_pool.length; ++i) {
          final ThreadInformation ti = new ThreadInformation(i);
          final Runnable r = new Runnable() { @Override
                                              public void run() {
            try {
              thread_pool_barrier_start.await();
              ioCompletionPortProcessor(io_completion_port, ti);
            } catch(Throwable t) {
              t.printStackTrace();
            } finally {
              try {
                thread_pool_barrier_stop.await();
              } catch(Throwable t) {
              }
            }
          } };
          ti.thread = new Thread(thread_group, r, "thread-" + running_thread_count.incrementAndGet());
          thread_pool[i] = ti;
          thread_pool[i].start();
        }

        //Wait for all threads to start up.
        try { thread_pool_barrier_start.await(); } catch(Throwable t) { }
      }

      final IOCompletionPortInformation info = new IOCompletionPortInformation(completion_ports.size(), associate, io_completion_port);
      if (AssociateHandleWithIoCompletionPort(io_completion_port, associate, associate.getPointer())) {
        completion_ports.put(associate, info);
        return info;
      } else {
        info.dispose();
        return null;
      }
    }
  }

  private static void releaseSharedIOCompletionPort(IOCompletionPortInformation info) {
    synchronized (io_port_lock) {
      completion_ports.remove(info.hPipeInst);
      info.dispose();

      if (io_completion_port != null && running_process_count.decrementAndGet() == 0) {
        for(int i = 0; i < thread_pool.length; ++i) {
          thread_pool[i].stop();
        }

        //Wait for all threads to stop.
        try { thread_pool_barrier_stop.await(); } catch(Throwable t) { }

        //All threads have drained out by now.

        CloseHandle(io_completion_port);
        io_completion_port = INVALID_HANDLE_VALUE;

        memory_pool.dispose();
      }
    }
  }

  public static void postThreadStop(int thread_id) {
    //Post message to thread asking him to exit.
    final OVERLAPPEDEX o = new OVERLAPPEDEX();
    o.op = OVERLAPPEDEX.OP_EXITTHREAD;
    PostQueuedCompletionStatus(io_completion_port, 0, thread_id, o);
  }

  private static void ioCompletionPortProcessor(final HANDLE completion_port, final ThreadInformation ti) throws Throwable {
    HANDLE port = new HANDLE();
    OVERLAPPEDEX overlapped = new OVERLAPPEDEX();
    IntByReference pBytesTransferred = new IntByReference();
    PointerByReference ppOverlapped = new PointerByReference();
    IntByReference pCompletionKey = new IntByReference();
    int bytes_transferred;
    Pointer pOverlapped;
    //PortInfo pi;
    boolean isImmediate;
    ByteBuffer bb = ByteBuffer.allocateDirect(4096);
    IOCompletionPortInformation pi;

    while(!ti.please_stop) {
      //Retrieve the queued event and then examine it.
      //
      //If theGetQueuedCompletionStatus function succeeds, it dequeued a completion packet for a successful I/O operation
      //from the completion port and has stored information in the variables pointed to by the following parameters:
      //lpNumberOfBytes, lpCompletionKey, and lpOverlapped. Upon failure (the return value is FALSE), those same
      //parameters can contain particular value combinations as follows:
      //
      //    If *lpOverlapped is NULL, the function did not dequeue a completion packet from the completion port. In this
      //    case, the function does not store information in the variables pointed to by the lpNumberOfBytes and
      //    lpCompletionKey parameters, and their values are indeterminate.
      //
      //    If *lpOverlapped is not NULL and the function dequeues a completion packet for a failed I/O operation from the
      //    completion port, the function stores information about the failed operation in the variables pointed to by
      //    lpNumberOfBytes, lpCompletionKey, and lpOverlapped. To get extended error information, call GetLastError.
      if (!GetQueuedCompletionStatus(completion_port, pBytesTransferred, pCompletionKey, ppOverlapped, INFINITE)) {
        switch(GetLastError()) {
          //If a call to GetQueuedCompletionStatus fails because the completion port handle associated with it is
          //closed while the call is outstanding, the function returns FALSE, *lpOverlapped will be NULL, and
          //GetLastError will return ERROR_ABANDONED_WAIT_0.
          //
          //    Windows Server 2003 and Windows XP:
          //      Closing the completion port handle while a call is outstanding will not result in the previously
          //      stated behavior. The function will continue to wait until an entry is removed from the port or
          //      until a time-out occurs, if specified as a value other than INFINITE.
          case ERROR_ABANDONED_WAIT_0:
            //The associated port has been closed -- abandon further processing.
            return;
          default:
            break;
        }
      }

      //Retrieve data from the event.
      //
      //We attempt to reuse existing references in order to avoid having to allocate
      //more objects than is necessary to process this event.
      overlapped.reuse(pOverlapped = ppOverlapped.getValue());

      //If we've received a message asking to break out of the thread, then loop back and around
      //and check that our flag has been set. If so, then it's time to go!
      if (overlapped.op == OVERLAPPEDEX.OP_EXITTHREAD) {
        //The completion key will be an integer id indicating the thread that this message is
        //intended for. If the ids match, then get out of here, otherwise relay the message.
        final int thread_id = pCompletionKey.getValue();
        if (thread_id == ti.id) {
          continue;
        } else {
          postThreadStop(thread_id);
        }
      }

      port.reuse(Pointer.createConstant(pCompletionKey.getValue()));

      //Get the number of bytes transferred based on what the OS has told us. Be sure and bound
      //it to the buffer size to avoid a corrupted value causing us to read off the end of the buffer.
      bytes_transferred = Math.max(0, Math.min(pBytesTransferred.getValue(), overlapped.bufferSize));

      //If, for some unknown reason, we are processing an event for a port we haven't seen before,
      //then go ahead and ignore it.
      if ((pi = completion_ports.get(port)) == null)
        continue;

      switch(overlapped.op) {
        case OVERLAPPEDEX.OP_READ:
          if (!GetOverlappedResult(port, pOverlapped, pBytesTransferred, false))
            continue;

          if (bytes_transferred > 0) {
            String output = Charset.defaultCharset().decode(overlapped.buffer.getByteBuffer(0, bytes_transferred)).toString();
            System.out.print(output);
          }
          //Schedule our next read.
          read(pi);

          break;
        case OVERLAPPEDEX.OP_MANUAL_READ:
          read(pi);
          break;
      }
    }
  }

  @Override
  public boolean launch(String... args) {
    if (args == null || args.length <= 1 || "".equals(args[0])) {
      throw new IllegalArgumentException("args cannot be null or empty and provide at least the executable as the first argument.");
    }

    if (args[0].length() > MAX_PATH) {
      throw new IllegalArgumentException("The application path cannot exceed " + MAX_PATH + " characters.");
    }

    int size = 0;
    for(int i = 0; i < args.length; ++i) {
      size += args[i].length();
    }

    final StringBuilder sb = new StringBuilder(size + (args.length * 3 /* Space and beginning and ending quotes */));
    for(int i = 0; i < args.length; ++i) {
      final String arg = args[i];
        //boolean  is_quoted_with_double_quotes = arg.startsWith("\"");
        //boolean  is_quoted_with_single_quote = arg.startsWith("\'");
        //boolean is_quoted = is_quoted_with_double_quotes || is_quoted_with_single_quote;
        //boolean ends_correctly = (is_quoted_with_double_quotes && arg.endsWith("\"")) || (is_quoted_with_single_quote && arg.endsWith("\'"));
        //
        //if (!ends_correctly) {
        //  throw new IllegalArgumentException("Malformed argument: " + arg + ". The ending character should match the beginning single or double quote.");
        //}
        //
        //if (!is_quoted)
        //  sb.append("\"");
        //
        //sb.append(arg);
        //
        //if (!is_quoted)
        //  sb.append("\"");
      sb.append(arg);

      //Separate each argument with a space.
      if (i != args.length - 1)
        sb.append(' ');
    }

    //Validate total length of the arguments.
    if (sb.length() > MAX_COMMAND_LINE_SIZE) {
      throw new IllegalArgumentException("The complete command line cannot exceed " + MAX_COMMAND_LINE_SIZE + " characters.");
    }

    //Setup pipes for stdout/stderr/stdin redirection.
    // Set the bInheritHandle flag so pipe handles are inherited.
    SECURITY_ATTRIBUTES saAttr = new SECURITY_ATTRIBUTES();
    saAttr.bInheritHandle = true;
    saAttr.lpSecurityDescriptor = null;

    HANDLEByReference g_hChildStd_OUT_Rd = new HANDLEByReference();
    HANDLEByReference g_hChildStd_OUT_Wr = new HANDLEByReference();

    // Create a pipe for the child process's STDOUT.

    if (!CreateOverlappedPipe(g_hChildStd_OUT_Rd, g_hChildStd_OUT_Wr, saAttr, 4096, FILE_FLAG_OVERLAPPED, 0)) {
      throw new IllegalStateException("Unable to create a pipe fpr the child process' stdout.");
    }

    // Ensure the read handle to the pipe for STDOUT is not inherited.

    HANDLE h_child_read_std_out = g_hChildStd_OUT_Rd.getValue();
    HANDLE h_child_write_std_out = g_hChildStd_OUT_Wr.getValue();

    if (!SetHandleInformation(h_child_read_std_out, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(h_child_read_std_out);
      CloseHandle(h_child_write_std_out);
      throw new IllegalStateException("Unable to ensure the pipe's stdout read handle is not inherited.");
    }

    HANDLEByReference g_hChildStd_IN_Rd = new HANDLEByReference();
    HANDLEByReference g_hChildStd_IN_Wr = new HANDLEByReference();

    // Create a pipe for the child process's STDIN.

    if (!CreatePipe(g_hChildStd_IN_Rd, g_hChildStd_IN_Wr, saAttr, 0)) {
      CloseHandle(h_child_read_std_out);
      CloseHandle(h_child_write_std_out);
      throw new IllegalStateException("Unable to create a pipe for the child process' stdin.");
    }

    // Ensure the write handle to the pipe for STDIN is not inherited.

    HANDLE h_child_read_std_in = g_hChildStd_IN_Rd.getValue();
    HANDLE h_child_write_std_in = g_hChildStd_IN_Wr.getValue();

    if (!SetHandleInformation(h_child_write_std_in, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(h_child_read_std_out);
      CloseHandle(h_child_write_std_out);
      CloseHandle(h_child_read_std_in);
      CloseHandle(h_child_write_std_in);
      throw new IllegalStateException("Unable to ensure the pipe's stdin write handle is not inherited.");
    }

    final String command_line = sb.toString();
    final STARTUPINFO inf = new STARTUPINFO();
    final PROCESS_INFORMATION.ByReference pi = new PROCESS_INFORMATION.ByReference();

    inf.lpReserved       = null;
    inf.lpDesktop        = null;
    inf.lpTitle          = null; /* title in console window */
    inf.dwX              = new DWORD(0); /* x-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    inf.dwY              = new DWORD(0); /* y-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    inf.dwXSize          = new DWORD(0); /* width of window in pixels, only used if STARTF_USESIZE is specified */
    inf.dwYSize          = new DWORD(0); /* height of window in pixels, only used if STARTF_USESIZE is specified */
    inf.dwXCountChars    = new DWORD(0); /* screen buffer width in char columns, only used if STARTF_USECOUNTCHARS is specified */
    inf.dwYCountChars    = new DWORD(0); /* screen buffer height in char rows, only used if STARTF_USECOUNTCHARS is specified */
    inf.dwFillAttribute  = new DWORD(0); /* initial text and background colors for a console window, only used if STARTF_USEFILLATTRIBUTE is specified */
    inf.dwFlags         |= STARTF_USESTDHANDLES;
    inf.wShowWindow      = new WORD(0);
    inf.cbReserved2      = new WORD(0);
    inf.lpReserved2      = null;
    inf.hStdInput        = h_child_read_std_in;
    inf.hStdOutput       = h_child_write_std_out;
    inf.hStdError        = h_child_write_std_out;

    IOCompletionPortInformation port_info = initSharedIOCompletionPort(h_child_read_std_out);

    try {
      final boolean success = CreateProcess(null, command_line, null, null, true, new DWORD(NORMAL_PRIORITY_CLASS), Pointer.NULL /* environment block */, null /* current dir */, inf, pi) != 0;
      if (!success) {
        throw new IllegalStateException("Unable to create a process with the following command line: " + command_line);
      }
    } catch(Throwable t) {
      throw new IllegalStateException("Unable to create a process with the following command line: " + command_line, t);
    }

    final long pid = pi.dwProcessId.longValue();
    System.out.println("pid: " + pid);

    //Close out the child process' stdout write.
    CloseHandle(h_child_write_std_out);

//    ByteBuffer bb = ByteBuffer.allocateDirect(4096);
//    IntByReference lp_bytes_read = new IntByReference();
//    IntByReference lp_bytes_written = new IntByReference();
//    HANDLE hParentStdOut = GetStdHandle(STD_OUTPUT_HANDLE);
    read(port_info);
//    if (!ReadFile(h_child_read_std_out, port_info.bbRead, port_info.bbRead.capacity(), lp_bytes_read, port_info.oOverlap) || lp_bytes_read.getValue() == 0) {
//      System.out.println("UNABLE TO READ FILE :(");
//    }
//
//    System.out.println("READ: " + lp_bytes_read.getValue());
//
//    switch(GetLastError()) {
//      case ERROR_IO_PENDING:
//        System.out.println("PENDING!");
//        break;
//      case ERROR_SUCCESS:
//        System.out.println("SUCCESS?");
//        break;
//      default:
//        System.out.println("???");
//        break;
//    }

    //Read from pipe
//    ByteBuffer bb = ByteBuffer.allocateDirect(4096);
//    IntByReference lp_bytes_read = new IntByReference();
//    IntByReference lp_bytes_written = new IntByReference();
//    HANDLE hParentStdOut = GetStdHandle(STD_OUTPUT_HANDLE);

//    while(true) {
//      if (!ReadFile(h_child_read_std_out, bb, bb.capacity(), lp_bytes_read, null) || lp_bytes_read.getValue() == 0) {
//        break;
//      }
//      if (!WriteFile(hParentStdOut, bb, lp_bytes_read.getValue(), lp_bytes_written, null)) {
//        break;
//      }
//    }

    WaitForSingleObject(pi.hProcess, INFINITE);

    CloseHandle(h_child_read_std_out);
//    CloseHandle(h_child_read_std_in);
//    CloseHandle(h_child_write_std_in);

    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);

    releaseSharedIOCompletionPort(port_info);

    return false;
  }

  private static void read(IOCompletionPortInformation port_info) {
    if (ReadFile(port_info.hPipeInst, port_info.oOverlap.buffer, port_info.oOverlap.bufferSize, null, port_info.oOverlap) /*|| lp_bytes_read.getValue() == 0*/) {
      //uh-oh! That means the data was processed synchronously. In that case, post it to the completion port for processing.
      //PostQueuedCompletionStatus(port_info.port, lp_bytes_read.getValue(), port_info.hPipeInst.getPointer(), port_info.oOverlap);
      OVERLAPPEDEX o = new OVERLAPPEDEX();
      o.op = OVERLAPPEDEX.OP_MANUAL_READ;
      o.buffer = null;
      o.bufferSize = 0;
      PostQueuedCompletionStatus(port_info.port, 0, port_info.hPipeInst.getPointer(), o);
      return;
    }

    switch(GetLastError()) {
      case ERROR_IO_PENDING:
        //This is what it should be. Indicates that the operation is being processed asynchronously.
        break;
      case ERROR_OPERATION_ABORTED:
        //The operation has been cancelled.
        break;
      case ERROR_INVALID_USER_BUFFER:
      case ERROR_NOT_ENOUGH_MEMORY:
        //The ReadFile function may fail with ERROR_INVALID_USER_BUFFER or ERROR_NOT_ENOUGH_MEMORY whenever there are
        //too many outstanding asynchronous I/O requests.
        break;
      case ERROR_SUCCESS:
        break;
      default:
        break;
    }
  }

  public boolean launch2(String... args) {
    if (args == null || args.length <= 1 || "".equals(args[0])) {
      throw new IllegalArgumentException("args cannot be null or empty and provide at least the executable as the first argument.");
    }

    if (args[0].length() > MAX_PATH) {
      throw new IllegalArgumentException("The application path cannot exceed " + MAX_PATH + " characters.");
    }

    int size = 0;
    for(int i = 0; i < args.length; ++i) {
      size += args[i].length();
    }

    final StringBuilder sb = new StringBuilder(size + (args.length * 3 /* Space and beginning and ending quotes */));
    for(int i = 0; i < args.length; ++i) {
      final String arg = args[i];
//        boolean  is_quoted_with_double_quotes = arg.startsWith("\"");
//        boolean  is_quoted_with_single_quote = arg.startsWith("\'");
//        boolean is_quoted = is_quoted_with_double_quotes || is_quoted_with_single_quote;
//        boolean ends_correctly = (is_quoted_with_double_quotes && arg.endsWith("\"")) || (is_quoted_with_single_quote && arg.endsWith("\'"));
//
//        if (!ends_correctly) {
//          throw new IllegalArgumentException("Malformed argument: " + arg + ". The ending character should match the beginning single or double quote.");
//        }
//
//        if (!is_quoted)
//          sb.append("\"");
//
//        sb.append(arg);
//
//        if (!is_quoted)
//          sb.append("\"");
      sb.append(arg);

      //Separate each argument with a space.
      if (i != args.length - 1)
        sb.append(' ');
    }

    //Validate total length of the arguments.
    if (sb.length() > MAX_COMMAND_LINE_SIZE) {
      throw new IllegalArgumentException("The complete command line cannot exceed " + MAX_COMMAND_LINE_SIZE + " characters.");
    }

    //Setup pipes for stdout/stderr/stdin redirection.
    // Set the bInheritHandle flag so pipe handles are inherited.
    SECURITY_ATTRIBUTES saAttr = new SECURITY_ATTRIBUTES();
    saAttr.bInheritHandle = true;
    saAttr.lpSecurityDescriptor = null;

    HANDLEByReference g_hChildStd_OUT_Rd = new HANDLEByReference();
    HANDLEByReference g_hChildStd_OUT_Wr = new HANDLEByReference();

    // Create a pipe for the child process's STDOUT.

    if (!CreatePipe(g_hChildStd_OUT_Rd, g_hChildStd_OUT_Wr, saAttr, 4096)) {
      throw new IllegalStateException("Unable to create a pipe fpr the child process' stdout.");
    }

    // Ensure the read handle to the pipe for STDOUT is not inherited.

    HANDLE h_child_read_std_out = g_hChildStd_OUT_Rd.getValue();
    HANDLE h_child_write_std_out = g_hChildStd_OUT_Wr.getValue();

    if (!SetHandleInformation(h_child_read_std_out, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(h_child_read_std_out);
      CloseHandle(h_child_write_std_out);
      throw new IllegalStateException("Unable to ensure the pipe's stdout read handle is not inherited.");
    }

    HANDLEByReference g_hChildStd_IN_Rd = new HANDLEByReference();
    HANDLEByReference g_hChildStd_IN_Wr = new HANDLEByReference();

    // Create a pipe for the child process's STDIN.

    if (!CreatePipe(g_hChildStd_IN_Rd, g_hChildStd_IN_Wr, saAttr, 0)) {
      CloseHandle(h_child_read_std_out);
      CloseHandle(h_child_write_std_out);
      throw new IllegalStateException("Unable to create a pipe for the child process' stdin.");
    }

    // Ensure the write handle to the pipe for STDIN is not inherited.

    HANDLE h_child_read_std_in = g_hChildStd_IN_Rd.getValue();
    HANDLE h_child_write_std_in = g_hChildStd_IN_Wr.getValue();

    if (!SetHandleInformation(h_child_write_std_in, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(h_child_read_std_out);
      CloseHandle(h_child_write_std_out);
      CloseHandle(h_child_read_std_in);
      CloseHandle(h_child_write_std_in);
      throw new IllegalStateException("Unable to ensure the pipe's stdin write handle is not inherited.");
    }

    final String command_line = sb.toString();
    final STARTUPINFO inf = new STARTUPINFO();
    final PROCESS_INFORMATION.ByReference pi = new PROCESS_INFORMATION.ByReference();

    inf.lpReserved       = null;
    inf.lpDesktop        = null;
    inf.lpTitle          = null; /* title in console window */
    inf.dwX              = new DWORD(0); /* x-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    inf.dwY              = new DWORD(0); /* y-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    inf.dwXSize          = new DWORD(0); /* width of window in pixels, only used if STARTF_USESIZE is specified */
    inf.dwYSize          = new DWORD(0); /* height of window in pixels, only used if STARTF_USESIZE is specified */
    inf.dwXCountChars    = new DWORD(0); /* screen buffer width in char columns, only used if STARTF_USECOUNTCHARS is specified */
    inf.dwYCountChars    = new DWORD(0); /* screen buffer height in char rows, only used if STARTF_USECOUNTCHARS is specified */
    inf.dwFillAttribute  = new DWORD(0); /* initial text and background colors for a console window, only used if STARTF_USEFILLATTRIBUTE is specified */
    inf.dwFlags         |= STARTF_USESTDHANDLES;
    inf.wShowWindow      = new WORD(0);
    inf.cbReserved2      = new WORD(0);
    inf.lpReserved2      = null;
    inf.hStdInput        = h_child_read_std_in;
    inf.hStdOutput       = h_child_write_std_out;
    inf.hStdError        = h_child_write_std_out;

    try {
      final boolean success = CreateProcess(null, command_line, null, null, true, new DWORD(NORMAL_PRIORITY_CLASS), Pointer.NULL /* environment block */, null /* current dir */, inf, pi) != 0;
      if (!success) {
        throw new IllegalStateException("Unable to create a process with the following command line: " + command_line);
      }
    } catch(Throwable t) {
      throw new IllegalStateException("Unable to create a process with the following command line: " + command_line, t);
    }

    final long pid = pi.dwProcessId.longValue();
    System.out.println("pid: " + pid);

    //Close out the child process' stdout write.
    CloseHandle(h_child_write_std_out);

    //Read from pipe
    ByteBuffer bb = ByteBuffer.allocateDirect(4096);
    IntByReference lp_bytes_read = new IntByReference();
    IntByReference lp_bytes_written = new IntByReference();
    HANDLE hParentStdOut = GetStdHandle(STD_OUTPUT_HANDLE);

    while(true) {
      if (!ReadFile(h_child_read_std_out, bb, bb.capacity(), lp_bytes_read, null) || lp_bytes_read.getValue() == 0) {
        break;
      }
      if (!WriteFile(hParentStdOut, bb, lp_bytes_read.getValue(), lp_bytes_written, null)) {
        break;
      }
    }

    CloseHandle(h_child_read_std_out);
    CloseHandle(h_child_read_std_in);
    CloseHandle(h_child_write_std_in);

    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);

    return false;
  }

  private static AtomicInteger pipe_serial_number = new AtomicInteger(0);
  public static boolean CreateOverlappedPipe(HANDLEByReference lpReadPipe, HANDLEByReference lpWritePipe, SECURITY_ATTRIBUTES lpPipeAttributes, int nSize, int dwReadMode, int dwWriteMode) {
    if (((dwReadMode | dwWriteMode) & (~FILE_FLAG_OVERLAPPED)) != 0) {
      throw new IllegalArgumentException("This method is to be used for overlapped IO only.");
    }

    if (nSize == 0) {
      nSize = 4096;
    }

    final String pipe_name = "\\\\.\\Pipe\\RemoteExeAnon." + GetCurrentProcessId() + "." + pipe_serial_number.incrementAndGet();

    final HANDLE ReadPipeHandle = CreateNamedPipe(pipe_name, PIPE_ACCESS_INBOUND | FILE_FLAG_OVERLAPPED | dwReadMode, PIPE_TYPE_BYTE | PIPE_WAIT | PIPE_READMODE_BYTE, 1, nSize, nSize, 0, lpPipeAttributes);
    if (ReadPipeHandle == INVALID_HANDLE_VALUE) {
      return false;
    }

    final HANDLE WritePipeHandle = CreateFile(pipe_name, GENERIC_WRITE, 0, lpPipeAttributes, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | dwWriteMode, null);
    if (WritePipeHandle == INVALID_HANDLE_VALUE) {
      CloseHandle(ReadPipeHandle);
      return false;
    }

    lpReadPipe.setValue(ReadPipeHandle);
    lpWritePipe.setValue(WritePipeHandle);

    return true;
  }

  public static class OVERLAPPEDEX extends OVERLAPPED {
    public OVERLAPPED ovl;
    public Pointer buffer;
    public int bufferSize;
    public int op;

    private static final List FIELD_ORDER = fromSeq(
        "Internal"
      , "InternalHigh"
      , "Offset"
      , "OffsetHigh"
      , "hEvent"
      , "ovl"
      , "buffer"
      , "bufferSize"
      , "op"
    );

    @Override
    protected List getFieldOrder() {
      return FIELD_ORDER;
    }

    public void reuse(Pointer memory) {
      useMemory(memory);
      read();
    }

    public static final int
        OP_OPEN                       = 0
      , OP_READ                       = 1
      , OP_MANUAL_READ                = 2
      , OP_WRITE                      = 3
      , OP_WRITE_IMMEDIATE            = 4
      , OP_CLOSE                      = 5
      , OP_EXITTHREAD                 = 6
    ;
  }
}
