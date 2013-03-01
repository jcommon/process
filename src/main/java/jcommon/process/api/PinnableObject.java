package jcommon.process.api;

import java.util.HashSet;
import java.util.Set;

public class PinnableObject<T extends Object> {
  private static Set<Object> pinned = new HashSet<Object>(10, 1.0f);

  public static <T extends Object> T pin(final T instance) {
    pinned.add(instance);
    return instance;
  }

  public static <T extends Object> T unpin(final T instance) {
    pinned.remove(instance);
    return instance;
  }
}
