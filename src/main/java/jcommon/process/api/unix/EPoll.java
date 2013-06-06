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
import java.util.Arrays;
import java.util.List;

import static jcommon.process.api.unix.C.*;

/**
 * 
 */
@SuppressWarnings("all")
public class EPoll {
  private static final String LIB = "c";

  static {
    Native.register(LIB);
  }

  public static final int
      EPOLL_CTL_ADD = 1
    , EPOLL_CTL_DEL = 2
    , EPOLL_CTL_MOD = 3
  ;

  // /usr/include/x86_64-linux-gnu/bits/epoll.h
  public static final int
      EPOLL_CLOEXEC  = 02000000
  ;

  public static int
      EPOLLIN      = (int)0x001
    , EPOLLPRI     = (int)0x002
    , EPOLLOUT     = (int)0x004
    , EPOLLRDNORM  = (int)0x040
    , EPOLLRDBAND  = (int)0x080
    , EPOLLWRNORM  = (int)0x100
    , EPOLLWRBAND  = (int)0x200
    , EPOLLMSG     = (int)0x400
    , EPOLLERR     = (int)0x008
    , EPOLLHUP     = (int)0x010
    , EPOLLRDHUP   = (int)0x2000
    , EPOLLWAKEUP  = (int)(1 << 29)
    , EPOLLONESHOT = (int)(1 << 30)
    , EPOLLET      = (int)(1 << 31)
  ;

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

    public static class ByReference extends epoll_data_t implements Structure.ByReference {
    }

    public static class ByValue extends epoll_data_t implements Structure.ByValue {
    }
  }

  /*
    struct epoll_event
    {
      uint32_t events;	//Epoll events
      epoll_data_t data;	//User data variable
    } __EPOLL_PACKED;
   */
  public static class epoll_event extends Structure {
    public int events;        //Epoll events
    public epoll_data_t data; //User data variable

    protected List getFieldOrder() {
      return Arrays.asList("events", "data");
    }

    public epoll_event() {
      super(Structure.ALIGN_NONE);
    }

    public epoll_event(Pointer p) {
      super(p, Structure.ALIGN_NONE);
      setAlignType(Structure.ALIGN_NONE);
      read();
    }

    public epoll_event(int events, epoll_data_t data) {
      super(Structure.ALIGN_NONE);
      this.events = events;
      this.data = data;
    }

    public void reuse(Pointer p, int offset) {
      useMemory(p, offset);
      read();
    }

    public static class ByReference extends epoll_event implements Structure.ByReference {
      public ByReference() {
        super();
      }

      public ByReference(int fd, int events) {
        super();
        this.events = events;
        data.fd = fd;
        data.setType(Integer.TYPE);
      }

      public ByReference(Pointer ptr, int events) {
        super();
        this.events = events;
        data.ptr = ptr;
        data.setType(Pointer.class);
      }

      public ByReference oneshot() {
        this.events = events | EPOLLONESHOT;
        return this;
      }
    }

    public static class ByValue extends epoll_event implements Structure.ByValue {
    }
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

  /**
   * Wait for events on an epoll instance "epfd".
   *
   * @param epfd The epoll file descriptor created with {@link #epoll_create(int)} or {@link #epoll_create1(int)}.
   * @param events A buffer that will contain triggered events.
   * @param maxevents The maximum number of events to be returned ( usually size of "events" ).
   * @param timeout Specifies the maximum wait time in milliseconds (-1 == infinite).
   * @return The number of triggered events returned in "events" buffer. Or -1 in case of error with the "errno" ({@link com.sun.jna.Native#getLastError()})
   *         variable set to the specific error code.
   */
  public static native int epoll_wait(int epfd, Pointer events, int maxevents, int timeout);
  //public static native int epoll_wait(int epfd, epoll_event.ByReference events, int maxevents, int timeout);
  //public static native int epoll_wait(int epfd, Structure events, int maxevents, int timeout);

  //extern int epoll_pwait (int __epfd, struct epoll_event *__events, int __maxevents, int __timeout, const __sigset_t *__ss);
  public static native int epoll_pwait(int epfd, Pointer events, int maxevents, int timeout, Pointer ss);
}
