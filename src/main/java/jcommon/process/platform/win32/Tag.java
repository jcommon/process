package jcommon.process.platform.win32;

import java.nio.ByteBuffer;

class Tag {
  public final ByteBuffer buffer;
  public final Object attachment;

  public Tag(ByteBuffer buffer, Object attachment) {
    this.buffer = buffer;
    this.attachment = attachment;
  }
}
