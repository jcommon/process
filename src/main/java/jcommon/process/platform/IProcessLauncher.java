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

package jcommon.process.platform;

import jcommon.core.platform.IPlatformImplementation;
import jcommon.process.IEnvironmentVariableBlock;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IProcess;
import jcommon.process.IProcessListener;

import java.io.Serializable;

public interface IProcessLauncher extends IPlatformImplementation {
  public static class Default implements IProcessLauncher, Serializable {
    public static final IProcessLauncher INSTANCE = new Default();

    private Default() {
      //Prevent outside instantiation
    }

    private Object readResolve() {
      return INSTANCE;
    }

    @Override
    public final IEnvironmentVariableBlock requestParentEnvironmentVariableBlock() {
      return null;
    }

    @Override
    public final IProcess launch(final boolean inherit_parent_environment, final IEnvironmentVariableBlock environment_variables, final String[] command_line, final IProcessListener[] listeners) {
      throw new UnsupportedOperationException("launch");
    }
  }

  public static final IProcessLauncher DEFAULT = Default.INSTANCE;

  IEnvironmentVariableBlock requestParentEnvironmentVariableBlock();
  IProcess launch(final boolean inherit_parent_environment, final IEnvironmentVariableBlock environment_variables, final String[] command_line, final IProcessListener[] listeners);
}
