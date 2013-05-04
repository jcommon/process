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

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static jcommon.process.ProcessResources.*;

import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class ProcessTest {
  final int times = 100;

  @BeforeClass
  public static void before() {
    assertTrue(Resources.loadAllResources());
  }

  private static int countNewLines(ByteBuffer buffer) {
    final String text = Charset.defaultCharset().decode(buffer).toString();
    //System.err.print("PID " + process.getPID() + " " + text);
    int idx = -1;
    int counter = 0;
    while((idx = text.indexOf("\n", idx + 1)) >= 0)
      ++counter;
    return counter;
  }

  @Test
  public void testEnvVarCoalescing() throws Throwable {
    final EnvironmentVariableBlockBuilder builder = new EnvironmentVariableBlockBuilder();
    builder.addEnvironmentVariable("E1", "V1");
    builder.addEnvironmentVariable("E2", "V2");
    builder.addEnvironmentVariable("E3", "V3");

    final EnvironmentVariableBlockBuilder original = builder.copy();

    final IEnvironmentVariableBlock block_1 = builder.toEnvironmentVariableBlock();

    assertEquals("V1", block_1.get("E1"));
    assertEquals("V2", block_1.get("E2"));
    assertEquals("V3", block_1.get("E3"));

    builder.clear();
    builder.addEnvironmentVariable("E2", "V2-Ammended");

    final IEnvironmentVariableBlock block_2 = builder.toEnvironmentVariableBlock();

    assertEquals("V1", block_1.get("E1"));
    assertEquals("V2", block_1.get("E2"));
    assertEquals("V3", block_1.get("E3"));

    //Other values are gone b/c we cleared them earlier in the test.
    assertEquals("V2-Ammended", block_2.get("E2"));

    final IEnvironmentVariableBlock coalesced_block_1 = original.toCoalescedEnvironmentVariableBlock(block_2);
    assertEquals("V1", coalesced_block_1.get("E1"));
    assertEquals("V2", coalesced_block_1.get("E2")); //Might seem confusing -- the original's values take precedence over what's passed in (not the other way around).
    assertEquals("V3", coalesced_block_1.get("E3"));
  }

  @Test
  public void testEnvVars() throws Throwable {
    //Creates environment variables and tests that they're being properly passed to the executable.
    final ProcessBuilder proc_builder = ENV_VAR_ECHO.copy();
    for(int i = 0; i < 2; ++i) {
      String variable_name = "TEST_" + (i + 1);
      String variable_value = "VALUE_" + (i + 1);

      proc_builder
        .addEnvironmentVariable(variable_name, variable_value)
        .addArgument(variable_name)
      ;
    }

    proc_builder.addArgument("PATH");

    proc_builder.start().await();
  }

  //@Test
  public void testStdIn() throws Throwable {
    //Test processes in succession.

    for(int time = 0; time < times; ++time) {
      final String p_time = "P:" + (time + 1);
      final AtomicInteger stdin_count = new AtomicInteger(0);
      final AtomicInteger stdout_count = new AtomicInteger(0);

      final ProcessBuilder proc_builder = STDIN_1.copy()
        .addListener(new ProcessListener<Integer>() {
          @Override
          protected void processStarted(IProcess process) throws Throwable {
            //This message should come before the one written in the main thread.
            process.println("M:0 " + p_time);
          }

          @Override
          protected void stdin(IProcess process, ByteBuffer buffer, int bytesWritten, byte[] availablePoolBuffer, int poolBufferSize, Integer attachment) throws Throwable {
            //final String written_message = Charset.defaultCharset().decode(buffer).toString();
            //System.err.println("\nPROCESS PID " + process.getPID() + " WROTE " + bytesWritten + " bytes(s): " + written_message);
            stdin_count.addAndGet(countNewLines(buffer));

            if (attachment <= STANDARD_MESSAGE_COUNT) {
              //Write another message from within the callback.
              assertTrue(process.println("M:" + attachment + " " + p_time, attachment + 1));
            } else {
              //An empty line will ask the test child process to exit.
              assertTrue(process.println());
            }
          }

          @Override
          protected void stdout(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable {
            stdout_count.addAndGet(countNewLines(buffer));
          }
        })
      ;
      final IProcess proc = proc_builder.start();

      //Write a message from the main thread.
      assertTrue(proc.println("M:1 " + p_time, 2));
      assertTrue(proc.await(10, TimeUnit.SECONDS));

      assertEquals(STANDARD_MESSAGE_COUNT + 2, stdin_count.get());
      assertEquals(STANDARD_MESSAGE_COUNT + 1, stdout_count.get());

      //Expected output:
      //M:0
      //M:1
      //M:2
      //M:3
      //M:4
      //M:5
    }
  }

  //@Test
  public void testLaunchProcess() throws Throwable {
//    for(int i = 45; i >= 0; --i) {
//      System.err.println(i + "...");
//      Thread.sleep(1000);
//    }


    final AtomicInteger start_count = new AtomicInteger(0);
    final CountDownLatch stop_latch = new CountDownLatch(times);

    //p.launch("cmd.exe", "/c", "echo", "%PATH%");
    //p.launch(Resources.STDOUT_1, "how", "are", "you");

    //p.launch("cmd.exe", "/c", Resources.STDOUT_ECHO_REPEAT, "4000", "an extremely long line should go here, wouldn't you say? and the number is: " + (i + 1));
    //p.launch(Resources.STDOUT_1, "how", "are", "you");

    for(int time = 0; time < times; ++time) {
      final ProcessBuilder proc_builder = STDOUT_ECHO_REPEAT.copy()
        .addArguments("P:" + (time + 1))
        .addListener(new ProcessListener() {
          private AtomicInteger counter = new AtomicInteger(0);

          @Override
          protected void processError(IProcess process, Throwable t) {
            fail("PID " + process.getPID() + ": " + t.getMessage());
          }

          @Override
          protected void processStarted(IProcess process) throws Throwable {
            System.out.println("PID " + process.getPID() + " STARTED [" + Thread.currentThread().getName() + "] @ " + (new Date().getTime()));
            start_count.incrementAndGet();
          }

          @Override
          protected void processStopped(IProcess process, int exitCode) throws Throwable {
            //System.out.println("PID " + process.getPID() + " STOPPED [" + Thread.currentThread().getName() + "] @ " + (new Date().getTime()));
            System.out.println("PID " + process.getPID() + " STOPPED [" + Thread.currentThread().getName() + "] EXIT CODE " + exitCode + " COUNTER " + counter.get());
            boolean fail = STANDARD_MESSAGE_COUNT > counter.get();
            stop_latch.countDown();
            if (fail)
              System.err.println("Message count (" + counter.get() + ") does not match expected count of " + STANDARD_MESSAGE_COUNT + " for PID " + process.getPID());
          }

          @Override
          protected void stdout(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable {
            final String text = Charset.defaultCharset().decode(buffer).toString();
            //System.out.println("STDOUT @ " + (new Date().getTime()));
            //System.out.print("PID " + process.getPID() + " " + text);
            int idx = -1;
            while((idx = text.indexOf("P", idx + 1)) >= 0)
              counter.incrementAndGet();
          }

          @Override
          protected void stderr(IProcess process, ByteBuffer buffer, int bytesRead, byte[] availablePoolBuffer, int poolBufferSize) throws Throwable {
            final String text = Charset.defaultCharset().decode(buffer).toString();
            //System.err.print("PID " + process.getPID() + " " + text);
            int idx = -1;
            while((idx = text.indexOf("P", idx + 1)) >= 0)
              counter.incrementAndGet();
          }
        })
      ;
      final IProcess process = proc_builder.start();
      //process.waitFor();
      //Thread.sleep(2000);
      //assertTrue(process.waitFor(5, TimeUnit.SECONDS));
    }

    stop_latch.await(10L, TimeUnit.MINUTES);

    //assertEquals(times, start_count.get());
    System.out.println("All done.");
  }
}
