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
import com.sun.jna.ptr.LongByReference;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
@SuppressWarnings("all")
public class C {
  private static final String LIB = "c";

  static {
    Native.register(LIB);
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

  // /usr/include/x86_64-linux-gnu/sys/stat.h
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

  // /usr/include/x86_64-linux-gnu/bits/fcntl-linux.h
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

  // /usr/include/x86_64-linux-gnu/bits/fcntl-linux.h
  public static final int
      F_GETFL   = 3  /* get file status flags */
    , F_SETFL   = 4  /* set file status flags */
    , F_NOCACHE = 48 /* Mac OS X specific flag, turns cache on/off */
  ;

  // /usr/include/x86_64-linux-gnu/bits/eventfd.h
  public static final int
      EFD_SEMAPHORE = 00000001
    , EFD_CLOEXEC   = 02000000
    , EFD_NONBLOCK  = 00004000
  ;

  // /usr/include/x86_64-linux-gnu/bits/signum.h
  public static final int
      SIGHUP		=  1	/* Hangup (POSIX).  */
    , SIGINT		=  2	/* Interrupt (ANSI).  */
    , SIGQUIT		=  3	/* Quit (POSIX).  */
    , SIGILL		=  4	/* Illegal instruction (ANSI).  */
    , SIGTRAP		=  5	/* Trace trap (POSIX).  */
    , SIGABRT		=  6	/* Abort (ANSI).  */
    , SIGIOT		=  6	/* IOT trap (4.2 BSD).  */
    , SIGBUS		=  7	/* BUS error (4.2 BSD).  */
    , SIGFPE		=  8	/* Floating-point exception (ANSI).  */
    , SIGKILL		=  9	/* Kill, unblockable (POSIX).  */
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

  // /usr/include/x86_64-linux-gnu/bits/waitflags.h
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

  public static native int close(int fd);

  public static native int getpid();

  public static native int kill(int pid, int sig);
  public static native int waitpid(int pid, IntByReference status, int options);

  public static native int pipe(int[] pipefd);
  public static native int read(int fd, ByteBuffer buffer, int count);
  public static native int write(int fd, ByteBuffer buffer, int count);
  public static native int fcntl(int fd, int command, long flags) throws LastErrorException;

  public static native int eventfd(int initval, int flags);
  public static native int eventfd_write(int fd, long value);
  public static native int eventfd_read(int fd, LongByReference value);
}
