package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.api.win32.Kernel32;

import java.util.List;

import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.JNAUtils.fromSeq;

public final class OVERLAPPED_WITH_BUFFER_AND_STATE extends OVERLAPPED {
  public OVERLAPPED ovl;
  public Pointer buffer;
  public int bufferSize;
  public int state;

  private static final List FIELD_ORDER = fromSeq(
      "Internal"
    , "InternalHigh"
    , "Offset"
    , "OffsetHigh"
    , "hEvent"
    , "ovl"
    , "buffer"
    , "bufferSize"
    , "state"
  );

  @Override
  protected List getFieldOrder() {
    return FIELD_ORDER;
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE() {
    super();
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE(Pointer memory) {
    super();
    reuse(memory);
  }

  public void reuse(Pointer memory) {
    useMemory(memory);
    read();
  }

  public static String nameForState(int state) {
    switch(state) {
      case STATE_EXITTHREAD:
        return "STATE_EXITTHREAD";

      default:
        return "<UNKNOWN:" + state + ">";
    }
  }

  public static final int
      STATE_EXITTHREAD = -1
  ;
}