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

package jcommon.process.api.unix;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 *
 * http://code.activestate.com/recipes/576759-subprocess-with-async-io-pipes-class/
 * http://linux.die.net/man/3/posix_spawn
 * http://unix.derkeiler.com/Newsgroups/comp.unix.programmer/2009-02/msg00222.html
 * https://bitbucket.org/burtonator/peregrine/src/854f608b7f4b0510d088b3a6d558db478cdd473c/src/java/peregrine/os/unistd.java
 * https://github.com/axiak/java_posix_spawn/blob/master/src/c/jlinuxfork.c
 * http://pubs.opengroup.org/onlinepubs/009695399/functions/posix_spawn.html
 * http://skife.org/java/2012/01/24/java_daemonization_with_gressil.html
 * https://kenai.com/projects/jna-posix/sources/mercurial/content/src/org/jruby/ext/posix/LibC.java?rev=59
 * https://github.com/bxm156/java_posix_spawn
 * http://bryanmarty.com/blog/2012/01/14/forking-jvm/
 */
public class C {
  private static final String LIB = "c";

  static {
    Native.register(LIB);
  }

  /*
    //Data structure to contain attributes for thread creation.
    typedef struct
    {
      short int __flags;
      pid_t __pgrp;
      sigset_t __sd;
      sigset_t __ss;
      struct sched_param __sp;
      int __policy;
      int __pad[16];
    } posix_spawnattr_t;
  */

  /*
    //Valid opcodes ( "op" parameter ) to issue to epoll_ctl().
    #define EPOLL_CTL_ADD 1	//Add a file descriptor to the interface.
    #define EPOLL_CTL_DEL 2	//Remove a file descriptor from the interface.
    #define EPOLL_CTL_MOD 3	//Change file descriptor epoll_event structure.
  */
  public static final int
      EPOLL_CTL_ADD = 1
    , EPOLL_CTL_DEL = 2
    , EPOLL_CTL_MOD = 3
  ;

  /*
    enum EPOLL_EVENTS
    {
      EPOLLIN = 0x001,
      EPOLLPRI = 0x002,
      EPOLLOUT = 0x004,
      EPOLLRDNORM = 0x040,
      EPOLLRDBAND = 0x080,
      EPOLLWRNORM = 0x100,
      EPOLLWRBAND = 0x200,
      EPOLLMSG = 0x400,
      EPOLLERR = 0x008,
      EPOLLHUP = 0x010,
      EPOLLRDHUP = 0x2000,
      EPOLLWAKEUP = 1u << 29,
      EPOLLONESHOT = 1u << 30,
      EPOLLET = 1u << 31
    };
  */
  public static interface EPOLL_EVENTS {
    public static final int EPOLLIN = (int)0x001;
    public static final int EPOLLPRI = (int)0x002;
    public static final int EPOLLOUT = (int)0x004;
    public static final int EPOLLRDNORM = (int)0x040;
    public static final int EPOLLRDBAND = (int)0x080;
    public static final int EPOLLWRNORM = (int)0x100;
    public static final int EPOLLWRBAND = (int)0x200;
    public static final int EPOLLMSG = (int)0x400;
    public static final int EPOLLERR = (int)0x008;
    public static final int EPOLLHUP = (int)0x010;
    public static final int EPOLLRDHUP = (int)0x2000;
    public static final int EPOLLWAKEUP = (int)(1 << 29);
    public static final int EPOLLONESHOT = (int)(1 << 30);
    public static final int EPOLLET = (int)(1 << 31);
  };

  /*
    typedef union epoll_data
    {
      void *ptr;
      int fd;
      uint32_t u32;
      uint64_t u64;
    } epoll_data_t;
   */
  public static class epoll_data_t extends Union {
    public Pointer ptr;
    public int fd;
    public int u32;
    public long u64;
    public epoll_data_t() {
      super();
    }
    public epoll_data_t(int fd_or_u32) {
      super();
      this.u32 = this.fd = fd_or_u32;
      setType(Integer.TYPE);
    }
    public epoll_data_t(long u64) {
      super();
      this.u64 = u64;
      setType(Long.TYPE);
    }
    public epoll_data_t(Pointer ptr) {
      super();
      this.ptr = ptr;
      setType(Pointer.class);
    }
    public static class ByReference extends epoll_data_t implements com.sun.jna.Structure.ByReference {

    };
    public static class ByValue extends epoll_data_t implements com.sun.jna.Structure.ByValue {

    };
  }

  /*
    struct epoll_event
    {
      uint32_t events;	//Epoll events
      epoll_data_t data;	//User data variable
    } __EPOLL_PACKED;
   */
  public static class epoll_event extends Structure {
    /// Epoll events
    public int events;
    /// User data variable
    public epoll_data_t data;
    public epoll_event() {
      super();
    }
    protected List getFieldOrder() {
      return Arrays.asList("events", "data");
    }
    public epoll_event(int events, epoll_data_t data) {
      super();
      this.events = events;
      this.data = data;
    }
    public static class ByReference extends epoll_event implements Structure.ByReference {

    };
    public static class ByValue extends epoll_event implements Structure.ByValue {

    };
  }

  /*
    /.* Creates an epoll instance.  Returns an fd for the new instance.
       The "size" parameter is a hint specifying the number of file
       descriptors to be associated with the new instance.  The fd
       returned by epoll_create() should be closed with close().  *./
    extern int epoll_create (int __size) __THROW;

    /.* Same as epoll_create but with an FLAGS parameter.  The unused SIZE
       parameter has been dropped.  *./
    extern int epoll_create1 (int __flags) __THROW;


    /.* Manipulate an epoll instance "epfd". Returns 0 in case of success,
       -1 in case of error ( the "errno" variable will contain the
       specific error code ) The "op" parameter is one of the EPOLL_CTL_*
       constants defined above. The "fd" parameter is the target of the
       operation. The "event" parameter describes which events the caller
       is interested in and any associated user data.  *./
    extern int epoll_ctl (int __epfd, int __op, int __fd,
              struct epoll_event *__event) __THROW;


    /.* Wait for events on an epoll instance "epfd". Returns the number of
       triggered events returned in "events" buffer. Or -1 in case of
       error with the "errno" variable set to the specific error code. The
       "events" parameter is a buffer that will contain triggered
       events. The "maxevents" is the maximum number of events to be
       returned ( usually size of "events" ). The "timeout" parameter
       specifies the maximum wait time in milliseconds (-1 == infinite).

       This function is a cancellation point and therefore not marked with
       __THROW.  *./
    extern int epoll_wait (int __epfd, struct epoll_event *__events,
               int __maxevents, int __timeout);


    /.* Same as epoll_wait, but the thread's signal mask is temporarily
       and atomically replaced with the one provided as parameter.

       This function is a cancellation point and therefore not marked with
       __THROW.  *./
    extern int epoll_pwait (int __epfd, struct epoll_event *__events,
          int __maxevents, int __timeout,
          const __sigset_t *__ss);

   */

  public static native int close(int fd);

  //extern int epoll_create (int __size) __THROW;
  public static native int epoll_create(int size);

  //extern int epoll_create1 (int __flags) __THROW;
  public static native int epoll_create1(int flags);

  //extern int epoll_ctl (int __epfd, int __op, int __fd, struct epoll_event *__event) __THROW;
  public static native int epoll_ctl(int epfd, int op, int fd, epoll_event.ByReference event);
  public static native int epoll_ctl(int epfd, int op, int fd, Pointer event);

  //extern int epoll_wait (int __epfd, struct epoll_event *__events, int __maxevents, int __timeout);
  public static native int epoll_wait(int epfd, epoll_event.ByReference events, int maxevents, int timeout);

  //extern int epoll_pwait (int __epfd, struct epoll_event *__events, int __maxevents, int __timeout, const __sigset_t *__ss);
  public static native int epoll_pwait(int epfd, epoll_event.ByReference events, int maxevents, int timeout, Pointer ss);


  /*
  /.* Data structure to contain information about the actions to be
   performed in the new process with respect to file descriptors.  *./
  typedef struct
  {
    int __allocated;
    int __used;
    struct __spawn_action *__actions;
    int __pad[16];
  } posix_spawn_file_actions_t;
  */
  public static class posix_spawn_file_actions_t extends Structure {
    public int __allocated;
    public int __used;
    public Pointer __actions;
    public int[] __pad = new int[16];

    protected List getFieldOrder() {
      return Arrays.asList("__allocated", "__used", "__actions", "__pad");
    }

    public posix_spawn_file_actions_t() {
      super();
    }

    public static class ByReference extends posix_spawn_file_actions_t implements Structure.ByReference {

    }

    public static class ByValue extends posix_spawn_file_actions_t implements Structure.ByValue {

    }
  }

  /*
    # define _SIGSET_NWORDS	(1024 / (8 * sizeof (unsigned long int)))
    typedef struct
    {
      unsigned long int __val[_SIGSET_NWORDS];
    } __sigset_t;
   */
  public static final int _SIGSET_NWORDS = (1024 / (8 * NativeLong.SIZE));
  public static class sigset_t extends Structure {
    public NativeLong[] __val = new NativeLong[_SIGSET_NWORDS];

    protected List getFieldOrder() {
      return Arrays.asList("__val");
    }

    public sigset_t() {
      super();
    }
  }

  /*
    struct sched_param
    {
      int __sched_priority;
    };
   */
  public static class sched_param extends Structure {
    public int __sched_priority;

    protected List getFieldOrder() {
      return Arrays.asList("__sched_priority");
    }

    public sched_param() {
      super();
    }
  }

  /*
    //Data structure to contain attributes for thread creation.
    typedef struct
    {
      short int __flags;
      pid_t __pgrp;
      sigset_t __sd;
      sigset_t __ss;
      struct sched_param __sp;
      int __policy;
      int __pad[16];
    } posix_spawnattr_t;
  */
  public static class posix_spawnattr_t extends Structure {
    public short __flags;
    public int __pgrp;
    public sigset_t __sd;
    public sigset_t __ss;
    public sched_param __sp;
    public int __policy;
    public int[] __pad = new int[16];

    protected List getFieldOrder() {
      return Arrays.asList("__flags", "__pgrp", "__sd", "__ss", "__sp", "__policy", "__pad");
    }

    public posix_spawnattr_t() {
      super();
    }

    public static class ByReference extends posix_spawnattr_t implements Structure.ByReference {

    }

    public static class ByValue extends posix_spawnattr_t implements Structure.ByValue {

    }
  }

  public static final int
      S_IRWXU = 0000700
    , S_IRUSR = 0000400
    , S_IWUSR = 0000200
    , S_IXUSR = 0000100
    , S_IRWXG = 0000070
    , S_IRGRP = 0000040
    , S_IWGRP = 0000020
    , S_IXGRP = 0000010
    , S_IRWXO = 0000007
    , S_IROTH = 0000004
    , S_IWOTH = 0000002
    , S_IXOTH = 0000001
    , S_ISUID = 0004000
    , S_ISGID = 0002000
    , S_ISVTX = 0001000
  ;

  public static final int
      O_RDONLY   = 0x0000
    , O_WRONLY   = 0x0001
    , O_RDWR     = 0x0002
    , O_ACCMODE  = 0x0003
    , O_NONBLOCK = 0x0004
    , O_APPEND   = 0x0008
    , O_SHLOCK   = 0x0010
    , O_EXLOCK   = 0x0020
    , O_ASYNC    = 0x0040
    , O_FSYNC    = 0x0080
    , O_CREAT    = 0x0200
    , O_TRUNC    = 0x0400
    , O_EXCL     = 0x0800
  ;

  public static final short
      POSIX_SPAWN_RESETIDS      = 0x01
    , POSIX_SPAWN_SETPGROUP     = 0x02
    , POSIX_SPAWN_SETSIGDEF     = 0x04
    , POSIX_SPAWN_SETSIGMASK    = 0x08
    , POSIX_SPAWN_SETSCHEDPARAM = 0x10
    , POSIX_SPAWN_SETSCHEDULER  = 0x20
    , POSIX_SPAWN_USEVFORK      = 0x40
  ;

  public static final int
      SIGHUP		= 1	/* Hangup (POSIX).  */
    , SIGINT		= 2	/* Interrupt (ANSI).  */
    , SIGQUIT		= 3	/* Quit (POSIX).  */
    , SIGILL		= 4	/* Illegal instruction (ANSI).  */
    , SIGTRAP		= 5	/* Trace trap (POSIX).  */
    , SIGABRT		= 6	/* Abort (ANSI).  */
    , SIGIOT		= 6	/* IOT trap (4.2 BSD).  */
    , SIGBUS		= 7	/* BUS error (4.2 BSD).  */
    , SIGFPE		= 8	/* Floating-point exception (ANSI).  */
    , SIGKILL		= 9	/* Kill, unblockable (POSIX).  */
    , SIGUSR1		= 10	/* User-defined signal 1 (POSIX).  */
    , SIGSEGV		= 11	/* Segmentation violation (ANSI).  */
    , SIGUSR2		= 12	/* User-defined signal 2 (POSIX).  */
    , SIGPIPE		= 13	/* Broken pipe (POSIX).  */
    , SIGALRM		= 14	/* Alarm clock (POSIX).  */
    , SIGTERM		= 15	/* Termination (ANSI).  */
    , SIGSTKFLT	= 16	/* Stack fault.  */
    , SIGCHLD		= 17	/* Child status has changed (POSIX).  */
    , SIGCONT		= 18	/* Continue (POSIX).  */
    , SIGSTOP		= 19	/* Stop, unblockable (POSIX).  */
    , SIGTSTP		= 20	/* Keyboard stop (POSIX).  */
    , SIGTTIN		= 21	/* Background read from tty (POSIX).  */
    , SIGTTOU		= 22	/* Background write to tty (POSIX).  */
    , SIGURG		= 23	/* Urgent condition on socket (4.2 BSD).  */
    , SIGXCPU		= 24	/* CPU limit exceeded (4.2 BSD).  */
    , SIGXFSZ		= 25	/* File size limit exceeded (4.2 BSD).  */
    , SIGVTALRM	= 26	/* Virtual alarm clock (4.2 BSD).  */
    , SIGPROF		= 27	/* Profiling alarm clock (4.2 BSD).  */
    , SIGWINCH	= 28	/* Window size change (4.3 BSD, Sun).  */
    , SIGPOLL		= 29	/* Pollable event occurred (System V).  */
    , SIGIO		  = 29	/* I/O now possible (4.2 BSD).  */
    , SIGPWR		= 30	/* Power failure restart (System V).  */
    , SIGSYS		= 31	/* Bad system call.  */
    , SIGUNUSED	= 31
  ;

  public static final int
      WNOHANG    = 1
    , WUNTRACED  = 2
    , WSTOPPED   = 2
    , WEXITED    = 4
    , WCONTINUED = 8
    , WNOWAIT    = 0x01000000
    , WNOTHREAD  = 0x20000000
    , WALL       = 0x40000000
    , WCLONE     = 0x80000000
  ;

  //Courtesy http://linux.die.net/include/bits/waitstatus.h

  public static int WEXITSTATUS(int status) {
    return (status & 0xff00) >> 8;
  }

  public static int WTERMSIG(int status) {
    return (status & 0x7f) >> 8;
  }

  public static boolean WIFEXITED(int status) {
    return WTERMSIG(status) == 0;
  }

  public static boolean WIFSIGNALED(int status) {
    return ((((status & 0x7f) + 1) >> 1) > 0);
  }

  public static boolean WIFSTOPPED(int status) {
    return (status & 0xff) == 0x7f;
  }

  public static boolean WIFCONTINUED(int status) {
    return status == 0xffff;
  }

  public static native int getpid();

  public static native int kill(int pid, int sig);
  public static native int waitpid(int pid, IntByReference status, int options);

  public static native int posix_spawn_file_actions_init(posix_spawn_file_actions_t.ByReference __file_actions);
  public static native int posix_spawn_file_actions_destroy(posix_spawn_file_actions_t.ByReference __file_actions);
  public static native int posix_spawn_file_actions_addopen(posix_spawn_file_actions_t.ByReference __file_actions, int _fd, String __path, int __oflag, int mode);
  public static native int posix_spawn_file_actions_addclose(posix_spawn_file_actions_t.ByReference __file_actions, int fildes);

  public static native int posix_spawnattr_init(posix_spawnattr_t.ByReference attr);
  public static native int posix_spawnattr_destroy(posix_spawnattr_t.ByReference attr);
  public static native int posix_spawnattr_setflags(posix_spawnattr_t.ByReference attr, short flags);

  //int posix_spawn(pid_t *restrict pid, const char *restrict path, const posix_spawn_file_actions_t *file_actions, const posix_spawnattr_t *restrict attrp, char *const argv[restrict], char *const envp[restrict]);
  //int posix_spawnp(pid_t *restrict pid, const char *restrict file, const posix_spawn_file_actions_t *file_actions, const posix_spawnattr_t *restrict attrp, char *const argv[restrict], char * const envp[restrict]);
  public static native int posix_spawn(IntByReference pid, String path, Pointer fileActions, Pointer attr, ByteBuffer argv, ByteBuffer envp);
  public static native int posix_spawnp(IntByReference pid, String file, posix_spawn_file_actions_t.ByReference file_actions, posix_spawnattr_t.ByReference attrp, ByteBuffer argv, ByteBuffer envp);
}

/*

#ifndef	_SYS_EPOLL_H
#define	_SYS_EPOLL_H	1

#include <stdint.h>
#include <sys/types.h>

#include <bits/sigset.h>

#ifndef __sigset_t_defined
# define __sigset_t_defined
typedef __sigset_t sigset_t;
#endif

#include <bits/epoll.h>

#ifndef __EPOLL_PACKED
# define __EPOLL_PACKED
#endif


enum EPOLL_EVENTS
  {
    EPOLLIN = 0x001,
#define EPOLLIN EPOLLIN
    EPOLLPRI = 0x002,
#define EPOLLPRI EPOLLPRI
    EPOLLOUT = 0x004,
#define EPOLLOUT EPOLLOUT
    EPOLLRDNORM = 0x040,
#define EPOLLRDNORM EPOLLRDNORM
    EPOLLRDBAND = 0x080,
#define EPOLLRDBAND EPOLLRDBAND
    EPOLLWRNORM = 0x100,
#define EPOLLWRNORM EPOLLWRNORM
    EPOLLWRBAND = 0x200,
#define EPOLLWRBAND EPOLLWRBAND
    EPOLLMSG = 0x400,
#define EPOLLMSG EPOLLMSG
    EPOLLERR = 0x008,
#define EPOLLERR EPOLLERR
    EPOLLHUP = 0x010,
#define EPOLLHUP EPOLLHUP
    EPOLLRDHUP = 0x2000,
#define EPOLLRDHUP EPOLLRDHUP
    EPOLLWAKEUP = 1u << 29,
#define EPOLLWAKEUP EPOLLWAKEUP
    EPOLLONESHOT = 1u << 30,
#define EPOLLONESHOT EPOLLONESHOT
    EPOLLET = 1u << 31
#define EPOLLET EPOLLET
  };


#define EPOLL_CTL_ADD 1	// Add a file descriptor to the interface.
#define EPOLL_CTL_DEL 2	// Remove a file descriptor from the interface.
#define EPOLL_CTL_MOD 3	// Change file descriptor epoll_event structure.


typedef union epoll_data
{
  void *ptr;
  int fd;
  uint32_t u32;
  uint64_t u64;
} epoll_data_t;

struct epoll_event
{
  uint32_t events;	//Epoll events
  epoll_data_t data;	//User data variable
} __EPOLL_PACKED;


__BEGIN_DECLS

/.* Creates an epoll instance.  Returns an fd for the new instance.
   The "size" parameter is a hint specifying the number of file
   descriptors to be associated with the new instance.  The fd
   returned by epoll_create() should be closed with close().  *./
extern int epoll_create (int __size) __THROW;

/.* Same as epoll_create but with an FLAGS parameter.  The unused SIZE
   parameter has been dropped.  *./
extern int epoll_create1 (int __flags) __THROW;


/.* Manipulate an epoll instance "epfd". Returns 0 in case of success,
   -1 in case of error ( the "errno" variable will contain the
   specific error code ) The "op" parameter is one of the EPOLL_CTL_*
   constants defined above. The "fd" parameter is the target of the
   operation. The "event" parameter describes which events the caller
   is interested in and any associated user data.  *./
extern int epoll_ctl (int __epfd, int __op, int __fd,
		      struct epoll_event *__event) __THROW;


/.* Wait for events on an epoll instance "epfd". Returns the number of
   triggered events returned in "events" buffer. Or -1 in case of
   error with the "errno" variable set to the specific error code. The
   "events" parameter is a buffer that will contain triggered
   events. The "maxevents" is the maximum number of events to be
   returned ( usually size of "events" ). The "timeout" parameter
   specifies the maximum wait time in milliseconds (-1 == infinite).

   This function is a cancellation point and therefore not marked with
   __THROW.  *./
extern int epoll_wait (int __epfd, struct epoll_event *__events,
		       int __maxevents, int __timeout);


/.* Same as epoll_wait, but the thread's signal mask is temporarily
   and atomically replaced with the one provided as parameter.

   This function is a cancellation point and therefore not marked with
   __THROW.  *./
extern int epoll_pwait (int __epfd, struct epoll_event *__events,
			int __maxevents, int __timeout,
			const __sigset_t *__ss);

__END_DECLS

#endif /.* sys/epoll.h *./
*/