package jcommon.process.api;

import java.util.Arrays;
import java.util.List;

public class JNAUtils {
  public static <T> List<T> fromSeq(T...iterable) {
    return Arrays.asList(iterable);
  }
}
