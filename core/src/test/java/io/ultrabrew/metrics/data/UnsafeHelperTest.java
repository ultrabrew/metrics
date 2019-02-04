// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import mockit.Expectations;
import org.junit.jupiter.api.Test;

public class UnsafeHelperTest {

  private UnsafeHelper dummy;

  @Test
  public void testIllegalAccessException() throws Exception {
    assertNotNull(UnsafeHelper.unsafe);

    Field field = UnsafeHelperTest.class.getDeclaredField("dummy");
    new Expectations(Field.class) {{
      field.get(null);
      result = new IllegalAccessException("test");
    }};
    try {
      UnsafeHelper.getUnsafe();
      fail("No exception thrown");
    } catch (Throwable t) {
      // Intellij gives different exception
      if (t instanceof RuntimeException) {
        assertTrue(t.getCause() instanceof IllegalAccessException);
        assertEquals("test", t.getCause().getMessage());
      } else {
        assertTrue(t.getCause().getCause() instanceof IllegalAccessException);
        assertEquals("test", t.getCause().getCause().getMessage());
      }
    }
  }
}
