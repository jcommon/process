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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class ProcessTest {
  @Test
  public void testLaunchProcess() throws Throwable {
    assertTrue(Resources.loadAllResources());

    final int times = 10;
    final int message_count = 1;
    final AtomicInteger start_count = new AtomicInteger(0);
    final CountDownLatch stop_latch = new CountDownLatch(times);

    //p.launch("cmd.exe", "/c", "echo", "%PATH%");
    //p.launch(Resources.STDOUT_1, "how", "are", "you");

    //p.launch("cmd.exe", "/c", Resources.STDOUT_ECHO_REPEAT, "4000", "an extremely long line should go here, wouldn't you say? and the number is: " + (i + 1));
    //p.launch(Resources.STDOUT_1, "how", "are", "you");

    final ProcessBuilder builder_stdout_1 = ProcessBuilder.create()
      .withExecutable(Resources.STDOUT_1)
      .andArgument(Integer.toString(message_count))
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
    ;

    final ProcessBuilder builder_stdout_echo_repeat = ProcessBuilder.create()
      .withExecutable("cmd.exe")
      .andArgument("/c")
      .andArgument(Resources.STDOUT_ECHO_REPEAT)
      .andArgument(Integer.toString(message_count))
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
    ;

    for(int time = 0; time < times; ++time) {
      final ProcessBuilder proc_builder = builder_stdout_echo_repeat.copy()
        .addArguments("P:" + (time + 1))
        .addListener(new ProcessListener() {
          private AtomicInteger counter = new AtomicInteger(0);

          @Override
          protected void processError(IProcess process, Throwable t) {
            fail("PID " + process.getPID() + ": " + t.getMessage());
          }

          @Override
          protected void processStarted(IProcess process) throws Throwable {
            //System.out.println("PID " + process.getPID() + " STARTED [" + Thread.currentThread().getName() + "]");
            start_count.incrementAndGet();
          }

          @Override
          protected void processStopped(IProcess process, int exitCode) throws Throwable {
            //System.out.println("PID " + process.getPID() + " STOPPED [" + Thread.currentThread().getName() + "]");
            //System.out.println("PID " + process.getPID() + " STOPPED [" + Thread.currentThread().getName() + "] EXIT CODE " + exitCode + " COUNTER " + counter.get());
            stop_latch.countDown();
            assertEquals(message_count, counter.get());
          }

          @Override
          protected void stdout(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable {
            final String text = Charset.defaultCharset().decode(buffer).toString();
            //System.out.print("PID " + process.getPID() + " " + text);
            int idx = -1;
            while((idx = text.indexOf("P", idx + 1)) >= 0)
              counter.incrementAndGet();
          }

          @Override
          protected void stderr(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable {
            final String text = Charset.defaultCharset().decode(buffer).toString();
            int idx = -1;
            while((idx = text.indexOf("P", idx + 1)) >= 0)
              counter.incrementAndGet();
          }
        });
      final IProcess process = proc_builder.start();
    }

    stop_latch.await(10L, TimeUnit.MINUTES);

    assertEquals(times, start_count.get());
    System.out.println("All done.");
  }
}
