// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import io.ultrabrew.metrics.util.TagArray;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TagArrayBenchmark {

  public static final String KEY_1 = "key1";
  public static final String KEY_2 = "key2";
  public static final String KEY_3 = "key3";
  // Intentionally not local or final to disallow optimizing the vararg array construction
  @SuppressWarnings("FieldCanBeLocal")
  private String thing1 = "abc";
  @SuppressWarnings("FieldCanBeLocal")
  private String thing2 = "abc";
  @SuppressWarnings("FieldCanBeLocal")
  private String thing3 = "abc";

  private TagArray tagArray;
  private TagArray.VariableKey key1;
  private TagArray.VariableKey key2;
  private TagArray.VariableKey key3;

  private StringMapTagArray stringMapTagArray;

  @Setup
  public void prepare() {
    TagArray.Builder b = TagArray.builder();
    b.constant("abc", "def");
    key1 = b.variable(KEY_1);
    key2 = b.variable(KEY_2);
    key3 = b.variable(KEY_3);
    tagArray = b.build();

    StringMapTagArray.Builder bb = StringMapTagArray.builder();
    bb.constant("abc", "def");
    bb.variable(KEY_1);
    bb.variable(KEY_2);
    bb.variable(KEY_3);
    stringMapTagArray = bb.build();
  }

  private void useArray(final Blackhole bh, final String... array) {
    bh.consume(array);
  }

  @Benchmark
  public void testVarArgs(final Blackhole bh) {
    useArray(bh, "abc", "def", KEY_1, thing1, KEY_2, thing2, KEY_3, thing3);
  }

  @Benchmark
  public void testTagArray(final Blackhole bh) {
    tagArray.put(key1, thing1);
    tagArray.put(key2, thing2);
    tagArray.put(key3, thing3);
    useArray(bh, tagArray.toArray());
  }

  @Benchmark
  public void testStringMapTagArray(final Blackhole bh) {
    stringMapTagArray.put(KEY_1, thing1);
    stringMapTagArray.put(KEY_2, thing2);
    stringMapTagArray.put(KEY_3, thing3);
    useArray(bh, stringMapTagArray.toArray());
  }

  @Benchmark
  public void testTagArrayPut() {
    tagArray.put(key1, thing1);
  }

  @Benchmark
  public void testStringMapTagArrayPut() {
    stringMapTagArray.put(KEY_1, thing1);
  }

  public static final class StringMapTagArray {

    private final ThreadLocal<String[]> array;
    private final Map<String, Integer> indices;

    private StringMapTagArray(final String[] array, final Map<String, Integer> indices) {
      this.array = ThreadLocal.<String[]>withInitial(array::clone);
      this.indices = indices;
    }

    public static Builder builder() {
      return new StringMapTagArray.Builder();
    }

    public void put(final String key, final String value) {
      array.get()[indices.get(key)] = value;
    }

    public String[] toArray() {
      return array.get();
    }

    public static class Builder {

      private final Map<String, String> tags = new HashMap<>();

      private Builder() {
      }

      public void constant(final String key, final String value) {
        tags.put(key, value);
      }

      public void variable(final String key) {
        tags.put(key, null);
      }

      public StringMapTagArray build() {
        List<Entry<String, String>> sortedByKey = tags.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, String>, String>comparing(Entry::getKey))
            .collect(Collectors.toList());
        String[] array = new String[sortedByKey.size() * 2];
        int i = 0;
        Map<String, Integer> indices = new HashMap<>();
        for (Map.Entry<String, String> entry : sortedByKey) {
          array[i] = entry.getKey();
          array[i + 1] = entry.getValue();
          if (entry.getValue() == null) {
            indices.put(entry.getKey(), i + 1);
          }
          i += 2;
        }
        return new StringMapTagArray(array, indices);
      }
    }
  }
}
