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

import jcommon.core.OSFamily;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ProcessResources {
  public static final int STANDARD_MESSAGE_COUNT = 10;

  private static final ProcessBuilder windows_builder_stdin_1 = ProcessBuilder.create()
    .withExecutable(Resources.STDIN_1)
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_stderr_1 = ProcessBuilder.create()
    .withExecutable(Resources.STDERR_1)
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_stdout_1 = ProcessBuilder.create()
    .withExecutable(Resources.STDOUT_1)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_env_var_echo = ProcessBuilder.create()
    .withExecutable("cmd.exe")
      .andArgument("/c")
      .andArgument(Resources.ENV_VAR_ECHO)
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_stdout_echo_repeat = ProcessBuilder.create()
    .withExecutable("cmd.exe")
      .andArgument("/c")
      .andArgument(Resources.STDOUT_ECHO_REPEAT)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_stderr_echo_repeat = ProcessBuilder.create()
    .withExecutable("cmd.exe")
      .andArgument("/c")
      .andArgument(Resources.STDERR_ECHO_REPEAT)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_stdout_stderr_echo_repeat = ProcessBuilder.create()
    .withExecutable("cmd.exe")
      .andArgument("/c")
      .andArgument(Resources.STDOUT_STDERR_ECHO_REPEAT)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder windows_builder_server = ProcessBuilder.create()
    .withExecutable("cmd.exe")
      .andArgument("/c")
      .andArgument("C:\\gitmo\\websphere-8.5.0\\bin\\server.bat")
      .andArgument("start")
      .andArgument("8280")
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder unix_builder_stdin_1 = ProcessBuilder.create()
    .withExecutable(Resources.STDIN_1)
      .withListener(
          StandardStreamPipe.create()
      )
  ;

  private static final ProcessBuilder unix_builder_stderr_1 = ProcessBuilder.create()
    .withExecutable(Resources.STDERR_1)
      .withListener(
          StandardStreamPipe.create()
      )
  ;

  private static final ProcessBuilder unix_builder_stdout_1 = ProcessBuilder.create()
    .withExecutable(Resources.STDOUT_1)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
      )
  ;

  private static final ProcessBuilder unix_builder_env_var_echo = ProcessBuilder.create()
    .withExecutable(Resources.ENV_VAR_ECHO)
      .withListener(
          StandardStreamPipe.create()
          //.redirectStdOut(StandardStream.Null)
          //.redirectStdErr(StandardStream.Null)
      )
  ;

  private static final ProcessBuilder unix_builder_stdout_echo_repeat = ProcessBuilder.create()
    .withExecutable(Resources.STDOUT_ECHO_REPEAT)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
      )
  ;

  private static final ProcessBuilder unix_builder_stderr_echo_repeat = ProcessBuilder.create()
    .withExecutable(Resources.STDERR_ECHO_REPEAT)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
      )
  ;

  private static final ProcessBuilder unix_builder_stdout_stderr_echo_repeat = ProcessBuilder.create()
    .withExecutable(Resources.STDOUT_STDERR_ECHO_REPEAT)
      .andArgument(Integer.toString(STANDARD_MESSAGE_COUNT))
      .withListener(
          StandardStreamPipe.create()
      )
  ;

  public static final ProcessBuilder
      STDIN_1
    , STDERR_1
    , STDOUT_1

    , ENV_VAR_ECHO

    , STDERR_ECHO_REPEAT
    , STDOUT_ECHO_REPEAT
    , STDOUT_STDERR_ECHO_REPEAT
  ;

  static {
    assertTrue(Resources.loadAllResources());

    switch (OSFamily.getSystemOSFamily()) {
      case Windows:
        STDIN_1 = windows_builder_stdin_1;
        STDERR_1 = windows_builder_stderr_1;
        STDOUT_1 = windows_builder_stdout_1;

        ENV_VAR_ECHO = windows_builder_env_var_echo;

        STDERR_ECHO_REPEAT = windows_builder_stderr_echo_repeat;
        STDOUT_ECHO_REPEAT = windows_builder_stdout_echo_repeat;
        STDOUT_STDERR_ECHO_REPEAT = windows_builder_stdout_stderr_echo_repeat;
        break;
      case Unix:
        STDIN_1 = unix_builder_stdin_1;
        STDERR_1 = unix_builder_stderr_1;
        STDOUT_1 = unix_builder_stdout_1;

        ENV_VAR_ECHO = unix_builder_env_var_echo;

        STDERR_ECHO_REPEAT = unix_builder_stderr_echo_repeat;
        STDOUT_ECHO_REPEAT = unix_builder_stdout_echo_repeat;
        STDOUT_STDERR_ECHO_REPEAT = unix_builder_stdout_stderr_echo_repeat;
        break;
      default:
        throw new UnsupportedOperationException("This operating system (" + OSFamily.getSystemOSFamily() + ") is unsupported");
    }
  }
}
