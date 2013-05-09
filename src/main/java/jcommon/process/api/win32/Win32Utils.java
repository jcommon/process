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

package jcommon.process.api.win32;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import jcommon.process.api.PinnableMemory;

import java.io.UnsupportedEncodingException;

/**
 *
 */
public class Win32Utils {
  /**
   * LPTSTRs are not constant and can be modified in-memory. So it's
   * important that we create a representation of the String that can
   * be modified.
   *
   * @param value The {@link String} to represent.
   * @return A pointer to the modifiable {@link String}.
   */
  public static Pointer toLPTSTR(String value) {
    return toLPTSTR(value, Win32Library.USE_UNICODE);
  }

  /**
   * LPTSTRs are not constant and can be modified in-memory. So it's
   * important that we create a representation of the String that can
   * be modified.
   *
   * @param value The {@link String} to represent.
   * @param wide <code>true</code> if the value {@link String} is wide, <code>false</code> otherwise.
   * @return A pointer to the modifiable {@link String}.
   */
  public static Pointer toLPTSTR(String value, boolean wide) {
    PinnableMemory ptr = null;
    if (value != null) {
      if (wide) {
        int len = (value.length() + 1) * Native.WCHAR_SIZE;
        ptr = PinnableMemory.pin(len);
        ptr.setString(0, value, true);
      } else {
        byte[] data = getBytes(value);
        ptr = PinnableMemory.pin(data.length + 1);
        ptr.write(0, data, 0, data.length);
        ptr.setByte(data.length, (byte)0);
      }
    }
    return ptr;
  }

  /** Return a byte array corresponding to the given String.  If the
   * system property <code>jna.encoding</code> is set, its value will override
   * the default platform encoding (if supported).
   *
   * Courtesy JNA
   */
  static byte[] getBytes(String s) {
    try {
      return getBytes(s, System.getProperty("jna.encoding"));
    }
    catch (UnsupportedEncodingException e) {
      return s.getBytes();
    }
  }

  /** Return a byte array corresponding to the given String, using the given
   * encoding.
   *
   * Courtesy JNA
   */
  static byte[] getBytes(String s, String encoding) throws UnsupportedEncodingException {
    if (encoding != null) {
      return s.getBytes(encoding);
    }
    return s.getBytes();
  }
}
