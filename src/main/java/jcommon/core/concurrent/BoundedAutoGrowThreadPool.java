package jcommon.core.concurrent;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Starts with a core number of threads but can grow to a given maximum.
 */
public class BoundedAutoGrowThreadPool<T extends Object> {
  public static interface IWorker<T extends Object> {
    void doWork() throws Throwable;
  }

  public static interface IGrowCallback<T extends Object> {
    IWorker<T> growNewWorker(T value);
  }

  public static interface IShrinkCallback<T extends Object> {
    void shrink(T value, Thread thread, IWorker<T> worker);
  }

  public static interface IShutdownCallback<T extends Object> {
    void shutdown(T value);
  }

  private static class ThreadInformation<T extends Object> {
    private boolean please_stop = false;
    private final CountDownLatch stop = new CountDownLatch(1);
    private final CountDownLatch stopped = new CountDownLatch(1);
    public Thread thread;
    public IWorker<T> worker;

    public ThreadInformation() {
    }

    public boolean isStopRequested() {
      return (please_stop == true);
    }

    public void waitForStop() throws InterruptedException {
      stop.await();
    }

    public void requestStop() {
      please_stop = true;
      stop.countDown();
    }

    public void stopped() {
      stopped.countDown();
    }

    public void waitForStopped() throws InterruptedException {
      stopped.await();
    }

    public boolean waitForStopped(long time, TimeUnit unit) throws InterruptedException {
      return stopped.await(time, unit);
    }
  }

  private final T value;
  private final int minimum_pool_size;
  private final int maximum_pool_size;
  private final ThreadFactory thread_factory;
  private final IGrowCallback<T> grow_callback;
  private final IShrinkCallback<T> shrink_callback;
  private final IShutdownCallback<T> shutdown_callback;
  private final ReentrantLock lock = new ReentrantLock();
  private boolean shutdown = false;
  private int pool_size = 0;
  private int core_size = 0;
  private Map<Thread, ThreadInformation<T>> threads;
  private LinkedBlockingQueue<ThreadInformation<T>> shrinking;

  @SuppressWarnings("unchecked")
  public static BoundedAutoGrowThreadPool create(final int minimumPoolSize, final int maximumPoolSize, final IGrowCallback createCallback, final IShrinkCallback shrinkCallback) {
    return new BoundedAutoGrowThreadPool(minimumPoolSize, maximumPoolSize, null, createCallback, shrinkCallback);
  }

  public static <T extends Object> BoundedAutoGrowThreadPool<T> create(final int minimumPoolSize, final int maximumPoolSize, final T value, final IGrowCallback<T> createCallback, final IShrinkCallback<T> shrinkCallback) {
    return new BoundedAutoGrowThreadPool<T>(minimumPoolSize, maximumPoolSize, value, createCallback, shrinkCallback);
  }

  public BoundedAutoGrowThreadPool(final int minimumPoolSize, final int maximumPoolSize, final T value, final IGrowCallback<T> growCallback, final IShrinkCallback<T> shrinkCallback) {
    this(minimumPoolSize, maximumPoolSize, value, growCallback, shrinkCallback, null, Executors.defaultThreadFactory());
  }

  public BoundedAutoGrowThreadPool(final int minimumPoolSize, final int maximumPoolSize, final T value, final IGrowCallback<T> growCallback, final IShrinkCallback<T> shrinkCallback, final IShutdownCallback<T> shutdownCallback) {
    this(minimumPoolSize, maximumPoolSize, value, growCallback, shrinkCallback, shutdownCallback, Executors.defaultThreadFactory());
  }

  public BoundedAutoGrowThreadPool(final int minimumPoolSize, final int maximumPoolSize, final T value, final IGrowCallback<T> growCallback, final IShrinkCallback<T> shrinkCallback, final IShutdownCallback<T> shutdownCallback, final ThreadFactory threadFactory) {
    if (minimumPoolSize > maximumPoolSize) {
      throw new IllegalArgumentException("minimumPoolSize must be less than or equal to the maximumPoolSize");
    }

    if (growCallback == null) {
      throw new NullPointerException("growCallback cannot be empty");
    }

    if (shrinkCallback == null) {
      throw new NullPointerException("shrinkCallback cannot be empty");
    }

    if (threadFactory == null) {
      throw new NullPointerException("threadFactory cannot be empty");
    }

    this.value = value;
    this.minimum_pool_size = minimumPoolSize;
    this.maximum_pool_size = maximumPoolSize;
    this.grow_callback = growCallback;
    this.shrink_callback = shrinkCallback;
    this.shutdown_callback = shutdownCallback;

    this.thread_factory = threadFactory;
    this.threads = new ConcurrentHashMap<Thread, ThreadInformation<T>>(maximumPoolSize, 1.0f);

    this.shrinking = new LinkedBlockingQueue<ThreadInformation<T>>(maximumPoolSize <= 0 ? 1 : maximumPoolSize);

    growThreadPoolBy(minimumPoolSize, false, true);
  }

  public T getValue() {
    return value;
  }

  public int getMinimumPoolSize() {
    return minimum_pool_size;
  }

  public int getMaximumPoolSize() {
    return maximum_pool_size;
  }

  public int getPoolSize() {
    lock.lock();
    try {
      return pool_size;
    } finally {
      lock.unlock();
    }
  }

  public int getCoreSize() {
    lock.lock();
    try {
      return core_size;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(128);
    sb.append("min pool size:");
    sb.append(Integer.toString(getMinimumPoolSize()));
    sb.append("; max pool size: ");
    sb.append(Integer.toString(getMaximumPoolSize()));
    sb.append("; pool size: ");
    sb.append(Integer.toString(getPoolSize()));
    sb.append("; core size: ");
    sb.append(Integer.toString(getCoreSize()));
    return sb.toString();
  }

  private void growThreadPoolBy(final int by, final boolean grow_core_size, final boolean starting_up) {
    if (by == 0)
      return;
    if (by < 0)
      throw new IllegalArgumentException("by must be greater than or equal to zero");

    int i = 0;
    lock.lock();

    try {
      final int size = starting_up ? by : Math.max(0, Math.min(maximum_pool_size, core_size + by) - pool_size);
      final CyclicBarrier barrier = new CyclicBarrier(size + 1);

      for(; i < size; ++i) {
        final ThreadInformation<T> ti = new ThreadInformation<T>();
        final IWorker<T> worker = grow_callback.growNewWorker(value);
        final Runnable runnable = new Runnable() {
          @Override
          public void run() {
            try {
              barrier.await();

              while(!ti.isStopRequested()) {
                try {
                  worker.doWork();
                } catch(InterruptedException ie) {
                  Thread.currentThread().interrupt();
                } catch(Throwable t) {
                } finally {
                  //Indicate to the shrinking logic that we're now done.
                  //If the worker exits before we've called shrink, that
                  //should be alright -- but we'll be consuming more memory
                  //than we absolutely have to. The thread will stay resident
                  //though, until it's explicitly shrunk.
                  shrinking.offer(threads.remove(Thread.currentThread()));

                  //Wait for the shrinking methods to reap this thread.
                  ti.waitForStop();
                }
              }
            } catch(InterruptedException ie) {
              Thread.currentThread().interrupt();
            } catch(Throwable t) {
              //Do nothing.
            } finally {
              ti.stopped();
            }
          }
        };

        final Thread thread = thread_factory.newThread(runnable);
        ti.thread = thread;
        ti.worker = worker;

        threads.put(thread, ti);

        thread.setDaemon(false);
        thread.start();
      }
      try {
        barrier.await();
      } catch(InterruptedException ie) {
        Thread.currentThread().interrupt();
      } catch(BrokenBarrierException bbe) {
      }
    } finally {
      pool_size += i;
      core_size += grow_core_size ? by : 0;
      lock.unlock();
    }
  }

  private void shrinkThreadPoolBy(int by, boolean wait_for_thread_to_stop, boolean shutting_down) {
    if (by == 0)
      return;
    if (by < 0)
      throw new IllegalArgumentException("by must be greater than or equal to zero");

    int i = 0;
    lock.lock();

    try {
      final int size = shutting_down ? pool_size : Math.max(0, pool_size - Math.max(minimum_pool_size, core_size - by));

      for(; i < size; ++i) {
        //Should cause a thread to exit.
        shrink_callback.shrink(value, null, null);

        //The shrinking member will not be added to unless the worker.doWork()
        //method has exited. Unfortunately this does mean that it could take a
        //while between the request to shrink and the worker complying.
        //
        //For instance, we could ask to shrink, and then the worker doesn't
        //respond, perhaps sleeping for X amount of time, and then finally he
        //exits, which pushes to shrinking which allows this take() to finally
        //return something. Thus the "wait_for_thread_to_stop" parameter is
        //basically meaningless.
        //
        //The .take() method will block until shrinking has been added to.
        final ThreadInformation<T> ti = shrinking.take();

        //The thread is waiting
        ti.requestStop();
        if (wait_for_thread_to_stop) {
          ti.waitForStopped();
        }
      }
    } catch(InterruptedException ie) {
      Thread.currentThread().interrupt();
    } finally {
      pool_size = Math.max(0, pool_size - i);
      core_size = Math.max(0, core_size - by);

      if (shutting_down) {
        shrinking.clear();
        threads.clear();

        if (shutdown_callback != null) {
          shutdown_callback.shutdown(value);
        }
      }

      lock.unlock();
    }
  }

  public void grow() {
    growThreadPoolBy(1, true, false);
  }

  public void growBy(int by) {
    growThreadPoolBy(by, true, false);
  }

  public void shrink() {
    shrinkThreadPoolBy(1, true, false);
  }

  public void shrinkBy(int by) {
    shrinkThreadPoolBy(by, true, false);
  }

  public void stopAll(boolean waitForThreadsToStop) {
    lock.lock();
    try {
      shrinkThreadPoolBy(Math.max(core_size, pool_size), waitForThreadsToStop, true);
    } finally {
      lock.unlock();
    }
  }

  public void stopAll() {
    stopAll(true);
  }
}