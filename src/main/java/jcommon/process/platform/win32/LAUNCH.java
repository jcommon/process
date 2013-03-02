package jcommon.process.platform.win32;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableObject;
import jcommon.process.api.PinnableStruct;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.platform.win32.Utils.*;

/**
 * Created with IntelliJ IDEA.
 * User: dhoyt
 * Date: 2/27/13
 * Time: 4:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class LAUNCH {
  private static Set<Pointer> pinned =  new HashSet<Pointer>();
  private static Map<Pointer, Memory> pinned_memory = new HashMap<Pointer, Memory>();
  private static Map<Pointer, Context> context_map = new HashMap<Pointer, Context>();

  private static final int
      STATE_CONNECTED             = 0
    , STATE_STDOUT_READ           = 1
    , STATE_STDOUT_READ_COMPLETED = 2
    , STATE_DISCONNECT            = 3
  ;

  private static class Sequencing {
    public static final int
        MAX_SEQUENCE_NUMBER = 5001
    ;

    public final Object lock = new Object();
    private final Map<Integer, IOCPBUFFER> bufferMap = new HashMap<Integer, IOCPBUFFER>(10, 1.0f);
    private int outstandingIOs = 0;
    private boolean closing = false;
    private boolean closed = false;
    private int currentSequenceNumber = 1;
    private int currentSequenceMarker = 1;

    public boolean isClosed() {
      return closed;
    }

    public int sequenceNumber() {
      return currentSequenceNumber;
    }

    public int incrementSequenceNumber() {
      return (currentSequenceNumber = (currentSequenceNumber + 1) % MAX_SEQUENCE_NUMBER);
    }

    public int decrementSequenceNumber() {
      if (currentSequenceNumber != 1)
        return (currentSequenceNumber = (currentSequenceNumber - 1) % MAX_SEQUENCE_NUMBER);
      else
        return (currentSequenceNumber = MAX_SEQUENCE_NUMBER);
    }

    private int incrementMarker() {
      return (currentSequenceMarker = (currentSequenceMarker + 1) % MAX_SEQUENCE_NUMBER);
    }

    public IOCPBUFFER nextBuffer() {
      return nextBuffer(null);
    }

    public IOCPBUFFER nextBuffer(IOCPBUFFER iocpBuffer) {
      //if (closed) {
      //  return createEndOfStreamMarker();
      //}

      IOCPBUFFER retBuff;

      if (iocpBuffer != null) {
        incrementSequenceNumber();

        int buffer_sequence_number = iocpBuffer.sequenceNumber;
        if (buffer_sequence_number == currentSequenceMarker) {
          incrementMarker();
          return iocpBuffer;
        }

        //Add to map.
        bufferMap.put(buffer_sequence_number, iocpBuffer);
      }

      retBuff = bufferMap.remove(currentSequenceMarker);
      if (retBuff != null) {
        incrementMarker();
        if (retBuff.isMarkedAsEndOfStream()) {
          closed = true;
        }
      }
      return retBuff;
    }

    private IOCPBUFFER createEndOfStreamMarker() {
      final IOCPBUFFER buff = new IOCPBUFFER();
      buff.buffer = null;
      buff.bufferSize = 0;
      buff.sequenceNumber = incrementSequenceNumber();
      buff.markAsEndOfStream();
      return buff;
    }

    public void endOfStream() {
      //Append a special marker buffer to the end of the stream.
      closing = true;

      final IOCPBUFFER buff = createEndOfStreamMarker();
      bufferMap.put(buff.sequenceNumber, buff);
      incrementMarker();
    }
  }

  private static class Context {
    public final ProcessInformation process_info;
    public final Sequencing stdout = new Sequencing();
    public final Sequencing stderr = new Sequencing();
    public final Sequencing stdin = new Sequencing();
    public final AtomicBoolean closed = new AtomicBoolean(false);

    public Context(ProcessInformation process_info) {
      this.process_info = process_info;
    }
  }

  private static final IOCompletionPort<Context> iocp = new IOCompletionPort<Context>(new IOCompletionPort.IProcessor<Context>() {
    @Override
    public void process(int state, int bytesTransferred, OVERLAPPED_WITH_BUFFER_AND_STATE ovl, Context context, Pointer completionKey, Pointer pOverlapped, IntByReference pBytesTransferred, Object overlappedPool, Object memoryPool, IOCompletionPort<Context> iocp) throws Throwable {
      completion_thread(state, bytesTransferred, ovl, context, completionKey, pOverlapped, pBytesTransferred, overlappedPool, memoryPool, iocp);
    }
  });

  private static void completion_thread(final int state, final int bytesTransferred, final OVERLAPPED_WITH_BUFFER_AND_STATE ovl, final Context context, final Pointer completion_key, final Pointer ptr_overlapped, final IntByReference ptr_bytes_transferred, final Object overlapped_pool, final Object memory_pool, final IOCompletionPort<Context> iocp) throws Throwable {
    int err;
    IOCPBUFFER iocp_buffer;
    boolean end_of_stream = false;

    switch(state) {
      case STATE_CONNECTED:
        //Connected
        context.process_info.notifyStarted();

        synchronized (context.stdout) {
          read(iocp, context, context.stdout, completion_key, overlapped_pool, memory_pool, context.process_info.stdout_child_process_read, STATE_STDOUT_READ_COMPLETED);
        }

        ResumeThread(context.process_info.main_thread);
        CloseHandle(context.process_info.main_thread);
        break;

      case STATE_STDOUT_READ:
        synchronized (context.stdout) {
          read(iocp, context, context.stdout, completion_key, overlapped_pool, memory_pool, context.process_info.stdout_child_process_read, STATE_STDOUT_READ_COMPLETED);
        }
        break;

      case STATE_STDOUT_READ_COMPLETED:
        synchronized (context.stdout) {
          iocp_buffer = PinnableStruct.unpin(ovl.iocpBuffer);

          if (iocp_buffer != null)
            iocp_buffer.bytesTransferred(bytesTransferred);

          while(!end_of_stream && (iocp_buffer = context.stdout.nextBuffer(iocp_buffer)) !=  null) {
            gc();

            end_of_stream = iocp_buffer.isMarkedAsEndOfStream();

            if (!end_of_stream) {
              ByteBuffer bb = iocp_buffer.buffer.getByteBuffer(0, iocp_buffer.bytesTransferred);
              context.process_info.notifyStdOut(bb, iocp_buffer.bytesTransferred);
            }

            PinnableMemory.dispose(iocp_buffer.buffer);

            iocp_buffer = null;
          }

          if (!end_of_stream) {
            //read(iocp, context, context.stdout, completion_key, overlapped_pool, memory_pool, context.process_info.stdout_child_process_read, STATE_STDOUT_READ_COMPLETED);
            iocp.postMessage(completion_key, STATE_STDOUT_READ);
          } else {
            iocp.postMessage(completion_key, STATE_DISCONNECT);
          }
        }
        break;

      case STATE_DISCONNECT: //Close
        synchronized (context.stdout) {
          if (!context.stdout.isClosed()) {
            iocp.postMessage(completion_key, STATE_STDOUT_READ);
            return;
          }
        }

        if (!context.closed.compareAndSet(false, true))
          return;

        CloseHandle(context.process_info.stdin_child_process_write);
        CloseHandle(context.process_info.stderr_child_process_read);
        CloseHandle(context.process_info.stdout_child_process_read);

        //By the time we get here, the process should have already exited.
        //But just in case it hasn't, we'll wait around for it.
        WaitForSingleObject(context.process_info.process, INFINITE);

        final IntByReference exit_code = new IntByReference();
        GetExitCodeProcess(context.process_info.process, exit_code);

        CloseHandle(context.process_info.process);

        context.process_info.exit_value.set(exit_code.getValue());

        context.process_info.notifyStopped(exit_code.getValue());

        iocp.release(completion_key);

        PinnableObject.unpin(context);

        context.process_info.exit_latch.countDown();
        break;
    }
  }

  public static IProcess launch(final boolean inherit_parent_environment, final IEnvironmentVariable[] environment_variables, final String[] args, final IProcessListener[] listeners) {
    final String command_line = formulateSanitizedCommandLine(args);

    //Setup pipes for stdout/stderr/stdin redirection.
    //Set the bInheritHandle flag so pipe handles are inherited.
    final SECURITY_ATTRIBUTES saAttr = new SECURITY_ATTRIBUTES();
    saAttr.bInheritHandle = true;
    saAttr.lpSecurityDescriptor = null;

    final HANDLEByReference ptr_stdout_child_process_read = new HANDLEByReference();
    final HANDLEByReference ptr_stdout_child_process_write = new HANDLEByReference();

    // Create a pipe for the child process's STDOUT.

    if (!CreateOverlappedPipe(ptr_stdout_child_process_read, ptr_stdout_child_process_write, saAttr, 1024, FILE_FLAG_OVERLAPPED, 0)) {
      throw new IllegalStateException("Unable to create a pipe for the child process' stdout.");
    }

    // Ensure the read handle to the pipe for STDOUT is not inherited.

    final HANDLE stdout_child_process_read = ptr_stdout_child_process_read.getValue();
    final HANDLE stdout_child_process_write = ptr_stdout_child_process_write.getValue();

    if (!SetHandleInformation(stdout_child_process_read, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      throw new IllegalStateException("Unable to ensure the pipe's stdout read handle is not inherited.");
    }

    final HANDLEByReference ptr_stderr_child_process_read = new HANDLEByReference();
    final HANDLEByReference ptr_stderr_child_process_write = new HANDLEByReference();

    // Create a pipe for the child process's STDERR.

    if (!CreateOverlappedPipe(ptr_stderr_child_process_read, ptr_stderr_child_process_write, saAttr, 1024, FILE_FLAG_OVERLAPPED, 0)) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      throw new IllegalStateException("Unable to create a pipe for the child process' stderr.");
    }

    // Ensure the read handle to the pipe for STDERR is not inherited.

    final HANDLE stderr_child_process_read = ptr_stderr_child_process_read.getValue();
    final HANDLE stderr_child_process_write = ptr_stderr_child_process_write.getValue();

    if (!SetHandleInformation(stderr_child_process_read, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      CloseHandle(stderr_child_process_read);
      CloseHandle(stderr_child_process_write);
      throw new IllegalStateException("Unable to ensure the pipe's stdout read handle is not inherited.");
    }

    final HANDLEByReference ptr_stdin_child_process_read = new HANDLEByReference();
    final HANDLEByReference ptr_stdin_child_process_write = new HANDLEByReference();

    // Create a pipe for the child process's STDIN.

    if (!CreatePipe(ptr_stdin_child_process_read, ptr_stdin_child_process_write, saAttr, 0)) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      CloseHandle(stderr_child_process_read);
      CloseHandle(stderr_child_process_write);
      throw new IllegalStateException("Unable to create a pipe for the child process' stdin.");
    }

    // Ensure the write handle to the pipe for STDIN is not inherited.

    final HANDLE stdin_child_process_read = ptr_stdin_child_process_read.getValue();
    final HANDLE stdin_child_process_write = ptr_stdin_child_process_write.getValue();


    if (!SetHandleInformation(stdin_child_process_write, HANDLE_FLAG_INHERIT, 0)) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      CloseHandle(stderr_child_process_read);
      CloseHandle(stderr_child_process_write);
      CloseHandle(stdin_child_process_read);
      CloseHandle(stdin_child_process_write);
      throw new IllegalStateException("Unable to ensure the pipe's stdin write handle is not inherited.");
    }

    final STARTUPINFO startup_info = new STARTUPINFO();
    final PROCESS_INFORMATION.ByReference proc_info = new PROCESS_INFORMATION.ByReference();

    startup_info.lpReserved       = null;
    startup_info.lpDesktop        = null;
    startup_info.lpTitle          = null; /* title in console window */
    startup_info.dwX              = new DWORD(0); /* x-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    startup_info.dwY              = new DWORD(0); /* y-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    startup_info.dwXSize          = new DWORD(0); /* width of window in pixels, only used if STARTF_USESIZE is specified */
    startup_info.dwYSize          = new DWORD(0); /* height of window in pixels, only used if STARTF_USESIZE is specified */
    startup_info.dwXCountChars    = new DWORD(0); /* screen buffer width in char columns, only used if STARTF_USECOUNTCHARS is specified */
    startup_info.dwYCountChars    = new DWORD(0); /* screen buffer height in char rows, only used if STARTF_USECOUNTCHARS is specified */
    startup_info.dwFillAttribute  = new DWORD(0); /* initial text and background colors for a console window, only used if STARTF_USEFILLATTRIBUTE is specified */
    startup_info.dwFlags         |= STARTF_USESTDHANDLES;
    startup_info.wShowWindow      = new WORD(0);
    startup_info.cbReserved2      = new WORD(0);
    startup_info.lpReserved2      = null;
//    startup_info.hStdInput        = stdin_child_process_read;
    startup_info.hStdOutput       = stdout_child_process_write;
    startup_info.hStdError        = GetStdHandle(STD_ERROR_HANDLE);
//    startup_info.hStdError        = stderr_child_process_write;
    startup_info.hStdInput        = GetStdHandle(STD_INPUT_HANDLE);

    //We create the process initially suspended while we setup a connection, inform
    //interested parties that the process has been created, etc. Once that's done,
    //we can start read operations.
    try {
      final boolean success = CreateProcess(null, command_line, null, null, true, new DWORD(NORMAL_PRIORITY_CLASS | CREATE_SUSPENDED | CREATE_NO_WINDOW), Pointer.NULL /* environment block */, null /* current dir */, startup_info, proc_info) != 0;
      if (!success) {
        throw new IllegalStateException("Unable to create a process with the following command line: " + command_line);
      }
    } catch(Throwable t) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      CloseHandle(stderr_child_process_read);
      CloseHandle(stderr_child_process_write);
      CloseHandle(stdin_child_process_read);
      CloseHandle(stdin_child_process_write);
      throw new IllegalStateException("Unable to create a process with the following command line: " + command_line, t);
    }

    //Close out the child process' stdout write.
    CloseHandle(stdout_child_process_write);

    //Close out the child process' stderr write.
    CloseHandle(stderr_child_process_write);

    //Close out the child process' stdin write.
    CloseHandle(stdin_child_process_read);

    //Don't need to pin b/c the associations object in IOCompletionPort will pin it for me.
    final Pointer pContext = new CONTEXT().getPointer();
    final Pointer completion_key = pContext;

    //Associate our new process with the shared i/o completion port.
    //This is *deliberately* done after the process is created mainly so that we have
    //the process id and process handle.
    final ProcessInformation process_info = new ProcessInformation(
        proc_info.dwProcessId.intValue(),
        proc_info.hProcess,
        proc_info.hThread,
        stdout_child_process_read,
        stderr_child_process_read,
        stdin_child_process_write,
//        null,
        inherit_parent_environment,
        environment_variables,
        args,
        listeners,
        null
//      new ProcessInformation.ICreateProcessExitCallback() {
//        @Override
//        public HANDLE createExitCallback(final ProcessInformation process_info) {
//          final HANDLEByReference ptr_process_exit_wait = new HANDLEByReference();
//
//          //Receive notification when the process exits.
//          RegisterWaitForSingleObject(
//              ptr_process_exit_wait,
//              proc_info.hProcess,
//              PinnableCallback.pin(new WAITORTIMERCALLBACK() {
//                @Override
//                public void WaitOrTimerCallback(Pointer lpParameter, boolean TimerOrWaitFired) {
//                  PinnableCallback.unpin(this);
//
//                  //Post a message to the iocp letting it know that the child has exited.
//                  //iocp.postMessage(completion_key, OP_CHILD_DISCONNECT);
//                }
//              }),
//              null,
//              INFINITE,
//              WT_EXECUTEDEFAULT | WT_EXECUTEONLYONCE
//          );
//
//          return ptr_process_exit_wait.getValue();
//        }
//      }
    );

    final Context context = new Context(process_info);

    if (!iocp.associateHandles(completion_key, context, process_info.stdout_child_process_read, process_info.stderr_child_process_read)) {
      //Ensure we don't get the callback.
      //UnregisterWait(process_info.process_exit_wait);

      //Kill the process forcefully.
      TerminateProcess(process_info.process, 0);

      //Close remaining open handles.
      CloseHandle(process_info.stdin_child_process_write);
      CloseHandle(process_info.stderr_child_process_read);
      CloseHandle(process_info.stdout_child_process_read);
      CloseHandle(process_info.process);

      //Tear down our I/O completion port if necessary.
      iocp.release(completion_key);

      //Not necessary -- we didn't really launch the process.
      ////Let waiting callers know we're done.
      //process_info.exit_latch.countDown();

      throw new IllegalStateException("Unable to initialize new process");
    }

    //ConnectNamedPipe(process_info.stdout_child_process_read, null);

    iocp.postMessage(completion_key, STATE_CONNECTED);
    //read(iocp, context, completion_key, null, null, stdout_child_process_read, 1);

    return process_info;
  }

  private static boolean read(final IOCompletionPort<Context> iocp, final Context context, final Sequencing sequencing, final Pointer completion_key, final Object overlapped_pool, final Object memory_pool, final HANDLE pipe, final int state) {
//      final OVERLAPPED_WITH_BUFFER_AND_STATE o = overlapped_pool.requestInstance(
//        op,
//        memory_pool.requestSlice(),
//        memory_pool.getSliceSize()
//      );

    final OVERLAPPED_WITH_BUFFER_AND_STATE o = PinnableStruct.pin(new OVERLAPPED_WITH_BUFFER_AND_STATE());
    final IOCPBUFFER.ByReference io = PinnableStruct.pin(new IOCPBUFFER.ByReference());
    o.state = state;
    o.iocpBuffer = io;
    io.buffer = PinnableMemory.pin(1024);
    io.bufferSize = 1024;
    io.sequenceNumber = sequencing.sequenceNumber();

    if (!ReadFile(pipe, io.buffer, io.bufferSize, null, o) /*|| lp_bytes_read.getValue() == 0*/) {
      int err = Native.getLastError();
      switch(err) {
        case ERROR_IO_PENDING:
          //Normal operation. Nothing to see here.
          return true;
      }

      PinnableMemory.dispose(io.buffer);
      PinnableStruct.unpin(io);
      PinnableStruct.unpin(o);

      switch(err) {
        case ERROR_BROKEN_PIPE:
          //Append a special message indicating that we've reached
          //the end of the stream. Outstanding reads will then drain
          //off until we reach this message.
          sequencing.endOfStream();
          iocp.postMessage(completion_key, state);
          break;
      }
    }

    return false;
  }
}
