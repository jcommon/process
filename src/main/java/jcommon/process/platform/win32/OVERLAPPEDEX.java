package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.api.win32.Kernel32;

import java.util.List;

import static jcommon.process.api.JNAUtils.fromSeq;

public final class OVERLAPPEDEX extends Kernel32.OVERLAPPED {
  public Kernel32.OVERLAPPED ovl;
  public Pointer buffer;
  public int bufferSize;
  public int op;

  private static final List FIELD_ORDER = fromSeq(
      "Internal"
    , "InternalHigh"
    , "Offset"
    , "OffsetHigh"
    , "hEvent"
    , "ovl"
    , "buffer"
    , "bufferSize"
    , "op"
  );

  @Override
  protected List getFieldOrder() {
    return FIELD_ORDER;
  }

  public void reuse(Pointer memory) {
    useMemory(memory);
    read();
  }

  public static String nameForOp(int op) {
    switch(op) {
      case OP_INITIATE_CONNECT:
        return "OP_INITIATE_CONNECT";

      case OP_STDOUT_CONNECT:
        return "OP_STDOUT_CONNECT";
      case OP_STDOUT_READ:
        return "OP_STDOUT_READ";
      case OP_STDOUT_REQUEST_READ:
        return "OP_STDOUT_REQUEST_READ";
      case OP_STDOUT_CLOSE:
        return "OP_STDOUT_CLOSE";
      case OP_STDOUT_CLOSE_WAIT:
        return "OP_STDOUT_CLOSE_WAIT";

      case OP_STDERR_CONNECT:
        return "OP_STDERR_CONNECT";
      case OP_STDERR_READ:
        return "OP_STDERR_READ";
      case OP_STDERR_REQUEST_READ:
        return "OP_STDERR_REQUEST_READ";
      case OP_STDERR_CLOSE:
        return "OP_STDERR_CLOSE";
      case OP_STDERR_CLOSE_WAIT:
        return "OP_STDERR_CLOSE_WAIT";

      case OP_STDIN_CONNECT:
        return "OP_STDIN_CONNECT";
      case OP_STDIN_WRITE:
        return "OP_STDIN_WRITE";
      case OP_STDIN_REQUEST_WRITE:
        return "OP_STDIN_REQUEST_WRITE";
      case OP_STDIN_CLOSE:
        return "OP_STDIN_CLOSE";
      case OP_STDIN_CLOSE_WAIT:
        return "OP_STDIN_CLOSE_WAIT";

      case OP_PARENT_DISCONNECT:
        return "OP_PARENT_DISCONNECT";
      case OP_CHILD_DISCONNECT:
        return "OP_CHILD_DISCONNECT";

      case OP_CONNECTED:
        return "OP_CONNECTED";
      case OP_INITIATE_CLOSE:
        return "OP_INITIATE_CLOSE";
      case OP_CLOSED:
        return "OP_CLOSED";
      case STATE_EXITTHREAD:
        return "STATE_EXITTHREAD";

      default:
        return "<UNKNOWN:" + op + ">";
    }
  }

  public static final int
      OP_INITIATE_CONNECT    =  0
    , OP_STDOUT_CONNECT      =  1
    , OP_STDOUT_READ         =  2
    , OP_STDOUT_REQUEST_READ =  3
    , OP_STDOUT_CLOSE        =  4
    , OP_STDOUT_CLOSE_WAIT   =  5

    , OP_STDERR_CONNECT      =  6
    , OP_STDERR_READ         =  7
    , OP_STDERR_REQUEST_READ =  8
    , OP_STDERR_CLOSE        =  9
    , OP_STDERR_CLOSE_WAIT   = 10

    , OP_STDIN_CONNECT       = 11
    , OP_STDIN_WRITE         = 12
    , OP_STDIN_REQUEST_WRITE = 13
    , OP_STDIN_CLOSE         = 14
    , OP_STDIN_CLOSE_WAIT    = 15

    , OP_PARENT_DISCONNECT   = 16
    , OP_CHILD_DISCONNECT    = 17

    , OP_CONNECTED           = 18
    , OP_INITIATE_CLOSE      = 19
    , OP_CLOSED              = 20
  ;

  public static final int
      STATE_EXITTHREAD = -1
  ;
}