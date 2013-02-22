package jcommon.process.api.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import static jcommon.process.api.win32.Win32.*;

public class User32 implements Win32Library  {

  @SuppressWarnings("unused")
  public static final int
      QS_ALLEVENTS = 0x04BF
  ;

  public static native int MsgWaitForMultipleObjectsEx(int nCount, Pointer pHandles, int dwMilliseconds, int dwWakeMask, int dwFlags);
  public static native int MsgWaitForMultipleObjectsEx(int nCount, HANDLE pHandles, int dwMilliseconds, int dwWakeMask, int dwFlags);


  private static final String LIB = "User32";

  static {
    Native.register(LIB);
    CharEncodingSpecific = USE_UNICODE ? new Unicode() : new ASCII();
  }

  static CharacterEncodingSpecificFunctions CharEncodingSpecific;

  public static interface CharacterEncodingSpecificFunctions {
  }

  public static class ASCII implements CharacterEncodingSpecificFunctions {
    static {
      Native.register(LIB);
    }
  }

  public static class Unicode implements CharacterEncodingSpecificFunctions {
    static {
      Native.register(LIB);
    }
  }
}
