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

package org.apache.pdfbox.jbig2.segments;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.pdfbox.jbig2.Bitmap;
import org.apache.pdfbox.jbig2.Dictionary;
import org.apache.pdfbox.jbig2.SegmentHeader;
import org.apache.pdfbox.jbig2.err.InvalidHeaderValueException;
import org.apache.pdfbox.jbig2.image.Bitmaps;
import org.apache.pdfbox.jbig2.io.SubInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the segment type "Pattern dictionary", 7.4.4.
 */
public class PatternDictionary implements Dictionary
{

    private final Logger log = LoggerFactory.getLogger(PatternDictionary.class);

    private SubInputStream subInputStream;

    /** Segment data structure (only necessary if MMR is used) */
    private long dataHeaderOffset;
    private long dataHeaderLength;
    private long dataOffset;
    private long dataLength;

    private short[] gbAtX = null;
    private short[] gbAtY = null;

    /** Pattern dictionary flags, 7.4.4.1.1 */
    private boolean isMMREncoded;
    private byte hdTemplate;

    /** Width of the patterns in the pattern dictionary, 7.4.4.1.2 */
    private short hdpWidth;

    /** Height of the patterns in the pattern dictionary, 7.4.4.1.3 */
    private short hdpHeight;

    /** Decoded bitmaps, stored to be used by segments, that refer to it */
    private ArrayList<Bitmap> patterns;

    /**
     * Largest gray-scale value, 7.4.4.1.4
     *
     * Value: one less than the number of patterns defined in this pattern dictionary
     */
    private int grayMax;

    private void parseHeader() throws IOException, InvalidHeaderValueException
    {
        /* Bit 3-7 */
        subInputStream.readBits(5); // Dirty read ...

        /* Bit 1-2 */
        readTemplate();

        /* Bit 0 */
        readIsMMREncoded();

        readPatternWidthAndHeight();

        readGrayMax();

        /* Segment data structure */
        computeSegmentDataStructure();

        this.checkInput();
    }

    private void readTemplate() throws IOException
    {
        /* Bit 1-2 */
        hdTemplate = (byte) subInputStream.readBits(2);
    }

    private void readIsMMREncoded() throws IOException
    {
        /* Bit 0 */
        if (subInputStream.readBit() == 1)
        {
            isMMREncoded = true;
        }
    }

    private void readPatternWidthAndHeight() throws IOException
    {
        hdpWidth = subInputStream.readByte();
        hdpHeight = subInputStream.readByte();
    }

    private void readGrayMax() throws IOException
    {
        grayMax = (int) (subInputStream.readBits(32) & 0xffffffff);
    }

    private void computeSegmentDataStructure() throws IOException
    {
        dataOffset = subInputStream.getStreamPosition();
        dataHeaderLength = dataOffset - dataHeaderOffset;
        dataLength = subInputStream.length() - dataHeaderLength;
    }

    private void checkInput() throws InvalidHeaderValueException
    {
        if (hdpHeight < 1 || hdpWidth < 1)
        {
            throw new InvalidHeaderValueException("Width/Heigth must be greater than zero.");
        }

        if (isMMREncoded)
        {
            if (hdTemplate != 0)
            {
                log.info("hdTemplate should contain the value 0");
            }
        }
    }

    /**
     * This method decodes a pattern dictionary segment and returns an array of {@link Bitmap} s. Each of this
     * {@link Bitmap}s is a pattern.<br>
     * The procedure is described in 6.7.5 (page 43).
     *
     * @return An array of {@link Bitmap}s as result of the decoding procedure.
     */
    @Override
    public ArrayList<Bitmap> getDictionary() throws IOException, InvalidHeaderValueException
    {
        if (null == patterns)
        {

            if (!isMMREncoded)
            {
                setGbAtPixels();
            }

            // 2)
            final GenericRegion genericRegion = new GenericRegion(subInputStream);
            genericRegion.setParameters(isMMREncoded, dataOffset, dataLength, hdpHeight,
                    (grayMax + 1) * hdpWidth, hdTemplate, false, false, gbAtX, gbAtY);

            final Bitmap collectiveBitmap = genericRegion.getRegionBitmap();

            // 4)
            extractPatterns(collectiveBitmap);
        }

        return patterns;
    }

    private void extractPatterns(Bitmap collectiveBitmap)
    {
        // 3)
        int gray = 0;
        patterns = new ArrayList<Bitmap>(grayMax + 1);

        // 4)
        while (gray <= grayMax)
        {
            // 4) a) Retrieve a pattern bitmap by extracting it out of the collective bitmap
            final Rectangle roi = new Rectangle(hdpWidth * gray, 0, hdpWidth, hdpHeight);
            final Bitmap patternBitmap = Bitmaps.extract(roi, collectiveBitmap);
            patterns.add(patternBitmap);

            // 4) b)
            gray++;
        }
    }

    private void setGbAtPixels()
    {
        if (hdTemplate == 0)
        {
            gbAtX = new short[4];
            gbAtY = new short[4];
            gbAtX[0] = (short) -hdpWidth;
            gbAtY[0] = 0;
            gbAtX[1] = -3;
            gbAtY[1] = -1;
            gbAtX[2] = 2;
            gbAtY[2] = -2;
            gbAtX[3] = -2;
            gbAtY[3] = -2;

        }
        else
        {
            gbAtX = new short[1];
            gbAtY = new short[1];
            gbAtX[0] = (short) -hdpWidth;
            gbAtY[0] = 0;
        }
    }

    @Override
    public void init(SegmentHeader header, SubInputStream sis)
            throws InvalidHeaderValueException, IOException
    {
        this.subInputStream = sis;
        parseHeader();
    }

    protected boolean isMMREncoded()
    {
        return isMMREncoded;
    }

    protected byte getHdTemplate()
    {
        return hdTemplate;
    }

    protected short getHdpWidth()
    {
        return hdpWidth;
    }

    protected short getHdpHeight()
    {
        return hdpHeight;
    }

    protected int getGrayMax()
    {
        return grayMax;
    }
}
