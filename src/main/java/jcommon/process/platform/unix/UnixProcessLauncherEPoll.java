package jcommon.process.platform.unix;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;

import static jcommon.process.api.JNAUtils.*;
import static jcommon.process.api.unix.C.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class UnixProcessLauncherEPoll {
  private static final byte
      READY_VALUE = (byte)0x0
  ;

  private static void check(int ret) {
    if (ret != 0)
      throw new IllegalStateException("Invalid return value from system call");
  }

  private static String sanitize_path(String path) {
    if (path != null)
      return path.replace("/", "//");
    else
      return null;
  }

  private static String sanitize_value(String value) {
    if (value != null)
      return value.replace("/", "//");
    else
      return null;
  }

  private static void write_path(final int fd, final String value) {
    write_line(fd, value, true);
  }

  private static void write_line(final int fd, final String value) {
    write_line(fd, value, false);
  }

  private static void write_byte(final int fd, final byte value) {
    final ByteBuffer bb = ByteBuffer
      .allocate(1)
      .order(ByteOrder.nativeOrder())
      .put(value);

    bb.flip();
    write(fd, bb, bb.limit());
    //fsync(write_fd);
  }

  private static void write_int(final int fd, final int value) {
    final ByteBuffer bb = ByteBuffer
      .allocate(4)
      .order(ByteOrder.nativeOrder())
      .putInt(value);

    bb.flip();
    write(fd, bb, bb.limit());
  }

  private static void write_line(final int fd, final String value, final boolean is_path) {
    final String sanitized = value != null ? (!is_path ? sanitize_value(value) : sanitize_path(value)) + '\0' : null;
    final ByteBuffer bb = sanitized != null ? Charset.forName("UTF-8").encode(sanitized) : ByteBuffer.allocate(0);
    final int len = bb.limit();

    //First write out the size of the message.
    write_int(fd, len);
    if (len > 0) {
      //If there's any content to the message, write that out afterwards.
      write(fd, bb, bb.limit());
    }
    //fsync(write_fd);
  }

  private static int read_bytes(final int fd, final ByteBuffer buffer) {
    if (!buffer.isDirect()) {
      throw new IllegalArgumentException("buffer must be a direct byte buffer");
    }

    //Assumes we have all of buffer to fill.
    buffer.position(0);
    final int bytes_read = read(fd, buffer, buffer.capacity());
    buffer.limit(bytes_read);
    return bytes_read;
  }

  private static void make_nonblocking(final int fd) {
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL, 0) | O_NONBLOCK);
  }

  public static IProcess launch(final boolean inherit_parent_environment, final IEnvironmentVariable[] environment_variables, final String[] args, final IProcessListener[] listeners) {
    //http://stackoverflow.com/questions/6606870/suspend-forked-process-at-startup
    //sigprocmask
    //call to sigsuspend(2), followed by a kill(pid, SIGXXX) from parent, where SIGXXX is the signal of choice. SIGCONT maybe?

    //https://github.com/rofl0r/butch/blob/master/butch.c
    //https://github.com/rofl0r/jobflow/blob/master/jobflow.c
    //http://smarden.org/runit/chpst.8.html

    //http://linux.die.net/man/2/execve

    //http://unix.superglobalmegacorp.com/Net2/newsrc/sys/stat.h.html
    //http://unix.superglobalmegacorp.com/Net2/newsrc/sys/fcntl.h.html

    //http://www.linuxprogrammingblog.com/all-about-linux-signals?page=show

//    posix_spawn_file_actions_t file_actions;
//    posix_spawn_file_actions_init(&file_actions);
//    posix_spawn_file_actions_addopen(&file_actions, 1, "newout", ...);
//    posix_spawn_file_actions_dup2(&file_actions, socket_pair[1], 0);
//    posix_spawn_file_actions_close(&file_actions, socket_pair[0]);
//    posix_spawn_file_actions_close(&file_actions, socket_pair[1]);
//    posix_spawn(..., &file_actions, ...);
//    posix_spawn_file_actions_destroy(&file_actions);

    //The first step is to create pipes that we'll use to redirect the child process
    //stdin/stdout/stderr. The right sides of the pipes need to be closed by the
    //appropriate process (child or parent) after we've forked.
    //
    //The parent communicates with the child across these pipes, writing out data the
    //child will use to ultimately run the requested command.
    //
    //What happens is that posix_spawn() is used to launch a small native executable
    //(spawn) with references to the pipes we've setup. After spawn is running, the
    //parent pushes across the working directory and then the arguments. The environment
    //variables are already pushed when spawn itself is launched.
    //
    //After that is complete, the parent (this) process allows callback handlers to run
    //to let them know the child process is running. What's really running is spawn which
    //hasn't yet called execve() to replace itself with the real child process. It's waiting
    //to hear from the parent process that this initial callback has completed. Once the
    //handlers are done, it sends a message to spawn letting it know it's time to begin
    //execution. At this point, the parent changes its side of the pipes to be non-blocking
    //and spawn calls execve() to begin executing the real child process. The child would
    //only see blocking i/o, but it can do whatever it would normally do at this point.
    //
    //One thing to note is that child processes, when they see redirected output (non-tty),
    //will switch from non-buffering i/o to line-buffered i/o. That's done at the libc level
    //and there's not much that we can do from here. Some processes might rely on this
    //behavior and even change their own behavior upon detecting this.

    final int[] pipe_child_stdin = new int[2]; //parent write, child read
    final int[] pipe_child_stdout = new int[2]; //parent read, child write
    final int[] pipe_child_stderr = new int[2]; //parent read, child write

    if (pipe(pipe_child_stdin) == -1) {
      throw new IllegalStateException("Unable to create a pipe"); //Too many files open? (errno EMFILE or ENFILE)
    }

    if (pipe(pipe_child_stdout) == -1) {
      throw new IllegalStateException("Unable to create a pipe"); //Too many files open? (errno EMFILE or ENFILE)
    }

    if (pipe(pipe_child_stderr) == -1) {
      throw new IllegalStateException("Unable to create a pipe"); //Too many files open? (errno EMFILE or ENFILE)
    }

    final int parent_write_to_child_stdin = pipe_child_stdin[1];
    final int parent_read_from_child_stdout = pipe_child_stdout[0];
    final int parent_read_from_child_stderr = pipe_child_stderr[0];

    final int child_read_from_stdin = pipe_child_stdin[0];
    final int child_write_to_stdout = pipe_child_stdout[1];
    final int child_write_to_stderr = pipe_child_stderr[1];

    final String str_child_read_from_stdin = Integer.toString(child_read_from_stdin);
    final String str_child_write_to_stdout = Integer.toString(child_write_to_stdout);
    final String str_child_write_to_stderr = Integer.toString(child_write_to_stderr);

    posix_spawnattr_t.ByReference attr = new posix_spawnattr_t.ByReference();
    check(posix_spawnattr_init(attr));
    check(posix_spawnattr_setflags(attr, POSIX_SPAWN_USEVFORK));

    posix_spawn_file_actions_t.ByReference file_actions = new posix_spawn_file_actions_t.ByReference();
    check(posix_spawn_file_actions_init(file_actions));
    posix_spawn_file_actions_addclose(file_actions, parent_write_to_child_stdin);
    posix_spawn_file_actions_addclose(file_actions, parent_read_from_child_stdout);
    posix_spawn_file_actions_addclose(file_actions, parent_read_from_child_stderr);

    //The process is started effectively suspended -- waiting for a special message to
    //be sent to it that we'll send later.

    IntByReference ptr_pid = new IntByReference();
    Pointer argv = createPointerToStringArray(false, "/path/to/spawn", str_child_read_from_stdin, str_child_write_to_stdout, str_child_write_to_stderr, null);
    boolean success = 0 == posix_spawnp(ptr_pid, "/home/sysadmin/work/jcommon/process/src/main/resources/native/unix/x86_64/bin/spawn", file_actions, attr, argv, null);

    //No matter what - even if posix_spawnp() did not complete successfully, we
    //still need to do some cleanup.

    //Cleanup the string array we created in memory.
    disposeStringArray(argv);

    //Cleanup memory used when calling posix_spawnp().
    posix_spawn_file_actions_destroy(file_actions);
    posix_spawnattr_destroy(attr);

    //Close out our end.
    close(child_read_from_stdin);
    close(child_write_to_stdout);
    close(child_write_to_stderr);

    //Now check and then cleanup if spawn didn't launch correctly.
    if (!success) {
      //If it failed, then we need to cleanup the parent pipes as well.
      close(parent_write_to_child_stdin);
      close(parent_read_from_child_stdout);
      close(parent_read_from_child_stderr);
      throw new IllegalStateException("Unable to run the spawn process.");
    }

    final int pid = ptr_pid.getValue();

    final String working_directory = "/";
    final String[] application_args = new String[] {
        "ls"
      , "-lah"
    };

    //Make the pipes non-blocking.
    make_nonblocking(parent_write_to_child_stdin);
    make_nonblocking(parent_read_from_child_stdout);
    make_nonblocking(parent_read_from_child_stderr);

    //Send the working directory.
    write_path(parent_write_to_child_stdin, working_directory);

    //Send an int indicating the # of args to expect.
    write_int(parent_write_to_child_stdin, application_args.length + 1);

    //Write out each argument.
    for(String a : application_args) {
      write_line(parent_write_to_child_stdin, a);
    }

    //Add an additional null value.
    write_line(parent_write_to_child_stdin, null);

    //Allow callbacks to do some processing before the child process really
    //begins to do work. This would allow the parent (this) process to kill
    //the child if it's deemed necessary at this point or do anything it wants
    //to.

//    //Do work...
//    try {
//      Thread.sleep(1000 * 5);
//    } catch (InterruptedException e) {
//    }

    //Allow the child process to run execve() and begin doing real work.
    write_byte(parent_write_to_child_stdin, READY_VALUE);

    //Should be a globally shared epoll descriptor...
    final int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    if (epoll_fd < 0) {
      //DOH!
      //Error out...
      //See http://linux.die.net/man/2/epoll_create1
    }

    //Use eventfd() and add to epoll in order to signal threads to exit.

    //http://www.gossamer-threads.com/lists/linux/kernel/1197050
    //https://banu.com/blog/2/how-to-use-epoll-a-complete-example-in-c/
    //http://stackoverflow.com/questions/5541054/how-to-correctly-read-data-when-using-epoll-wait
    //http://linux.die.net/man/2/epoll_wait

    final epoll_event.ByReference event = new epoll_event.ByReference();
    event.data.fd = parent_write_to_child_stdin;
    event.events = EPOLLIN | EPOLLET | EPOLLHUP | EPOLLONESHOT;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, parent_write_to_child_stdin, event);

    //Create a thread pool
    final AutoGrowCallbackExecutorService e = new AutoGrowCallbackExecutorService<Object>(
      2,
      Math.max(2, Runtime.getRuntime().availableProcessors()),
      null,

      new AutoGrowCallbackExecutorService.IGrowCallback<Object>() {
        @Override
        public AutoGrowCallbackExecutorService.IWorker create(final Object value) {
          return new AutoGrowCallbackExecutorService.IWorker() {
            @Override
            public void doWork() throws Throwable {
              System.out.println("CREATING THREAD");
              Thread.sleep(1000);
            }
          };
        }
      },
      new AutoGrowCallbackExecutorService.IShutdownCallback<Object>() {
        @Override
        public void shutdown(final Object value) {
        }
      }
    );

    System.out.println(e);

    try { Thread.sleep(1000 * 2); } catch (InterruptedException exc) { }

    e.growBy(1);
    System.out.println(e);

    try { Thread.sleep(1000 * 2); } catch (InterruptedException exc) { }

    e.growBy(10);
    System.out.println(e);

    try { Thread.sleep(1000 * 2); } catch (InterruptedException exc) { }

    e.shutdown();

    System.out.println(e);

    //After sending the last message, spawn will execvpe() into the child
    //process. From here on out, all i/o should be redirected to callbacks
    //and no more writing/reading should take place from this method, b/c it's
    //now the child running and not spawn.

    //Now we want to register stdin/stdout/stderr fd's with epoll.
    //...

    //What happens if the child writes to stdout/stderr, requests to read
    //from stdin, before we have the chance to register these fd's with
    //epoll?

    ByteBuffer read_buffer = ByteBuffer.allocateDirect(1024);
    int bytes_read = 0;
    String output;

    while((bytes_read = read_bytes(parent_read_from_child_stdout, read_buffer)) > 0) {
      output = Charset.forName("UTF-8").decode(read_buffer).toString();
      System.out.print(output);
    }

    IntByReference status = new IntByReference();
    waitpid(pid, status, 0);



    //try { Thread.sleep(30 * 1000); } catch(Throwable t) { }
    return null;
  }

  /**
   * Starts with a core number of threads but can grow to a given maximum.
   */
  private static class AutoGrowCallbackExecutorService<T extends Object> extends AbstractExecutorService {
    public static interface IWorker {
      void doWork() throws Throwable;
    }

    public static interface IGrowCallback<T extends Object> {
      IWorker create(T value);
    }

    public static interface IShrinkCallback<T extends Object> {
      void shrink(T value);
    }

    public static interface IShutdownCallback<T extends Object> {
      void shutdown(T value);
    }

    private static class ThreadInformation {
      private boolean please_stop = false;
      private final CountDownLatch stop = new CountDownLatch(1);
      private final CountDownLatch stopped = new CountDownLatch(1);
      public Thread thread;

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
    private final IGrowCallback<T> create_callback;
    private final IShutdownCallback<T> shutdown_callback;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean shutdown = false;
    private int pool_size = 0;
    private int core_size = 0;
    private HashSet<ThreadInformation> threads;

    public AutoGrowCallbackExecutorService(final int minimumPoolSize, final int maximumPoolSize, final T value, final IGrowCallback<T> createCallback, final IShutdownCallback<T> shutdownCallback) {
      if (minimumPoolSize > maximumPoolSize) {
        throw new IllegalArgumentException("minimumPoolSize must be less than or equal to the maximumPoolSize");
      }

      this.value = value;
      this.minimum_pool_size = minimumPoolSize;
      this.maximum_pool_size = maximumPoolSize;
      this.create_callback = createCallback;
      this.shutdown_callback = shutdownCallback;

      this.threads = new HashSet<ThreadInformation>(maximumPoolSize, 1.0f);

      growThreadPoolBy(minimumPoolSize);
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
      return pool_size;
    }

    public int getCoreSize() {
      return core_size;
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

    private void growThreadPoolBy(final int by) {
      if (by == 0)
        return;
      if (by < 0)
        throw new IllegalArgumentException("by must be greater than or equal to zero");

      int i = 0;
      lock.lock();

      try {
        final int size = Math.min(by, Math.max(0, maximum_pool_size - pool_size));

        for(; i < size; ++i) {
          final ThreadInformation ti = new ThreadInformation();
          final IWorker runner = create_callback.create(value);
          final Runnable runnable = new Runnable() {
            @Override
            public void run() {
              try {
                while(!ti.isStopRequested()) {
                  try {
                    runner.doWork();
                  } catch(Throwable t) {
                  }
                  ti.waitForStop();
                }
              } catch(Throwable t) {
                //Do nothing.
              } finally {
                ti.stopped();
              }
            }
          };
          final Thread thread = new Thread(runnable);
          ti.thread = thread;

          threads.add(ti);

          thread.setDaemon(false);
          thread.start();
        }
      } finally {
        pool_size += i;
        core_size += by;
        lock.unlock();
      }
    }

    public void growBy(int by) {
      growThreadPoolBy(by);
    }

    public void stopAll(boolean waitForThreadToStop) {
      lock.lock();
      try {
        for(ThreadInformation ti : threads) {
          ti.requestStop();
          if (waitForThreadToStop)
            ti.waitForStopped();
        }
      } catch(InterruptedException ie) {
        Thread.currentThread().interrupt();
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void shutdown() {
      stopAll(true);
    }

    @Override
    public List<Runnable> shutdownNow() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isShutdown() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isTerminated() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void execute(Runnable command) {
      //To change body of implemented methods use File | Settings | File Templates.
    }
  }
}
