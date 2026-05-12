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

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SlidingBitmapWindowTest
{

    private Bitmap createTestBitmap(int[][] pixels)
    {
        int height = pixels.length;
        int width = pixels[0].length;
        Bitmap bitmap = new Bitmap(width, height);

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                if (pixels[y][x] != 0)
                {
                    bitmap.setPixel(x, y, (byte) 1);
                }
            }
        }
        return bitmap;
    }

    @Test
    public void testBasicBitAccess()
    {
        int[][] pixels = { { 1, 0, 1 }, { 0, 1, 0 }, { 1, 0, 1 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(1, 1); // Center of the bitmap

        assertEquals(1, window.getBit(0, 0)); // Center (1,1) -> 1
        assertEquals(0, window.getBit(0, -1)); // Left (1,0) -> 0
        assertEquals(0, window.getBit(0, 1)); // Right (1,2) -> 0
        assertEquals(0, window.getBit(-1, 0)); // Above (0,1) -> 0
        assertEquals(0, window.getBit(1, 0)); // Below (2,1) -> 0
    }

    @Test
    public void testOutOfBoundsAccess()
    {
        int[][] pixels = { { 1, 0 }, { 0, 1 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);

        assertEquals(0, window.getBit(-2, 0)); // Row out of bounds
        assertEquals(0, window.getBit(0, -1)); // Column out of bounds (left)
        assertEquals(0, window.getBit(0, 2)); // Column out of bounds (right)
    }

    @Test
    public void testAdvance()
    {
        // Create a 1x8 bitmap: 1 0 1 0 1 0 1 0
        int[][] pixels = { { 1, 0, 1, 0, 1, 0, 1, 0 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);

        // Check initial position
        assertEquals(1, window.getBit(0, 0)); // First bit

        window.advance();
        assertEquals(0, window.getBit(0, 0)); // Second bit

        window.advance();
        assertEquals(1, window.getBit(0, 0)); // Third bit
    }

    @Test
    public void testNextRow()
    {
        // Create a 2x2 bitmap:
        // 1 0
        // 0 1
        int[][] pixels = { { 1, 0 }, { 0, 1 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);
        assertEquals(1, window.getBit(0, 0)); // (0,0) -> 1

        window.nextRow();
        assertEquals(0, window.getBit(0, 0)); // (0,1) -> 0
    }

    @Test
    public void testWithOffsets()
    {
        int[][] pixels = { { 1, 0, 1 }, { 0, 1, 0 }, { 1, 0, 1 } };
        Bitmap bitmap = createTestBitmap(pixels);

        // To read bitmap (1,1) from logical (0,0):
        // We need: bmpX = 0 - dx = 1 → dx = -1
        // bmpY = 0 - dy = 1 → dy = -1
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap, -1, -1);

        window.moveTo(0, 0); // Logical (0,0) → bitmap (1,1)

        assertEquals(1, window.getBit(0, 0)); // bitmap (1,1) → 1
        assertEquals(0, window.getBit(0, -1)); // bitmap (1,0) → 0
        assertEquals(0, window.getBit(-1, 0)); // bitmap (0,1) → 0
    }

    @Test
    public void testByteBoundaryCrossing()
    {
        // Create a 1x16 bitmap with alternating bits
        int[][] pixels = new int[1][16];
        for (int x = 0; x < 16; x++)
        {
            pixels[0][x] = x % 2;
        }
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);

        // Check bits across byte boundary (x=7 and x=8)
        for (int x = 0; x < 16; x++)
        {
            assertEquals(x % 2, window.getBit(0, 0));
            window.advance();
        }
    }

    @Test
    public void testByteBoundaryWithNegativeColOffset()
    {
        // At x=8 (start of second byte), colOffset=-1 reaches back into first byte
        // This is the case that fails with a 16-bit register
        // Pattern: 0xAA 0x55 = 10101010 01010101
        Bitmap bitmap = new Bitmap(16, 1);
        bitmap.setByte(0, (byte) 0xAA); // 10101010
        bitmap.setByte(1, (byte) 0x55); // 01010101

        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);
        window.moveTo(8, 0); // Start of second byte

        // At x=8, colOffset=-1 should reach x=7 in first byte (bit 0 of 0xAA = 0)
        assertEquals(0, window.getBit(0, -1)); // x=7 → last bit of 0xAA = 0
        assertEquals(0, window.getBit(0, 0)); // x=8 → first bit of 0x55 = 0
        assertEquals(1, window.getBit(0, 1)); // x=9 → second bit of 0x55 = 1
    }

    @Test
    public void testByteBoundaryWithNegativeColOffsetMinus2()
    {
        // colOffset=-2 is used by template 0 context (x-2, y-1)
        // At x=8, colOffset=-2 reaches x=6 in first byte
        Bitmap bitmap = new Bitmap(16, 1);
        bitmap.setByte(0, (byte) 0xAA); // 10101010
        bitmap.setByte(1, (byte) 0x55); // 01010101

        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);
        window.moveTo(8, 0);

        // x=6 → bit 6 of 0xAA (0-indexed from MSB) = 1
        assertEquals(1, window.getBit(0, -2));
    }

    @Test
    public void testNegativeBmpXWithPositiveDx()
    {
        // dx=2 means logical x=0 maps to bitmap x=-2 (out of bounds → 0)
        // logical x=1 maps to bitmap x=-1 (out of bounds → 0)
        // logical x=2 maps to bitmap x=0 (in bounds)
        int[][] pixels = { { 1, 0, 1, 0, 1, 0, 1, 0 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap, 2, 0);

        window.moveTo(0, 0); // bmpX = 0 - 2 = -2 → out of bounds
        assertEquals(0, window.getBit(0, 0)); // bmpX=-2 → 0
        assertEquals(0, window.getBit(0, 1)); // bmpX=-1 → 0
        assertEquals(1, window.getBit(0, 2)); // bmpX= 0 → 1
    }

    @Test
    public void testNextRowAtFirstRow()
    {
        int[][] pixels = { { 1, 0 }, { 0, 1 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);
        // Above row 0 is out of bounds → must return 0
        assertEquals(0, window.getBit(-1, 0));
        assertEquals(1, window.getBit(0, 0)); // current row
        assertEquals(0, window.getBit(+1, 0)); // row below
    }

    @Test
    public void testNextRowAtLastRow()
    {
        int[][] pixels = { { 1, 0 }, { 0, 1 } };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 1); // Last row
        assertEquals(1, window.getBit(-1, 0)); // row above
        assertEquals(0, window.getBit(0, 0)); // current row
        // Below last row is out of bounds → must return 0
        assertEquals(0, window.getBit(+1, 0));
    }

    @Test
    public void testNextRowRotation()
    {
        // Verify that nextRow() correctly rotates:
        // after nextRow(), what was rowBelow is now rowCurrent
        int[][] pixels = { { 1, 1, 1 }, // row 0
                { 0, 0, 0 }, // row 1
                { 1, 0, 1 } // row 2
        };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);
        assertEquals(1, window.getBit(0, 0)); // row 0, x=0 → 1
        assertEquals(0, window.getBit(+1, 0)); // row 1, x=0 → 0

        window.nextRow();
        assertEquals(1, window.getBit(-1, 0)); // row 0, x=0 → 1 (was above)
        assertEquals(0, window.getBit(0, 0)); // row 1, x=0 → 0 (was below, now current)
        assertEquals(1, window.getBit(+1, 0)); // row 2, x=0 → 1 (newly fetched)
    }

    @Test
    public void testAdvanceAcrossMultipleByteBoundaries()
    {
        // 3 bytes: 0xFF 0x00 0xFF = 11111111 00000000 11111111
        Bitmap bitmap = new Bitmap(24, 1);
        bitmap.setByte(0, (byte) 0xFF);
        bitmap.setByte(1, (byte) 0x00);
        bitmap.setByte(2, (byte) 0xFF);

        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);
        window.moveTo(0, 0);

        for (int x = 0; x < 24; x++)
        {
            int expected = (x < 8 || x >= 16) ? 1 : 0;
            assertEquals("x=" + x, expected, window.getBit(0, 0));
            if (x < 23)
                window.advance();
        }
    }

    @Test
    public void testNextRowResetsToX0()
    {
        // After nextRow(), window must be at x=0, not where advance() left off
        int[][] pixels = { { 1, 0, 1, 0, 1, 0, 1, 0, 1, 0 }, // row 0
                { 0, 1, 0, 1, 0, 1, 0, 1, 0, 1 } // row 1
        };
        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);

        window.moveTo(0, 0);
        // Advance to x=5
        for (int i = 0; i < 5; i++)
            window.advance();

        window.nextRow();
        // Should be at x=0 of row 1, not x=5
        assertEquals(0, window.getBit(0, 0)); // row 1, x=0 → 0
    }

    @Test
    public void testGenericRefinementContext()
    {
        // 3x3 checkerboard: (x+y)%2
        // 0 1 0
        // 1 0 1
        // 0 1 0
        int[][] pixels = new int[3][3];
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                pixels[y][x] = (x + y) % 2;

        Bitmap bitmap = createTestBitmap(pixels);
        SlidingBitmapWindow window = new SlidingBitmapWindow(bitmap);
        window.moveTo(1, 1); // Center

        assertEquals(0, window.getBit(-1, -1)); // (0,0) → 0
        assertEquals(1, window.getBit(-1, 0)); // (1,0) → 1
        assertEquals(0, window.getBit(-1, 1)); // (2,0) → 0
        assertEquals(1, window.getBit(0, -1)); // (0,1) → 1
        assertEquals(0, window.getBit(0, 0)); // (1,1) → 0
        assertEquals(1, window.getBit(0, 1)); // (2,1) → 1
        assertEquals(0, window.getBit(1, -1)); // (0,2) → 0
        assertEquals(1, window.getBit(1, 0)); // (1,2) → 1
        assertEquals(0, window.getBit(1, 1)); // (2,2) → 0
    }
}
