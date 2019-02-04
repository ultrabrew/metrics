// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * An internal helper class to access {@link Unsafe}.
 */
public class UnsafeHelper {

  public static final Unsafe unsafe = getUnsafe();

  static Unsafe getUnsafe() {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      return (Unsafe) f.get(null);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to get unsafe instance, are you running Oracle JDK?", e);
    }
  }
}
