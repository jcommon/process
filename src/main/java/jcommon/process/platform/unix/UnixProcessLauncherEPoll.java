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
import java.nio.IntBuffer;
import java.nio.charset.Charset;

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
    //make_nonblocking(parent_read_from_child_stdout);
    //make_nonblocking(parent_read_from_child_stderr);

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
}
