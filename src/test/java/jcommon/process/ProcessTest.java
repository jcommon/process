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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class ProcessTest {
  @Test
  public void testLaunchProcess() {
    Extract.extractAllResources();

    ILaunchProcess p = new Win32LaunchProcess();

    //p.launch("cmd.exe", "/c", "echo", "%PATH%");
    for(int i = 0; i < 100; ++i) {
      p.launch("cmd.exe", "/c", "echo", "blah");
    }
  }
}
