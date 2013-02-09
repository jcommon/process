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

/**
 */
public interface IProcess {
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
}
