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

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;

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
    //Native.register(LIB);
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