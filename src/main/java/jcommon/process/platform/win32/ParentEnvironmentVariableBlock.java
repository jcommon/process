package jcommon.process.platform.win32;

import com.sun.jna.Pointer;
import jcommon.process.EnvironmentVariable;
import jcommon.process.IEnvironmentVariable;
import jcommon.process.IEnvironmentVariableBlock;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static jcommon.process.api.win32.Kernel32.*;

class ParentEnvironmentVariableBlock implements IEnvironmentVariableBlock {
  private static final Charset charset_utf16_le = Charset.forName("UTF-16LE");
  private Set<IEnvironmentVariable> vars = new LinkedHashSet<IEnvironmentVariable>(8, 1.0f);

  public ParentEnvironmentVariableBlock() {
    init();
  }

  private void init() {
    Pointer ptr = GetEnvironmentStrings();
    byte b1, b2;
    int offset = 0;
    int i = 0;

    while(true) {
      b1 = ptr.getByte(i++);
      b2 = ptr.getByte(i++);

      if (b1 == 0 && b2 == 0) {
        final ByteBuffer bb = ptr.getByteBuffer(offset, i - offset - 2);
        final String s = charset_utf16_le.decode(bb).toString();
        final int index_of_equals = s.indexOf('=');
        if (index_of_equals >= 0) {
          final String name = s.substring(0, index_of_equals);
          final String value = s.substring(index_of_equals + 1);

          vars.add(new EnvironmentVariable(name, value));
        }
        offset = i;

        if (ptr.getByte(i + 1) == 0 && ptr.getByte(i + 2) == 0) {
          break;
        }
      }
    }
    FreeEnvironmentStrings(ptr);
  }

  @Override
  public String get(String name) {
    for(IEnvironmentVariable env_var : vars) {
      if (env_var.getName().equals(name)) {
        return env_var.getValue();
      }
    }
    return null;
  }

  @Override
  public boolean contains(String name) {
    for(IEnvironmentVariable env_var : vars) {
      if (env_var.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void enumerateVariables(final IVisitor visitor) {
    for(IEnvironmentVariable env_var : vars) {
      if (!visitor.visit(env_var)) {
        return;
      }
    }
  }

  @Override
  public Iterator<IEnvironmentVariable> iterator() {
    return vars.iterator();
  }
}
