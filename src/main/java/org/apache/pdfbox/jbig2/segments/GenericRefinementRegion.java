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

import org.apache.pdfbox.jbig2.Bitmap;
import org.apache.pdfbox.jbig2.Region;
import org.apache.pdfbox.jbig2.SegmentHeader;
import org.apache.pdfbox.jbig2.decoder.GenericRefinementRegionDecodingProcedure;
import org.apache.pdfbox.jbig2.decoder.arithmetic.ArithmeticDecoder;
import org.apache.pdfbox.jbig2.decoder.arithmetic.CX;
import org.apache.pdfbox.jbig2.err.IntegerMaxValueException;
import org.apache.pdfbox.jbig2.err.InvalidHeaderValueException;
import org.apache.pdfbox.jbig2.image.Bitmaps;
import org.apache.pdfbox.jbig2.io.SubInputStream;
import org.apache.pdfbox.jbig2.util.CombinationOperator;

/**
 * Handles a JBIG2 Generic Refinement Region segment (§7.4.7).
 *
 * <p>This class is responsible for segment-level concerns only: parsing the
 * region segment information and flags from the bitstream, resolving the
 * reference bitmap, and delegating pixel decoding to
 * {@link GenericRefinementRegionDecodingProcedure}, which implements the
 * pure algorithm defined in §6.3.5.6.</p>
 *
 * <h2>Segment types (§7.4.7)</h2>
 * <p>The three generic refinement region segment types — intermediate,
 * immediate, and immediate lossless — share an identical data encoding.
 * They differ only in how the decoded bitmap is acted upon during page
 * image composition (§8.2):</p>
 * <ul>
 *   <li><b>Intermediate generic refinement region</b> — the decoded bitmap
 *       is stored but not yet composited onto the page buffer.</li>
 *   <li><b>Immediate generic refinement region</b> — the decoded bitmap is
 *       composited onto the page buffer immediately using lossy encoding.</li>
 *   <li><b>Immediate lossless generic refinement region</b> — as above but
 *       using lossless encoding.</li>
 * </ul>
 *
 * <h2>Usage modes</h2>
 * <ul>
 *   <li><b>Header-driven (standalone segment):</b>
 *       Initialise via {@link #init(SegmentHeader, SubInputStream)}.
 *       The reference bitmap is resolved from referred-to segments or,
 *       if none are present, from the current page buffer (§7.4.7.4).
 *       Per Table 35, {@code GRREFERENCEDX} and {@code GRREFERENCEDY}
 *       are implicitly zero in this mode.</li>
 *
 *   <li><b>Parameter-driven:</b>
 *       Call {@link #setParameters(CX, ArithmeticDecoder, short, int, int,
 *       Bitmap, int, int, boolean, short[], short[])} to supply the shared
 *       {@code ArithmeticDecoder}, {@code CX}, reference bitmap, and offsets
 *       before calling {@link #getRegionBitmap()}.
 *       <br><em>Deprecated:</em> callers should migrate to invoking
 *       {@link GenericRefinementRegionDecodingProcedure#decode(
 *       ArithmeticDecoder, CX, int, int, short, boolean, Bitmap, int, int,
 *       short[], short[])} directly.</li>
 * </ul>
 */
public class GenericRefinementRegion implements Region
{
    /**
     *
     * @deprecated Moved to {@link GenericRefinementRegionDecodingProcedure.Template}.
     *     This declaration will be removed in the next major release.
     */
    @Deprecated
    public static abstract class Template
    {
        @Deprecated
        protected abstract short form(short c1, short c2, short c3, short c4, short c5);

        @Deprecated
        protected abstract void setIndex(CX cx);
    }

    private SubInputStream subInputStream;

    private SegmentHeader segmentHeader;

    /** Region segment information flags, 7.4.1 */
    private RegionSegmentInformation regionInfo;

    /** Generic refinement region segment flags, 7.4.7.2 */
    private boolean isTPGROn;
    private short templateID;

    /** Generic refinement region segment AT flags, 7.4.7.3 */
    private short grAtX[];
    private short grAtY[];

    /** Variables for decoding */
    private Bitmap referenceBitmap;
    private int referenceDX;
    private int referenceDY;

    private ArithmeticDecoder arithDecoder;
    private CX cx;

    private Bitmap pageBitmap;

    public GenericRefinementRegion()
    {
    }

    public GenericRefinementRegion(final SubInputStream subInputStream)
    {
        this.subInputStream = subInputStream;
        this.regionInfo = new RegionSegmentInformation(subInputStream);
    }

    public GenericRefinementRegion(final SubInputStream subInputStream,
            final SegmentHeader segmentHeader)
    {
        this.subInputStream = subInputStream;
        this.segmentHeader = segmentHeader;
        this.regionInfo = new RegionSegmentInformation(subInputStream);
    }

    /**
     * Parses the flags described in JBIG2 ISO standard:
     * <ul>
     * <li>7.4.7.2 Generic refinement region segment flags</li>
     * <li>7.4.7.3 Generic refinement region segment AT flags</li>
     * </ul>
     * 
     * @throws IOException
     */
    private void parseHeader() throws IOException
    {
        regionInfo.parseHeader();

        /* Bit 2-7 */
        subInputStream.readBits(6); // Dirty read...

        /* Bit 1 */
        if (subInputStream.readBit() == 1)
        {
            isTPGROn = true;
        }

        /* Bit 0 */
        templateID = (short) subInputStream.readBit();

        switch (templateID)
        {
        case 0:
            readAtPixels();
            break;
        case 1:
            break;
        }
    }

    // 7.4.7.3 Generic refinement region segment AT flags
    private void readAtPixels() throws IOException
    {
        grAtX = new short[2];
        grAtY = new short[2];

        /* Byte 0 */
        grAtX[0] = subInputStream.readByte();
        /* Byte 1 */
        grAtY[0] = subInputStream.readByte();
        /* Byte 2 */
        grAtX[1] = subInputStream.readByte();
        /* Byte 3 */
        grAtY[1] = subInputStream.readByte();
    }

    /**
     * Decode using a template and arithmetic coding, as described in 6.3.5.6
     * 
     * @throws IOException if an underlying IO operation fails
     * @throws InvalidHeaderValueException if a segment header value is invalid
     * @throws IntegerMaxValueException if the maximum value limit of an integer is exceeded
     */
    @Override
    public Bitmap getRegionBitmap()
            throws IOException, IntegerMaxValueException, InvalidHeaderValueException {

        if (referenceBitmap == null) {
            // Get the reference bitmap, which is the base of refinement process
            referenceBitmap = getGrReference();
        }

        if (arithDecoder == null) {
            arithDecoder = new ArithmeticDecoder(subInputStream);
        }

        if (cx == null) {
            cx = new CX(8192, 1);
        }

        return GenericRefinementRegionDecodingProcedure.decode(
                arithDecoder, cx,
                regionInfo.getBitmapWidth(), regionInfo.getBitmapHeight(),
                templateID, isTPGROn, referenceBitmap, referenceDX, referenceDY,
                grAtX, grAtY);
    }

    /**
     * Call this to pass the page bitmap in case there is no reference bitmap.
     *
     * @param pageBitmap 
     */
    public void setPageBitmap(Bitmap pageBitmap)
    {
        this.pageBitmap = pageBitmap;
    }

    private Bitmap getGrReference()
            throws IntegerMaxValueException, InvalidHeaderValueException, IOException
    {
        final SegmentHeader[] segments = segmentHeader.getRtSegments();
        if (segments == null)
        {
            if (CombinationOperator.REPLACE != regionInfo.getCombinationOperator())
            {
                // 7.4.7.5 1) "If this segment does not refer to another region segment 
                //             then its external combination operator must be REPLACE"
                throw new InvalidHeaderValueException("REPLACE combination operator expected");
            }
            // See page 79:
            // "The region segment is an immediate refinement region segment that refers to no other segments.
            //  In this case, the region segment is acting as a refinement of part of the page buffer."
            // 7.4.7.4 Reference bitmap selection:
            // If this segment does not refer to another region segment, set GRREFERENCE to be a bitmap containing the current
            // contents of the page buffer (see clause 8), restricted to the area of the page buffer
            // specified by this segment’s region segment information field.
            Rectangle roi = new Rectangle(regionInfo.getXLocation(),
                                          regionInfo.getYLocation(),
                                            regionInfo.getBitmapWidth(),
                                            regionInfo.getBitmapHeight());
            return Bitmaps.extract(roi, pageBitmap);
        }
        final Region region = (Region) segments[0].getSegmentData();

        return region.getRegionBitmap();
    }

    @Override
    public void init(final SegmentHeader header, final SubInputStream sis) throws IOException
    {
        this.segmentHeader = header;
        this.subInputStream = sis;
        this.regionInfo = new RegionSegmentInformation(subInputStream);
        parseHeader();
    }

    /**
     * @deprecated Use {@link GenericRefinementRegionDecodingProcedure#decode(
     *     ArithmeticDecoder, CX, int, int, short, boolean, Bitmap, int, int, short[], short[])}
     *     directly, supplying the shared {@code ArithmeticDecoder} and {@code CX} as arguments.
     *     This method will be removed in the next major release.
     */
    @Deprecated
    protected void setParameters(final CX cx, final ArithmeticDecoder arithmeticDecoder,
            final short grTemplate, final int regionWidth, final int regionHeight,
            final Bitmap grReference, final int grReferenceDX, final int grReferenceDY,
            final boolean isTPGRon, final short[] grAtX, final short[] grAtY)
    {
        if (null != cx)
        {
            this.cx = cx;
        }

        if (null != arithmeticDecoder)
        {
            this.arithDecoder = arithmeticDecoder;
        }

        this.templateID = grTemplate;

        this.regionInfo.setBitmapWidth(regionWidth);
        this.regionInfo.setBitmapHeight(regionHeight);

        this.referenceBitmap = grReference;
        this.referenceDX = grReferenceDX;
        this.referenceDY = grReferenceDY;

        this.isTPGROn = isTPGRon;

        this.grAtX = grAtX;
        this.grAtY = grAtY;
    }

    @Override
    public RegionSegmentInformation getRegionInfo()
    {
        return regionInfo;
    }
}
