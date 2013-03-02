package jcommon.process.platform.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jcommon.process.api.PinnableStruct;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.api.win32.Kernel32.*;

public class IOCompletionPort<TAssociation extends Object> implements Serializable {
  public static final int
      STATE_STOP = -1
  ;

  private static final int
      //Gets the total # of processors (cores, actually) on the system.
      NUMBER_OF_PROCESSORS = Math.max(1, Runtime.getRuntime().availableProcessors())
  ;

  private static final int
      //Fire up more threads than we'll typically need. Twice the # of logical processes
      //(2 x the total # of cores) has shown to be a good tradeoff for scheduling
      //and ensuring maximum CPU usage.
      NUMBER_OF_THREADS = Math.max(2, NUMBER_OF_PROCESSORS * 2)
  ;

  private static final int
      //Ensures a few extra threads are left in the pool if others are blocked in
      //a wait of any kind.
      IOCP_CONCURRENCY_VALUE = Math.max(2, (int)Math.ceil(NUMBER_OF_THREADS * 0.75))
  ;

  private Object memory_pool = null;
  private Object overlapped_pool = null;

  private HANDLE io_completion_port = INVALID_HANDLE_VALUE;
  private final Object io_completion_port_lock = new Object();
  private final AtomicInteger running_count = new AtomicInteger(0);
  private final AtomicInteger running_io_completion_port_thread_count = new AtomicInteger(0);
  private final ThreadGroup io_completion_port_thread_group = new ThreadGroup("external processes");
  private final CyclicBarrier io_completion_port_thread_pool_barrier_start = new CyclicBarrier(NUMBER_OF_THREADS + 1);
//  private final CyclicBarrier io_completion_port_thread_pool_barrier_stop = new CyclicBarrier(NUMBER_OF_THREADS + 1);
  private final IOCompletionPortThreadInformation[] io_completion_port_thread_pool = new IOCompletionPortThreadInformation[NUMBER_OF_THREADS];
  private final LinkedBlockingQueue<AssociationInformation<TAssociation>> release_queue = new LinkedBlockingQueue<AssociationInformation<TAssociation>>();
  private final Map<Pointer, AssociationInformation<TAssociation>> associations = new HashMap<Pointer, AssociationInformation<TAssociation>>(2);

  private final IProcessor<TAssociation> processor;

  public IOCompletionPort(final IProcessor<TAssociation> processor) {
    this.processor = processor;
  }

  public Object getLock() {
    return io_completion_port_lock;
  }

  public static interface IAssociateHandlesCallback {
    void callback(boolean success);
  }

  public static interface IProcessor<TAssociation extends Object> {
    void process(int state, int bytesTransferred, OVERLAPPED_WITH_BUFFER_AND_STATE ovl, TAssociation association, Pointer completionKey, Pointer pOverlapped, IntByReference pBytesTransferred, Object overlappedPool, Object memoryPool, IOCompletionPort<TAssociation> iocp) throws Throwable;
  }

  private static class AssociationInformation<TAssociation extends Object> {
    final Pointer completionKey;
    final TAssociation association;

    public AssociationInformation(Pointer completionKey, TAssociation association) {
      this.completionKey = completionKey;
      this.association = association;
    }
  }

  private static class IOCompletionPortThreadInformation<TAssociation extends Object> {
    public Thread thread;
    public boolean please_stop;
    public int id;

    private IOCompletionPort<TAssociation> iocp;

    public IOCompletionPortThreadInformation(IOCompletionPort<TAssociation> iocp, int id) {
      this.iocp = iocp;
      this.id = id;
      this.please_stop = false;
    }

    public void start() {
      thread.start();
    }

    public void stop() {
      please_stop = true;
      iocp.postThreadStop(id);
    }
  }

  private boolean initialize() {
    synchronized (io_completion_port_lock) {
      if (running_count.getAndIncrement() == 0 && io_completion_port == INVALID_HANDLE_VALUE) {
        //memory_pool = new MemoryPool(1024, NUMBER_OF_PROCESSORS, MemoryPool.INFINITE_SLICE_COUNT /* Should be max # of concurrent associations effectively since it won't give any more slices than that. */);
        //overlapped_pool = new OverlappedPool(NUMBER_OF_PROCESSORS);

        //Keep a few threads in the
        io_completion_port = CreateUnassociatedIoCompletionPort(IOCP_CONCURRENCY_VALUE);

        //Ensure everything was created successfully. If not, cleanup and get out of here.
        if (io_completion_port == null || io_completion_port == INVALID_HANDLE_VALUE) {
          //overlapped_pool.dispose();
          overlapped_pool = null;

          //memory_pool.dispose();
          memory_pool = null;

          running_count.decrementAndGet();
          return false;
        }

        //Create thread pool.
        running_io_completion_port_thread_count.set(0);
        io_completion_port_thread_pool_barrier_start.reset();
//        io_completion_port_thread_pool_barrier_stop.reset();
        for(int i = 0; i < io_completion_port_thread_pool.length; ++i) {
          final IOCompletionPortThreadInformation<TAssociation> ti = new IOCompletionPortThreadInformation<TAssociation>(this, i);
          final Runnable r = new Runnable() { @Override
                                              public void run() {
            try {
              io_completion_port_thread_pool_barrier_start.await();
              completion_thread(io_completion_port, ti);
            } catch(Throwable t) {
              t.printStackTrace();
            } finally {
//              try {
//                io_completion_port_thread_pool_barrier_stop.await();
//              } catch(Throwable t) {
//                t.printStackTrace();
//              }
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

  public boolean associateHandles(final Pointer completionKey, final TAssociation association, final HANDLE...handles) {
    return associateHandles(null, completionKey, association, handles);
  }

  public boolean associateHandles(final IAssociateHandlesCallback callback, final Pointer completionKey, final TAssociation association, final HANDLE...handles) {
    synchronized (io_completion_port_lock) {
      initialize();

      boolean success = true;
      for(HANDLE handle : handles) {
        success = success && AssociateHandleWithIoCompletionPort(io_completion_port, handle, completionKey);
      }

      if (success) {
        associations.put(completionKey, new AssociationInformation<TAssociation>(completionKey, association));
      }

      if (callback != null) {
        callback.callback(success);
      }

      return success;
    }
  }

  private void completion_thread(final HANDLE completion_port, final IOCompletionPortThreadInformation ti) throws Throwable {
    final IntByReference pBytesTransferred = new IntByReference();
    final PointerByReference ppOverlapped = new PointerByReference();
    final IntByReference pCompletionKey = new IntByReference();
    OVERLAPPED_WITH_BUFFER_AND_STATE overlapped;
    Pointer pOverlapped;
    int completion_key;
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
        err = Native.getLastError();
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
        }
      }

      //Retrieve data from the event.
      //
      //We attempt to reuse existing references in order to avoid having to allocate
      //more objects than is necessary to process this event.
      if ((pOverlapped = ppOverlapped.getValue()) == null || pOverlapped == Pointer.NULL) {
        continue;
      }

      completion_key = pCompletionKey.getValue();

      //Always unpin the overlapped instance.
      overlapped = PinnableStruct.unpin(pOverlapped); //new OVERLAPPED_WITH_BUFFER_AND_STATE(pOverlapped);

      if (overlapped == null) {
        throw new IllegalStateException(OVERLAPPED_WITH_BUFFER_AND_STATE.class.getName() + " must always be pinned. Please use " + PinnableStruct.class.getName() + ".pin()");
      }

      //Look for request to stop this thread. If we truly want to stop then
      //please_stop should have been set before posting this message.
      if (overlapped.state == STATE_STOP) {
        //If this is a stop message, then we expect the completion key to
        //actually be the id of the thread we want to stop. If this isn't that
        //thread, then please forward it along.
        if (completion_key != ti.id) {
          postThreadStop(completion_key);
        }

        //Loop back around.
        continue;
      }

      final Pointer completionKey = Pointer.createConstant(completion_key);

      //If, for some unknown reason, we are processing an event for a port we haven't seen before,
      //then go ahead and ignore it.
      final AssociationInformation<TAssociation> association_info = associations.get(completionKey);

      try {
        processor.process(overlapped.state, pBytesTransferred.getValue(), overlapped, association_info.association, completionKey, pOverlapped, pBytesTransferred, overlapped_pool, memory_pool, this);
      } catch(Throwable ignored) {
        //t.printStackTrace();
      }
    }
  }

  public void postMessage(final Pointer completion_key, final int state) {
    final OVERLAPPED_WITH_BUFFER_AND_STATE o = PinnableStruct.pin(new OVERLAPPED_WITH_BUFFER_AND_STATE());
    o.state = state;
    PostQueuedCompletionStatus(io_completion_port, 0, completion_key, o);
  }

  private void postThreadStop(int thread_id) {
    //Post message to thread asking him to exit.
    final OVERLAPPED_WITH_BUFFER_AND_STATE o = PinnableStruct.pin(new OVERLAPPED_WITH_BUFFER_AND_STATE());
    o.state = STATE_STOP;
    PostQueuedCompletionStatus(io_completion_port, 0, thread_id, o);
  }

  public void release(final Pointer completionKey) {
    synchronized (io_completion_port_lock) {
      if (completionKey != null) {
        associations.remove(completionKey);
      }

      if (io_completion_port != null && running_count.decrementAndGet() == 0) {
        for(int i = 0; i < io_completion_port_thread_pool.length; ++i) {
          io_completion_port_thread_pool[i].stop();
        }

        //Wait for the threads to stop.
        //try { io_completion_port_thread_pool_barrier_stop.await(); } catch(Throwable t) { }

        //Closing this could result in an ERROR_ABANDONED_WAIT_0 on threads' GetQueuedCompletionStatus() call.
        CloseHandle(io_completion_port);
        io_completion_port = INVALID_HANDLE_VALUE;

        //memory_pool.dispose();
        memory_pool = null;

        //overlapped_pool.dispose();
        overlapped_pool = null;
      }
    }
  }
}
