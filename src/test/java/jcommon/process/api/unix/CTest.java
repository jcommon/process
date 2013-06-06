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

import jcommon.core.OSFamily;
import jcommon.process.platform.unix.UnixProcessLauncherEPoll;
import org.junit.BeforeClass;
import org.junit.Test;

import static jcommon.process.api.unix.C.*;
import static jcommon.process.api.unix.EPoll.*;

import static jcommon.process.ProcessResources.*;
import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class CTest {

  @BeforeClass
  public static void before() {
  }

  @Test
  public void testBasicLibraryLoad() throws Throwable {
    //jcommon.process.ProcessBuilder.create("notepad").start().await();
    //jcommon.process.ProcessBuilder.create("C:\\Program Files (x86)\\Internet Explorer\\iexplore.exe").start().await();
    //jcommon.process.ProcessBuilder.create("C:\\Program Files (x86)\\Bitvise SSH Client\\BvSsh.exe").start().await();
    //jcommon.process.ProcessBuilder.create("C:\\Program Files\\Internet Explorer\\iexplore.exe", "http://www.yahoo.com/").start().await();
    //jcommon.process.ProcessBuilder.create("C:\\Progra~2\\Intern~1\\IEXPLORE.EXE", "http://www.yahoo.com/").start().await();
    //Runtime.getRuntime().exec("C:\\Progra~1\\Intern~1\\IEXPLORE.EXE").waitFor();
    if (OSFamily.getSystemOSFamily() == OSFamily.Windows)
      return;

    final int fd = epoll_create(1);
    assertTrue(fd > 0);
    assertTrue(C.close(fd) == 0);

    UnixProcessLauncherEPoll.launch(false, null, null, null);
  }
}
