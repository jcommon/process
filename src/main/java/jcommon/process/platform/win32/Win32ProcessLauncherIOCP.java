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
import com.sun.jna.ptr.PointerByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.MemoryPool;
import jcommon.process.api.PinnableCallback;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableStruct;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.platform.win32.Utils.CreateOverlappedPipe;
import static jcommon.process.platform.win32.Utils.formulateSanitizedCommandLine;

@SuppressWarnings("unused")
public class Win32ProcessLauncherIOCP {


  private static MemoryPool memory_pool = null;
  private static OverlappedPool overlapped_pool = null;

  private static HANDLE io_completion_port = INVALID_HANDLE_VALUE;

  private static final Object io_completion_port_lock = new Object();
  private static final AtomicInteger running_process_count = new AtomicInteger(0);
  private static final AtomicInteger running_io_completion_port_thread_count = new AtomicInteger(0);
  private static final ThreadGroup io_completion_port_thread_group = new ThreadGroup("external processes");
  private static final int number_of_processors = Runtime.getRuntime().availableProcessors();
  private static final CyclicBarrier io_completion_port_thread_pool_barrier_start = new CyclicBarrier(number_of_processors + 1);
  private static final CyclicBarrier io_completion_port_thread_pool_barrier_stop = new CyclicBarrier(number_of_processors + 1);
  private static final IOCompletionPortThreadInformation[] io_completion_port_thread_pool = new IOCompletionPortThreadInformation[number_of_processors];
  private static final LinkedBlockingQueue<ProcessInformation> process_dispose_queue = new LinkedBlockingQueue<ProcessInformation>();
  private static final Map<HANDLE, ProcessInformation> processes = new HashMap<HANDLE, ProcessInformation>(2);

  private static class IOCompletionPortThreadInformation {
    public Thread thread;
    public boolean please_stop;
    public int id;

    public IOCompletionPortThreadInformation(int id) {
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

  private static boolean initializeIOCompletionPort() {
    synchronized (io_completion_port_lock) {
      if (running_process_count.getAndIncrement() == 0 && io_completion_port == INVALID_HANDLE_VALUE) {
        memory_pool = new MemoryPool(1024, number_of_processors, MemoryPool.INFINITE_SLICE_COUNT /* Should be max # of concurrent processes effectively since it won't give any more slices than that. */);
        overlapped_pool = new OverlappedPool(number_of_processors);
        io_completion_port = CreateUnassociatedIoCompletionPort(Runtime.getRuntime().availableProcessors());

        //Ensure everything was created successfully. If not, cleanup and get out of here.
        if (io_completion_port == null || io_completion_port == INVALID_HANDLE_VALUE) {
          overlapped_pool.dispose();
          overlapped_pool = null;

          memory_pool.dispose();
          memory_pool = null;

          running_process_count.decrementAndGet();
          return false;
        }

        //Create process reaper.
        final Thread reaper = new Thread(io_completion_port_thread_group, new Runnable() {
          @Override
          public void run() {
            try {
              ProcessInformation process_info;

              process_dispose_queue.clear();

              //Block waiting for instances to be placed in the queue.
              //See closeProcess().
              while((process_info = process_dispose_queue.take()) != ProcessInformation.PROCESS_INFORMATION_STOP_SENTINEL) {
                releaseProcess(process_info);
              }

            } catch(InterruptedException ie) {
              //Do nothing.
            } catch(Throwable t) {
              t.printStackTrace();
            }
          }
        }, "reaper");
        reaper.setDaemon(false);
        reaper.start();

        //Create thread pool.
        running_io_completion_port_thread_count.set(0);
        io_completion_port_thread_pool_barrier_start.reset();
        io_completion_port_thread_pool_barrier_stop.reset();
        for(int i = 0; i < io_completion_port_thread_pool.length; ++i) {
          final IOCompletionPortThreadInformation ti = new IOCompletionPortThreadInformation(i);
          final Runnable r = new Runnable() { @Override
                                              public void run() {
            try {
              io_completion_port_thread_pool_barrier_start.await();
              ioCompletionPortProcessor(io_completion_port, ti);
            } catch(Throwable t) {
              t.printStackTrace();
            } finally {
              try {
                io_completion_port_thread_pool_barrier_stop.await();
              } catch(Throwable t) {
                t.printStackTrace();
              }
            }
          } };
          ti.thread = new Thread(io_completion_port_thread_group, r, "thread-" + running_io_completion_port_thread_count.incrementAndGet());
          io_completion_port_thread_pool[i] = ti;
          io_completion_port_thread_pool[i].start();
        }

        //Wait for all threads to start up.
        try { io_completion_port_thread_pool_barrier_start.await(); } catch(Throwable ignored) { }
      }
      return true;
    }
  }

  private static boolean initializeProcess(final ProcessInformation process_info) {
    synchronized (io_completion_port_lock) {
      final Pointer ptr_process = process_info.process.getPointer();

      //stdin does synchronous writes (for now).
      final boolean stdout_associated = AssociateHandleWithIoCompletionPort(io_completion_port, process_info.stdout_child_process_read, ptr_process);
      final boolean stderr_associated = AssociateHandleWithIoCompletionPort(io_completion_port, process_info.stderr_child_process_read, ptr_process);
      final boolean success = stdout_associated && stderr_associated;

      if (success) {
        processes.put(process_info.process, process_info);
        return true;
      } else {
        return false;
      }
    }
  }

  private static void closeProcess(final ProcessInformation info) {
//    try {
//      //Ship the instance over to be processed by the reaper.
//      process_dispose_queue.put(info);
//    } catch(InterruptedException ignored) {
//    }
  }

  private static void releaseProcess(final ProcessInformation info) {
    synchronized (io_completion_port_lock) {
      if (info != null) {
        processes.remove(info.process);
      }

      if (io_completion_port != null && running_process_count.decrementAndGet() == 0) {
        for(int i = 0; i < io_completion_port_thread_pool.length; ++i) {
          io_completion_port_thread_pool[i].stop();
        }

        //Wait for the threads to stop.
        try { io_completion_port_thread_pool_barrier_stop.await(); } catch(Throwable t) { }

        //Ask the reaper to stop.
        try { process_dispose_queue.put(ProcessInformation.PROCESS_INFORMATION_STOP_SENTINEL); } catch (Throwable t) { }

        //Closing this could result in an ERROR_ABANDONED_WAIT_0 on threads' GetQueuedCompletionStatus() call.
        CloseHandle(io_completion_port);
        io_completion_port = INVALID_HANDLE_VALUE;

        memory_pool.dispose();
        memory_pool = null;

        overlapped_pool.dispose();
        overlapped_pool = null;
      }
    }
  }

  private static void postThreadStop(int thread_id) {
    //Post message to thread asking him to exit.
    PostQueuedCompletionStatus(io_completion_port, 0, thread_id, overlapped_pool.requestInstance(OVERLAPPEDEX.OP_EXITTHREAD));
  }

  private static void ioCompletionPortProcessor(final HANDLE completion_port, final IOCompletionPortThreadInformation ti) throws Throwable {
    final HANDLE process = new HANDLE();
    final OVERLAPPEDEX overlapped = new OVERLAPPEDEX();
    final IntByReference pBytesTransferred = new IntByReference();
    final PointerByReference ppOverlapped = new PointerByReference();
    final IntByReference pCompletionKey = new IntByReference();
    int bytes_transferred;
    Pointer pOverlapped;
    ProcessInformation process_info;
    int err;

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
        err = GetLastError();
        switch(err) {
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
//          case ERROR_BROKEN_PIPE:
//            //Other end of pipe has closed. So all outstanding operations at this point must close.
//            //Send a message out indicating that it's time to close.
//
//            process.reuse(Pointer.createConstant(pCompletionKey.getValue()));
//            process_info = processes.get(process);
//
//            overlapped.reuse(ppOverlapped.getValue());
//
//            if (process_info != null) {
//              process_info.decrementOps(overlapped.op);
//              postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
//            }
//
//            PinnableMemory.unpin(overlapped.buffer);
//            PinnableStruct.unpin(overlapped);
//            continue;
//          case ERROR_INVALID_HANDLE:
//            continue;
//          default:
//            continue;
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
        PinnableMemory.unpin(overlapped.buffer);
        PinnableStruct.unpin(overlapped);

        //The completion key will be an integer id indicating the thread that this message is
        //intended for. If the ids match, then get out of here, otherwise relay the message.
        final int thread_id = pCompletionKey.getValue();
        if (thread_id == ti.id) {
          continue;
        } else {
          postThreadStop(thread_id);
          break;
        }
      }

      process.reuse(Pointer.createConstant(pCompletionKey.getValue()));

      //If, for some unknown reason, we are processing an event for a port we haven't seen before,
      //then go ahead and ignore it.
      if ((process_info = processes.get(process)) == null) {
        PinnableMemory.unpin(overlapped.buffer);
        PinnableStruct.unpin(overlapped);
        continue;
      }

      process_info.decrementOps(overlapped.op);

      //When we start up, we follow this chain of events:
      //  CreateProcess() -> OP_INITIATE_CONNECT -> OP_STDOUT_CONNECT -> OP_STDERR_CONNECT -> OP_CONNECTED -> OP_STDOUT_READ, OP_STDERR_READ

      //System.err.println("PID: " + process_info.pid + ", OP: " + OVERLAPPEDEX.nameForOp(overlapped.op));
      //System.err.println("PID " + process_info.getPID() + ", OP: " + OVERLAPPEDEX.nameForOp(overlapped.op) + " " + process_info.outstandingOpsAsString());

      switch(overlapped.op) {
        case OVERLAPPEDEX.OP_INITIATE_CONNECT:
          //Next we connect to stdout.
          connect(process_info, process_info.stdout_child_process_read, OVERLAPPEDEX.OP_INITIATE_CONNECT, OVERLAPPEDEX.OP_STDOUT_CONNECT);
          break;
        case OVERLAPPEDEX.OP_STDOUT_CONNECT:
          //Next we connect to stderr.
          connect(process_info, process_info.stderr_child_process_read, OVERLAPPEDEX.OP_STDOUT_CONNECT, OVERLAPPEDEX.OP_STDERR_CONNECT);
          break;
        case OVERLAPPEDEX.OP_STDERR_CONNECT:
          //Transition to the connected state.
          postOpMessage(process_info, OVERLAPPEDEX.OP_CONNECTED);
          break;
        case OVERLAPPEDEX.OP_CONNECTED:
          if (process_info.starting.compareAndSet(true, false)) {
            process_info.notifyStarted();

            //Now we start reading.
            read(process_info, process_info.stdout_child_process_read, OVERLAPPEDEX.OP_STDOUT_READ);
            read(process_info, process_info.stderr_child_process_read, OVERLAPPEDEX.OP_STDERR_READ);

            //Allow the main thread to begin executing.
            ResumeThread(process_info.main_thread);
            CloseHandle(process_info.main_thread);
          }
          break;
        case OVERLAPPEDEX.OP_STDOUT_READ:
          //Get the number of bytes transferred based on what the OS has told us. Be sure and bound
          //it to the buffer size to avoid a corrupted value causing us to read off the end of the buffer.
          bytes_transferred = Math.max(0, Math.min(pBytesTransferred.getValue(), overlapped.bufferSize));

          if (!GetOverlappedResult(process_info.stdout_child_process_read, pOverlapped, pBytesTransferred, false)) {
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
            process_info.notifyStdOut(overlapped.buffer.getByteBuffer(0, bytes_transferred), bytes_transferred);
          }

          PinnableStruct.unpin(overlapped);
          PinnableMemory.unpin(overlapped.buffer);

          //Schedule our next read.

          if (!process_info.closing.get()) {
            read(process_info, process_info.stdout_child_process_read, OVERLAPPEDEX.OP_STDOUT_READ);
          }
          break;

        case OVERLAPPEDEX.OP_STDOUT_REQUEST_READ:
          PinnableStruct.unpin(overlapped);
          PinnableMemory.unpin(overlapped.buffer);

          read(process_info, process_info.stdout_child_process_read, OVERLAPPEDEX.OP_STDOUT_READ);
          break;

        case OVERLAPPEDEX.OP_STDERR_READ:
          //Get the number of bytes transferred based on what the OS has told us. Be sure and bound
          //it to the buffer size to avoid a corrupted value causing us to read off the end of the buffer.
          bytes_transferred = Math.max(0, Math.min(pBytesTransferred.getValue(), overlapped.bufferSize));

          if (!GetOverlappedResult(process_info.stderr_child_process_read, pOverlapped, pBytesTransferred, false)) {
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
            process_info.notifyStdErr(overlapped.buffer.getByteBuffer(0, bytes_transferred), bytes_transferred);
          }

          PinnableStruct.unpin(overlapped);
          PinnableMemory.unpin(overlapped.buffer);

          //Schedule our next read.
          if (!process_info.closing.get()) {
            read(process_info, process_info.stderr_child_process_read, OVERLAPPEDEX.OP_STDERR_READ);
          }
          break;

        case OVERLAPPEDEX.OP_STDERR_REQUEST_READ:
          PinnableStruct.unpin(overlapped);
          PinnableMemory.unpin(overlapped.buffer);

          read(process_info, process_info.stderr_child_process_read, OVERLAPPEDEX.OP_STDERR_READ);
          break;

        case OVERLAPPEDEX.OP_PARENT_DISCONNECT:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          //Shouldn't be a problem if this comes in after we've initiated a close.
          //This message will effectively just be ignored.

          if (process_info.closing.compareAndSet(false, true)) {
            //If this succeeds, then I was the first one to initiate the close.
            //Other threads/attempts should just continue.
            postOpMessage(process_info, OVERLAPPEDEX.OP_INITIATE_CLOSE);
          }
          break;

        case OVERLAPPEDEX.OP_CHILD_DISCONNECT:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          //Shouldn't be a problem if this comes in after we've initiated a close.
          //This message will effectively just be ignored.

          if (process_info.closing.compareAndSet(false, true)) {
            //If this succeeds, then I was the first one to initiate the close.
            //Other threads/attempts should just continue.
            postOpMessage(process_info, OVERLAPPEDEX.OP_INITIATE_CLOSE);
          }
          break;

        case OVERLAPPEDEX.OP_INITIATE_CLOSE:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          //At this point, this thread should be the only one processing the close operation.
          //Any other attempts would have been funnelled through the AtomicBoolean on
          //process_info.closing and would not be allowed to post this message if it has
          //already done so.

          UnregisterWait(process_info.process_exit_wait);

          postOpMessage(process_info, OVERLAPPEDEX.OP_STDIN_CLOSE);
          break;

        case OVERLAPPEDEX.OP_STDIN_CLOSE:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          CloseHandle(process_info.stdin_child_process_write);
          postOpMessage(process_info, OVERLAPPEDEX.OP_STDERR_CLOSE);
          break;

        case OVERLAPPEDEX.OP_STDERR_CLOSE:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          CloseHandle(process_info.stderr_child_process_read);
          postOpMessage(process_info, OVERLAPPEDEX.OP_STDOUT_CLOSE);
          break;

        case OVERLAPPEDEX.OP_STDOUT_CLOSE:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          CloseHandle(process_info.stdout_child_process_read);
          postOpMessage(process_info, OVERLAPPEDEX.OP_CLOSED);
          break;

        case OVERLAPPEDEX.OP_CLOSED:
          PinnableMemory.unpin(overlapped.buffer);
          PinnableStruct.unpin(overlapped);

          //By the time we get here, the process should have already exited.
          //But just in case it hasn't, we'll wait around for it.
          WaitForSingleObject(process_info.process, INFINITE);

          final IntByReference exit_code = new IntByReference();
          GetExitCodeProcess(process_info.process, exit_code);

          CloseHandle(process_info.process);

          process_info.exit_value.set(exit_code.getValue());

          process_info.notifyStopped(exit_code.getValue());

          //closeProcess(process_info);
          releaseProcess(process_info);

          process_info.exit_latch.countDown();
          break;
        default:
          //System.err.println("\n************** PID: " + process_info.pid + " UNKNOWN OP: " + OVERLAPPEDEX.nameForOp(overlapped.op) + "\n");
          break;
      }
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

    if (!initializeIOCompletionPort()) {
      CloseHandle(stdout_child_process_read);
      CloseHandle(stdout_child_process_write);
      CloseHandle(stderr_child_process_read);
      CloseHandle(stderr_child_process_write);
      CloseHandle(stdin_child_process_read);
      CloseHandle(stdin_child_process_write);
      throw new IllegalStateException("Unable to initialize I/O completion port");
    }

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
                  postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
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

    if (!initializeProcess(process_info)) {
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
      releaseProcess(process_info);

      //Not necessary -- we didn't really launch the process.
      ////Let waiting callers know we're done.
      //process_info.exit_latch.countDown();

      throw new IllegalStateException("Unable to initialize new process");
    }

    //Initiate the connection process.
    postOpMessage(process_info, OVERLAPPEDEX.OP_INITIATE_CONNECT);

    return process_info;
  }

  private static void postOpMessage(final ProcessInformation process_info, final int op) {
    process_info.incrementOps(op);
    PostQueuedCompletionStatus(io_completion_port, 0, process_info.process.getPointer(), overlapped_pool.requestInstance(op));
  }

  private static boolean hasOverlappedIoCompleted(final OVERLAPPEDEX o) {
    return o.Internal.intValue() != STATUS_PENDING;
  }

  private static void connect(final ProcessInformation process_info, final HANDLE pipe, final int current_state, final int next_state) {
    //for(;;) {
      if (connect_attempt(process_info, pipe, current_state, next_state)) {
        return;
      }
    //}
  }

  private static boolean connect_attempt(final ProcessInformation process_info, final HANDLE pipe, final int current_state, final int next_state) {
    if (process_info.outstanding_ops.contains(OVERLAPPEDEX.OP_INITIATE_CONNECT)) {
      System.err.println("DOH");
    }

    attempt: {
      //final OVERLAPPEDEX o = overlapped_pool.requestInstance(op);

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
            postOpMessage(process_info, next_state);
            return true;
          case ERROR_NO_DATA: //It ended before it even started.
          case ERROR_OPERATION_ABORTED:
          case ERROR_BROKEN_PIPE:
            postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
            return true;
          case ERROR_PROC_NOT_FOUND:
            postOpMessage(process_info, current_state);
            return true;
          case ERROR_SUCCESS:
            postOpMessage(process_info, current_state);
            return true;
          case ERROR_INVALID_HANDLE:
            throw new IllegalStateException("Invalid handle when connecting to a pipe");
          default:
            System.err.println("\n************** PID: " + process_info.pid + " ERROR: UNKNOWN (connect():" + err + ")\n");
            postOpMessage(process_info, current_state);
            return true;
        }
      } else {
        postOpMessage(process_info, next_state);
        return true;
      }
    }
  }

  private static void read(final ProcessInformation process_info, final HANDLE pipe, final int op) {
    //for(;;) {
      if (read_attempt(process_info, pipe, op)) {
        return;
      }
    //}
  }

  private static boolean read_attempt(final ProcessInformation process_info, final HANDLE pipe, final int op) {
    process_info.incrementOps(op);
    attempt: {
      final OVERLAPPEDEX o = overlapped_pool.requestInstance(
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
            postOpMessage(process_info, op);
            return true;
          case ERROR_OPERATION_ABORTED:
          case ERROR_BROKEN_PIPE:
            postOpMessage(process_info, OVERLAPPEDEX.OP_CHILD_DISCONNECT);
            return true;
          case ERROR_ACCESS_DENIED:
          case ERROR_INVALID_HANDLE:
            return true;
          case ERROR_NO_DATA:
          case ERROR_INVALID_USER_BUFFER:
          case ERROR_NOT_ENOUGH_MEMORY:
          case ERROR_PROC_NOT_FOUND:
          case ERROR_SUCCESS:
            postOpMessage(process_info, op);
            return true;
            //break attempt;
          default:
            System.err.println("\n************** PID: " + process_info.pid + " ERROR: UNKNOWN (read():" + err + ")\n");
            postOpMessage(process_info, op);
            return true;
            //break attempt;
        }
      }
    }
    return false;
  }
}
