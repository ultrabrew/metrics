// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@Threads(1)
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Benchmark)
public class HashFunctionBenchmark {

  private static final SecureRandom secureRandom = new SecureRandom();

  private RawHashTable primeHashTable;
  private RawHashTable nonPrimeHashTable;
  private Input[] inputs;

  @Param({"16384"})
  private int inputSize;

  /*
  @Param({
      "210", "222", "226", "228", "232", "238", "240", "250", "256", "262", "268", "270", "276", "280", "282", "292",
      "306", "310", "312", "316", "330", "336", "346", "348", "352", "358", "366", "372", "378", "382", "388", "396",
      "400", "408", "418", "420", "430", "432", "438", "442", "448", "456", "460", "462", "466", "478", "488", "490",
      "498", "502", "508", "520", "522", "540", "546", "556", "562", "568", "570", "576", "586", "592", "598", "600",
      "606", "612", "616", "618", "630", "640", "642", "646", "652", "658", "660", "672", "676", "682", "690", "700"
  })*/
  @Param({"210"})
  private int capacity;

  private static class Input {

    private String[] tags;
    private long value;
  }

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class OpCounters {

    public int put;
    public int scans;
    public double filled;
  }

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.EVENTS)
  public static class EventCounters {

    public int capacity;
  }

  @Setup
  public void setup() {
    primeHashTable = new RawHashTable(
        BigInteger.valueOf(capacity).nextProbablePrime().intValueExact());
    nonPrimeHashTable = new RawHashTable(capacity);
    inputs = new Input[inputSize];
    for (int i = 0; i < inputs.length; i++) {
      inputs[i] = generateInput(0L, 100L, 0, 2, 100);
    }
  }

  private Input generateInput(final long minValue, final long maxValue,
      final int minTags, final int maxTags,
      final int tagCardinality) {
    Input input = new Input();

    if (minValue == maxValue) {
      input.value = minValue;
    } else {
      input.value = ThreadLocalRandom.current().nextLong(maxValue - minValue) + minValue;
    }

    int tags = secureRandom.nextInt(maxTags - minTags) + minTags;
    if (tags > 0) {
      input.tags = new String[tags * 2];
      for (int i = 0; i < tags; i++) {
        input.tags[i] = "tag" + i;
        input.tags[i + 1] = String.valueOf(secureRandom.nextInt(tagCardinality));
      }
    }

    return input;
  }

  @Benchmark
  public void primeCapacity(OpCounters opCounters, EventCounters eventCounters) {
    for (final Input i : inputs) {
      primeHashTable.put(i.tags, i.value);
      opCounters.put++;
      opCounters.scans += primeHashTable.lastScanLength();
    }
    opCounters.filled = (double) primeHashTable.size() / primeHashTable.capacity();
    eventCounters.capacity = primeHashTable.capacity();
  }

  @Benchmark
  public void nonPrimeCapacity(OpCounters opCounters, EventCounters eventCounters) {
    for (final Input i : inputs) {
      nonPrimeHashTable.put(i.tags, i.value);
      opCounters.put++;
      opCounters.scans += nonPrimeHashTable.lastScanLength();
    }
    opCounters.filled = (double) nonPrimeHashTable.size() / nonPrimeHashTable.capacity();
    eventCounters.capacity = nonPrimeHashTable.capacity();
  }
}
