// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;

public class MultiCursor {

  private final Cursor[] cursors;
  private final String[][] nextTagSets;

  private String[] next;
  private int cursorIndex = -1;

  public MultiCursor(final Collection<Aggregator> aggregators) {
    cursors = new Cursor[aggregators.size()];
    nextTagSets = new String[aggregators.size()][];
    initialize(aggregators);
  }

  public boolean next() {
    // Update cursors
    if (next != null) {
      for (int i = 0, l = cursors.length; i < l; i++) {
        if (nextTagSets[i] != null && TagSetsHelper.compare(next, nextTagSets[i]) == 0) {
          nextTagSets[i] = cursors[i].next() ? cursors[i].getTags() : null;
        }
      }
    }

    // Clear the indices
    next = null;
    cursorIndex = -1;

    // Find the next lexical tag set
    for (int i = 0, l = cursors.length; i < l; i++) {
      if (nextTagSets[i] != null) {
        if (next == null || TagSetsHelper.compare(nextTagSets[i], next) < 0) {
          next = nextTagSets[i];
        }
      }
    }

    return next != null;
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP"},
      justification = "Avoid creating copies for performance reasons.")
  public String[] getTags() {
    return next;
  }

  public CursorEntry nextCursorEntry() {
    if (next == null || cursorIndex >= cursors.length) {
      return null;
    }
    cursorIndex++;
    for (int l = cursors.length; cursorIndex < l; cursorIndex++) {
      if (TagSetsHelper.compare(next, nextTagSets[cursorIndex]) == 0) {
        return cursors[cursorIndex];
      }
    }
    return null;
  }

  private void initialize(final Collection<Aggregator> aggregators) {
    int i = 0;
    for (final Aggregator aggregator : aggregators) {
      final Cursor c = aggregator.sortedCursor();
      cursors[i] = c;
      if (c != null && c.next()) {
        nextTagSets[i] = c.getTags();
      }
      i++;
    }
  }
}
