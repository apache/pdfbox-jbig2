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

import java.awt.Rectangle;
import java.util.Arrays;

/**
 * This class represents a bi-level image that is organized like a bitmap.
 */
public class Bitmap
{

    /** The height of the bitmap in pixels. */
    private final int height;

    /** The width of the bitmap in pixels. */
    private final int width;

    /** The amount of bytes used per row. */
    private final int rowStride;

    /** 8 pixels per byte, 0 for white, 1 for black */
    private byte[] bitmap;

    /**
     * Creates an instance of a blank image.<br>
     * The image data is stored in a byte array. Each pixels is stored as one bit, so that each byte contains 8 pixel. A
     * pixel has by default the value {@code 0} for white and {@code 1} for black. <br>
     * Row stride means the amount of bytes per line. It is computed automatically and fills the pad bits with 0.<br>
     * 
     * @param height - The real height of the bitmap in pixels.
     * @param width - The real width of the bitmap in pixels.
     */
    public Bitmap(int width, int height)
    {
        this.height = height;
        this.width = width;
        this.rowStride = (width + 7) >> 3;

        bitmap = new byte[this.height * this.rowStride];
    }

    /**
     * Returns the value of a pixel specified by the given coordinates.
     * <p>
     * By default, the value is {@code 0} for a white pixel and {@code 1} for a black pixel. The value is placed in the
     * rightmost bit in the byte.
     * 
     * @param x - The x coordinate of the pixel.
     * @param y - The y coordinate of the pixel.
     * @return The value of a pixel.
     */
    public byte getPixel(int x, int y)
    {
        int byteIndex = this.getByteIndex(x, y);
        int bitOffset = this.getBitOffset(x);

        int toShift = 7 - bitOffset;
        return (byte) ((this.getByte(byteIndex) >> toShift) & 0x01);
    }

    public void setPixel(int x, int y, byte pixelValue)
    {
        final int byteIndex = getByteIndex(x, y);
        final int bitOffset = getBitOffset(x);

        final int shift = 7 - bitOffset;

        final byte src = bitmap[byteIndex];
        final byte result = (byte) (src | (pixelValue << shift));
        bitmap[byteIndex] = result;
    }

    /**
     * 
     * <p>
     * Returns the index of the byte that contains the pixel, specified by the pixel's x and y coordinates.
     * 
     * @param x - The pixel's x coordinate.
     * @param y - The pixel's y coordinate.
     * @return The index of the byte that contains the specified pixel.
     */
    public int getByteIndex(int x, int y)
    {
        return y * this.rowStride + (x >> 3);
    }

    /**
     * Simply returns the byte array of this bitmap.
     * 
     * @return The byte array of this bitmap.
     * 
     * @deprecated don't expose the underlying byte array, will be removed in a future release.
     */
    public byte[] getByteArray()
    {
        return bitmap;
    }

    /**
     * Simply returns a byte from the bitmap byte array. Throws an {@link IndexOutOfBoundsException} if the given index
     * is out of bound.
     * 
     * @param index - The array index that specifies the position of the wanted byte.
     * @return The byte at the {@code index}-position.
     * 
     * @throws IndexOutOfBoundsException if the index is out of bound.
     */
    public byte getByte(int index)
    {
        return this.bitmap[index];
    }

    /**
     * Simply sets the given value at the given array index position. Throws an {@link IndexOutOfBoundsException} if the
     * given index is out of bound.
     * 
     * @param index - The array index that specifies the position of a byte.
     * @param value - The byte that should be set.
     * 
     * @throws IndexOutOfBoundsException if the index is out of bound.
     */
    public void setByte(int index, byte value)
    {
        this.bitmap[index] = value;
    }

    /**
     * Converts the byte at specified index into an integer and returns the value. Throws an
     * {@link IndexOutOfBoundsException} if the given index is out of bound.
     * 
     * @param index - The array index that specifies the position of the wanted byte.
     * @return The converted byte at the {@code index}-position as an integer.
     * 
     * @throws IndexOutOfBoundsException if the index is out of bound.
     */
    public int getByteAsInteger(int index)
    {
        return (this.bitmap[index] & 0xff);
    }

    /**
     * Computes the offset of the given x coordinate in its byte. The method uses optimized modulo operation for a
     * better performance.
     * 
     * @param x - The x coordinate of a pixel.
     * @return The bit offset of a pixel in its byte.
     */
    public int getBitOffset(int x)
    {
        // The same like x % 8.
        // The rightmost three bits are 1. The value masks all bits upon the value "7".
        return (x & 0x07);
    }

    /**
     * Simply returns the height of this bitmap.
     * 
     * @return The height of this bitmap.
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Simply returns the width of this bitmap.
     * 
     * @return The width of this bitmap.
     */
    public int getWidth()
    {
        return width;
    }

    /**
     * Simply returns the row stride of this bitmap. <br>
     * (Row stride means the amount of bytes per line.)
     * 
     * @return The row stride of this bitmap.
     */
    public int getRowStride()
    {
        return rowStride;
    }

    public Rectangle getBounds()
    {
        return new Rectangle(0, 0, width, height);
    }

    /**
     * Returns the length of the underlying byte array.
     * 
     * @return byte array length
     * 
     * @deprecated renamed, will be removed in a future release. Use {@link Bitmap#getLength()} instead.
     */
    public int getMemorySize()
    {
        return getLength();
    }

    /**
     * Returns the length of the underlying byte array.
     * 
     * @return byte array length
     */
    public int getLength()
    {
        return bitmap.length;
    }

    /**
     * Fill the underlying bitmap with the given byte value.
     * 
     * @param fillByte the value to be stored in all elements of the bitmap
     */
    public void fillBitmap(byte fillByte)
    {
        Arrays.fill(bitmap, fillByte);
    }
    
    @Override
    public boolean equals(Object obj)
    {
        // most likely used for tests
        if (!(obj instanceof Bitmap))
        {
            return false;
        }
        Bitmap other = (Bitmap)obj;
        return Arrays.equals(bitmap, other.bitmap);
    }
    
    /**
     * Copy parts of the underlying array of a Bitmap to another Bitmap.
     *  
     * @param src the source Bitmap
     * @param srcPos start position within the source Bitmap
     * @param dest the destination Bitmap
     * @param destPos start position within the destination Bitmap
     * @param length the number of bytes to be copied
     */
    public static void arraycopy(Bitmap src, int srcPos, Bitmap dest, int destPos,  int length)
    {
        System.arraycopy(src.bitmap, srcPos, dest.bitmap, destPos, length);
    }
}
