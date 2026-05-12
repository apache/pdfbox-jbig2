/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pdfbox.jbig2;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(0)
public class SlidingBitmapWindowBenchmark
{
    private static final int WIDTH  = 1728;
    private static final int HEIGHT = 2339;

    private Bitmap regionBitmap;
    private Bitmap referenceBitmap;

    private SlidingBitmapWindow regWindow;
    private SlidingBitmapWindow refWindow;

    @Setup(Level.Trial)
    public void setUp()
    {
        regionBitmap    = new Bitmap(WIDTH, HEIGHT);
        referenceBitmap = new Bitmap(WIDTH, HEIGHT);

        // Fill with realistic patterns — different for each bitmap
        for (int i = 0; i < regionBitmap.getLength(); i++)
        {
            regionBitmap.setByte(i,    (byte) (i % 2 == 0 ? 0xAA : 0x55));
            referenceBitmap.setByte(i, (byte) (i % 3 == 0 ? 0xFF : 0x00));
        }

        regWindow = new SlidingBitmapWindow(regionBitmap);
        refWindow = new SlidingBitmapWindow(referenceBitmap);
    }

    @Setup(Level.Invocation)
    public void resetWindows()
    {
        regWindow.reset();
        refWindow.reset();
    }

    /**
     * Baseline: pixel-by-pixel using getPixelSafe() on two bitmaps.
     */
    @Benchmark
    public void pixelByPixel(Blackhole bh)
    {
        int sum = 0;
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                sum += buildContextGetPixel(x, y);
            }
        }
        bh.consume(sum);
    }

    /**
     * New approach: SlidingBitmapWindow on two windows.
     */
    @Benchmark
    public void slidingWindow(Blackhole bh)
    {
        int sum = 0;
        for (int y = 0; y < HEIGHT; y++, regWindow.nextRow(), refWindow.nextRow())
        {
            for (int x = 0; x < WIDTH; x++, regWindow.advance(), refWindow.advance())
            {
                sum += buildContextWindow();
            }
        }
        bh.consume(sum);
    }
    
    @Benchmark
    public void slidingWindowFast(Blackhole bh)
    {
        int sum = 0;

        // Row 0 and last row — safe path
        for (int x = 0; x < WIDTH; x++, regWindow.advance(), refWindow.advance())
            sum += buildContextWindow();
        regWindow.nextRow();
        refWindow.nextRow();

        // Interior rows — fast path for interior pixels, safe for edges
        for (int y = 1; y < HEIGHT - 1; y++, regWindow.nextRow(), refWindow.nextRow())
        {
            // x=0 — left edge, safe path
            sum += buildContextWindow();
            regWindow.advance();
            refWindow.advance();

            // x=1 to WIDTH-2 — interior, fast path
            for (int x = 1; x < WIDTH - 1; x++, regWindow.advance(), refWindow.advance())
                sum += buildContextWindowFast();

            // x=WIDTH-1 — right edge, safe path
            sum += buildContextWindow();
            regWindow.advance();
            refWindow.advance();
        }

        // Last row — safe path
        for (int x = 0; x < WIDTH; x++, regWindow.advance(), refWindow.advance())
            sum += buildContextWindow();

        bh.consume(sum);
    }

    // -------------------------------------------------------------------------
    // Context builders — mirror buildContextT1 from the decoding procedure
    // -------------------------------------------------------------------------

    private int buildContextGetPixel(final int x, final int y)
    {
        return (getPixelSafe(regionBitmap,    x - 1, y - 1) << 9)
             | (getPixelSafe(regionBitmap,    x,     y - 1) << 8)
             | (getPixelSafe(regionBitmap,    x + 1, y - 1) << 7)
             | (getPixelSafe(regionBitmap,    x - 1, y    ) << 6)
             | (getPixelSafe(referenceBitmap, x,     y - 1) << 5)
             | (getPixelSafe(referenceBitmap, x - 1, y    ) << 4)
             | (getPixelSafe(referenceBitmap, x,     y    ) << 3)
             | (getPixelSafe(referenceBitmap, x + 1, y    ) << 2)
             | (getPixelSafe(referenceBitmap, x,     y + 1) << 1)
             | (getPixelSafe(referenceBitmap, x + 1, y + 1));
    }

    private int buildContextWindow()
    {
        return (regWindow.getBit(-1, -1) << 9)
             | (regWindow.getBit( 0, -1) << 8)
             | (regWindow.getBit( 1, -1) << 7)
             | (regWindow.getBit(-1,  0) << 6)
             | (refWindow.getBit( 0, -1) << 5)
             | (refWindow.getBit(-1,  0) << 4)
             | (refWindow.getBit( 0,  0) << 3)
             | (refWindow.getBit( 1,  0) << 2)
             | (refWindow.getBit( 0,  1) << 1)
             | (refWindow.getBit( 1,  1));
    }

    private int buildContextWindowFast()
    {
        return (regWindow.getBitFast(-1, -1) << 9)
            | (regWindow.getBitFast( 0, -1) << 8)
            | (regWindow.getBitFast( 1, -1) << 7)
            | (regWindow.getBitFast(-1,  0) << 6)
            | (refWindow.getBitFast( 0, -1) << 5)
            | (refWindow.getBitFast(-1,  0) << 4)
            | (refWindow.getBitFast( 0,  0) << 3)
            | (refWindow.getBitFast( 1,  0) << 2)
            | (refWindow.getBitFast( 0,  1) << 1)
            | (refWindow.getBitFast( 1,  1));
    }

    private int getPixelSafe(final Bitmap bitmap, final int x, final int y)
    {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT)
            return 0;
        return bitmap.getPixel(x, y);
    }
}