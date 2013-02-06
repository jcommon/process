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

import com.sun.jna.FromNativeContext;
import com.sun.jna.IntegerType;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.ptr.ByReference;

public class Win32 {
  /** Constant value representing an invalid HANDLE. */
  public static final HANDLE
    INVALID_HANDLE_VALUE = new HANDLE(Pointer.createConstant(Pointer.SIZE == 8 ? -1 : 0xFFFFFFFFL))
  ;

  /**
   * Unsigned LONG_PTR.
   */
  public static class ULONG_PTR extends IntegerType {
    public ULONG_PTR() {
      this(0);
    }

    public ULONG_PTR(long value) {
      super(Pointer.SIZE, value, true);
    }

    public Pointer toPointer() {
      return Pointer.createConstant(longValue());
    }
  }

  /**
   * 16-bit unsigned integer.
   */
  public static class WORD extends IntegerType {
    public WORD() {
      this(0);
    }

    public WORD(long value) {
      super(2, value, true);
    }
  }

  /**
   * 32-bit unsigned integer.
   */
  public static class DWORD extends IntegerType {
    public DWORD() {
      this(0);
    }

    public DWORD(long value) {
      super(4, value, true);
    }

    public WORD getLow() {
      return new WORD(longValue() & 0xFF);
    }

    public WORD getHigh() {
      return new WORD((longValue() >> 16) & 0xFF);
    }
  }

  /**
   * Handle to an object.
   */
  public static class HANDLE extends PointerType {
    private boolean immutable;

    public HANDLE() {
    }

    public HANDLE(Pointer p) {
      setPointer(p);
      immutable = true;
    }

    /** Override to the appropriate object for INVALID_HANDLE_VALUE. */
    public Object fromNative(Object nativeValue, FromNativeContext context) {
      Object o = super.fromNative(nativeValue, context);
      if (INVALID_HANDLE_VALUE.equals(o)) {
        return INVALID_HANDLE_VALUE;
      }
      return o;
    }

    public void setPointer(Pointer p) {
      if (immutable) {
        throw new UnsupportedOperationException("immutable reference");
      }

      super.setPointer(p);
    }

    public void reuse(Pointer p) {
      setPointer(p);
    }
  }

  /**
   * LPHANDLE
   */
  public static class HANDLEByReference extends ByReference {
    public HANDLEByReference() {
      this(null);
    }

    public HANDLEByReference(HANDLE h) {
      super(Pointer.SIZE);
      setValue(h);
    }

    public void setValue(HANDLE h) {
      getPointer().setPointer(0, h != null ? h.getPointer() : null);
    }

    public HANDLE getValue() {
      Pointer p = getPointer().getPointer(0);
      if (p == null) {
        return null;
      }
      if (INVALID_HANDLE_VALUE.getPointer().equals(p)) {
        return INVALID_HANDLE_VALUE;
      }
      HANDLE h = new HANDLE();
      h.setPointer(p);
      return h;
    }
  }

}
