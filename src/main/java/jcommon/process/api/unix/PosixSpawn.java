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

import static jcommon.process.api.unix.C.*;

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
@SuppressWarnings("all")
public class PosixSpawn {
  private static final String LIB = "c";

  static {
    Native.register(LIB);
  }

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

  public static final short
      POSIX_SPAWN_RESETIDS      = 0x01
    , POSIX_SPAWN_SETPGROUP     = 0x02
    , POSIX_SPAWN_SETSIGDEF     = 0x04
    , POSIX_SPAWN_SETSIGMASK    = 0x08
    , POSIX_SPAWN_SETSCHEDPARAM = 0x10
    , POSIX_SPAWN_SETSCHEDULER  = 0x20
    , POSIX_SPAWN_USEVFORK      = 0x40
  ;

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
  public static native int posix_spawnp(IntByReference pid, String file, posix_spawn_file_actions_t.ByReference file_actions, posix_spawnattr_t.ByReference attrp, Pointer argv, ByteBuffer envp);
}
