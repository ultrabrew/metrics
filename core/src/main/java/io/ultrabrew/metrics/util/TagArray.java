// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.util;

import io.ultrabrew.metrics.Metric;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Helper class for handling tag arrays.
 * <p>Using this class ensures the keys are always in same order and minimizes temporary objects
 * created.</p>
 * <pre>{@code
 *     public class TestResource {
 *         private static final String TAG_HOST = "host";
 *         private static final String TAG_RESOURCE = "resource";
 *
 *         private final TagArray tagArray;
 *         private final VariableKey resourceKey;
 *
 *         private final GaugeDouble cpuUsageGauge;
 *
 *         public TestResource(final MetricRegistry metricRegistry, final String hostName) {
 *             TagArray.Builder b = TagArray.builder();
 *             b.constant(TAG_HOST, hostName);
 *             this.resourceKey = b.variable(TAG_RESOURCE);
 *             this.tagArray = b.build();
 *
 *             this.cpuUsageGauge = metricRegistry.gaugeDouble("cpuUsage");
 *         }
 *
 *         public void doSomething(String resource) {
 *             double d = getCpuUsage();
 *             tagArray.put(resourceKey, resource);
 *             cpuUsageGauge.set(d, tagArray.toArray());
 *         }
 *     }
 * }</pre>
 */
public class TagArray {

  private final ThreadLocal<String[]> array;

  private TagArray(final String[] array) {
    this.array = ThreadLocal.<String[]>withInitial(array::clone);
  }

  /**
   * Create a new builder for constructing a {@link TagArray}.
   *
   * @return new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Set the value for given key in the tag array.
   * <p>If all values are not set before {@link #toArray()} is called the result is undefined.</p>
   * <p>Must be called for each {@link VariableKey} in the {@link TagArray} immediately before
   * calling {@link #toArray()} in the same thread.</p>
   * <p><b>Note:</b> The key must be related to this {@link TagArray}.</p>
   *
   * @param key key to set value for
   * @param value the new value
   */
  public void put(final VariableKey key, final String value) {
    array.get()[key.index] = value;
  }

  /**
   * Get the raw array representation of this {@link TagArray} for passing to a {@link Metric}.
   *
   * @return the raw array
   */
  public String[] toArray() {
    return array.get();
  }

  private abstract static class Key {

    protected String getInitialValue() {
      return null;
    }

    protected void setIndex(final int index) {
    }
  }

  /**
   * Builder class for constructing {@link TagArray} instances.
   */
  public static class Builder {

    private final Map<String, Key> tags = new HashMap<>();

    private Builder() {
    }

    /**
     * Add a new key/value pair to the array with constant value.
     *
     * @param key the key
     * @param value the constant value
     */
    public void constant(final String key, final String value) {
      tags.put(key, new ConstantKey(value));
    }

    /**
     * Add a new key with variable value set later to the array.
     *
     * @param key the key
     * @return key token to be passed to {@link #put(VariableKey, String)}
     */
    public VariableKey variable(final String key) {
      VariableKey holder = new VariableKey();
      tags.put(key, holder);
      return holder;
    }

    /**
     * Build a new {@link TagArray} with the keys and values configured to this {@link Builder}.
     *
     * @return a new {@link TagArray} instance
     */
    public TagArray build() {
      List<Map.Entry<String, Key>> sortedByKey = tags.entrySet().stream()
          .sorted(Comparator.<Entry<String, Key>, String>comparing(Entry::getKey))
          .collect(Collectors.toList());
      String[] array = new String[sortedByKey.size() * 2];
      int i = 0;
      for (Map.Entry<String, Key> entry : sortedByKey) {
        array[i] = entry.getKey();
        array[i + 1] = entry.getValue().getInitialValue();
        entry.getValue().setIndex(i + 1);
        i += 2;
      }
      return new TagArray(array);
    }
  }

  private static final class ConstantKey extends Key {

    private final String value;

    private ConstantKey(final String value) {
      this.value = value;
    }

    @Override
    public String getInitialValue() {
      return value;
    }
  }

  /**
   * A token representing a key in the {@link TagArray}.
   *
   * @see #put(VariableKey, String)
   * @see Builder#variable(String)
   */
  public static final class VariableKey extends Key {

    private int index;

    @Override
    protected void setIndex(final int index) {
      this.index = index;
    }
  }
}
