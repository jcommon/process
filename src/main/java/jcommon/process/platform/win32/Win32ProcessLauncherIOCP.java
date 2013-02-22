/*
  Copyright (C) 2013 the original author or authors.

  See the LICENSE.txt file distributed with this work for additional
  information regarding copyright ownership.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

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

import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.platform.win32.Utils.*;

@SuppressWarnings("unused")
public class Win32ProcessLauncherIOCP {
  private static final IOCompletionPort<ProcessInformation> iocp = new IOCompletionPort<ProcessInformation>(new IOCompletionPort.IProcessor<ProcessInformation>() {
    @Override
    public void process(int state, Pointer buffer, int bufferSize, ProcessInformation processInformation, Pointer completionKey, Pointer pOverlapped, IntByReference pBytesTransferred, OverlappedPool overlappedPool, MemoryPool memoryPool, IOCompletionPort<ProcessInformation> iocp) throws Throwable {
      completion_thread(state, buffer, bufferSize, processInformation, completionKey, pOverlapped, pBytesTransferred, overlappedPool, memoryPool, iocp);
    }
  });

  public static final int
      OP_INITIATE_CONNECT    =  0
    , OP_STDOUT_CONNECT      =  1
    , OP_STDOUT_READ         =  2
    , OP_STDOUT_REQUEST_READ =  3
    , OP_STDOUT_CLOSE        =  4
    , OP_STDOUT_CLOSE_WAIT   =  5

    , OP_STDERR_CONNECT      =  6
    , OP_STDERR_READ         =  7
    , OP_STDERR_REQUEST_READ =  8
    , OP_STDERR_CLOSE        =  9
    , OP_STDERR_CLOSE_WAIT   = 10

    , OP_STDIN_CONNECT       = 11
    , OP_STDIN_WRITE         = 12
    , OP_STDIN_REQUEST_WRITE = 13
    , OP_STDIN_CLOSE         = 14
    , OP_STDIN_CLOSE_WAIT    = 15

    , OP_PARENT_DISCONNECT   = 16
    , OP_CHILD_DISCONNECT    = 17

    , OP_CONNECTED           = 18
    , OP_INITIATE_CLOSE      = 19
    , OP_CLOSED              = 20
    , OP_EXITTHREAD          = OVERLAPPED_WITH_BUFFER_AND_STATE.STATE_EXITTHREAD
  ;

  private static void completion_thread(final int state, final Pointer buffer, final int buffer_size, final ProcessInformation process_info, final Pointer completion_key, final Pointer ptr_overlapped, final IntByReference ptr_bytes_transferred, final OverlappedPool overlapped_pool, final MemoryPool memory_pool, final IOCompletionPort<ProcessInformation> iocp) throws Throwable {
    int bytes_transferred;

    //When we start up, we follow this chain of events:
    //  CreateProcess() -> OP_INITIATE_CONNECT -> OP_STDOUT_CONNECT -> OP_STDERR_CONNECT -> OP_CONNECTED -> OP_STDOUT_READ, OP_STDERR_READ

    switch(state) {
      case OP_INITIATE_CONNECT:
        //Next we connect to stdout.
        connect(iocp, process_info, completion_key, process_info.stdout_child_process_read, OP_INITIATE_CONNECT, OP_STDOUT_CONNECT);
        break;
      case OP_STDOUT_CONNECT:
        //Next we connect to stderr.
        connect(iocp, process_info, completion_key, process_info.stderr_child_process_read, OP_STDOUT_CONNECT, OP_STDERR_CONNECT);
        break;
      case OP_STDERR_CONNECT:
        //Transition to the connected state.
        iocp.postMessage(completion_key, OP_CONNECTED);
        break;
      case OP_CONNECTED:
        if (process_info.starting.compareAndSet(true, false)) {
          process_info.notifyStarted();

          //Now we start reading.
          read(iocp, process_info, completion_key, overlapped_pool, memory_pool, process_info.stdout_child_process_read, OP_STDOUT_READ);
          read(iocp, process_info, completion_key, overlapped_pool, memory_pool, process_info.stderr_child_process_read, OP_STDERR_READ);

          //Allow the main thread to begin executing.
          ResumeThread(process_info.main_thread);
          CloseHandle(process_info.main_thread);
        }
        break;
      case OP_STDOUT_READ:
        //Get the number of bytes transferred based on what the OS has told us. Be sure and bound
        //it to the buffer size to avoid a corrupted value causing us to read off the end of the buffer.
        bytes_transferred = Math.max(0, Math.min(ptr_bytes_transferred.getValue(), buffer_size));

        if (!GetOverlappedResult(process_info.stdout_child_process_read, ptr_overlapped, ptr_bytes_transferred, false)) {
//            err = GetLastError();
//
//            PinnableStruct.unpin(overlapped);
//            PinnableMemory.unpin(overlapped.buffer);
//
//            switch(err) {
//              case ERROR_HANDLE_EOF:
//              case ERROR_BROKEN_PIPE:
//                postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
//                continue;
//              case ERROR_INVALID_HANDLE:
//                continue;
//              default:
//                continue;
//            }
        }

//          System.out.println("BYTES LEFT: " + bytes_transferred + "/" + pBytesTransferred.getValue());

        if (bytes_transferred > 0) {
          process_info.notifyStdOut(buffer.getByteBuffer(0, bytes_transferred), bytes_transferred);
        }

        //Schedule our next read.

        if (!process_info.closing.get()) {
          read(iocp, process_info, completion_key, overlapped_pool, memory_pool, process_info.stdout_child_process_read, OP_STDOUT_READ);
        }
        break;

      case OP_STDOUT_REQUEST_READ:
        read(iocp, process_info, completion_key, overlapped_pool, memory_pool, process_info.stdout_child_process_read, OP_STDOUT_READ);
        break;

      case OP_STDERR_READ:
        //Get the number of bytes transferred based on what the OS has told us. Be sure and bound
        //it to the buffer size to avoid a corrupted value causing us to read off the end of the buffer.
        bytes_transferred = Math.max(0, Math.min(ptr_bytes_transferred.getValue(), buffer_size));

        if (!GetOverlappedResult(process_info.stderr_child_process_read, ptr_overlapped, ptr_bytes_transferred, false)) {
//            err = GetLastError();
//
//            PinnableStruct.unpin(overlapped);
//            PinnableMemory.unpin(overlapped.buffer);
//
//            switch(err) {
//              case ERROR_HANDLE_EOF:
//              case ERROR_BROKEN_PIPE:
//                postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
//                continue;
//              case ERROR_INVALID_HANDLE:
//                continue;
//              default:
//                continue;
//            }
        }

        if (bytes_transferred > 0) {
          process_info.notifyStdErr(buffer.getByteBuffer(0, bytes_transferred), bytes_transferred);
        }

        //Schedule our next read.
        if (!process_info.closing.get()) {
          read(iocp, process_info, completion_key, overlapped_pool, memory_pool, process_info.stderr_child_process_read, OP_STDERR_READ);
        }
        break;

      case OP_STDERR_REQUEST_READ:
        read(iocp, process_info, completion_key, overlapped_pool, memory_pool, process_info.stderr_child_process_read, OP_STDERR_READ);
        break;

      case OP_PARENT_DISCONNECT:
        //Shouldn't be a problem if this comes in after we've initiated a close.
        //This message will effectively just be ignored.

        if (process_info.closing.compareAndSet(false, true)) {
          //If this succeeds, then I was the first one to initiate the close.
          //Other threads/attempts should just continue.
          iocp.postMessage(completion_key, OP_INITIATE_CLOSE);
        }
        break;

      case OP_CHILD_DISCONNECT:
        //Shouldn't be a problem if this comes in after we've initiated a close.
        //This message will effectively just be ignored.

        if (process_info.closing.compareAndSet(false, true)) {
          //If this succeeds, then I was the first one to initiate the close.
          //Other threads/attempts should just continue.
          iocp.postMessage(completion_key, OP_INITIATE_CLOSE);
        }
        break;

      case OP_INITIATE_CLOSE:
        //At this point, this thread should be the only one processing the close operation.
        //Any other attempts would have been funnelled through the AtomicBoolean on
        //process_info.closing and would not be allowed to post this message if it has
        //already done so.

        UnregisterWait(process_info.process_exit_wait);

        iocp.postMessage(completion_key, OP_STDIN_CLOSE);
        break;

      case OP_STDIN_CLOSE:
        CloseHandle(process_info.stdin_child_process_write);
        iocp.postMessage(completion_key, OP_STDERR_CLOSE);
        break;

      case OP_STDERR_CLOSE:
        CloseHandle(process_info.stderr_child_process_read);
        iocp.postMessage(completion_key, OP_STDOUT_CLOSE);
        break;

      case OP_STDOUT_CLOSE:
        CloseHandle(process_info.stdout_child_process_read);
        iocp.postMessage(completion_key, OP_CLOSED);
        break;

      case OP_CLOSED:
        //By the time we get here, the process should have already exited.
        //But just in case it hasn't, we'll wait around for it.
        WaitForSingleObject(process_info.process, INFINITE);

        final IntByReference exit_code = new IntByReference();
        GetExitCodeProcess(process_info.process, exit_code);

        CloseHandle(process_info.process);

        process_info.exit_value.set(exit_code.getValue());

        process_info.notifyStopped(exit_code.getValue());

        //closeProcess(process_info);
        iocp.release(completion_key);

        process_info.exit_latch.countDown();
        break;
      default:
        //System.err.println("\n************** PID: " + process_info.pid + " UNKNOWN STATE: " + OVERLAPPEDEX.nameForState(overlapped.state) + "\n");
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
    startup_info.hStdInput        = stdin_child_process_read;
    startup_info.hStdOutput       = stdout_child_process_write;
    startup_info.hStdError        = stderr_child_process_write;
//    startup_info.hStdInput        = GetStdHandle(STD_INPUT_HANDLE);

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

    final Pointer completion_key = proc_info.hProcess.getPointer();

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
                  iocp.postMessage(completion_key, OP_CHILD_DISCONNECT);
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

    if (!iocp.associateHandles(completion_key, process_info, process_info.stdout_child_process_read, process_info.stderr_child_process_read)) {
      //Ensure we don't get the callback.
      UnregisterWait(process_info.process_exit_wait);

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

    //Initiate the connection process.
    iocp.postMessage(completion_key, OP_INITIATE_CONNECT);

    return process_info;
  }

  private static boolean hasOverlappedIoCompleted(final OVERLAPPED_WITH_BUFFER_AND_STATE o) {
    return o.Internal.intValue() != STATUS_PENDING;
  }

  private static boolean connect(final IOCompletionPort<ProcessInformation> iocp, final ProcessInformation process_info, final Pointer completion_key, final HANDLE pipe, final int current_state, final int next_state) {
    attempt: {
      //final OVERLAPPEDEX o = overlapped_pool.requestInstance(state);

      //When using the overlapped version...
      //What should we do if this returns true? Unpin the overlapped instance and then do nothing?
      //Some discussion on MSDN indicates that even if this returns true, the completion packet should have been
      //posted.

      //Do not use the overlapped version as recommended by MSDN.
      if (!ConnectNamedPipe(pipe, null)) {
        int err = GetLastError();
        switch(err) {
          case ERROR_IO_PENDING:
          case ERROR_PIPE_LISTENING:
            //Normal operation. Nothing to see here.
            return true;
        }

        ////For abnormal completion, clean up pinned instances and re-evaluate
        ////the error to determine the next course of action.
        //PinnableStruct.unpin(o);

        //System.out.println("PID " + process_info.getPID() + " CONNECT ERROR " + err + "[" + Thread.currentThread().getName() + "]");

        switch(err) {
          case ERROR_PIPE_CONNECTED:
            //The other side is already connected. So send out a new message.
            iocp.postMessage(completion_key, next_state);
            return true;
          case ERROR_NO_DATA: //It ended before it even started.
          case ERROR_OPERATION_ABORTED:
          case ERROR_BROKEN_PIPE:
            iocp.postMessage(completion_key, OP_CHILD_DISCONNECT);
            return true;
          case ERROR_PROC_NOT_FOUND:
            iocp.postMessage(completion_key, current_state);
            return true;
          case ERROR_SUCCESS:
            iocp.postMessage(completion_key, current_state);
            return true;
          case ERROR_INVALID_HANDLE:
            throw new IllegalStateException("Invalid handle when connecting to a pipe");
          default:
            System.err.println("\n************** PID: " + process_info.pid + " ERROR: UNKNOWN (connect():" + err + ")\n");
            iocp.postMessage(completion_key, current_state);
            return true;
        }
      } else {
        iocp.postMessage(completion_key, next_state);
        return true;
      }
    }
  }

  private static boolean read(final IOCompletionPort<ProcessInformation> iocp, final ProcessInformation process_info, final Pointer completion_key, final OverlappedPool overlapped_pool, final MemoryPool memory_pool, final HANDLE pipe, final int op) {
    attempt: {
      final OVERLAPPED_WITH_BUFFER_AND_STATE o = overlapped_pool.requestInstance(
        op,
        memory_pool.requestSlice(),
        memory_pool.getSliceSize()
      );

      if (!ReadFile(pipe, o.buffer, o.bufferSize, null, o) /*|| lp_bytes_read.getValue() == 0*/) {
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

        //System.out.println("PID " + process_info.getPID() + " READ ERROR " + err + "[" + Thread.currentThread().getName() + "]");

        switch(err) {
          case ERROR_PIPE_CONNECTED:
            //The other side is already connected. So send out a new message.
            iocp.postMessage(completion_key, op);
            return true;
          case ERROR_OPERATION_ABORTED:
          case ERROR_BROKEN_PIPE:
            iocp.postMessage(completion_key, OP_CHILD_DISCONNECT);
            return true;
          case ERROR_ACCESS_DENIED:
          case ERROR_INVALID_HANDLE:
            return true;
          case ERROR_NO_DATA:
          case ERROR_INVALID_USER_BUFFER:
          case ERROR_NOT_ENOUGH_MEMORY:
          case ERROR_PROC_NOT_FOUND:
          case ERROR_SUCCESS:
            iocp.postMessage(completion_key, op);
            return true;
            //break attempt;
          default:
            System.err.println("\n************** PID: " + process_info.pid + " ERROR: UNKNOWN (read():" + err + ")\n");
            iocp.postMessage(completion_key, op);
            return true;
            //break attempt;
        }
      }
    }
    return false;
  }
}
