package jcommon.process.platform.win32;

import com.sun.jna.Structure;

import java.util.List;

import static jcommon.process.api.JNAUtils.fromSeq;

public class CONTEXT extends Structure {
  public int sentinel;

  private static final List FIELD_ORDER = fromSeq(
      "sentinel"
  );

  @Override
  protected List getFieldOrder() {
    return FIELD_ORDER;
  }
}