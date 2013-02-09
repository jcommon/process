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

import org.junit.Test;

import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class ProcessTest {
  @Test
  public void testLaunchProcess() throws Throwable {
    assertTrue(Resources.loadAllResources());

    final int concurrent_processes = 2;
    final int times = 20;

    final ProcessBuilder builder = ProcessBuilder.create()
      .withExecutable("cmd.exe")
      .andArgument("/c");


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
              final IProcess process = builder
                .copy()
                .addArguments(idx % 2 == 0 ? Resources.STDOUT_ECHO_REPEAT : Resources.STDERR_ECHO_REPEAT, "P:" + (idx + 1), "100")
                .start();

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
      stop_barrier.await();
    }

    Thread.sleep(5000L);
    System.out.println("All done.");
  }
}
