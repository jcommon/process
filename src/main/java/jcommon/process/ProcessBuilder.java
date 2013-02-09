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

import jcommon.core.StringUtil;
import jcommon.core.platform.PlatformProviders;
import jcommon.process.platform.IProcessLauncher;

import java.util.*;

/**
 * Configures a child process for execution using the builder pattern.
 *
 * This class is <b>not</b> thread safe.
 */
@SuppressWarnings("unused")
public class ProcessBuilder implements Cloneable {
  private static final IProcessLauncher impl = PlatformProviders.find(IProcessLauncher.class, IProcessLauncher.DEFAULT);
  private boolean inherit_parent_environment = true;
  private String executable = StringUtil.empty;
  private List<String> arguments = new LinkedList<String>();
  private Map<String, String> env = new LinkedHashMap<String, String>(2, 1.0f);
  private List<IProcessListener> listeners = new LinkedList<IProcessListener>();

  /**
   * Prevent outside instantiation of {@link ProcessBuilder} instances.
   */
  private ProcessBuilder() {
  }

  /**
   * @see Cloneable#clone()
   */
  @Override
  public Object clone() throws CloneNotSupportedException {
    return copy();
  }

  /**
   * Creates a deep copy of this instance.
   *
   * @see Cloneable#clone()
   */
  public ProcessBuilder copy() {
    final ProcessBuilder builder = new ProcessBuilder();
    builder.inherit_parent_environment = inherit_parent_environment;
    builder.executable = executable;
    builder.arguments.addAll(arguments);
    builder.listeners.addAll(listeners);
    builder.env.putAll(env);
    return builder;
  }

  /**
   * Creates a new instance of {@link ProcessBuilder}.
   *
   * @return The new instance oaf {@link ProcessBuilder}.
   */
  public static ProcessBuilder create() {
    return new ProcessBuilder();
  }

  /**
   * Creates a new instance of {@link ProcessBuilder}.
   *
   * @param executable The name of an executable on the system path or the path to an executable.
   * @param arguments Zero or more arguments that will be passed to the executable.
   * @return The new instance of {@link ProcessBuilder}.
   */
  public static ProcessBuilder create(final String executable, final String...arguments) {
    return new ProcessBuilder()
      .withExecutable(executable)
      .withArguments(arguments)
    ;
  }

  /**
   * Indicates if the parent environment variables will be inherited by the child process.
   *
   * By default this is <code>true</code>.
   *
   * @return <code>true</code> if the child process will inherit its parent process'
   *         environment variables.
   */
  public boolean isParentEnvironmentInherited() {
    return this.inherit_parent_environment;
  }

  /**
   * The name or path to the executable that the parent process will launch.
   *
   * @return The name or path to the executable.
   */
  public String getExecutable() {
    return this.executable;
  }

  /**
   * Provides the list of arguments to the executable.
   *
   * @return A {@link String} array containing the list of arguments to the
   *         executable.
   */
  public String[] getArguments() {
    return this.arguments.toArray(new String[this.arguments.size()]);
  }

  /**
   * Constructs the full command line with the executable and arguments.
   * It does not include environment variables.
   *
   * @return A {@link String} array containing the executable and the list
   *         of arguments to the executable.
   */
  public String[] getCommandLine() {
    final String[] command_line = new String[arguments.size() + 1];
    command_line[0] = executable;
    int i = 1;
    for(String arg : arguments) {
      command_line[i] = arg;
      ++i;
    }
    return command_line;
  }

  /**
   * Returns an unmodifiable {@link Map} holding the list of environment variables
   * (their name and value) that will be provided to the child process upon creation.
   *
   * @return An unmodifiable {@link Map} containing the list of currently set environment
   *         variables.
   */
  public Map<String, String> getEnvironmentVariableMap() {
    return Collections.unmodifiableMap(env);
  }

  /**
   * Returns an array of {@link IEnvironmentVariable} instances representing environment
   * variables (their name and value) that will be provided to the child process upon creation.
   *
   * @return An array of {@link IEnvironmentVariable} instances representing the list of
   *         currently set environment variables.
   */
  public IEnvironmentVariable[] getEnvironmentVariables() {
    final Set<Map.Entry<String, String>> vars = env.entrySet();
    final IEnvironmentVariable[] env_vars = new IEnvironmentVariable[vars.size()];

    int i = 0;
    for(Map.Entry<String, String> var : vars) {
      env_vars[i] = new EnvironmentVariable(var.getKey(), var.getValue());
      ++i;
    }
    return env_vars;
  }

  /**
   * Returns an array of {@link IProcessListener} instances who are interested in events
   * on the child process.
   *
   * @return An array of {@link IProcessListener} instances.
   */
  public IProcessListener[] getListeners() {
    return listeners.toArray(new IProcessListener[listeners.size()]);
  }

  /**
   * Saves the name of an executable on the system path or the path to an executable.
   * <br /><br />
   * <b>Note:</b> this will overwrite the previously saved value.
   *
   * @param executable The name of an executable on the system path or the path to an executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withExecutable(final String executable) {
    if (StringUtil.isNullOrEmpty(executable)) {
      throw new IllegalArgumentException("executable cannot be null or empty");
    }

    this.executable = executable;
    return this;
  }

  /**
   * Saves the provided list of arguments.
   * <br /><br />
   * <b>Note:</b> this will overwrite all previously saved arguments.
   *
   * @param arguments Zero or more arguments that will be passed to the executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withArguments(final String...arguments) {
    this.arguments.clear();
    for(String arg : arguments) {
      this.arguments.add(arg);
    }
    return this;
  }

  /**
   * Appends to the list of arguments.
   *
   * @param arguments Zero or more arguments that will be passed to the executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder andArguments(final String...arguments) {
    for(String arg : arguments) {
      this.arguments.add(arg);
    }
    return this;
  }

  /**
   * Appends to the list of arguments.
   *
   * @param argument A single additional argument that will be passed to the executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder andArgument(final String argument) {
    this.arguments.add(argument);
    return this;
  }

  /**
   * Appends to the list of arguments.
   *
   * @param argument A single additional argument that will be passed to the executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder addArgument(final String argument) {
    this.arguments.add(argument);
    return this;
  }

  /**
   * Appends to the list of arguments.
   *
   * @param arguments Zero or more arguments that will be passed to the executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder addArguments(final String...arguments) {
    for(String arg : arguments) {
      this.arguments.add(arg);
    }
    return this;
  }

  /**
   * Removes all saved arguments.
   *
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder clearArguments() {
    this.arguments.clear();
    return this;
  }

  /**
   * Considers the first value in the list to be the executable and then appends the
   * rest to the list of arguments.
   * <br /><br />
   * <b>Note:</b> this will overwrite all previously saved values for both the executable
   * and arguments.
   *
   * @param command_line One or more values that will set the executable and the rest to be passed to the executable.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withCommandLine(final String...command_line) {
    if (command_line == null || command_line.length < 1) {
      throw new IllegalArgumentException("Command line must at least include an executable");
    }

    this.arguments.clear();
    withExecutable(command_line[0]);
    for(int i = 1; i < command_line.length; ++i) {
      this.arguments.add(command_line[i]);
    }
    return this;
  }

  /**
   * Adds an environment variable with the provided name and value to the list of environment
   * variables to be set when the process is created.
   *
   * Unless configured otherwise, these will be appended to the parent process' environment
   * variables.
   *
   * @param name The name of the environment variable to add.
   * @param value The value of the environment variable to add.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder addEnvironmentVariable(final String name, final String value) {
    if (StringUtil.isNullOrEmpty(name)) {
      throw new IllegalArgumentException("name cannot be null or empty");
    }
    if (value == null) {
      throw new IllegalArgumentException("value cannot be null");
    }

    this.env.put(name, value);
    return this;
  }

  /**
   * Sets the list of environment variables when the process is created to be the provided name and value
   * pairs.
   * <br /><br />
   * <b>Note:</b> this will overwrite all previously saved environment variables.
   *
   * Unless configured otherwise, these will be appended to the parent process' environment
   * variables.
   *
   * @param name_value_pairs An alternating list of names and associated values.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withEnvironmentVariables(final String...name_value_pairs) {
    if (name_value_pairs == null || (name_value_pairs.length % 2) != 0) {
      throw new IllegalArgumentException("There must be a matching name and value (there should be an even number of provided arguments)");
    }

    this.env.clear();
    for(int i = 0; i < name_value_pairs.length; i += 2) {
      addEnvironmentVariable(name_value_pairs[i], name_value_pairs[i + 1]);
    }
    return this;
  }

  /**
   * Sets the list of environment variables when the process is created to be the provided name and value
   * pairs.
   * <br /><br />
   * <b>Note:</b> this will overwrite all previously saved environment variables.
   *
   * Unless configured otherwise, these will be appended to the parent process' environment
   * variables.
   *
   * @param name_value_pairs An alternating list of names and associated values.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withEnvironmentVariables(final Map<String, String> name_value_pairs) {
    if (name_value_pairs == null) {
      throw new IllegalArgumentException("There must be a matching name and value (there should be an even number of provided arguments)");
    }

    this.env.clear();
    for(Map.Entry<String, String> e : name_value_pairs.entrySet()) {
      addEnvironmentVariable(e.getKey(), e.getValue());
    }
    return this;
  }

  /**
   * Clears all environment variables.
   *
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder clearEnvironmentVariables() {
    this.env.clear();
    return this;
  }

  /**
   * Sets a flag indicating whether the created child process should inherit its parent
   * environment variables.
   *
   * @param inherit <code>true</code> if the child process will also include its parent
   *                environment variables, <code>false</code> otherwise.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder inheritParentEnvironment(final boolean inherit) {
    this.inherit_parent_environment = inherit;
    return this;
  }

  /**
   * Adds a single listener to the list of {@link IProcessListener} listeners
   * interested in events on the child process.
   *
   * @param listener A single {@link IProcessListener} instance interested
   *                 in events on the child process.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder addListener(final IProcessListener listener) {
    this.listeners.add(listener);
    return this;
  }

  /**
   * Sets the list of {@link IProcessListener} listeners interested in events
   * on the child process to a single {@link IProcessListener} instance.
   * <br /><br />
   * <b>Note:</b> this will overwrite all previously saved listeners.
   *
   * @param listener A single {@link IProcessListener} instance interested
   *                 in events on the child process.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withListener(final IProcessListener listener) {
    this.listeners.clear();
    this.listeners.add(listener);
    return this;
  }

  /**
   * Sets the list of {@link IProcessListener} listeners interested in events
   * on the child process.
   * <br /><br />
   * <b>Note:</b> this will overwrite all previously saved listeners.
   *
   * @param listeners A list of {@link IProcessListener} instances interested
   *                  in events on the child process.
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder withListeners(final IProcessListener...listeners) {
    this.listeners.clear();
    for(IProcessListener listener : listeners) {
      addListener(listener);
    }
    return this;
  }

  /**
   * Clears all {@link IProcessListener} listeners.
   *
   * @return This instance of {@link ProcessBuilder}.
   */
  public ProcessBuilder clearListeners() {
    this.listeners.clear();
    return this;
  }

  public IProcess start() {
    if (executable == null) {
      throw new IllegalStateException("executable must be set before launching a child process");
    }

    impl.launch(inherit_parent_environment, getEnvironmentVariables(), getCommandLine(), getListeners());
    return null;
  }

  private static class EnvironmentVariable implements IEnvironmentVariable {
    private final String name;
    private final String value;

    public EnvironmentVariable(final String name, final String value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EnvironmentVariable that = (EnvironmentVariable) o;

      if (!name.equals(that.name)) return false;
      if (!value.equals(that.value)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + value.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "{" + name + ": " + value + "}";
    }
  }
}
