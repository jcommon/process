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

package jcommon.process.api;

import com.sun.jna.ptr.ByReference;

public class ByteByReference extends ByReference {
  public ByteByReference() {
    this((byte)0);
  }

  public ByteByReference(byte value) {
    super(1);
    setValue(value);
  }

  public void setValue(byte value) {
    getPointer().setByte(0, value);
  }

  public byte getValue() {
    return getPointer().getByte(0);
  }
}
