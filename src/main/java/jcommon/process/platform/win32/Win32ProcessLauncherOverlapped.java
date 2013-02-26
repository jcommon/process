package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.MemoryPool;
import jcommon.process.api.PinnableCallback;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableStruct;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.platform.win32.Utils.*;

public class Win32ProcessLauncherOverlapped {
  private static final Object io_completion_port_lock = new Object();
  private static final Map<HANDLE, ProcessInformation> processes = new HashMap<HANDLE, ProcessInformation>(2);
  private static final AtomicInteger running_process_count = new AtomicInteger(0);
  private static final int number_of_processors = Runtime.getRuntime().availableProcessors();

  private static MemoryPool memory_pool = null;
  private static OverlappedPool overlapped_pool = null;

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

    final STARTUPINFO startup_info = new STARTUPINFO();
    final PROCESS_INFORMATION.ByReference proc_info = new PROCESS_INFORMATION.ByReference();

    startup_info.lpReserved       = null;
    startup_info.lpDesktop        = null;
    startup_info.lpTitle          = null; /* title in console window */
    startup_info.dwX              = null; /* x-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    startup_info.dwY              = null; /* y-coord offset in pixels, only used if STARTF_USEPOSITION is specified */
    startup_info.dwXSize          = null; /* width of window in pixels, only used if STARTF_USESIZE is specified */
    startup_info.dwYSize          = null; /* height of window in pixels, only used if STARTF_USESIZE is specified */
    startup_info.dwXCountChars    = null; /* screen buffer width in char columns, only used if STARTF_USECOUNTCHARS is specified */
    startup_info.dwYCountChars    = null; /* screen buffer height in char rows, only used if STARTF_USECOUNTCHARS is specified */
    startup_info.dwFillAttribute  = null; /* initial text and background colors for a console window, only used if STARTF_USEFILLATTRIBUTE is specified */
    startup_info.dwFlags         |= STARTF_USESTDHANDLES;
    startup_info.wShowWindow      = null;
    startup_info.cbReserved2      = null;
    startup_info.lpReserved2      = null;
    //startup_info.hStdInput        = stdin_child_process_read;
    startup_info.hStdOutput       = stdout_child_process_write;
    //startup_info.hStdError        = stderr_child_process_write;
    startup_info.hStdInput        = GetStdHandle(STD_INPUT_HANDLE);
    //startup_info.hStdOutput       = GetStdHandle(STD_OUTPUT_HANDLE);
    startup_info.hStdError        = GetStdHandle(STD_ERROR_HANDLE);

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
      throw new IllegalStateException("Unable to create a process with the following command line: " + command_line, t);
    }

    //Close out the child process' stdout write.
    CloseHandle(stdout_child_process_write);

    final HANDLE h_stdout_child_process_read = CreateEvent(null, true, false, null);

    //Associate our new process with the shared i/o completion port.
    //This is *deliberately* done after the process is created mainly so that we have
    //the process id and process handle.
    final ProcessInformation process_info = new ProcessInformation(
      proc_info.dwProcessId.intValue(),
      proc_info.hProcess,
      proc_info.hThread,
      stdout_child_process_read,
      null /*stderr_child_process_read*/,
      null /*stdin_child_process_write*/,
      inherit_parent_environment,
      environment_variables,
      args,
      listeners,
      new ProcessInformation.ICreateProcessExitCallback() {
        @Override
        public HANDLE createExitCallback(final ProcessInformation process_info) {
          final HANDLEByReference ptr_process_exit_wait = new HANDLEByReference();

          //Receive notification when the process exits.
          RegisterWaitForSingleObject(
              ptr_process_exit_wait,
              proc_info.hProcess,
              PinnableCallback.pin(new WAITORTIMERCALLBACK() {
                @Override
                public void WaitOrTimerCallback(Pointer lpParameter, boolean TimerOrWaitFired) {
                  PinnableCallback.unpin(this);

                  //Post a message to the iocp letting it know that the child has exited.
                  //postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
                  SetEvent(h_stdout_child_process_read);
                }
              }),
              null,
              INFINITE,
              WT_EXECUTEDEFAULT | WT_EXECUTEONLYONCE
          );

          return ptr_process_exit_wait.getValue();
        }
      }
    );

    synchronized (io_completion_port_lock) {
      if (running_process_count.getAndIncrement() == 0) {
        memory_pool = new MemoryPool(1024, number_of_processors, MemoryPool.INFINITE_SLICE_COUNT /* Should be max # of concurrent processes effectively since it won't give any more slices than that. */);
        overlapped_pool = new OverlappedPool(300);
      }
      processes.put(process_info.process, process_info);
    }

    //Initiate the connection process.
    //postOpMessage(process_info, OVERLAPPEDEX.OP_INITIATE_CONNECT);

    process_info.notifyStarted();

    ResumeThread(process_info.main_thread);

    if (!ConnectNamedPipe(stdout_child_process_read, null)) {
      int err = GetLastError();
      switch(err) {
        case ERROR_PIPE_CONNECTED:
        case ERROR_IO_PENDING:
        case ERROR_PIPE_LISTENING:
          //Normal operation. Nothing to see here.
          //System.err.println("OKAY");
          break;
        default:
          break;
      }
    }

//    int num_handles = 2;
//    Pointer[] handles = new Pointer[num_handles];
//    handles[0] = h_stdout_child_process_read.getPointer();
//    handles[1] = proc_info.hProcess.getPointer();
//
//    Memory mem = new Memory(Pointer.SIZE * num_handles);
//    for(int i = 0; i < handles.length; ++i) {
//      mem.setPointer(Pointer.SIZE * i, handles[i]);
//    }

//    System.err.println("HERE");
//
//    HANDLE2 h = new HANDLE2(h_stdout_child_process_read, proc_info.hProcess);
//
//    int dwWait;
//    while(true) {
//      dwWait = WaitForMultipleObjects(2, h, false, INFINITE) - WAIT_OBJECT_0;
//      //dwWait = WaitForMultipleObjects(1, mem.share(0L), false, INFINITE) - WAIT_OBJECT_0;
//      if (dwWait < 0 || dwWait > 2) {
//        break;
//      }
//    }

    new Thread(new Runnable() {
      @Override
      public void run() {
        do {
          read(process_info, h_stdout_child_process_read, stdout_child_process_read, 0);
        } while(WaitForSingleObjectEx(h_stdout_child_process_read, INFINITE, true) == WAIT_IO_COMPLETION);
        CloseHandle(h_stdout_child_process_read);
        CloseHandle(proc_info.hProcess);
        process_info.notifyStopped(0);
      }
    }).start();
//
//    while(WaitForSingleObjectEx(h_stdout_child_process_read, INFINITE, true) == WAIT_IO_COMPLETION)
//      ;
//
//    IntByReference exit_code = new IntByReference();
//    GetExitCodeProcess(proc_info.hProcess, exit_code);
//    process_info.notifyStopped(exit_code.getValue());

    return process_info;
  }

  private static boolean read(final ProcessInformation process_info, final HANDLE hEvent, final HANDLE pipe, final int state) {
    attempt: {
//      synchronized (memory_pool.getLock()) {
//        final OVERLAPPED_WITH_BUFFER_AND_STATE o = overlapped_pool.requestInstance(
//          state,
//          hEvent
//        );


      OVERLAPPED_WITH_BUFFER_AND_STATE o = new OVERLAPPED_WITH_BUFFER_AND_STATE();
      o.buffer = memory_pool.requestSlice();
      o.bufferSize = memory_pool.getSliceSize();
      o.hEvent = hEvent;

        final POVERLAPPED_COMPLETION_ROUTINE callback = PinnableCallback.pin(new POVERLAPPED_COMPLETION_ROUTINE() {
          @Override
          public void FileIOCompletionRoutine(int dwErrorCode, int dwNumberOfBytesTransferred, Pointer lpOverlapped) {
            OVERLAPPED_WITH_BUFFER_AND_STATE o = new OVERLAPPED_WITH_BUFFER_AND_STATE(lpOverlapped);

            //synchronized (memory_pool.getLock()) {
              ByteBuffer bb = o.buffer.getByteBuffer(0, dwNumberOfBytesTransferred);

              process_info.notifyStdOut(bb, dwNumberOfBytesTransferred);


              PinnableCallback.unpin(this);
              PinnableMemory.unpin(o.buffer);
            //  PinnableStruct.unpin(o);
            //}

            //read(process_info, hEvent, pipe, op);
          }
        });

        if (!ReadFileEx(pipe, o.buffer, o.bufferSize, o, callback) /*|| lp_bytes_read.getValue() == 0*/) {
          int err = GetLastError();
          switch(err) {
            case ERROR_IO_PENDING:
              //Normal operation. Nothing to see here.
              return true;
          }

          //For abnormal completion, clean up pinned instances and re-evaluate
          //the error to determine the next course of action.
          //PinnableStruct.unpin(o);
          PinnableMemory.unpin(o.buffer);
        } else {
          //callback.FileIOCompletionRoutine(GetLastError(), o.bufferSize, o.getPointer());
        }
      }
//    }
    return false;
  }
}
