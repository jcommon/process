package jcommon.process.api;

import com.sun.jna.ptr.ByReference;

public class ByteByReference extends ByReference {
  public ByteByReference() {
    this((byte)0);
  }

  public ByteByReference(byte value) {
    super(1);
    setValue(value);
  }

  public void setValue(byte value) {
    getPointer().setByte(0, value);
  }

  public byte getValue() {
    return getPointer().getByte(0);
  }
}
