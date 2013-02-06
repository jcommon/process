package jcommon.process.api.win32;

import com.sun.jna.Library;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.HashMap;
import java.util.Map;

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
