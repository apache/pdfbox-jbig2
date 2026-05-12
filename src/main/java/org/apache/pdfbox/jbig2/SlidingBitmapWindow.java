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

/**
 * A sliding 3-row bit window over a {@link Bitmap}, providing efficient
 * single-bit access to a 3-row neighbourhood around a current position.
 *
 * <p>The window maintains three rows simultaneously: the row above (-1),
 * the current row (0), and the row below (+1). Each row is held as a
 * 24-bit register (prevByte | currentByte | nextByte). As the window
 * advances horizontally via {@link #advance()}, bits slide through the
 * register so that only a new byte needs to be fetched at each byte
 * boundary. As the window moves down via {@link #nextRow()}, registers
 * rotate so that only the new bottom row needs to be fetched.</p>
 *
 * <p>The optional {@code dx}/{@code dy} offsets allow the window to track
 * a spatially shifted bitmap (e.g. GRREFERENCEDX / GRREFERENCEDY in §6.3.5).
 * A call to {@link #getBit(int, int)} at logical position (x, y) reads
 * bitmap position (x - dx, y - dy).</p>
 *
 * <p>All out-of-bounds accesses return 0 per §6.3.5.2.</p>
 *
 * <p><b>Current status:</b> this class is not yet used in production decoding
 * paths. It exists as a standalone, testable abstraction of the sliding window
 * bit-access mechanics that are currently inlined inside
 * {@link org.apache.pdfbox.jbig2.decoder.GenericRefinementRegionDecodingProcedure}
 * for the template 0 paths ({@code decodeTemplate},
 * {@code decodeTypicalPredictedLineTemplate0}). Its primary purpose is to
 * provide a test bed for verifying the correctness of those paths — in
 * particular at byte boundaries, edge rows, and non-zero reference offsets —
 * where latent bugs may exist that are not yet covered by the current test
 * suite.</p>
 */
public class SlidingBitmapWindow
{
    private final Bitmap bitmap;
    private final int dx;
    private final int dy;
    private final int width;
    private final int height;
    private final int rowStride;

    // Bitmap coordinates after applying offset: bitmapX = currentX - dx, bitmapY =
    // currentY - dy
    private int bitmapX;
    private int bitmapY;

    // Bit position within the current byte (0=MSB=leftmost, 7=LSB=rightmost)
    private int bitPosition;

    // Current byte column in the bitmap: bitmapX >> 3
    private int byteColumn;

    // 24-bit sliding registers: (prevByte << 16) | (currentByte << 8) | nextByte
    // Bit for colOffset c relative to current position is at shift (15 -
    // bitPosition - c)
    private int previousLine;
    private int currentLine;
    private int nextLine;

    /**
     * Constructs a window over the given bitmap, positioned at (0, 0).
     *
     * @param bitmap the bitmap to window over
     */
    public SlidingBitmapWindow(Bitmap bitmap)
    {
        this(bitmap, 0, 0);
    }

    /**
     * Constructs a window over the given bitmap with a coordinate offset, positioned at (0, 0). The offsets are
     * subtracted from all pixel accesses: getBit() at logical (x, y) reads bitmap position (x-dx, y-dy).
     *
     * @param bitmap the bitmap to window over
     * @param dx horizontal offset (GRREFERENCEDX)
     * @param dy vertical offset (GRREFERENCEDY)
     */
    public SlidingBitmapWindow(Bitmap bitmap, int dx, int dy)
    {
        this.bitmap = bitmap;
        this.dx = dx;
        this.dy = dy;
        this.width = bitmap.getWidth();
        this.height = bitmap.getHeight();
        this.rowStride = bitmap.getRowStride();
        moveTo(0, 0);
    }

    /**
     * Repositions the window to (x, y), loading the 3-row neighbourhood. Must be called when changing position
     * non-incrementally.
     *
     * @param x logical x coordinate
     * @param y logical y coordinate
     */
    public void moveTo(int x, int y)
    {
        this.bitmapX = x - dx;
        this.bitmapY = y - dy;
        this.bitPosition = bitmapX & 7;
        this.byteColumn = bitmapX >> 3;

        previousLine = buildRegister(bitmapY - 1);
        currentLine = buildRegister(bitmapY);
        nextLine = buildRegister(bitmapY + 1);
    }

    /**
     * Resets the window to (0, 0), reloading the 3-row neighbourhood. Equivalent to {@link #moveTo(int, int) moveTo(0,
     * 0)} but intended for use in benchmarks to avoid object allocation between invocations.
     */
    public void reset()
    {
        moveTo(0, 0);
    }

    /**
     * Advances the window one pixel to the right. Leftmost bits fall out, existing bits shift left, new rightmost bits
     * enter from the data layer at byte boundaries.
     */
    public void advance()
    {
        bitmapX++;
        bitPosition = (bitPosition + 1) & 7;

        if (bitPosition == 0)
        {
            // Byte boundary — slide all registers and load the next byte
            byteColumn++;
            previousLine = slideRegister(previousLine, bitmapY - 1);
            currentLine = slideRegister(currentLine, bitmapY);
            nextLine = slideRegister(nextLine, bitmapY + 1);
        }
    }

    /**
     * Moves the window one row down and resets to x=0. The row above is discarded, current becomes above, below becomes
     * current, and a new bottom row is fetched.
     */
    public void nextRow()
    {
        bitmapY++;
        // Reset to start of row (x=0) and recompute bitmap coordinates
        bitmapX = -dx; // logical x=0 maps to bitmap x=-dx
        bitPosition = (-dx) & 7;
        // IMPORTANT: must use >> (not /8) to preserve floor division for negatives
        byteColumn = (-dx) >> 3;

        previousLine = buildRegister(bitmapY - 1);
        currentLine = buildRegister(bitmapY);
        nextLine = buildRegister(bitmapY + 1);
    }

    /**
     * Returns the bit value at the given row and column offset relative to the current position.
     *
     * @param rowOffset -1 (above), 0 (current), or +1 (below)
     * @param colOffset column offset relative to current position, typically in the range -2 to +2
     * @return 0 or 1; 0 if the target position is out of bounds
     */
    public int getBit(int rowOffset, int colOffset)
    {
        // Early exit for invalid rowOffset
        if (rowOffset < -1 || rowOffset > 1)
        {
            return 0;
        }

        final int targetBmpX = bitmapX + colOffset;
        final int targetBmpY = bitmapY + rowOffset;

        if (targetBmpX < 0 || targetBmpX >= width || targetBmpY < 0 || targetBmpY >= height)
        {
            return 0;
        }

        final int reg;
        switch (rowOffset)
        {
        case -1:
            reg = previousLine;
            break;
        case 0:
            reg = currentLine;
            break;
        default:
            reg = nextLine;
            break;
        }

        final int baseShift = 15 - bitPosition;
        return (reg >>> (baseShift - colOffset)) & 1;
    }

    /**
     * Returns the bit value at the given row and column offset without bounds checking. Only call this when the current
     * position is guaranteed to be in the interior of the bitmap (not near any edge).
     *
     * @param rowOffset -1 (above), 0 (current), or +1 (below)
     * @param colOffset column offset, typically -2 to +2
     * @return 0 or 1
     */
    public int getBitFast(final int rowOffset, final int colOffset)
    {
        final int reg = (rowOffset == -1) ? previousLine
                : (rowOffset == 0) ? currentLine : nextLine;
        final int baseShift = 15 - bitPosition;
        return (reg >>> (baseShift - colOffset)) & 1;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a fresh 24-bit register for the given bitmap row at the current byte column position.
     */
    private int buildRegister(final int bmpRow)
    {
        if (bmpRow < 0 || bmpRow >= height)
        {
            return 0;
        }
        final int prev = loadByte(byteColumn - 1, bmpRow);
        final int current = loadByte(byteColumn, bmpRow);
        final int next = loadByte(byteColumn + 1, bmpRow);
        return (prev << 16) | (current << 8) | next;
    }

    /**
     * Advances a 24-bit register by one byte: prevByte is dropped, currentByte becomes prevByte, nextByte becomes
     * currentByte, and a new nextByte is fetched.
     */
    private int slideRegister(final int reg, final int bmpRow)
    {
        if (bmpRow < 0 || bmpRow >= height)
        {
            return 0;
        }
        final int newNext = loadByte(byteColumn + 1, bmpRow);
        return ((reg << 8) | newNext) & 0xFFFFFF; // Keep only 24 bits
    }

    /**
     * Loads one byte from the bitmap at the given byte column and row. Returns 0 for out-of-bounds positions per
     * §6.3.5.2.
     */
    private int loadByte(final int col, final int row)
    {
        if (col < 0 || col >= rowStride || row < 0 || row >= height)
        {
            return 0;
        }
        return bitmap.getByteAsInteger(row * rowStride + col);
    }
}
