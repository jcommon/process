package jcommon.process.platform.win32;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.List;

import static jcommon.process.api.JNAUtils.fromSeq;

public class IOCPBUFFER extends Structure {
  public Pointer buffer;
  public int bufferSize;
  public int sequenceNumber;
  public int bytesTransferred;

  private static final List FIELD_ORDER = fromSeq(
      "buffer"
    , "bufferSize"
    , "sequenceNumber"
    , "bytesTransferred"
  );

  @Override
  protected List getFieldOrder() {
    return FIELD_ORDER;
  }

  public IOCPBUFFER() {
    super();
  }

  public IOCPBUFFER(Pointer memory) {
    super(memory);
    read();
  }

  public void bytesTransferred(int size) {
    bytesTransferred += size;
  }

  public boolean isMarkedAsEndOfStream() {
    return (bytesTransferred == -1);
  }

  public void markAsEndOfStream() {
    bytesTransferred = -1;
  }

  public static class ByReference extends IOCPBUFFER implements Structure.ByReference {
    public ByReference() {
    }

    public ByReference(Pointer memory) {
        super(memory);
    }
  }
}
