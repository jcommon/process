package jcommon.process.platform.win32;

import java.util.concurrent.atomic.AtomicInteger;

import static jcommon.process.api.win32.Win32.*;
import static jcommon.process.api.win32.Kernel32.*;

public final class Utils {
  private static final AtomicInteger overlapped_pipe_serial_number = new AtomicInteger(0);

  public static void gc() {
    for(int i = 0; i <= 5; ++i) {
      System.gc();
    }
  }

  public static int indexOfAny(final String value, final String lookingFor) {
    for(int i = 0; i < lookingFor.length(); ++i) {
      int index = lookingFor.indexOf(lookingFor.charAt(i));
      if (index >= 0)
        return index;
    }
    return -1;
  }

  public static void fill(final StringBuilder sb, final char c, final int times) {
    if (times < 0)
      return;

    for(int i = 0; i < times; ++i) {
      sb.append(c);
    }
  }

  /**
   * Attempt to discover if an executable is cmd.exe or not. If it
   * is, then we'll need to (later on) specially encode its parameters.
   */
  public static boolean isCmdExe(final String executable) {
    return (
         "\"cmd.exe\"".equalsIgnoreCase(executable)
      || "\"cmd\"".equalsIgnoreCase(executable)
      || "cmd.exe".equalsIgnoreCase(executable)
      || "cmd".equalsIgnoreCase(executable)
    );
  }

  /**
   * When parsing the command line for cmd.exe, special care must be taken
   * to escape certain meta characters to avoid malicious attempts to inject
   * commands.
   *
   * All meta characters will have a '^' placed in front of them which instructs
   * cmd.exe to interpret the next character literally.
   */
  public static boolean isCmdExeMetaCharacter(final char c) {
    return (
         c == '('
      || c == ')'
      || c == '%'
      || c == '!'
      || c == '^'
      || c == '\"'
      || c == '<'
      || c == '>'
      || c == '&'
      || c == '|'
    );
  }

  /**
   * Encodes a string for proper interpretation by CreateProcess().
   *
   * @see <a href="http://blogs.msdn.com/b/twistylittlepassagesallalike/archive/2011/04/23/everyone-quotes-arguments-the-wrong-way.aspx">http://blogs.msdn.com/b/twistylittlepassagesallalike/archive/2011/04/23/everyone-quotes-arguments-the-wrong-way.aspx</a>
   */
  public static String encode(final boolean is_cmd_exe, final String arg) {
    if (!"".equals(arg) && indexOfAny(arg, " \t\n\11\"") < 0) {
      return arg;
    } else {
      final int len = arg.length();
      final StringBuilder sb = new StringBuilder(len);

      if (is_cmd_exe) {
        //sb.append('^');
      }

      if (!is_cmd_exe) {
        sb.append('\"');
      }

      char c;
      int number_backslashes;

      for(int i = 0; i < len; ++i) {
        number_backslashes = 0;

        while(i < len && '\\' == arg.charAt(i)) {
          ++i;
          ++number_backslashes;
        }

        c = arg.charAt(i);

        if (i == len) {
          //Escape all backslashes, but let the terminating
          //double quotation mark we add below be interpreted
          //as a metacharacter.
          fill(sb, '\\', number_backslashes * 2);
          break;
        } else if (c == '\"') {
          //Escape all backslashes and the following
          //double quotation mark.
          fill(sb, '\\', number_backslashes * 2 + 1);
          if (is_cmd_exe) {
            sb.append('^');
          }
          sb.append(c);
        } else {
          //Backslashes aren't special here.
          fill(sb, '\\', number_backslashes);
          if (is_cmd_exe && isCmdExeMetaCharacter(c)) {
            sb.append('^');
          }
          sb.append(c);
        }
      }

      if (is_cmd_exe) {
        //sb.append('^');
      }

      if (!is_cmd_exe) {
        sb.append('\"');
      }
      return sb.toString();
    }
  }

  public static String formulateSanitizedCommandLine(final String...args) {
    if (args == null || args.length <= 1 || "".equals(args[0])) {
      throw new IllegalArgumentException("args cannot be null or empty and provide at least the executable as the first argument.");
    }

    if (args[0].length() > MAX_PATH) {
      throw new IllegalArgumentException("The application path cannot exceed " + MAX_PATH + " characters.");
    }

    int size = 0;
    for(int i = 0; i < args.length; ++i) {
      size += args[i].length();
    }

    final StringBuilder sb = new StringBuilder(size + (args.length * 3 /* Space and beginning and ending quotes */));
    final String executable = encode(false, args[0].trim());
    final boolean is_cmd_exe = isCmdExe(executable);

    sb.append(executable);
    for(int i = 1; i < args.length; ++i) {
      //Separate each argument with a space.
      sb.append(' ');
      sb.append(encode(!is_cmd_exe ? false : args[i].startsWith("/") ? false : true, args[i]));
    }

    //Validate total length of the arguments.
    if (sb.length() > MAX_COMMAND_LINE_SIZE) {
      throw new IllegalArgumentException("The complete command line cannot exceed " + MAX_COMMAND_LINE_SIZE + " characters.");
    }

    return sb.toString();
  }

  @SuppressWarnings("all")
  public static boolean CreateOverlappedPipe(HANDLEByReference lpReadPipe, HANDLEByReference lpWritePipe, SECURITY_ATTRIBUTES lpPipeAttributes, int nSize, int dwReadMode, int dwWriteMode) {
    if (((dwReadMode | dwWriteMode) & (~FILE_FLAG_OVERLAPPED)) != 0) {
      throw new IllegalArgumentException("This method is to be used for overlapped IO only.");
    }

    if (nSize == 0) {
      nSize = 1024;
    }

    final String pipe_name = "\\\\.\\Pipe\\RemoteExeAnon." + GetCurrentProcessId() + "." + overlapped_pipe_serial_number.incrementAndGet();

    final HANDLE ReadPipeHandle = CreateNamedPipe(pipe_name, PIPE_ACCESS_INBOUND | FILE_FLAG_OVERLAPPED, PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT, 1, nSize, nSize, 0, lpPipeAttributes);
    if (ReadPipeHandle == INVALID_HANDLE_VALUE) {
      return false;
    }

    final HANDLE WritePipeHandle = CreateFile(pipe_name, GENERIC_WRITE, 0, lpPipeAttributes, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, null);
    if (WritePipeHandle == INVALID_HANDLE_VALUE) {
      CloseHandle(ReadPipeHandle);
      return false;
    }

    lpReadPipe.setValue(ReadPipeHandle);
    lpWritePipe.setValue(WritePipeHandle);

    return true;
  }
}
