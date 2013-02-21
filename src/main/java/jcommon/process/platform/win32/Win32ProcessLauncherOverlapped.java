package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.MemoryPool;
import jcommon.process.api.PinnableCallback;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableStruct;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
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
        overlapped_pool = new OverlappedPool(number_of_processors);
      }
      processes.put(process_info.process, process_info);
    }

    //Initiate the connection process.
    //postOpMessage(process_info, OVERLAPPEDEX.OP_INITIATE_CONNECT);

    process_info.notifyStarted();

    ResumeThread(process_info.main_thread);

    ConnectNamedPipe(process_info.stdout_child_process_read, null);

    read(process_info, process_info.stdout_child_process_read, 0);

    WaitForSingleObject(proc_info.hProcess, INFINITE);
    CloseHandle(process_info.stdout_child_process_read);

    IntByReference exit_code = new IntByReference();
    GetExitCodeProcess(proc_info.hProcess, exit_code);
    process_info.notifyStopped(exit_code.getValue());
//    try {
//      Thread.sleep(1000 * 10);
//    } catch (InterruptedException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    }

    return process_info;
  }

  private static boolean read(final ProcessInformation process_info, final HANDLE pipe, final int op) {
    attempt: {
//      final OVERLAPPEDEX o = overlapped_pool.requestInstance(
//        op,
//        memory_pool.requestSlice(),
//        memory_pool.getSliceSize()
//      );

      OVERLAPPEDEX o = new OVERLAPPEDEX();
      o.buffer = memory_pool.requestSlice();
      o.bufferSize = memory_pool.getSliceSize();
      o.hEvent = CreateEvent(null, true, true, null);

      if (!ReadFileEx(pipe, o.buffer, o.bufferSize, o, PinnableCallback.pin(new OVERLAPPED_COMPLETION_ROUTINE() {
        @Override
        public void FileIOCompletionRoutine(DWORD dwErrorCode, DWORD dwNumberOfBytesTransfered, OVERLAPPED lpOverlapped) {
          OVERLAPPEDEX o = new OVERLAPPEDEX();
          o.reuse(lpOverlapped.getPointer());

          ByteBuffer bb = o.buffer.getByteBuffer(0, dwNumberOfBytesTransfered.longValue());

          process_info.notifyStdOut(bb, dwNumberOfBytesTransfered.intValue());

          PinnableMemory.unpin(o.buffer);

          PinnableCallback.unpin(this);
        }
      })) /*|| lp_bytes_read.getValue() == 0*/) {
        int err = GetLastError();
        switch(err) {
          case ERROR_IO_PENDING:
            //Normal operation. Nothing to see here.
            return true;
        }

        //For abnormal completion, clean up pinned instances and re-evaluate
        //the error to determine the next course of action.
        PinnableStruct.unpin(o);
        PinnableMemory.unpin(o.buffer);
      } else {
        System.err.println("**** READFILE() COMPLETED SYNCHRONOUSLY: " + OVERLAPPEDEX.nameForOp(op));
        IntByReference ibr = new IntByReference();
        GetOverlappedResult(pipe, o, ibr, true);

        ByteBuffer bb = o.buffer.getByteBuffer(0, ibr.getValue());

        System.out.println("bb: " + Charset.defaultCharset().decode(bb));

        process_info.notifyStdOut(bb, ibr.getValue());

        PinnableMemory.unpin(o.buffer);
        break attempt;
      }
    }
    return false;
  }
}
