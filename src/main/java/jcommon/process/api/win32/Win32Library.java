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

import com.sun.jna.Library;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public interface Win32Library extends StdCallLibrary, Win32Errors {
  //<editor-fold defaultstate="collapsed" desc="Options">
  /** Standard options to use the unicode version of a w32 API. */
  public static final Map UNICODE_OPTIONS = new HashMap() {{
    put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
    put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
  }};

  /** Standard options to use the ASCII/MBCS version of a w32 API. */
  public static final Map ASCII_OPTIONS = new HashMap() {{
    put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.ASCII);
    put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.ASCII);
  }};

  public static final boolean USE_ASCII = Boolean.getBoolean("w32.ascii");
  public static final boolean USE_UNICODE = !USE_ASCII;

  public static final Map DEFAULT_OPTIONS = USE_ASCII ? ASCII_OPTIONS : UNICODE_OPTIONS;
  //</editor-fold>
}
