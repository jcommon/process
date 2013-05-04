package jcommon.process.api.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import static jcommon.process.api.win32.Win32.HANDLE;

public class Userenv implements Win32Library  {

  public static native boolean CreateEnvironmentBlock(PointerByReference lpEnvironment, HANDLE hToken, boolean bInherit);
  public static native boolean DestroyEnvironmentBlock(Pointer lpEnvironment);


  private static final String LIB = "Userenv";

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
