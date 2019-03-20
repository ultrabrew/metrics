package io.ultrabrew.metrics.util;

public class Commons {

  public static void checkArgument(final boolean condition, final String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
