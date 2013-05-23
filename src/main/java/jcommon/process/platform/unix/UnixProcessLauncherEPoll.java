package jcommon.process.platform.unix;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;
import jcommon.process.api.PinnableMemory;
import jcommon.process.api.PinnableObject;
import jcommon.process.api.PinnableStruct;
import static jcommon.process.api.unix.C.*;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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

    posix_spawnattr_t.ByReference attr = new posix_spawnattr_t.ByReference();
    check(posix_spawnattr_init(attr));
    check(posix_spawnattr_setflags(attr, POSIX_SPAWN_USEVFORK));

    posix_spawn_file_actions_t.ByReference file_actions = new posix_spawn_file_actions_t.ByReference();
    check(posix_spawn_file_actions_init(file_actions));
    //posix_spawn_file_actions_addclose(file_actions, 0);
    //posix_spawn_file_actions_addclose(file_actions, 1);
    //posix_spawn_file_actions_addclose(file_actions, 2);


    //posix_spawnattr_setflags(&attr, POSIX_SPAWN_USEVFORK)

    //The process is started effectively suspended -- waiting for SIGSTOP,
    //SIGKILL, or SIGUSR1. Under normal circumstances, we send SIGUSR1 to the
    //child process, allowing it to continue execution.
    IntByReference ptr_pid = new IntByReference();
    check(posix_spawnp(ptr_pid, "/home/sysadmin/work/jcommon/process/src/main/resources/native/unix/x86_64/bin/spawn", file_actions, attr, null, null));
    check(posix_spawn_file_actions_destroy(file_actions));
    check(posix_spawnattr_destroy(attr));

    //expects a SIGINT to be sent
    int pid = ptr_pid.getValue();

    System.out.println("PID: " + pid);

    //Allow the child process to run execve() and begin doing real work.
    check(kill(pid, SIGINT));

    IntByReference status = new IntByReference();
    waitpid(pid, status, 0);

    //try { Thread.sleep(30 * 1000); } catch(Throwable t) { }
    return null;
  }
}
