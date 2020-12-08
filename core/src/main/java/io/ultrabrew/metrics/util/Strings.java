// Copyright 2020, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.
package io.ultrabrew.metrics.util;

public class Strings {

  ///CLOVER:OFF
  private Strings() {
    // static class
  }
  ///CLOVER:ON
  
  /**
   * Whether or not the string is empty or null.
   * @param s A string to test.
   * @return True if the string is empty or null, false if not.
   */
  public static boolean isNullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }
}
