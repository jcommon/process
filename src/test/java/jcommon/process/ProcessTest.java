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

package jcommon.process;

import jcommon.process.platform.ILaunchProcess;
import jcommon.process.platform.win32.Win32LaunchProcess;
import org.junit.Test;

import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class ProcessTest {
  @Test
  public void testLaunchProcess() throws Throwable {
    final ILaunchProcess p = new Win32LaunchProcess();
    final int concurrent_processes = 8;
    final int times = 3;

    for(int time = 0; time < times; ++time) {
      final CyclicBarrier start_barrier = new CyclicBarrier(concurrent_processes + 1);
      final CyclicBarrier stop_barrier = new CyclicBarrier(concurrent_processes + 1);

      //p.launch("cmd.exe", "/c", "echo", "%PATH%");
      //p.launch(Resources.STDOUT_1, "how", "are", "you");

      for(int i = 0; i < concurrent_processes; ++i) {
        final int idx = i;
        final Thread t = new Thread (new Runnable() {
          @Override
          public void run() {
            try {
              start_barrier.await();
              p.launch("cmd.exe", "/c", Resources.STDERR_ECHO_REPEAT, "P:" + (idx + 1) + "...........................................................", "10");
              stop_barrier.await();
            } catch(Throwable t) {
              t.printStackTrace();
            }
          }
        });
        t.setDaemon(false);
        t.start();

        //p.launch("cmd.exe", "/c", Resources.STDOUT_ECHO_REPEAT, "an extremely long line should go here, wouldn't you say? and the number is: " + (i + 1), "4000");
        //p.launch(Resources.STDOUT_1, "how", "are", "you");
      }

      start_barrier.await();
      stop_barrier.await();
    }

    System.out.println(Resources.STDOUT_ECHO_REPEAT);
  }
}
