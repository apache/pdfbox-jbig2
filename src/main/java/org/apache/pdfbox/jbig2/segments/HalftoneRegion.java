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

import java.io.IOException;
import java.util.ArrayList;

import org.apache.pdfbox.jbig2.Bitmap;
import org.apache.pdfbox.jbig2.Region;
import org.apache.pdfbox.jbig2.SegmentHeader;
import org.apache.pdfbox.jbig2.err.InvalidHeaderValueException;
import org.apache.pdfbox.jbig2.image.Bitmaps;
import org.apache.pdfbox.jbig2.io.SubInputStream;
import org.apache.pdfbox.jbig2.util.CombinationOperator;

/**
 * This class represents the data of segment type "Halftone region". Parsing is described in 7.4.5, page 67. Decoding
 * procedure in 6.6.5 and 7.4.5.2.
 */
public class HalftoneRegion implements Region
{

    private SubInputStream subInputStream;
    private SegmentHeader segmentHeader;
    private long dataHeaderOffset;
    private long dataHeaderLength;
    private long dataOffset;
    private long dataLength;

    /** Region segment information field, 7.4.1 */
    private RegionSegmentInformation regionInfo;

    /** Halftone segment information field, 7.4.5.1.1 */
    private byte hDefaultPixel;
    private CombinationOperator hCombinationOperator;
    private boolean hSkipEnabled;
    private byte hTemplate;
    private boolean isMMREncoded;

    /** Halftone grid position and size, 7.4.5.1.2 */
    /** Width of the gray-scale image, 7.4.5.1.2.1 */
    private int hGridWidth;
    /** Height of the gray-scale image, 7.4.5.1.2.2 */
    private int hGridHeight;
    /** Horizontal offset of the grid, 7.4.5.1.2.3 */
    private int hGridX;
    /** Vertical offset of the grid, 7.4.5.1.2.4 */
    private int hGridY;

    /** Halftone grid vector, 7.4.5.1.3 */
    /** Horizontal coordinate of the halftone grid vector, 7.4.5.1.3.1 */
    private int hRegionX;
    /** Vertical coordinate of the halftone grod vector, 7.4.5.1.3.2 */
    private int hRegionY;

    /** Decoded data */
    private Bitmap halftoneRegionBitmap;

    /**
     * Previously decoded data from other regions or dictionaries, stored to use as patterns in this region.
     */
    private ArrayList<Bitmap> patterns; // HPATS

    public HalftoneRegion()
    {
    }

    public HalftoneRegion(final SubInputStream subInputStream)
    {
        this.subInputStream = subInputStream;
        this.regionInfo = new RegionSegmentInformation(subInputStream);
    }

    public HalftoneRegion(final SubInputStream subInputStream, final SegmentHeader segmentHeader)
    {
        this.subInputStream = subInputStream;
        this.segmentHeader = segmentHeader;
        this.regionInfo = new RegionSegmentInformation(subInputStream);
    }

    private void parseHeader() throws IOException, InvalidHeaderValueException
    {
        regionInfo.parseHeader();

        // 7.4.5.1.1 Halftone region segment flags

        /* Bit 7: HDEFPIXEL */
        hDefaultPixel = (byte) subInputStream.readBit();

        /* Bit 4-6: HCOMBOP */
        hCombinationOperator = CombinationOperator
                .translateOperatorCodeToEnum((short) (subInputStream.readBits(3) & 0xf));

        /* Bit 3: HENABLESKIP */
        if (subInputStream.readBit() == 1)
        {
            hSkipEnabled = true;
        }

        /* Bit 1-2: HTEMPLATE */
        hTemplate = (byte) (subInputStream.readBits(2) & 0xf);

        /* Bit 0: HMMR */
        if (subInputStream.readBit() == 1)
        {
            isMMREncoded = true;
        }

        // 7.4.5.1.2 Halftone grid position and size
        hGridWidth = (int) (subInputStream.readBits(32) & 0xffffffff); // HGW
        hGridHeight = (int) (subInputStream.readBits(32) & 0xffffffff); // HGH

        hGridX = (int) subInputStream.readBits(32); // HGX
        hGridY = (int) subInputStream.readBits(32); // HGY

        // 7.4.5.1.3 Halftone grid vector
        hRegionX = (int) subInputStream.readBits(16) & 0xffff; // HRX
        hRegionY = (int) subInputStream.readBits(16) & 0xffff; // HRY

        /* Segment data structure */
        computeSegmentDataStructure();
    }

    private void computeSegmentDataStructure() throws IOException
    {
        dataOffset = subInputStream.getStreamPosition();
        dataHeaderLength = dataOffset - dataHeaderOffset;
        dataLength = subInputStream.length() - dataHeaderLength;
    }

    /**
     * The procedure is described in JBIG2 ISO standard, 6.6.5.
     * 
     * @return The decoded {@link Bitmap} of this region.
     * 
     * @throws IOException if an underlying IO operation fails
     * @throws InvalidHeaderValueException if a segment header value is invalid
     */
    public Bitmap getRegionBitmap() throws IOException, InvalidHeaderValueException
    {
        if (null == halftoneRegionBitmap)
        {

            /* 6.6.5, page 40 */
            /* 1) */
            halftoneRegionBitmap = new Bitmap(regionInfo.getBitmapWidth(),
                    regionInfo.getBitmapHeight());

            if (patterns == null)
            {
                patterns = getPatterns();
            }

            if (hDefaultPixel == 1)
            {
                halftoneRegionBitmap.fillBitmap((byte) 0xff);
            }

            /* 2) 6.6.5.1 Computing HSKIP */

            Bitmap hSkip = null;
            if (hSkipEnabled)
            {
                int hPatternHeight = (int) patterns.get(0).getHeight(); // HPW
                int hPatternWidth = (int) patterns.get(0).getWidth(); // HPH
                hSkip = computeHSkip(hPatternWidth, hPatternHeight);
            }

            /* 3) */
            final int bitsPerValue = (int) Math.ceil(Math.log(patterns.size()) / Math.log(2));

            /* 4) */
            final int[][] grayScaleValues = grayScaleDecoding(bitsPerValue, hSkip);

            /* 5), rendering the pattern, described in 6.6.5.2 */
            renderPattern(grayScaleValues);
        }
        /* 6) */
        return halftoneRegionBitmap;
    }

    /**
     * This method draws the pattern into the region bitmap ({@code htReg}), as described in 6.6.5.2, page 42
     */
    private void renderPattern(final int[][] grayScaleValues)
    {
        int x = 0, y = 0;

        // 1)
        for (int m = 0; m < hGridHeight; m++)
        {
            // a)
            for (int n = 0; n < hGridWidth; n++)
            {
                // i)
                x = computeX(m, n);
                y = computeY(m, n);

                // ii)
                final Bitmap patternBitmap = patterns.get(grayScaleValues[m][n]);
                Bitmaps.blit(patternBitmap, halftoneRegionBitmap, x, y,
                        hCombinationOperator);
            }
        }
    }

    /**
     * @throws IOException
     * @throws InvalidHeaderValueException
     * 
     */
    private ArrayList<Bitmap> getPatterns() throws InvalidHeaderValueException, IOException
    {
        final ArrayList<Bitmap> patterns = new ArrayList<Bitmap>();

        for (SegmentHeader s : segmentHeader.getRtSegments())
        {
            final PatternDictionary patternDictionary = (PatternDictionary) s.getSegmentData();
            patterns.addAll(patternDictionary.getDictionary());
        }

        return patterns;
    }

    /**
     * Gray-scale image decoding procedure is special for halftone region decoding and is described in Annex C.5 on page
     * 98.
     */
    private int[][] grayScaleDecoding(final int bitsPerValue, final Bitmap hSkip) throws IOException
    {

        short[] gbAtX = null;
        short[] gbAtY = null;

        if (!isMMREncoded)
        {
            gbAtX = new short[4];
            gbAtY = new short[4];
            // Set AT pixel values
            if (hTemplate <= 1)
                gbAtX[0] = 3;
            else if (hTemplate >= 2)
                gbAtX[0] = 2;

            gbAtY[0] = -1;
            gbAtX[1] = -3;
            gbAtY[1] = -1;
            gbAtX[2] = 2;
            gbAtY[2] = -2;
            gbAtX[3] = -2;
            gbAtY[3] = -2;
        }

        Bitmap[] grayScalePlanes = new Bitmap[bitsPerValue];

        // 1)
        GenericRegion genericRegion = new GenericRegion(subInputStream);
        genericRegion.setParameters(isMMREncoded, dataOffset, dataLength, hGridHeight, hGridWidth,
                hTemplate, false, hSkipEnabled, hSkip, gbAtX, gbAtY);

        // 2)
        int j = bitsPerValue - 1;

        grayScalePlanes[j] = genericRegion.getRegionBitmap();

        while (j > 0)
        {
            j--;
            genericRegion.resetBitmap();
            // 3) a)
            grayScalePlanes[j] = genericRegion.getRegionBitmap();
            // 3) b)
            grayScalePlanes = combineGrayScalePlanes(grayScalePlanes, j);
        }

        // 4)
        return computeGrayScaleValues(grayScalePlanes, bitsPerValue);
    }

    private Bitmap[] combineGrayScalePlanes(Bitmap[] grayScalePlanes, int j)
    {
        int byteIndex = 0;
        for (int y = 0; y < grayScalePlanes[j].getHeight(); y++)
        {

            for (int x = 0; x < grayScalePlanes[j].getWidth(); x += 8)
            {
                final byte newValue = grayScalePlanes[j + 1].getByte(byteIndex);
                final byte oldValue = grayScalePlanes[j].getByte(byteIndex);

                grayScalePlanes[j].setByte(byteIndex++,
                        Bitmaps.combineBytes(oldValue, newValue, CombinationOperator.XOR));
            }
        }
        return grayScalePlanes;
    }

    private int[][] computeGrayScaleValues(final Bitmap[] grayScalePlanes, final int bitsPerValue)
    {
        // Gray-scale decoding procedure, page 98
        final int[][] grayScaleValues = new int[hGridHeight][hGridWidth];

        // 4)
        for (int y = 0; y < hGridHeight; y++)
        {
            for (int x = 0; x < hGridWidth; x += 8)
            {
                final int minorWidth = hGridWidth - x > 8 ? 8 : hGridWidth - x;
                int byteIndex = grayScalePlanes[0].getByteIndex(x, y);

                for (int minorX = 0; minorX < minorWidth; minorX++)
                {
                    final int i = minorX + x;
                    grayScaleValues[y][i] = 0;

                    for (int j = 0; j < bitsPerValue; j++)
                    {
                        grayScaleValues[y][i] += ((grayScalePlanes[j]
                                .getByte(byteIndex) >> (7 - i & 7)) & 1) * (1 << j);
                    }
                }
            }
        }
        return grayScaleValues;
    }

    private int computeX(final int m, final int n)
    {
        return (hGridX + m * hRegionY + n * hRegionX) >> 8;
    }

    private int computeY(final int m, final int n)
    {
        return (hGridY + m * hRegionX - n * hRegionY) >> 8;
    }

    public void init(final SegmentHeader header, final SubInputStream sis)
            throws InvalidHeaderValueException, IOException
    {
        this.segmentHeader = header;
        this.subInputStream = sis;
        this.regionInfo = new RegionSegmentInformation(subInputStream);
        parseHeader();
    }

    public CombinationOperator getCombinationOperator()
    {
        return hCombinationOperator;
    }

    public RegionSegmentInformation getRegionInfo()
    {
        return regionInfo;
    }

    protected byte getHTemplate()
    {
        return hTemplate;
    }

    protected boolean isHSkipEnabled()
    {
        return hSkipEnabled;
    }

    protected boolean isMMREncoded()
    {
        return isMMREncoded;
    }

    protected int getHGridWidth()
    {
        return hGridWidth;
    }

    protected int getHGridHeight()
    {
        return hGridHeight;
    }

    protected int getHGridX()
    {
        return hGridX;
    }

    protected int getHGridY()
    {
        return hGridY;
    }

    protected int getHRegionX()
    {
        return hRegionX;
    }

    protected int getHRegionY()
    {
        return hRegionY;
    }

    protected byte getHDefaultPixel()
    {
        return hDefaultPixel;
    }

    // 6.6.5.1 Computing HSKIP
    private Bitmap computeHSkip(int hPatternWidth, int hPatternHeight) throws IOException
    {
        Bitmap bitmap = new Bitmap(hGridWidth, hGridHeight); // HSKIP is HGW by HGH pixels
        for (int m = 0; m < hGridHeight; ++m)
        {
            for (int n = 0; n < hGridWidth; ++n)
            {
                int x = computeX(m, n);
                int y = computeY(m, n);
                // HBW = halftoneRegionBitmap.getWidth()
                // HBH = halftoneRegionBitmap.getHeight()
                if (x + hPatternWidth <= 0 || x >= halftoneRegionBitmap.getWidth() || y + hPatternHeight <= 0 || y >= halftoneRegionBitmap.getHeight())
                {
                    bitmap.setPixel(n, m, (byte) 1);
                }
                // else no need to set 0 pixels
            }
        }
        return bitmap;
    }
}
