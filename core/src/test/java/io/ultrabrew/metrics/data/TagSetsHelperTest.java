// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TagSetsHelperTest {

  @Test
  public void testComparison() {
    final String[] tagSet1 = new String[]{"testTag", "value"};
    final String[] tagSet2 = new String[]{"testTag", "value", "testTag2", "value2"};
    final String[] tagSet3 = new String[]{"testTag", "value2"};
    final String[] tagSet4 = new String[]{"testTag3", "value"};
    final String[] tagSet5 = new String[]{"testTag3", "value"};
    final String[] tagSet6 = null;

    assertEquals(0, TagSetsHelper.compare(tagSet1, tagSet1));
    assertEquals(0, TagSetsHelper.compare(tagSet2, tagSet2));
    assertEquals(0, TagSetsHelper.compare(tagSet3, tagSet3));
    assertEquals(0, TagSetsHelper.compare(tagSet4, tagSet4));
    assertEquals(0, TagSetsHelper.compare(tagSet4, tagSet5));
    assertEquals(0, TagSetsHelper.compare(tagSet6, tagSet6));

    assertThat(TagSetsHelper.compare(tagSet1, tagSet2), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet1, tagSet3), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet1, tagSet4), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet2, tagSet3), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet2, tagSet4), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet3, tagSet4), lessThan(0));

    assertThat(TagSetsHelper.compare(tagSet1, tagSet6), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet2, tagSet6), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet3, tagSet6), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet4, tagSet6), lessThan(0));

    assertThat(TagSetsHelper.compare(tagSet5, tagSet3), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet5, tagSet2), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet5, tagSet1), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet3, tagSet2), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet3, tagSet1), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet2, tagSet1), greaterThan(0));

    assertThat(TagSetsHelper.compare(tagSet6, tagSet4), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet6, tagSet3), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet6, tagSet2), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet6, tagSet1), greaterThan(0));
  }

  @Test
  public void testNullComparison() {
    final String[] tagSet1 = new String[]{"testTag", null};
    final String[] tagSet2 = new String[]{"testTag", "value"};
    final String[] tagSet3 = new String[]{null, "value"};

    assertEquals(0, TagSetsHelper.compare(tagSet1, tagSet1));
    assertEquals(0, TagSetsHelper.compare(tagSet2, tagSet2));
    assertEquals(0, TagSetsHelper.compare(tagSet3, tagSet3));

    assertThat(TagSetsHelper.compare(tagSet2, tagSet1), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet2, tagSet3), lessThan(0));
    assertThat(TagSetsHelper.compare(tagSet1, tagSet3), lessThan(0));

    assertThat(TagSetsHelper.compare(tagSet1, tagSet2), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet3, tagSet1), greaterThan(0));
    assertThat(TagSetsHelper.compare(tagSet3, tagSet2), greaterThan(0));
  }
  
  @Test
  public void testComparator() {
    final String[] tagSet1 = new String[] {"testTag", null};
    final String[] tagSet2 = new String[] {"testTag", "value"};
    final String[] tagSet3 = new String[] {null, "value"};
    final String[] tagSet4 = new String[] {null, null};
    String[][] tagSet = new String[5][2];
    tagSet[0] = tagSet1;
    tagSet[1] = tagSet2;
    tagSet[2] = tagSet3;
    tagSet[3] = tagSet4;
    tagSet[4] = null;
    TagSetsHelper comparator = new TagSetsHelper(tagSet);
    Integer first = null;
    Integer second = null;

    int compare = comparator.compare(first, second);
    assertEquals(compare, 0);

    first = null;
    second = 0;
    compare = comparator.compare(first, second);
    assertEquals(compare, 1);
    first = 0;
    second = null;
    compare = comparator.compare(first, second);
    assertEquals(compare, -1);

    first = 1;
    second = 1;
    compare = comparator.compare(first, second);
    assertEquals(compare, 0);

    first = 4;
    compare = comparator.compare(first, second);
    
    first = 1;
    second = 4;
    compare = comparator.compare(first, second);
    
    first = 5;
    try {
      compare = comparator.compare(first, second);
      Assertions.fail("Index is out of bounds, should have been caught");
    } catch (Exception e) {
    }
    first = 1;
    second = 5;
    try {
      compare = comparator.compare(first, second);
      Assertions.fail("Index is out of bounds, should have been caught");
    } catch (Exception e) {
    }
  }
  
  @Test
  public void testNullComparator() {
    final String[] tagSet1 = new String[]{"testTag", null};
    final String[] tagSet2 = new String[]{"testTag", "value"};
    final String[] tagSet3 = new String[]{null, "value"};
    
    String[][] tagSet = new String[3][2];
    tagSet[0] = tagSet1;
    tagSet[1] = tagSet2;
    tagSet[2] = tagSet3;
    TagSetsHelper comparator = new TagSetsHelper(tagSet);
    Integer first = 0;
    Integer second = 1;
    assertEquals(1, comparator.compare(first, second));
    
    first = 1;
    second = 0;
    assertEquals(-1, comparator.compare(first, second));
    
    first = 0;
    second = 2;
    assertEquals(-1, comparator.compare(first, second));
    
    first = 0;
    second = 0;
    assertEquals(0, comparator.compare(first, second));
    
    first = 2;
    second = 0;
    assertEquals(1, comparator.compare(first, second));
    
    first = 2;
    second = 2;
    assertEquals(0, comparator.compare(first, second));
    
  }
}
