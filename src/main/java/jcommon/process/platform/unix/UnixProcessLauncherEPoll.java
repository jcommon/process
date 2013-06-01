package jcommon.process.platform.unix;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;

import static jcommon.process.api.JNAUtils.*;
import static jcommon.process.api.unix.C.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class UnixProcessLauncherEPoll {
  private static void check(int ret) {
    if (ret != 0)
      throw new IllegalStateException("Invalid return value from system call");
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

    final int[] pipe_parent_to_child = new int[2]; //parent write, child read
    final int[] pipe_child_to_parent = new int[2]; //parent read, child write
    if (pipe(pipe_parent_to_child) == -1) {
      throw new IllegalStateException("Unable to create a pipe");
    }

    if (pipe(pipe_child_to_parent) == -1) {
      throw new IllegalStateException("Unable to create a pipe");
    }

    final int parent_read = pipe_child_to_parent[0];
    final int parent_write = pipe_parent_to_child[1];

    final int child_read = pipe_parent_to_child[0];
    final int child_write = pipe_child_to_parent[1];

    final String str_child_read = Integer.toString(child_read);
    final String str_child_write = Integer.toString(child_write);


    posix_spawnattr_t.ByReference attr = new posix_spawnattr_t.ByReference();
    check(posix_spawnattr_init(attr));
    check(posix_spawnattr_setflags(attr, POSIX_SPAWN_USEVFORK));

    posix_spawn_file_actions_t.ByReference file_actions = new posix_spawn_file_actions_t.ByReference();
    check(posix_spawn_file_actions_init(file_actions));
    //posix_spawn_file_actions_addclose(file_actions, parent_read);
    //posix_spawn_file_actions_addclose(file_actions, parent_write);

    //The process is started effectively suspended -- waiting for SIGSTOP,
    //SIGKILL, or SIGUSR1. Under normal circumstances, we send SIGUSR1 to the
    //child process, allowing it to continue execution.
    IntByReference ptr_pid = new IntByReference();
    Pointer argv = createPointerToStringArray(false, "/path/to/spawn", str_child_read, str_child_write, null);
    check(posix_spawnp(ptr_pid, "/work/etc/jcommon/process/src/main/resources/native/unix/x86_64/bin/spawn", file_actions, attr, argv, null));
    disposeStringArray(argv);

    //close(child_read);
    //close(child_write);

    //expects a SIGINT to be sent
    final int pid = ptr_pid.getValue();

    System.out.println("PID: " + pid);

    int written = write(parent_write, Charset.forName("ASCII").encode("HELLO BABY\0"), "HELLO BABY".length());


    //Allow the child process to run execve() and begin doing real work.
    //check(kill(pid, SIGINT));

    IntByReference status = new IntByReference();
    waitpid(pid, status, 0);

    check(posix_spawn_file_actions_destroy(file_actions));
    check(posix_spawnattr_destroy(attr));

    //try { Thread.sleep(30 * 1000); } catch(Throwable t) { }
    return null;
  }
}
