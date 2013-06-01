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

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.util.Arrays;
import java.util.List;

public class JNAUtils {
  public static <T> List<T> fromSeq(T...iterable) {
    return Arrays.asList(iterable);
  }

  /**
   * Creates a pointer representing a native {@link String}.
   *
   * @param wide <code>true</code> if the String should be wide; <code>false</code> otherwise.
   * @param value The {@link String} that you want a {@link com.sun.jna.Pointer} for.
   *
   * @return An instance of {@link PinnableMemory} which is also a {@link com.sun.jna.Pointer}.
   */
  public static PinnableMemory createPointerToString(boolean wide, String value) {
    PinnableMemory ref;

    if (wide) {
      int len = (value.length() + 1) * Native.WCHAR_SIZE;
      ref = PinnableMemory.pin(len);
      ref.setString(0, value, wide);
    } else {
      byte[] data = Native.toByteArray(value);
      ref = PinnableMemory.pin(data.length + 1);
      ref.write(0, data, 0, data.length);
      ref.setByte(data.length, (byte)0);
    }

    return ref;
  }

  /**
   * Marshals from an array of instances of {@link String} to a {@link com.sun.jna.Pointer}.
   * It's important that you call {@link #disposeStringArray(com.sun.jna.Pointer)} when you
   * are done with it to remove strong references to the memory backing it.
   */
  public static Pointer createPointerToStringArray(boolean wide, String...values) {
    final PinnableMemory m = PinnableMemory.pin(values.length * Pointer.SIZE);
    for(int i = 0; i < values.length; ++i) {
      m.setPointer(i * Pointer.SIZE, values[i] != null ? createPointerToString(wide, values[i]) : null);
    }
    return m;
  }

  public static void disposeStringArray(Pointer ptr) {
    final PinnableMemory orig = PinnableMemory.unpin(ptr);
    final int length = (int)(orig.size() / (long)Pointer.SIZE);

    PinnableMemory entry;
    for(int i = 0; i < length; ++i) {
      if ((entry = PinnableMemory.unpin(orig.getPointer(i * Pointer.SIZE))) != null) {
        entry.dispose();
      }
    }
    orig.dispose();
  }
}
