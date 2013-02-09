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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class ProcessTest {
  @Test
  public void testLaunchProcess() throws Throwable {
    assertTrue(Resources.loadAllResources());

    final int times = 20;
    final CountDownLatch stop_latch = new CountDownLatch(times);

    //p.launch("cmd.exe", "/c", "echo", "%PATH%");
    //p.launch(Resources.STDOUT_1, "how", "are", "you");

    //p.launch("cmd.exe", "/c", Resources.STDOUT_ECHO_REPEAT, "an extremely long line should go here, wouldn't you say? and the number is: " + (i + 1), "4000");
    //p.launch(Resources.STDOUT_1, "how", "are", "you");

    final ProcessBuilder builder = ProcessBuilder.create()
      .withExecutable("cmd.exe")
      .andArgument("/c")
      .withListener(
        StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
    ;

    for(int time = 0; time < times; ++time) {
      final IProcess process = builder.copy()
        .addArguments(time % 2 == 0 ? Resources.STDOUT_ECHO_REPEAT : Resources.STDERR_ECHO_REPEAT, "P:" + (time + 1), "100")
        .addListener(new ProcessListener() {
          @Override
          protected void processStarted(IProcess process) throws Throwable {
            //System.out.println("PID " + process.getPID() + " STARTED [" + Thread.currentThread().getName() + "]");
          }

          @Override
          protected void processStopped(IProcess process) throws Throwable {
            //System.out.println("PID " + process.getPID() + " STOPPED [" + Thread.currentThread().getName() + "]");
            stop_latch.countDown();
          }
        })
        .start();
    }

    stop_latch.await(10L, TimeUnit.MINUTES);
    System.out.println("All done.");
  }
}
