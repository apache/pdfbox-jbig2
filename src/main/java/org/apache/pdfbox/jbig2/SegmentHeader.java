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

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.jbig2.io.SubInputStream;
import org.apache.pdfbox.jbig2.segments.EndOfStripe;
import org.apache.pdfbox.jbig2.segments.GenericRefinementRegion;
import org.apache.pdfbox.jbig2.segments.GenericRegion;
import org.apache.pdfbox.jbig2.segments.HalftoneRegion;
import org.apache.pdfbox.jbig2.segments.PageInformation;
import org.apache.pdfbox.jbig2.segments.PatternDictionary;
import org.apache.pdfbox.jbig2.segments.Profiles;
import org.apache.pdfbox.jbig2.segments.SymbolDictionary;
import org.apache.pdfbox.jbig2.segments.Table;
import org.apache.pdfbox.jbig2.segments.TextRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The basic class for all JBIG2 segments.
 */
@SuppressWarnings("unchecked")
public class SegmentHeader
{
    private static final Logger log = LoggerFactory.getLogger(SegmentHeader.class);

    private static final Map<Integer, Class<? extends SegmentData>> SEGMENT_TYPE_MAP = new HashMap<Integer, Class<? extends SegmentData>>();

    static
    {
        Object SEGMENT_TYPES[][] = { { 0, SymbolDictionary.class }, { 4, TextRegion.class },
                { 6, TextRegion.class }, { 7, TextRegion.class }, { 16, PatternDictionary.class },
                { 20, HalftoneRegion.class }, { 22, HalftoneRegion.class },
                { 23, HalftoneRegion.class }, { 36, GenericRegion.class },
                { 38, GenericRegion.class }, { 39, GenericRegion.class },
                { 40, GenericRefinementRegion.class }, { 42, GenericRefinementRegion.class },
                { 43, GenericRefinementRegion.class }, { 48, PageInformation.class },
                { 50, EndOfStripe.class }, { 52, Profiles.class }, { 53, Table.class }, };

        for (int i = 0; i < SEGMENT_TYPES.length; i++)
        {
            Object[] objects = SEGMENT_TYPES[i];
            SEGMENT_TYPE_MAP.put((Integer) objects[0], (Class<? extends SegmentData>) objects[1]);
        }
    }

    private int segmentNr;
    private int segmentType;
    private byte retainFlag;
    private int pageAssociation;
    private byte pageAssociationFieldSize;
    private SegmentHeader[] rtSegments;
    private long segmentHeaderLength;
    private long segmentDataLength;
    private long segmentDataStartOffset;
    private final SubInputStream subInputStream;

    private Reference<SegmentData> segmentData;

    public SegmentHeader(JBIG2Document document, SubInputStream sis, long offset,
            int organisationType) throws IOException
    {
        this.subInputStream = sis;
        parse(document, sis, offset, organisationType);
    }

    /**
     *
     *
     * @param document
     * @param subInputStream
     * @param organisationType
     * @param offset - The offset where the segment header starts
     * @throws IOException
     */
    private void parse(JBIG2Document document, ImageInputStream subInputStream, long offset,
            int organisationType) throws IOException
    {

        printDebugMessage("\n########################");
        printDebugMessage("Segment parsing started.");

        subInputStream.seek(offset);
        printDebugMessage("|-Seeked to offset: " + offset);

        /* 7.2.2 Segment number */
        readSegmentNumber(subInputStream);

        /* 7.2.3 Segment header flags */
        readSegmentHeaderFlag(subInputStream);

        /* 7.2.4 Amount of referred-to segments */
        int countOfRTS = readAmountOfReferredToSegments(subInputStream);

        /* 7.2.5 Referred-to segments numbers */
        int[] rtsNumbers = readReferredToSegmentsNumbers(subInputStream, countOfRTS);

        /* 7.2.6 Segment page association (Checks how big the page association field is.) */
        readSegmentPageAssociation(document, subInputStream, countOfRTS, rtsNumbers);

        /* 7.2.7 Segment data length (Contains the length of the data part (in bytes).) */
        readSegmentDataLength(subInputStream);

        readDataStartOffset(subInputStream, organisationType);
        readSegmentHeaderLength(subInputStream, offset);
        printDebugMessage("########################\n");
    }

    /**
     * 7.2.2 Segment number
     *
     * @param subInputStream
     * @throws IOException
     */
    private void readSegmentNumber(ImageInputStream subInputStream) throws IOException
    {
        segmentNr = (int) (subInputStream.readBits(32) & 0xffffffff);
        printDebugMessage("|-Segment Nr: " + segmentNr);
    }

    /**
     * 7.2.3 Segment header flags
     *
     * @param subInputStream
     * @throws IOException
     */
    private void readSegmentHeaderFlag(ImageInputStream subInputStream) throws IOException
    {
        // Bit 7: Retain Flag, if 1, this segment is flagged as retained;
        retainFlag = (byte) subInputStream.readBit();
        printDebugMessage("|-Retain flag: " + retainFlag);

        // Bit 6: Size of the page association field. One byte if 0, four bytes if 1;
        pageAssociationFieldSize = (byte) subInputStream.readBit();
        printDebugMessage("|-Page association field size=" + pageAssociationFieldSize);

        // Bit 5-0: Contains the values (between 0 and 62 with gaps) for segment types, specified in 7.3
        segmentType = (int) (subInputStream.readBits(6) & 0xff);
        printDebugMessage("|-Segment type=" + segmentType);
    }

    /**
     * 7.2.4 Amount of referred-to segments
     *
     * @param subInputStream
     * @return The amount of referred-to segments.
     * @throws IOException
     */
    private int readAmountOfReferredToSegments(ImageInputStream subInputStream) throws IOException
    {
        int countOfRTS = (int) (subInputStream.readBits(3) & 0xf);
        printDebugMessage("|-RTS count: " + countOfRTS);

        byte[] retainBit;

        printDebugMessage("  |-Stream position before RTS: " + subInputStream.getStreamPosition());

        if (countOfRTS <= 4)
        {
            /* short format */
            retainBit = new byte[5];
            for (int i = 0; i <= 4; i++)
            {
                retainBit[i] = (byte) subInputStream.readBit();
            }
        }
        else
        {
            /* long format */
            countOfRTS = (int) (subInputStream.readBits(29) & 0xffffffff);

            int arrayLength = (countOfRTS + 8) >> 3;
            retainBit = new byte[arrayLength <<= 3];

            for (int i = 0; i < arrayLength; i++)
            {
                retainBit[i] = (byte) subInputStream.readBit();
            }
        }

        printDebugMessage("  |-Stream position after RTS: " + subInputStream.getStreamPosition());

        return countOfRTS;
    }

    /**
     * 7.2.5 Referred-to segments numbers
     * <p>
     * Gathers all segment numbers of referred-to segments. The segments itself are stored in the {@link #rtSegments}
     * array.
     *
     * @param subInputStream - Wrapped source data input stream.
     * @param countOfRTS - The amount of referred-to segments.
     *
     * @return An array with the segment number of all referred-to segments.
     *
     * @throws IOException
     */
    private int[] readReferredToSegmentsNumbers(ImageInputStream subInputStream, int countOfRTS)
            throws IOException
    {
        int[] rtsNumbers = new int[countOfRTS];

        if (countOfRTS > 0)
        {
            short rtsSize = 1;
            if (segmentNr > 256)
            {
                rtsSize = 2;
                if (segmentNr > 65536)
                {
                    rtsSize = 4;
                }
            }

            rtSegments = new SegmentHeader[countOfRTS];

            printDebugMessage("|-Length of RT segments list: " + rtSegments.length);

            for (int i = 0; i < countOfRTS; i++)
            {
                rtsNumbers[i] = (int) (subInputStream.readBits(rtsSize << 3) & 0xffffffff);
            }
        }

        return rtsNumbers;
    }

    /**
     * 7.2.6 Segment page association
     *
     * @param document
     * @param subInputStream
     * @param countOfRTS
     * @param rtsNumbers
     * @throws IOException
     */
    private void readSegmentPageAssociation(JBIG2Document document, ImageInputStream subInputStream,
            int countOfRTS, int[] rtsNumbers) throws IOException
    {
        if (pageAssociationFieldSize == 0)
        {
            // Short format
            pageAssociation = (short) (subInputStream.readBits(8) & 0xff);
        }
        else
        {
            // Long format
            pageAssociation = (int) (subInputStream.readBits(32) & 0xffffffff);
        }

        if (countOfRTS > 0)
        {
            final JBIG2Page page = document.getPage(pageAssociation);
            for (int i = 0; i < countOfRTS; i++)
            {
                rtSegments[i] = (null != page ? page.getSegment(rtsNumbers[i])
                        : document.getGlobalSegment(rtsNumbers[i]));
            }
        }
    }

    /**
     * 7.2.7 Segment data length
     * <p>
     * Contains the length of the data part in bytes.
     *
     * @param subInputStream
     * @throws IOException
     */
    private void readSegmentDataLength(ImageInputStream subInputStream) throws IOException
    {
        segmentDataLength = (subInputStream.readBits(32) & 0xffffffff);
        printDebugMessage("|-Data length: " + segmentDataLength);
    }

    /**
     * Sets the offset only if organization type is SEQUENTIAL. If random, data starts after segment headers and can be
     * determined when all segment headers are parsed and allocated.
     *
     * @param subInputStream
     * @param organisationType
     * @throws IOException
     */
    private void readDataStartOffset(ImageInputStream subInputStream, int organisationType)
            throws IOException
    {
        if (organisationType == JBIG2Document.SEQUENTIAL)
        {
            printDebugMessage("|-Organization is sequential.");
            segmentDataStartOffset = subInputStream.getStreamPosition();
        }
    }

    private void readSegmentHeaderLength(ImageInputStream subInputStream, long offset)
            throws IOException
    {
        segmentHeaderLength = subInputStream.getStreamPosition() - offset;
        printDebugMessage("|-Segment header length: " + segmentHeaderLength);
    }

    private void printDebugMessage(String message)
    {
        log.debug(message);
    }

    public int getSegmentNr()
    {
        return segmentNr;
    }

    public int getSegmentType()
    {
        return segmentType;
    }

    public long getSegmentHeaderLength()
    {
        return segmentHeaderLength;
    }

    public long getSegmentDataLength()
    {
        return segmentDataLength;
    }

    public long getSegmentDataStartOffset()
    {
        return segmentDataStartOffset;
    }

    public void setSegmentDataStartOffset(long segmentDataStartOffset)
    {
        this.segmentDataStartOffset = segmentDataStartOffset;
    }

    public SegmentHeader[] getRtSegments()
    {
        return rtSegments;
    }

    public int getPageAssociation()
    {
        return pageAssociation;
    }

    public short getRetainFlag()
    {
        return retainFlag;
    }

    /**
     * Creates and returns a new {@link SubInputStream} that provides the data part of this segment. It is a clipped
     * view of the source input stream.
     *
     * @return The {@link SubInputStream} that represents the data part of the segment.
     */
    public SubInputStream getDataInputStream()
    {
        return new SubInputStream(subInputStream, segmentDataStartOffset, segmentDataLength);
    }

    /**
     * Retrieves the segments' data part.
     *
     * @return Retrieved {@link SegmentData} instance.
     */
    public SegmentData getSegmentData()
    {
        SegmentData segmentDataPart = null;

        if (null != segmentData)
        {
            segmentDataPart = segmentData.get();
        }

        if (null == segmentDataPart)
        {
            try
            {

                Class<? extends SegmentData> segmentClass = SEGMENT_TYPE_MAP.get(segmentType);

                if (null == segmentClass)
                {
                    throw new IllegalArgumentException("No segment class for type " + segmentType);
                }

                segmentDataPart = segmentClass.newInstance();
                segmentDataPart.init(this, getDataInputStream());

                segmentData = new SoftReference<SegmentData>(segmentDataPart);

            }
            catch (Exception e)
            {
                throw new RuntimeException("Can't instantiate segment class", e);
            }
        }

        return segmentDataPart;
    }

    public void cleanSegmentData()
    {
        if (segmentData != null)
        {
            segmentData = null;
        }
    }

    @Override
    public String toString()
    {
        StringBuilder stringBuilder = new StringBuilder();

        if (rtSegments != null)
        {
            for (SegmentHeader s : rtSegments)
            {
                stringBuilder.append(s.segmentNr + " ");
            }
        }
        else
        {
            stringBuilder.append("none");
        }

        return "\n#SegmentNr: " + segmentNr //
                + "\n SegmentType: " + segmentType //
                + "\n PageAssociation: " + pageAssociation //
                + "\n Referred-to segments: " + stringBuilder.toString() //
                + "\n"; //
    }
}
