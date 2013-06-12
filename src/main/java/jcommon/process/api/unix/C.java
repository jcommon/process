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
import jcommon.core.Arch;

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

    public static class ByReference extends sigset_t implements Structure.ByReference {

    }

    public static class ByValue extends sigset_t implements Structure.ByValue {

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
    //Structure describing the action to be taken when a signal arrives.
    struct sigaction
    {
      //Signal handler.
      #ifdef __USE_POSIX199309
      union
      {
        //Used if SA_SIGINFO is not set.
        __sighandler_t sa_handler;
        //Used if SA_SIGINFO is set.
        void (*sa_sigaction) (int, siginfo_t *, void *);
      }
      __sigaction_handler;
      # define sa_handler	__sigaction_handler.sa_handler
      # define sa_sigaction	__sigaction_handler.sa_sigaction
      #else
      __sighandler_t sa_handler;
      #endif

      //Additional set of signals to be blocked.
      __sigset_t sa_mask;

      //Special flags.
      int sa_flags;

      //Restore handler.
      void (*sa_restorer) (void);
    };
  */
  public static class sigaction extends Structure {
    public static class __sigaction_handler extends Union {
      public sighandler_t /*sighandler_t*/ sa_handler;
      public sa_sigaction /*sa_sigaction*/ sa_sigaction;
    }

    public __sigaction_handler __sigaction_handler;
    public sigset_t sa_mask;
    public int sa_flags;
    public Pointer sa_restorer;

    protected List getFieldOrder() {
      return Arrays.asList("__sigaction_handler", "sa_mask", "sa_flags", "sa_restorer");
    }

    public sigaction() {
      super();
    }

    public sigaction(int sa_flags, sighandler_t sa_handler) {
      super();
      sigemptyset(sa_mask.getPointer());
      this.sa_flags = sa_flags;
      this.__sigaction_handler.sa_handler = sa_handler;
      this.__sigaction_handler.setType(sighandler_t.class);
    }

    public sigaction(int sa_flags, sa_sigaction sa_sigaction) {
      super();
      sigemptyset(sa_mask.getPointer());
      this.sa_flags = sa_flags | SA_SIGINFO;
      this.__sigaction_handler.sa_sigaction = sa_sigaction;
      this.__sigaction_handler.setType(sa_sigaction.class);
    }

    public static class ByReference extends sigaction implements Structure.ByReference {
      public ByReference() {
        super();
      }

      public ByReference(int sa_flags, sighandler_t sa_handler) {
        super(sa_flags, sa_handler);
      }

      public ByReference(int sa_flags, sa_sigaction sa_sigaction) {
        super(sa_flags, sa_sigaction);
      }
    }

    public static class ByValue extends sigset_t implements Structure.ByValue {

    }
  }

  public static interface sighandler_t extends Callback {
    void handler(int signal);
  }

  public static interface sa_sigaction extends Callback {
    void action(int signum, siginfo_t info, Pointer context);
  }

  /*
    # define __SI_MAX_SIZE     128
    # if __WORDSIZE == 64
    #  define __SI_PAD_SIZE     ((__SI_MAX_SIZE / sizeof (int)) - 4)
    # else
    #  define __SI_PAD_SIZE     ((__SI_MAX_SIZE / sizeof (int)) - 3)
    # endif
  */
  public static final int __SI_MAX_SIZE = 128;
  public static final int __SI_PAD_SIZE =
    Arch.getSystemArch().is64Bit() ?
      ((__SI_MAX_SIZE / (Integer.SIZE / Byte.SIZE)) - 4)
      :
      ((__SI_MAX_SIZE / (Integer.SIZE / Byte.SIZE)) - 3)
  ;

  //sigval
  //{
  //  int sival_int;
  //  void *sival_ptr;
  //} sigval_t;
  public static class sigval_t extends Union {
    public int sival_int;
    Pointer sival_ptr;
  }

  /*
    typedef struct
    {
      int si_signo;		//Signal number.
      int si_errno;		//If non-zero, an errno value associated with this signal, as defined in <errno.h>.
      int si_code;		//Signal code.

      union
      {
        int _pad[__SI_PAD_SIZE];

        // kill().
        struct
        {
          __pid_t si_pid;	// Sending process ID.
          __uid_t si_uid;	// Real user ID of sending process.
        } _kill;

        // POSIX.1b timers.
        struct
        {
          int si_tid;		// Timer ID.
          int si_overrun;	// Overrun count.
          sigval_t si_sigval;	// Signal value.
        } _timer;

        // POSIX.1b signals.
        struct
        {
          __pid_t si_pid;	// Sending process ID.
          __uid_t si_uid;	// Real user ID of sending process.
          sigval_t si_sigval;	// Signal value.
        } _rt;

        // SIGCHLD.
        struct
        {
          __pid_t si_pid;	// Which child.
          __uid_t si_uid;	// Real user ID of sending process.
          int si_status;	// Exit value or signal.
          __sigchld_clock_t si_utime;
          __sigchld_clock_t si_stime;
        } _sigchld;

        // SIGILL, SIGFPE, SIGSEGV, SIGBUS.
        struct
        {
          void *si_addr;	// Faulting insn/memory ref.
        } _sigfault;

        // SIGPOLL.
        struct
        {
          long int si_band;	// Band event for SIGPOLL.
          int si_fd;
        } _sigpoll;

        // SIGSYS.
        struct
        {
          void *_call_addr;	// Calling user insn.
          int _syscall;	// Triggering system call number.
          unsigned int _arch; // AUDIT_ARCH_* of syscall.
        } _sigsys;
      } _sifields;
    } siginfo_t __SI_ALIGNMENT;
   */
  public static class siginfo_t extends Structure {
    public int si_signo; //Signal number.
    public int si_errno; //If non-zero, an errno value associated with this signal, as defined in <errno.h>.
    public int si_code;  //Signal code.

    public static class _sifields extends Union {
      public int[] _pad = new int[__SI_PAD_SIZE];

      // kill().
      //struct
      //{
      //  __pid_t si_pid;	// Sending process ID.
      //  __uid_t si_uid;	// Real user ID of sending process.
      //} _kill;
      public static class _kill extends Structure {
        public int si_pid;
        public short si_uid;
        protected List getFieldOrder() {
          return Arrays.asList("si_pid", "si_uid");
        }
      }
      public _kill kill;

      // POSIX.1b timers.
      //struct
      //{
      //  int si_tid;		// Timer ID.
      //  int si_overrun;	// Overrun count.
      //  sigval_t si_sigval;	// Signal value.
      //} _timer;
      public static class _timer extends Structure {
        public int si_tid;
        public int si_overrun;
        public sigval_t si_sigval;
        protected List getFieldOrder() {
          return Arrays.asList("si_tid", "si_overrun", "si_sigval");
        }
      }
      public _timer timer;

      // POSIX.1b signals.
      //struct
      //{
      //  __pid_t si_pid;	// Sending process ID.
      //  __uid_t si_uid;	// Real user ID of sending process.
      //  sigval_t si_sigval;	// Signal value.
      //} _rt;
      public static class _rt extends Structure {
        public int si_pid;
        public short si_uid;
        public sigval_t si_sigval;
        protected List getFieldOrder() {
          return Arrays.asList("si_pid", "si_uid", "si_sigval");
        }
      }
      public _rt rt;

      // SIGCHLD.
      //struct
      //{
      //  __pid_t si_pid;	// Which child.
      //  __uid_t si_uid;	// Real user ID of sending process.
      //  int si_status;	// Exit value or signal.
      //  __sigchld_clock_t si_utime;
      //  __sigchld_clock_t si_stime;
      //} _sigchld;
      public static class _sigchld extends Structure {
        public int si_pid;
        public short si_uid;
        public int si_status;
        public long si_utime;
        public long si_stime;
        protected List getFieldOrder() {
          return Arrays.asList("si_pid", "si_uid", "si_status", "si_utime", "si_stime");
        }
      }
      public _sigchld sig_chld;

      // SIGILL, SIGFPE, SIGSEGV, SIGBUS.
      //struct
      //{
      //  void *si_addr;	// Faulting insn/memory ref.
      //} _sigfault;
      public static class _sigfault extends Structure {
        public Pointer si_addr;
        protected List getFieldOrder() {
          return Arrays.asList("si_addr");
        }
      }
      public _sigfault sig_fault;

      // SIGPOLL.
      //struct
      //{
      //  long int si_band;	// Band event for SIGPOLL.
      //  int si_fd;
      //} _sigpoll;
      public static class _sigpoll extends Structure {
        public int si_band;
        public int si_fd;
        protected List getFieldOrder() {
          return Arrays.asList("si_band", "si_fd");
        }
      }
      public _sigpoll sig_poll;

      // SIGSYS.
      //struct
      //{
      //  void *_call_addr;	// Calling user insn.
      //  int _syscall;	// Triggering system call number.
      //  unsigned int _arch; // AUDIT_ARCH_* of syscall.
      //} _sigsys;
      public static class _sigsys extends Structure {
        public Pointer _call_addr;
        public int _syscall;
        public int _arch;
        protected List getFieldOrder() {
          return Arrays.asList("_call_addr", "_syscall", "_arch");
        }
      }
      public _sigsys sig_sys;
    }

    public _sifields si_field;

    protected List getFieldOrder() {
      return Arrays.asList("si_signo", "si_errno", "si_code", "si_field");
    }

    public siginfo_t() {
      super();
      loadBySignal();
    }

    public void loadBySignal() {
      loadBySignal(si_signo);
    }

    public void loadBySignal(int signum) {
      switch(signum) {
        case SIGCHLD:
          si_field.setType(_sifields._sigchld.class);
          break;
        case SIGKILL:
        case SIGTERM:
          si_field.setType(_sifields._kill.class);
          break;
        case SIGILL:
        case SIGFPE:
        case SIGSEGV:
        case SIGBUS:
        case SIGTRAP:
          si_field.setType(_sifields._sigfault.class);
          break;
        //case SIGIO:
        case SIGPOLL:
          si_field.setType(_sifields._sigpoll.class);
          break;
      }
    }

    public static class ByReference extends siginfo_t implements Structure.ByReference {
      public ByReference() {
        super();
      }
    }

    public static class ByValue extends siginfo_t implements Structure.ByValue {

    }
  }

  /*
    struct signalfd_siginfo
    {
      uint32_t ssi_signo;
      int32_t ssi_errno;
      int32_t ssi_code;
      uint32_t ssi_pid;
      uint32_t ssi_uid;
      int32_t ssi_fd;
      uint32_t ssi_tid;
      uint32_t ssi_band;
      uint32_t ssi_overrun;
      uint32_t ssi_trapno;
      int32_t ssi_status;
      int32_t ssi_int;
      uint64_t ssi_ptr;
      uint64_t ssi_utime;
      uint64_t ssi_stime;
      uint64_t ssi_addr;
      uint8_t __pad[48];
    };
   */
  public static class signalfd_siginfo extends Structure {
    public int ssi_signo;
    public int ssi_errno;
    public int ssi_code;
    public int ssi_pid;
    public int ssi_uid;
    public int ssi_fd;
    public int ssi_tid;
    public int ssi_band;
    public int ssi_overrun;
    public int ssi_trapno;
    public int ssi_status;
    public int ssi_int;
    public long ssi_ptr;
    public long ssi_utime;
    public long ssi_stime;
    public long ssi_addr;
    public byte[] __pad = new byte[48];

    protected List getFieldOrder() {
      return Arrays.asList("ssi_signo", "ssi_errno", "ssi_code", "ssi_pid", "ssi_uid", "ssi_fd", "ssi_tid", "ssi_band", "ssi_overrun", "ssi_trapno", "ssi_status", "ssi_int", "ssi_ptr", "ssi_utime", "ssi_stime", "ssi_addr", "__pad");
    }

    public signalfd_siginfo() {
      super();
    }
  }
  
  public static final int
      EPERM           =   1 //Operation not permitted
    , ENOENT          =   2 //No such file or directory
    , ESRCH           =   3 //No such process
    , EINTR           =   4 //Interrupted system call
    , EIO             =   5 //I/O error
    , ENXIO           =   6 //No such device or address
    , E2BIG           =   7 //Arg list too long
    , ENOEXEC         =   8 //Exec format error
    , EBADF           =   9 //Bad file number
    , ECHILD          =  10 //No child processes
    , EAGAIN          =  11 //Try again
    , ENOMEM          =  12 //Out of memory
    , EACCES          =  13 //Permission denied
    , EFAULT          =  14 //Bad address
    , ENOTBLK         =  15 //Block device required
    , EBUSY           =  16 //Device or resource busy
    , EEXIST          =  17 //File exists
    , EXDEV           =  18 //Cross-device link
    , ENODEV          =  19 //No such device
    , ENOTDIR         =  20 //Not a directory
    , EISDIR          =  21 //Is a directory
    , EINVAL          =  22 //Invalid argument
    , ENFILE          =  23 //File table overflow
    , EMFILE          =  24 //Too many open files
    , ENOTTY          =  25 //Not a typewriter
    , ETXTBSY         =  26 //Text file busy
    , EFBIG           =  27 //File too large
    , ENOSPC          =  28 //No space left on device
    , ESPIPE          =  29 //Illegal seek
    , EROFS           =  30 //Read-only file system
    , EMLINK          =  31 //Too many links
    , EPIPE           =  32 //Broken pipe
    , EDOM            =  33 //Math argument out of domain of func
    , ERANGE          =  34 //Math result not representable
    , EDEADLK         =  35 //Resource deadlock would occur
    , ENAMETOOLONG    =  36 //File name too long
    , ENOLCK          =  37 //No record locks available
    , ENOSYS          =  38 //Function not implemented
    , ENOTEMPTY       =  39 //Directory not empty
    , ELOOP           =  40 //Too many symbolic links encountered
    , ENOMSG          =  42 //No message of desired type
    , EIDRM           =  43 //Identifier removed
    , ECHRNG          =  44 //Channel number out of range
    , EL2NSYNC        =  45 //Level 2 not synchronized
    , EL3HLT          =  46 //Level 3 halted
    , EL3RST          =  47 //Level 3 reset
    , ELNRNG          =  48 //Link number out of range
    , EUNATCH         =  49 //Protocol driver not attached
    , ENOCSI          =  50 //No CSI structure available
    , EL2HLT          =  51 //Level 2 halted
    , EBADE           =  52 //Invalid exchange
    , EBADR           =  53 //Invalid request descriptor
    , EXFULL          =  54 //Exchange full
    , ENOANO          =  55 //No anode
    , EBADRQC         =  56 //Invalid request code
    , EBADSLT         =  57 //Invalid slot
    , EBFONT          =  59 //Bad font file format
    , ENOSTR          =  60 //Device not a stream
    , ENODATA         =  61 //No data available
    , ETIME           =  62 //Timer expired
    , ENOSR           =  63 //Out of streams resources
    , ENONET          =  64 //Machine is not on the network
    , ENOPKG          =  65 //Package not installed
    , EREMOTE         =  66 //Object is remote
    , ENOLINK         =  67 //Link has been severed
    , EADV            =  68 //Advertise error
    , ESRMNT          =  69 //Srmount error
    , ECOMM           =  70 //Communication error on send
    , EPROTO          =  71 //Protocol error
    , EMULTIHOP       =  72 //Multihop attempted
    , EDOTDOT         =  73 //RFS specific error
    , EBADMSG         =  74 //Not a data message
    , EOVERFLOW       =  75 //Value too large for defined data type
    , ENOTUNIQ        =  76 //Name not unique on network
    , EBADFD          =  77 //File descriptor in bad state
    , EREMCHG         =  78 //Remote address changed
    , ELIBACC         =  79 //Can not access a needed shared library
    , ELIBBAD         =  80 //Accessing a corrupted shared library
    , ELIBSCN         =  81 //.lib section in a.out corrupted
    , ELIBMAX         =  82 //Attempting to link in too many shared libraries
    , ELIBEXEC        =  83 //Cannot exec a shared library directly
    , EILSEQ          =  84 //Illegal byte sequence
    , ERESTART        =  85 //Interrupted system call should be restarted
    , ESTRPIPE        =  86 //Streams pipe error
    , EUSERS          =  87 //Too many users
    , ENOTSOCK        =  88 //Socket operation on non-socket
    , EDESTADDRREQ    =  89 //Destination address required
    , EMSGSIZE        =  90 //Message too long
    , EPROTOTYPE      =  91 //Protocol wrong type for socket
    , ENOPROTOOPT     =  92 //Protocol not available
    , EPROTONOSUPPORT =  93 //Protocol not supported
    , ESOCKTNOSUPPORT =  94 //Socket type not supported
    , EOPNOTSUPP      =  95 //Operation not supported on transport endpoint
    , EPFNOSUPPORT    =  96 //Protocol family not supported
    , EAFNOSUPPORT    =  97 //Address family not supported by protocol
    , EADDRINUSE      =  98 //Address already in use
    , EADDRNOTAVAIL   =  99 //Cannot assign requested address
    , ENETDOWN        = 100 //Network is down
    , ENETUNREACH     = 101 //Network is unreachable
    , ENETRESET       = 102 //Network dropped connection because of reset
    , ECONNABORTED    = 103 //Software caused connection abort
    , ECONNRESET      = 104 //Connection reset by peer
    , ENOBUFS         = 105 //No buffer space available
    , EISCONN         = 106 //Transport endpoint is already connected
    , ENOTCONN        = 107 //Transport endpoint is not connected
    , ESHUTDOWN       = 108 //Cannot send after transport endpoint shutdown
    , ETOOMANYREFS    = 109 //Too many references: cannot splice
    , ETIMEDOUT       = 110 //Connection timed out
    , ECONNREFUSED    = 111 //Connection refused
    , EHOSTDOWN       = 112 //Host is down
    , EHOSTUNREACH    = 113 //No route to host
    , EALREADY        = 114 //Operation already in progress
    , EINPROGRESS     = 115 //Operation now in progress
    , ESTALE          = 116 //Stale NFS file handle
    , EUCLEAN         = 117 //Structure needs cleaning
    , ENOTNAM         = 118 //Not a XENIX named type file
    , ENAVAIL         = 119 //No XENIX semaphores available
    , EISNAM          = 120 //Is a named type file
    , EREMOTEIO       = 121 //Remote I/O error
    , EDQUOT          = 122 //Quota exceeded
    , ENOMEDIUM       = 123 //No medium found
    , EMEDIUMTYPE     = 124 //Wrong medium type
  ;

  public static final int
      EWOULDBLOCK = EAGAIN //Operation would block
    , EDEADLOCK   = EDEADLK
  ;

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

  // /usr/include/x86_64-linux-gnu/bits/signalfd.h
  public static final int
      SFD_CLOEXEC  = 02000000
    , SFD_NONBLOCK = 00004000
  ;

  // /usr/include/x86_64-linux-gnu/bits/sigaction.h
  public static final int
      SIG_BLOCK   = 0
    , SIG_UNBLOCK = 1
    , SIG_SETMASK = 2
  ;

  // /usr/include/x86_64-linux-gnu/bits/signum.h
  public static final Pointer
      SIG_ERR = Pointer.createConstant(-1) /* Error return.  */
    , SIG_DFL = Pointer.createConstant(0)  /* Default action.  */
    , SIG_IGN = Pointer.createConstant(1)  /* Ignore signal.  */
  ;

  // /usr/include/x86_64-linux-gnu/bits/sigaction.h
  public static final int
      SA_NOCLDSTOP =          1 /* Don't send SIGCHLD when children stop.  */
    , SA_NOCLDWAIT =          2 /* Don't create zombie on child death.  */
    , SA_SIGINFO   =          4 /* Invoke signal-catching function with three arguments instead of one.  */
    , SA_ONSTACK   = 0x08000000 /* Use signal stack by using `sa_restorer'. */
    , SA_RESTART   = 0x10000000 /* Restart syscall on signal return.  */
    , SA_NODEFER   = 0x40000000 /* Don't automatically block the signal when its handler is being executed.  */
    , SA_RESETHAND = 0x80000000 /* Reset to SIG_DFL on entry to handler.  */
    , SA_INTERRUPT = 0x20000000 /* Historical no-op.  */
  ;

  //Some aliases for the SA_ constants.
  public static final int
      SA_NOMASK  = SA_NODEFER
    , SA_ONESHOT = SA_RESETHAND
    , SA_STACK   = SA_ONSTACK
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

  public static native Pointer signal(int signum, Pointer handler);
  public static native sighandler_t signal(int signum, sighandler_t handler);

  public static native int sigemptyset(Pointer set);
  public static native int sigemptyset(sigset_t.ByReference set);
  public static native int sigfillset(sigset_t.ByReference set);
  public static native int sigaddset(sigset_t.ByReference set, int signum);
  public static native int sigdelset(sigset_t.ByReference set, int signum);
  public static native int sigismember(sigset_t.ByReference set, int signum);

  public static native int sigaction(int signum, sigaction.ByReference act, sigaction.ByReference oldact);
  public static native int sigprocmask(int how, sigset_t.ByReference set, sigset_t.ByReference oldset);

  public static native int signalfd(int fd, sigset_t.ByReference mask, int flags);
}
