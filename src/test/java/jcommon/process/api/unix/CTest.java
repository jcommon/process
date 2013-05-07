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

package jcommon.process.api.unix;

import org.junit.BeforeClass;
import org.junit.Test;

import static jcommon.process.ProcessResources.*;
import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class CTest {

  @BeforeClass
  public static void before() {
  }

  @Test
  public void testBasicLibraryLoad() throws Throwable {
    final int fd = C.epoll_create(1);
    assertTrue(fd > 0);
    assertTrue(C.close(fd) == 0);
  }
}
