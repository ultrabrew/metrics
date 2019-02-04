// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

public enum Type {
  LONG {
    @Override
    public String readAndReset(final CursorEntry cursorEntry, final int index) {
      return String.valueOf(cursorEntry.readAndResetLong(index));
    }
  }, DOUBLE {
    @Override
    public String readAndReset(final CursorEntry cursorEntry, final int index) {
      return String.valueOf(cursorEntry.readAndResetDouble(index));
    }
  };

  public abstract String readAndReset(final CursorEntry cursorEntry, final int index);
}
