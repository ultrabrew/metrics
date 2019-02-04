// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.ultrabrew.metrics.util.TagArray.VariableKey;
import org.junit.jupiter.api.Test;

public class TagArrayTest {

  @Test
  public void testSorting() {
    TagArray.Builder b = TagArray.builder();
    b.constant("z", "1");
    b.constant("a", "2");
    TagArray s = b.build();
    assertArrayEquals(new String[]{"a", "2", "z", "1"}, s.toArray());
  }

  @Test
  public void testVariable() {
    TagArray.Builder b = TagArray.builder();
    VariableKey v1 = b.variable("var1");
    VariableKey v2 = b.variable("var2");
    TagArray s = b.build();
    s.put(v1, "123");
    s.put(v2, "456");
    assertArrayEquals(new String[]{"var1", "123", "var2", "456"}, s.toArray());
  }
}
