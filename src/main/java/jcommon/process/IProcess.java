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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 */
public interface IProcess {
  /**
   * Gets the OS-specific process ID.
   *
   * @return An integer corresponding to the OS-specific process
   *         ID.
   */
  int getPID();

  /**
   * Provides the full command line with the executable and arguments.
   * It does not include environment variables.
   *
   * @return A {@link String} array containing the executable and the list
   *         of arguments to the executable.
   */
  String[] getCommandLine();

  /**
   * Indicates if the parent environment variables will be inherited by the child process.
   *
   * By default this is <code>true</code>.
   *
   * @return <code>true</code> if the child process will inherit its parent process'
   *         environment variables.
   */
  boolean isParentEnvironmentInherited();

  /**
   * Returns an array of {@link IEnvironmentVariable} instances representing environment
   * variables (their name and value) that will be provided to the child process upon creation.
   *
   * @return An array of {@link IEnvironmentVariable} instances representing the list of
   *         currently set environment variables.
   */
  IEnvironmentVariable[] getEnvironmentVariables();

  /**
   * Returns an array of {@link IProcessListener} instances who are interested in events
   * on the child process.
   *
   * @return An array of {@link IProcessListener} instances.
   */
  IProcessListener[] getListeners();

  /**
   * Returns an integer representing the exit code for the process. This value is only
   * valid after the process has exited by a call to {@link #await()} or from
   * {@link IProcessListener#stopped(IProcess, int)} or {@link ProcessListener#processStopped(IProcess, int)}.
   *
   * @return An integer representing the process exit code.
   */
  int getExitCode();

  /**
   * Causes the current thread to wait until the process has exited, unless
   * the thread is {@linkplain Thread#interrupt interrupted}.
   *
   * <p>If the process has already exited, then this method returns
   * immediately.
   *
   * <p>If the process is running then the current thread becomes disabled
   * for thread scheduling purposes and lies dormant until one of two things
   * happen:
   * <ul>
   * <li>The process exits; or
   * <li>Some other thread {@linkplain Thread#interrupt interrupts}
   * the current thread.
   * </ul>
   *
   * <p>If the current thread:
   * <ul>
   * <li>has its interrupted status set on entry to this method; or
   * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
   * </ul>
   * then {@link InterruptedException} is captured, the current thread's
   * interrupted status is cleared, and {@code false} is returned.
   */
  boolean await();

  /**
   * Causes the current thread to wait until the process has exited, unless
   * the thread is {@linkplain Thread#interrupt interrupted}, or the
   * specified waiting time elapses.
   *
   * <p>If the process has already exited then this method returns immediately
   * with the value {@code true}.
   *
   * <p>If the process is running then the current thread becomes disabled for
   * thread scheduling purposes and lies dormant until one of three things happen:
   * <ul>
   * <li>The process exits; or
   * <li>Some other thread {@linkplain Thread#interrupt interrupts}
   * the current thread; or
   * <li>The specified waiting time elapses.
   * </ul>
   *
   * <p>If the process exits then the method returns with the
   * value {@code true}.
   *
   * <p>If the current thread:
   * <ul>
   * <li>has its interrupted status set on entry to this method; or
   * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
   * </ul>
   * then {@link InterruptedException} is captured, the current thread's
   * interrupted status is cleared, and {@code false} is returned.
   *
   * <p>If the specified waiting time elapses then the value {@code false}
   * is returned.  If the time is less than or equal to zero, the method
   * will not wait at all.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the {@code timeout} argument
   * @return {@code true} if the count reached zero and {@code false}
   *         if the waiting time elapsed before the count reached zero
   *         or an {@link InterruptedException} was thrown.
   */
  boolean await(long timeout, TimeUnit unit);

  /**
   * @see #await()
   */
  boolean waitFor();

  /**
   * @see #await(long, java.util.concurrent.TimeUnit)
   */
  boolean waitFor(long timeout, TimeUnit unit);

  boolean write(byte b[]);
  boolean write(byte b[], int off, int len);
  boolean write(ByteBuffer bb);
  boolean println();
  boolean print(CharSequence seq);
  boolean println(CharSequence seq);
  boolean print(Charset charset, CharSequence seq);
  boolean println(Charset charset, CharSequence seq);
}
