package jcommon.process.platform.win32;

import com.sun.jna.Pointer;

import java.util.List;

import static jcommon.process.api.win32.Kernel32.*;
import static jcommon.process.api.JNAUtils.fromSeq;

public class OVERLAPPED_WITH_BUFFER_AND_STATE extends OVERLAPPED {
  public OVERLAPPED ovl;

  public IOCPBUFFER.ByReference iocpBuffer;

  public int state;

  private static final List FIELD_ORDER = fromSeq(
      "Internal"
    , "InternalHigh"
    , "Offset"
    , "hEvent"
    , "iocpBuffer"
    , "state"
    , "ovl"
  );

  @Override
  protected List getFieldOrder() {
    return FIELD_ORDER;
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE() {
    super();
  }

  public OVERLAPPED_WITH_BUFFER_AND_STATE(Pointer memory) {
    super(memory);
    read();
  }
}