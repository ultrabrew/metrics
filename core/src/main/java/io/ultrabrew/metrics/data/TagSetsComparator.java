// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import java.io.Serializable;
import java.util.Comparator;

class TagSetsComparator implements Comparator<Integer>, Serializable {

  /**
   * Compares its two arguments for order. Returns a negative integer, zero, or a positive integer
   * as the first argument is less than, equal to, or greater than the second.
   *
   * @param o1 the first object to be compared
   * @param o2 the second object to be compared
   * @return a negative integer, zero, or a positive integer as the first argument is less than,
   * equal to, or greater than the second.
   */
  private final String[][] set;
  public TagSetsComparator(String[][] set) {
    this.set = set;
  }
  
  @Override
  public int compare(final Integer i1, final Integer i2) {
    if(i1 == null && i2 == null) {
      return 0;
    }
    
    if (i2 == null) {
      return -1;
    }
    if (i1 == null) {
      return 1;
    }
    
    if (i1 >= set.length) {
      throw new IndexOutOfBoundsException(
          "Set length is " + set.length + " and index passed is " + i1);
    }
    if (i2 >= set.length) {
      throw new IndexOutOfBoundsException(
          "Set length is " + set.length + " and index passed is " + i2);
    }
    String[] o1 = set[i1];
    String[] o2 = set[i2];
    if (o1 == null && o2 == null) {
      return 0;
    }
    if (o2 == null) {
      return -1;
    }
    if (o1 == null) {
      return 1;
    }

    final int len1 = o1.length;
    final int len2 = o2.length;
    final int lim = Math.min(len1, len2);

    for (int k = 0; k < lim; k++) {
      if (o1[k] == null && o2[k] != null) {
        return 1;
      }
      if (o1[k] != null && o2[k] == null) {
        return -1;
      }
      if (o1[k] != null && o2[k] != null) {
        int i = o1[k].compareTo(o2[k]);
        if (i != 0) {
          return i;
        }
      }
    }

    return len1 - len2;
  }
  
  static int compare(final String[] o1, final String[] o2) {

    if (o1 == null && o2 == null) {
      return 0;
    }
    if (o2 == null) {
      return -1;
    }
    if (o1 == null) {
      return 1;
    }

    final int len1 = o1.length;
    final int len2 = o2.length;
    final int lim = Math.min(len1, len2);

    for (int k = 0; k < lim; k++) {
      if (o1[k] == null && o2[k] != null) {
        return 1;
      }
      if (o1[k] != null && o2[k] == null) {
        return -1;
      }
      if (o1[k] != null && o2[k] != null) {
        int i = o1[k].compareTo(o2[k]);
        if (i != 0) {
          return i;
        }
      }
    }

    return len1 - len2;
  }
}
