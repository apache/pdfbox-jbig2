/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.jbig2.decoder.arithmetic;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Baseline benchmark for ArithmeticDecoder.
 *
 * Run with: mvn clean test -Pbenchmark
 * Results:  target/benchmark-results.json
 *
 * Fork is intentionally 0 — the benchmark profile uses exec-maven-plugin
 * which already provides an isolated JVM. Warmup is increased to compensate.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)  // 10s iterations
@Fork(0)
public class ArithmeticDecoderBenchmark {

    private byte[] encodedBytes;
    private ArithmeticDecoder decoder;

    private int symbolsDecoded;
    private volatile int expectedSymbolCount = -1;

    // -----------------------------------------------------------------------
    // Setup / TearDown
    // -----------------------------------------------------------------------

    @Setup(Level.Trial)
    public void loadBytes() throws IOException {
        InputStream is = getClass()
                .getResourceAsStream("/images/arith/encoded testsequence");
        if (is == null) {
            throw new IllegalStateException(
                "Test resource not found. Run 'mvn generate-test-resources' first.");
        }
        encodedBytes = toByteArray(is);
        is.close();
        expectedSymbolCount = -1;
    }

    @Setup(Level.Invocation)
    public void setup() throws IOException {
        // MemoryCacheImageInputStream — pure in-memory, eliminates the
        // FileCacheImageInputStream temp-file backed native seek() calls
        // that were consuming 32.7% of CPU time per JFR profiling.
        ImageInputStream iis = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(encodedBytes));
        decoder = new ArithmeticDecoder(iis);
        symbolsDecoded = 0;
    }

    @TearDown(Level.Invocation)
    public void verifySymbolCount() {
        if (expectedSymbolCount == -1) {
            expectedSymbolCount = symbolsDecoded;
            System.out.printf(
                "[ArithmeticDecoderBenchmark] baseline symbol count: %d%n", expectedSymbolCount);
        } else if (symbolsDecoded != expectedSymbolCount) {
            throw new IllegalStateException(String.format(
                "Symbol count mismatch: expected %d but decoded %d. " +
                "Check byteIn() for early termination or over-read bugs.",
                expectedSymbolCount, symbolsDecoded));
        }
    }

    // -----------------------------------------------------------------------
    // Benchmark
    // -----------------------------------------------------------------------

    @Benchmark
    public void decodeFullStream(Blackhole bh) throws IOException {
        CX cx = new CX(1, 0);
        for (int i = 0; i < 257; i++) {       // fixed count matching the test
            bh.consume(decoder.decode(cx));
            symbolsDecoded++;
        }
    }
    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Heuristic to distinguish normal end-of-stream termination from a
     * genuine decode error. ImageInputStream implementations typically
     * produce a null or empty message on EOF.
     */
    private static boolean isEndOfStream(IOException e) {
        String msg = e.getMessage();
        return msg == null || msg.isEmpty();
    }

    /** Java-8-compatible equivalent of InputStream.readAllBytes(). */
    private static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}