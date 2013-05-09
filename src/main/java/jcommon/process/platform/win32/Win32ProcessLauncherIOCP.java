package jcommon.process.platform.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IEnvironmentVariableBlock;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableObject;
import jcommon.process.api.PinnableStruct;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.platform.win32.Utils.*;

public class Win32ProcessLauncherIOCP {
  private static final int
      STATE_CONNECTED             = 0
    , STATE_STDOUT_READ           = 1
    , STATE_STDOUT_READ_COMPLETED = 2
    , STATE_STDERR_READ           = 3
    , STATE_STDERR_READ_COMPLETED = 4
    , STATE_STDIN_WRITE           = 5
    , STATE_STDIN_WRITE_COMPLETED = 6
    , STATE_STDIN_WRITE_FAILED    = 7
    , STATE_DISCONNECT            = 8
  ;

  private static class Sequencing {
    public static final int
        MAX_SEQUENCE_NUMBER = 5001
    ;

    private final Map<Integer, IOCPBUFFER> bufferMap = new HashMap<Integer, IOCPBUFFER>(10, 1.0f);
    private boolean closed = false;
    private int currentSequenceNumber = 1;
    private int currentSequenceMarker = 1;

    public Sequencing() {
    }

    public Sequencing(int sequenceNumber, int sequenceMarker) {
      this.currentSequenceNumber = sequenceNumber;
      this.currentSequenceMarker = sequenceMarker;
    }

    public boolean isClosed() {
      return closed;
    }

    public int sequenceNumber() {
      return currentSequenceNumber;
    }

    public int incrementSequenceNumber() {
      return (currentSequenceNumber = (currentSequenceNumber + 1) % MAX_SEQUENCE_NUMBER);
    }

    @SuppressWarnings("unused")
    public int decrementSequenceNumber() {
      if (currentSequenceNumber != 1)
        return (currentSequenceNumber = (currentSequenceNumber - 1) % MAX_SEQUENCE_NUMBER);
      else
        return (currentSequenceNumber = MAX_SEQUENCE_NUMBER);
    }

    private int incrementMarker() {
      return (currentSequenceMarker = (currentSequenceMarker + 1) % MAX_SEQUENCE_NUMBER);
    }

    public IOCPBUFFER nextBuffer(IOCPBUFFER iocpBuffer) {
      return nextBuffer(iocpBuffer, true);
    }

    public IOCPBUFFER nextBuffer(IOCPBUFFER iocpBuffer, boolean incrementSequenceNumber) {
      //if (closed) {
      //  return createEndOfStreamMarker();
      //}

      IOCPBUFFER retBuff;

      if (iocpBuffer != null) {
        if (incrementSequenceNumber) {
          incrementSequenceNumber();
        }

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

    private IOCPBUFFER createEndOfStreamMarker(boolean incrementSequenceNumber) {
      final IOCPBUFFER buff = new IOCPBUFFER();
      buff.buffer = null;
      buff.bufferSize = 0;
      buff.sequenceNumber = incrementSequenceNumber ? incrementSequenceNumber() : sequenceNumber();
      buff.markAsEndOfStream();
      return buff;
    }

    public void endOfStream() {
      endOfStream(true, true);
    }

    public void endOfStream(boolean incrementSequenceNumber, boolean incrementMarker) {
      //Append a special marker buffer to the end of the stream.
      final IOCPBUFFER buff = createEndOfStreamMarker(incrementSequenceNumber);
      bufferMap.put(buff.sequenceNumber, buff);
      if (incrementMarker) {
        incrementMarker();
      }
    }
  }

  private static class Context implements ProcessInformation.IWriteCallback {
    public final ProcessInformation process_info;
    public final Sequencing stdout = new Sequencing();
    public final Sequencing stderr = new Sequencing();
    public final Sequencing stdin = new Sequencing(0, 1);
    public final Deque<Tag> pending_start_writes = new LinkedList<Tag>();
    public final AtomicBoolean started = new AtomicBoolean(false);
    public final AtomicBoolean closed = new AtomicBoolean(false);
    public final Pointer completion_key;

    public Context(final Pointer completion_key, final int pid, final HANDLE process, final HANDLE main_thread, final HANDLE stdout_child_process_read, final HANDLE stderr_child_process_read, final HANDLE stdin_child_process_write, final boolean inherit_parent_environment, final IEnvironmentVariableBlock environment_variables, final String[] command_line, final IProcessListener[] listeners) {
      this.completion_key = completion_key;
      this.process_info = new ProcessInformation(
        pid,
        process,
        main_thread,
        stdout_child_process_read,
        stderr_child_process_read,
        stdin_child_process_write,
        inherit_parent_environment,
        environment_variables,
        command_line,
        listeners,
        this
      );
    }

    @Override
    public boolean write(ByteBuffer bb, Object attachment) {
      synchronized (stdin) {
        if (!started.get()) {
          pending_start_writes.offer(new Tag(bb, attachment));
          return true;
        }

        if (!process_info.running.get()) {
          return false;
        }

        return Win32ProcessLauncherIOCP.write(iocp, this, stdin, completion_key, iocp.getOverlappedPool(), iocp.getMemoryPool(), process_info.stdin_child_process_write, STATE_STDIN_WRITE_COMPLETED, new Tag(bb, attachment));
      }
    }
  }

  private static final IOCompletionPort<Context> iocp = new IOCompletionPort<Context>(new IOCompletionPort.IProcessor<Context>() {
    @Override
    public void process(int state, int bytesTransferred, OVERLAPPED_WITH_BUFFER_AND_STATE ovl, Context context, Pointer completionKey, Pointer pOverlapped, IntByReference pBytesTransferred, Object overlappedPool, Object memoryPool, IOCompletionPort<Context> iocp) throws Throwable {
      completion_thread(state, bytesTransferred, ovl, context, completionKey, pOverlapped, pBytesTransferred, overlappedPool, memoryPool, iocp);
    }
  });

  private static void completion_thread(final int state, final int bytesTransferred, final OVERLAPPED_WITH_BUFFER_AND_STATE ovl, final Context context, final Pointer completion_key, final Pointer ptr_overlapped, final IntByReference ptr_bytes_transferred, final Object overlapped_pool, final Object memory_pool, final IOCompletionPort<Context> iocp) throws Throwable {
    Tag tag;
    IOCPBUFFER iocp_buffer;
    boolean end_of_stream = false;

    switch(state) {
      case STATE_CONNECTED:
        synchronized (context.stdin) {
          if (!context.process_info.running.compareAndSet(false, true))
            return;

          //Connected
          context.process_info.notifyStarted();
        }

        synchronized (context.stdout) {
          read(iocp, context, context.stdout, completion_key, overlapped_pool, memory_pool, context.process_info.stdout_child_process_read, STATE_STDOUT_READ_COMPLETED);
        }

        synchronized (context.stderr) {
          read(iocp, context, context.stderr, completion_key, overlapped_pool, memory_pool, context.process_info.stderr_child_process_read, STATE_STDERR_READ_COMPLETED);
        }

        ResumeThread(context.process_info.main_thread);
        CloseHandle(context.process_info.main_thread);

        synchronized (context.stdin) {
          //We want to ensure that the child process is up and running and able to process anything we're
          //going to write. This is so our writes don't block waiting on the child process to read from its
          //stdin and emptying its buffer. So our pending writes need to happen after we've started up.
          //This means that all writes done during notifyStarted() will be pending until this point.
          if (context.started.compareAndSet(false, true)) {
            Tag t;
            while((t = context.pending_start_writes.poll()) != null) {
              write(iocp, context, context.stdin, completion_key, overlapped_pool, memory_pool, context.process_info.stdin_child_process_write, STATE_STDIN_WRITE_COMPLETED, t);
            }
          }
        }
        break;

      case STATE_STDOUT_READ:
        synchronized (context.stdout) {
          read(iocp, context, context.stdout, completion_key, overlapped_pool, memory_pool, context.process_info.stdout_child_process_read, STATE_STDOUT_READ_COMPLETED);
        }
        break;

      case STATE_STDERR_READ:
        synchronized (context.stderr) {
          read(iocp, context, context.stderr, completion_key, overlapped_pool, memory_pool, context.process_info.stderr_child_process_read, STATE_STDERR_READ_COMPLETED);
        }
        break;

      case STATE_STDOUT_READ_COMPLETED:
        synchronized (context.stdout) {
          iocp_buffer = PinnableStruct.unpin(ovl.iocpBuffer);

          if (iocp_buffer != null)
            iocp_buffer.bytesTransferred(bytesTransferred);

          while(!end_of_stream && (iocp_buffer = context.stdout.nextBuffer(iocp_buffer)) !=  null) {
            //gc();

            end_of_stream = iocp_buffer.isMarkedAsEndOfStream();

            if (!end_of_stream) {
              ByteBuffer bb = iocp_buffer.buffer.getByteBuffer(0, iocp_buffer.bytesTransferred);
              context.process_info.notifyStdOut(bb, iocp_buffer.bytesTransferred);
            }

            PinnableMemory.dispose(iocp_buffer.buffer);

            iocp_buffer = null;
          }

          if (!end_of_stream) {
            iocp.postMessage(completion_key, STATE_STDOUT_READ);
          } else {
            iocp.postMessage(completion_key, STATE_DISCONNECT);
          }
        }
        break;

      case STATE_STDERR_READ_COMPLETED:
        synchronized (context.stderr) {
          iocp_buffer = PinnableStruct.unpin(ovl.iocpBuffer);

          if (iocp_buffer != null)
            iocp_buffer.bytesTransferred(bytesTransferred);

          while(!end_of_stream && (iocp_buffer = context.stderr.nextBuffer(iocp_buffer)) !=  null) {
            //gc();

            end_of_stream = iocp_buffer.isMarkedAsEndOfStream();

            if (!end_of_stream) {
              ByteBuffer bb = iocp_buffer.buffer.getByteBuffer(0, iocp_buffer.bytesTransferred);
              context.process_info.notifyStdErr(bb, iocp_buffer.bytesTransferred);
            }

            PinnableMemory.dispose(iocp_buffer.buffer);

            iocp_buffer = null;
          }

          if (!end_of_stream) {
            iocp.postMessage(completion_key, STATE_STDERR_READ);
          } else {
            iocp.postMessage(completion_key, STATE_DISCONNECT);
          }
        }
        break;

      case STATE_STDIN_WRITE:
        break;

      case STATE_STDIN_WRITE_FAILED:
        break;

      case STATE_STDIN_WRITE_COMPLETED:
        synchronized (context.stdin) {
          //Do NOT unpin here. We need its tag to stay resident along
          //with the struct. We'll unpin it later after processing it.
          iocp_buffer = ovl.iocpBuffer;

          if (iocp_buffer != null)
            iocp_buffer.bytesTransferred(bytesTransferred);

          while(!end_of_stream && (iocp_buffer = context.stdin.nextBuffer(iocp_buffer, false)) !=  null) {
            //gc();

            tag = PinnableStruct.untag(iocp_buffer);
            PinnableStruct.unpin(iocp_buffer);

            end_of_stream = iocp_buffer.isMarkedAsEndOfStream();

            if (!end_of_stream) {
              context.process_info.notifyStdIn(tag.buffer, iocp_buffer.bytesTransferred, tag.attachment);
            }

            PinnableMemory.dispose(iocp_buffer.buffer);

            iocp_buffer = null;
          }

          if (end_of_stream) {
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

        synchronized (context.stderr) {
          if (!context.stderr.isClosed()) {
            iocp.postMessage(completion_key, STATE_STDERR_READ);
            return;
          }
        }

        synchronized (context.stdin) {
          if (!context.stdin.isClosed()) {
            context.stdin.endOfStream(true, false);
            iocp.postMessage(completion_key, STATE_STDIN_WRITE_COMPLETED);
            return;
          }
        }

        synchronized (context.stdin) {
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

          context.process_info.running.set(false);

          context.process_info.exit_latch.countDown();
        }
        break;
    }
  }

  public static IEnvironmentVariableBlock requestParentEnvironmentVariableBlock() {
    return new ParentEnvironmentVariableBlock();
  }

  public static IProcess launch(final boolean inherit_parent_environment, final IEnvironmentVariableBlock environment_variables, final String[] args, final IProcessListener[] listeners) {
    final String command_line = formulateSanitizedCommandLine(args);
    final ByteBuffer env_vars = formulateEnvironmentVariableBlock(environment_variables);

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

    if (!CreateOverlappedPipe(ptr_stdin_child_process_read, ptr_stdin_child_process_write, saAttr, 1024, 0, FILE_FLAG_OVERLAPPED)) {
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
    //final Memory lpReserved2 = new Memory(4L);
    //lpReserved2.setInt(0L, 0);

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
    startup_info.cbReserved2      = new WORD(0); //new WORD(lpReserved2.size());
    startup_info.lpReserved2      = null; //lpReserved2;
    startup_info.hStdInput        = stdin_child_process_read;
    startup_info.hStdOutput       = stdout_child_process_write;
//    startup_info.hStdError        = GetStdHandle(STD_ERROR_HANDLE);
    startup_info.hStdError        = stderr_child_process_write;
//    startup_info.hStdInput        = GetStdHandle(STD_INPUT_HANDLE);

    //We create the process initially suspended while we setup a connection, inform
    //interested parties that the process has been created, etc. Once that's done,
    //we can start read operations.
    try {
      final boolean success = CreateProcess(null, command_line, null, null, true, new DWORD(NORMAL_PRIORITY_CLASS | CREATE_SUSPENDED | CREATE_UNICODE_ENVIRONMENT), env_vars /* environment block */, null /* current dir */, startup_info, proc_info) != 0;
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
    final Pointer completion_key = new CONTEXT().getPointer();

    //Associate our new process with the shared i/o completion port.
    //This is *deliberately* done after the process is created mainly so that we have
    //the process id and process handle.
    final Context context = new Context(
      completion_key,
      proc_info.dwProcessId.intValue(),
      proc_info.hProcess,
      proc_info.hThread,
      stdout_child_process_read,
      stderr_child_process_read,
      stdin_child_process_write,
      inherit_parent_environment,
      environment_variables,
      args,
      listeners
    );

    final ProcessInformation process_info = context.process_info;

    if (!iocp.associateHandles(completion_key, context, process_info.stdout_child_process_read, process_info.stderr_child_process_read, process_info.stdin_child_process_write)) {
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

  @SuppressWarnings("unused")
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
        case ERROR_INVALID_HANDLE:
        case ERROR_BROKEN_PIPE:
          //Append a special message indicating that we've reached
          //the end of the stream. Outstanding reads will then drain
          //off until we reach this message.
          sequencing.endOfStream();
          iocp.postMessage(completion_key, state);
          break;
        default:
          System.err.println("****ReadFile() PID " + context.process_info.getPID() + " ERROR " + err);
          break;
      }
    }

    return false;
  }

  private static boolean write(final IOCompletionPort<Context> iocp, final Context context, final Sequencing sequencing, final Pointer completion_key, final Object overlapped_pool, final Object memory_pool, final HANDLE pipe, final int state, final Tag t) {
    //memory from bytebuffer

    final OVERLAPPED_WITH_BUFFER_AND_STATE o = PinnableStruct.pin(new OVERLAPPED_WITH_BUFFER_AND_STATE());
    final IOCPBUFFER.ByReference io = PinnableStruct.pin(new IOCPBUFFER.ByReference(), t);
    o.state = state;
    o.iocpBuffer = io;
    io.buffer = null;
    io.bufferSize = 0;
    io.sequenceNumber = sequencing.incrementSequenceNumber();

    if (!WriteFile(pipe, t.buffer, t.buffer.remaining(), null, o)) {
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
        case ERROR_INVALID_HANDLE:
        case ERROR_BROKEN_PIPE:
          //In the case of a write, do nothing but return false;
          return false;
        default:
          System.err.println("****WriteFile() PID " + context.process_info.getPID() + " ERROR " + err);
          break;
      }
    }

    return true;
  }
}
