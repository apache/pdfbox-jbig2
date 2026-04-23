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
 * Reusable implementation of the JBIG2 generic refinement region decoding
 * procedure as defined in ITU-T T.88 (JBIG2), §6.3.
 *
 * <p>This class implements only the <b>decoding algorithm</b> for a generic
 * refinement region. It does not inherently define how parameters are obtained;
 * instead, it relies on the caller to supply or initialize the required inputs.
 * Different parts of the JBIG2 specification reuse this same procedure with
 * different parameter sources:</p>
 *
 * <ul>
 *   <li><b>Generic refinement region segment</b> (§7.4.7):
 *     <ul>
 *       <li>Parameters are parsed from the segment header via {@link #init(...)}.</li>
 *       <li>The reference bitmap is derived from referred-to segments or the page buffer.</li>
 *       <li>Per Table 35, {@code GRREFERENCEDX} and {@code GRREFERENCEDY} are fixed to 0.</li>
 *     </ul>
 *   </li>
 *
 *   <li><b>Symbol dictionary refinement / aggregation</b> (§6.5.8.2):
 *     <ul>
 *       <li>Parameters (including reference bitmap and offsets {@code RDX}, {@code RDY})
 *           are decoded as part of the symbol dictionary procedure.</li>
 *       <li>These parameters must be supplied via {@link #setParameters(...)}.</li>
 *     </ul>
 *   </li>
 *
 *   <li><b>Text region refinement</b> (§6.4, reusing §6.3):
 *     <ul>
 *       <li>Used indirectly by {@link TextRegion} when symbols are refined or aggregated.</li>
 *       <li>All parameters are provided programmatically, similar to the symbol dictionary case.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Usage patterns:</b></p>
 * <ul>
 *   <li><b>Header-driven (segment-based):</b>
 *       Initialize via {@link #init(SegmentHeader, SubInputStream)}.
 *       In this mode, refinement offsets are implicitly zero as defined by Table 35.</li>
 *
 *   <li><b>Parameter-driven (dictionary/text region):</b>
 *       Call {@link #setParameters(...)} before {@link #getRegionBitmap()} to supply
 *       all required decoding parameters explicitly.</li>
 * </ul>
 *
 * <p><b>Important:</b> This class does not explicitly enforce which mode is used.
 * Correct behavior depends on the caller selecting the appropriate initialization
 * path. Mixing header-based initialization with explicit parameter setting may
 * lead to undefined results.</p>
 */
public class GenericRefinementRegion implements Region
{
    public static abstract class Template
    {
        protected abstract short form(short c1, short c2, short c3, short c4, short c5);

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
            grAtX, grAtY
        );
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
