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
import org.apache.pdfbox.jbig2.Region;
import org.apache.pdfbox.jbig2.SegmentHeader;
import org.apache.pdfbox.jbig2.decoder.GenericRefinementRegionDecodingProcedure;
import org.apache.pdfbox.jbig2.decoder.arithmetic.ArithmeticDecoder;
import org.apache.pdfbox.jbig2.decoder.arithmetic.ArithmeticIntegerDecoder;
import org.apache.pdfbox.jbig2.decoder.arithmetic.CX;
import org.apache.pdfbox.jbig2.decoder.huffman.EncodedTable;
import org.apache.pdfbox.jbig2.decoder.huffman.HuffmanTable;
import org.apache.pdfbox.jbig2.decoder.huffman.StandardTables;
import org.apache.pdfbox.jbig2.err.IntegerMaxValueException;
import org.apache.pdfbox.jbig2.err.InvalidHeaderValueException;
import org.apache.pdfbox.jbig2.image.Bitmaps;
import org.apache.pdfbox.jbig2.io.SubInputStream;

/**
 * This class represents the data of segment type "Symbol dictionary". Parsing is described in 7.4.2.1.1 - 7.4.1.1.5 and
 * decoding procedure is described in 6.5.
 */
public class SymbolDictionary implements Dictionary
{

    private SubInputStream subInputStream;

    /** Symbol dictionary flags, 7.4.2.1.1 */
    private short sdrTemplate;
    private byte sdTemplate;
    private boolean isCodingContextRetained;
    private boolean isCodingContextUsed;
    private short sdHuffAggInstanceSelection;
    private short sdHuffBMSizeSelection;
    private short sdHuffDecodeWidthSelection;
    private short sdHuffDecodeHeightSelection;
    private boolean useRefinementAggregation;
    private boolean isHuffmanEncoded;

    /** Symbol dictionary AT flags, 7.4.2.1.2 */
    private short[] sdATX;
    private short[] sdATY;

    /** Symbol dictionary refinement AT flags, 7.4.2.1.3 */
    private short[] sdrATX;
    private short[] sdrATY;

    /** Number of exported symbols, 7.4.2.1.4 */
    private int amountOfExportSymbolss;

    /** Number of new symbols, 7.4.2.1.5 */
    private int amountOfNewSymbols;

    /** Further parameters */
    private SegmentHeader segmentHeader;
    private int amountOfImportedSymbols;
    private ArrayList<Bitmap> importSymbols;
    private int amountOfDecodedSymbols;
    private Bitmap[] newSymbols;

    /** User-supplied tables * */
    private HuffmanTable dhTable;
    private HuffmanTable dwTable;
    private HuffmanTable bmSizeTable;
    private HuffmanTable aggInstTable;

    /** Return value of that segment */
    private ArrayList<Bitmap> exportSymbols;
    private ArrayList<Bitmap> sbSymbols;

    private ArithmeticDecoder arithmeticDecoder;
    private ArithmeticIntegerDecoder iDecoder;

    private TextRegion textRegion;
    private GenericRegion genericRegion;
    private CX cx;

    private CX cxIADH;
    private CX cxIADW;
    private CX cxIAAI;
    private CX cxIAEX;
    private CX cxIARDX;
    private CX cxIARDY;
    private CX cxIADT;

    protected CX cxIAID;
    private int sbSymCodeLen;

    SymbolDictionary lastSymbolDictionary;

    public SymbolDictionary()
    {
    }

    public SymbolDictionary(final SubInputStream subInputStream, final SegmentHeader segmentHeader)
            throws IOException
    {
        this.subInputStream = subInputStream;
        this.segmentHeader = segmentHeader;
    }

    private void parseHeader()
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {
        readRegionFlags();
        setAtPixels();
        setRefinementAtPixels();
        readAmountOfExportedSymbols();
        readAmountOfNewSymbols();
        setInSyms();

        boolean isContextAdopted = false;

        SegmentHeader[] rtSegments = segmentHeader.getRtSegments();

        if (rtSegments != null)
        {
            for (int i = rtSegments.length - 1; i >= 0; i--)
            {

                if (rtSegments[i].getSegmentType() == 0)
                {
                    lastSymbolDictionary = (SymbolDictionary) rtSegments[i]
                        .getSegmentData();

                    if (isCodingContextUsed && lastSymbolDictionary.isCodingContextRetained)
                    {
                        isContextAdopted = true;
                    }
                    break;
                }
            }
        }

        if (isCodingContextUsed && !isContextAdopted)
        {
            throw new InvalidHeaderValueException(
                lastSymbolDictionary == null
                    ? "Coding context reuse requested, but no referred symbol dictionary found"
                    : "Coding context reuse requested, but last referred symbol dictionary does not retain coding context"
            );
        }

        this.checkInput();
    }

    private void readRegionFlags() throws IOException
    {
        /* Bit 13-15 */
        subInputStream.readBits(3); // Dirty read... reserved bits must be 0

        /* Bit 12 */
        sdrTemplate = (short) subInputStream.readBit();

        /* Bit 10-11 */
        sdTemplate = (byte) (subInputStream.readBits(2) & 0xf);

        /* Bit 9 */
        if (subInputStream.readBit() == 1)
        {
            isCodingContextRetained = true;
        }

        /* Bit 8 */
        if (subInputStream.readBit() == 1)
        {
            isCodingContextUsed = true;
        }

        /* Bit 7 */
        sdHuffAggInstanceSelection = (short) subInputStream.readBit();

        /* Bit 6 */
        sdHuffBMSizeSelection = (short) subInputStream.readBit();

        /* Bit 4-5 */
        sdHuffDecodeWidthSelection = (short) (subInputStream.readBits(2) & 0xf);

        /* Bit 2-3 */
        sdHuffDecodeHeightSelection = (short) (subInputStream.readBits(2) & 0xf);

        /* Bit 1 */
        if (subInputStream.readBit() == 1)
        {
            useRefinementAggregation = true;
        }

        /* Bit 0 */
        if (subInputStream.readBit() == 1)
        {
            isHuffmanEncoded = true;
        }
    }

    private void setAtPixels() throws IOException
    {
        if (!isHuffmanEncoded)
        {
            if (sdTemplate == 0)
            {
                readAtPixels(4);
            }
            else
            {
                readAtPixels(1);
            }
        }
    }

    private void setRefinementAtPixels() throws IOException
    {
        if (useRefinementAggregation && sdrTemplate == 0)
        {
            readRefinementAtPixels(2);
        }
    }

    private void readAtPixels(final int amountOfPixels) throws IOException
    {
        sdATX = new short[amountOfPixels];
        sdATY = new short[amountOfPixels];

        for (int i = 0; i < amountOfPixels; i++)
        {
            sdATX[i] = subInputStream.readByte();
            sdATY[i] = subInputStream.readByte();
        }
    }

    private void readRefinementAtPixels(final int amountOfAtPixels) throws IOException
    {
        sdrATX = new short[amountOfAtPixels];
        sdrATY = new short[amountOfAtPixels];

        for (int i = 0; i < amountOfAtPixels; i++)
        {
            sdrATX[i] = subInputStream.readByte();
            sdrATY[i] = subInputStream.readByte();
        }
    }

    private void readAmountOfExportedSymbols() throws IOException
    {
        amountOfExportSymbolss = (int) subInputStream.readBits(32); // & 0xffffffff;
    }

    private void readAmountOfNewSymbols() throws IOException
    {
        amountOfNewSymbols = (int) subInputStream.readBits(32); // & 0xffffffff;
    }

    private void setInSyms()
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {
        if (segmentHeader.getRtSegments() != null)
        {
            retrieveImportSymbols();
        }
        else
        {
            importSymbols = new ArrayList<Bitmap>();
        }
    }

    /**
     * Adopt retained arithmetic coding context from another symbol dictionary.
     *
     * Per spec §7.4.2.2:
     * - Configuration MUST match (validated here)
     * - Only bitmap coding statistics (CX) are reused
     * - ArithmeticDecoder MUST NOT be reused (stream-bound)
     * @throws InvalidHeaderValueException 
     */
    private void adoptRetainedCodingContexts(final SymbolDictionary sd) throws InvalidHeaderValueException
    {
        validateContextValues(sd);
        this.cx = sd.cx.copy();
    }
    
    /**
     * The values of SDHUFF, SDREFAGG, SDTEMPLATE, SDRTEMPLATE, and all of the AT locations
     * (both direct and refinement) for this symbol dictionary must match the corresponding
     * values from the symbol dictionary whose context values are being used.
     * @param sd
     * @throws InvalidHeaderValueException 
     */
    private void validateContextValues(final SymbolDictionary sd) throws InvalidHeaderValueException
    {
        if ( this.isHuffmanEncoded != sd.isHuffmanEncoded
            || this.useRefinementAggregation != sd.useRefinementAggregation
            || this.sdTemplate != sd.sdTemplate
            || this.sdrTemplate != sd.sdrTemplate
            || !java.util.Arrays.equals(this.sdATX, sd.sdATX)
            || !java.util.Arrays.equals(this.sdATY, sd.sdATY)
            || !java.util.Arrays.equals(this.sdrATX, sd.sdrATX)
            || !java.util.Arrays.equals(this.sdrATY, sd.sdrATY))
        {
            throw new InvalidHeaderValueException("SymbolDictionary reuse values don't match");
        }
    }

    private void checkInput() throws InvalidHeaderValueException
    {
        if (isHuffmanEncoded)
        {
            if (sdTemplate != 0)
            {
                sdTemplate = 0;
            }
            if (!useRefinementAggregation)
            {
                if (isCodingContextRetained)
                {
                    isCodingContextRetained = false;
                }

                if (isCodingContextUsed)
                {
                    isCodingContextUsed = false;
                }
            }

        }
        else
        {
            if (sdHuffBMSizeSelection != 0)
            {
                sdHuffBMSizeSelection = 0;
            }
            if (sdHuffDecodeWidthSelection != 0)
            {
                sdHuffDecodeWidthSelection = 0;
            }
            if (sdHuffDecodeHeightSelection != 0)
            {
                sdHuffDecodeHeightSelection = 0;
            }
        }

        if (!useRefinementAggregation)
        {
            if (sdrTemplate != 0)
            {
                sdrTemplate = 0;
            }
        }

        if (!isHuffmanEncoded || !useRefinementAggregation)
        {
            if (sdHuffAggInstanceSelection != 0)
            {
                sdHuffAggInstanceSelection = 0;
            }
        }
    }

    private void ensureBitmapCxInitialized() throws InvalidHeaderValueException, IOException
    {
        if (cx != null)
        {
            return;
        }

        if (isCodingContextUsed)
        {
            if (lastSymbolDictionary == null)
            {
                throw new InvalidHeaderValueException(
                    "Coding context reuse requested but no previous dictionary available");
            }

            adoptRetainedCodingContexts(lastSymbolDictionary);
        }
        else
        {
            resetBitmapCodingStatistics();
        }
    }

    /**
     * 6.5.5 Decoding the symbol dictionary
     * 
     * @return List of decoded symbol bitmaps as an <code>ArrayList</code>
     */
    @Override
    public ArrayList<Bitmap> getDictionary()
            throws IOException, IntegerMaxValueException, InvalidHeaderValueException
    {
        if (null == exportSymbols)
        {
            ensureBitmapCxInitialized();

            if (useRefinementAggregation)
                sbSymCodeLen = getSbSymCodeLen();

            if (!isHuffmanEncoded) {
                resetIntegerCoderStatistics();
            }

            // decodes all referred segments including lastSymbolDictionary
            setSymbolsArray();

            // Now safe: lastSymbolDictionary was decoded by setSymbolsArray above
            if (!isHuffmanEncoded && isCodingContextUsed) {
                adoptRetainedCodingContexts(lastSymbolDictionary);
            }

            /* 6.5.5 1) */
            newSymbols = new Bitmap[amountOfNewSymbols];

            /* 6.5.5 2) */
            int[] newSymbolsWidths = null;
            if (isHuffmanEncoded && !useRefinementAggregation)
            {
                newSymbolsWidths = new int[amountOfNewSymbols];
            }

            /* 6.5.5 3) */
            int heightClassHeight = 0;
            amountOfDecodedSymbols = 0;

            /* 6.5.5 4 a) */
            while (amountOfDecodedSymbols < amountOfNewSymbols)
            {

                /* 6.5.5 4 b) */
                heightClassHeight += decodeHeightClassDeltaHeight();
                int symbolWidth = 0;
                int totalWidth = 0;
                final int heightClassFirstSymbolIndex = amountOfDecodedSymbols;

                /* 6.5.5 4 c) */

                // Repeat until OOB - OOB sends a break;
                while (true)
                {
                    /* 4 c) i) */
                    final long differenceWidth = decodeDifferenceWidth();

                    /*
                     * If result is OOB, then all the symbols in this height class has been decoded; proceed to step 4
                     * d). Also exit, if the expected number of symbols have been decoded.
                     * 
                     * The latter exit condition guards against pathological cases where a symbol's DW never contains
                     * OOB and thus never terminates.
                     */
                    if (differenceWidth == Long.MAX_VALUE
                            || amountOfDecodedSymbols >= amountOfNewSymbols)
                    {
                        break;
                    }

                    symbolWidth += differenceWidth;
                    totalWidth += symbolWidth;

                    /* 4 c) ii) */
                    if (!isHuffmanEncoded || useRefinementAggregation)
                    {
                        if (!useRefinementAggregation)
                        {
                            // 6.5.8.1 - Direct coded
                            decodeDirectlyThroughGenericRegion(symbolWidth, heightClassHeight);
                        }
                        else
                        {
                            // 6.5.8.2 - Refinement/Aggregate-coded
                            decodeAggregate(symbolWidth, heightClassHeight);
                        }
                    }
                    else if (isHuffmanEncoded && !useRefinementAggregation)
                    {
                        /* 4 c) iii) */
                        newSymbolsWidths[amountOfDecodedSymbols] = symbolWidth;
                    }
                    amountOfDecodedSymbols++;
                }

                /* 6.5.5 4 d) */
                if (isHuffmanEncoded && !useRefinementAggregation)
                {
                    /* 6.5.9 */
                    final long bmSize;
                    if (sdHuffBMSizeSelection == 0)
                    {
                        bmSize = StandardTables.getTable(1).decode(subInputStream);
                    }
                    else
                    {
                        bmSize = huffDecodeBmSize();
                    }

                    subInputStream.skipBits();

                    final Bitmap heightClassCollectiveBitmap = decodeHeightClassCollectiveBitmap(
                            bmSize, heightClassHeight, totalWidth);

                    subInputStream.skipBits();
                    decodeHeightClassBitmap(heightClassCollectiveBitmap,
                            heightClassFirstSymbolIndex, heightClassHeight, newSymbolsWidths);
                }
            }

            /* 5) */
            /* 6.5.10 1) - 5) */

            final int[] exFlags = getToExportFlags();

            /* 6.5.10 6) - 8) */
            setExportedSymbols(exFlags);
        }

        return exportSymbols;
    }

    /**
     * Step 4 (§7.4.2.2): Reset arithmetic coding statistics for the generic
     * region and generic refinement region decoding procedures to zero.
     * Only the bitmap CX is reset here; integer coder contexts are separate (step 5).
     */
    private void resetBitmapCodingStatistics()
    {
        cx = new CX(65536, 1);
    }

    /**
     * Step 5 (§7.4.2.2): Reset arithmetic coding statistics for all contexts
     * of all arithmetic integer coders to zero.
     */
    private void resetIntegerCoderStatistics() throws IOException
    {
        cxIADT = new CX(512, 1);
        cxIADH = new CX(512, 1);
        cxIADW = new CX(512, 1);
        cxIAAI = new CX(512, 1);
        cxIAEX = new CX(512, 1);

        if (useRefinementAggregation)
        {
            cxIAID  = new CX(1 << sbSymCodeLen, 1);
            cxIARDX = new CX(512, 1);
            cxIARDY = new CX(512, 1);
        }

        arithmeticDecoder = new ArithmeticDecoder(subInputStream);
        iDecoder = new ArithmeticIntegerDecoder(arithmeticDecoder);
    }

    private void decodeHeightClassBitmap(final Bitmap heightClassCollectiveBitmap,
            final int heightClassFirstSymbol, final int heightClassHeight,
            final int[] newSymbolsWidths)
            throws IntegerMaxValueException, InvalidHeaderValueException, IOException
    {

        for (int i = heightClassFirstSymbol; i < amountOfDecodedSymbols; i++)
        {
            int startColumn = 0;

            for (int j = heightClassFirstSymbol; j <= i - 1; j++)
            {
                startColumn += newSymbolsWidths[j];
            }

            final Rectangle roi = new Rectangle(startColumn, 0, newSymbolsWidths[i],
                    heightClassHeight);
            final Bitmap symbolBitmap = Bitmaps.extract(roi, heightClassCollectiveBitmap);
            newSymbols[i] = symbolBitmap;
            sbSymbols.add(symbolBitmap);
        }
    }

    private void decodeAggregate(final int symbolWidth, final int heightClassHeight)
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {
        // 6.5.8.2 1)
        // 6.5.8.2.1 - Number of symbol instances in aggregation
        final long amountOfRefinementAggregationInstances;
        if (isHuffmanEncoded)
        {
            amountOfRefinementAggregationInstances = huffDecodeRefAggNInst();
        }
        else
        {
            amountOfRefinementAggregationInstances = iDecoder.decode(cxIAAI);
        }

        if (amountOfRefinementAggregationInstances > 1)
        {
            // 6.5.8.2 2)
            decodeThroughTextRegion(symbolWidth, heightClassHeight,
                    amountOfRefinementAggregationInstances);
        }
        else if (amountOfRefinementAggregationInstances == 1)
        {
            // 6.5.8.2 3) refers to 6.5.8.2.2
            decodeRefinedSymbol(symbolWidth, heightClassHeight);
        }
    }

    private long huffDecodeRefAggNInst() throws IOException, InvalidHeaderValueException
    {
        if (sdHuffAggInstanceSelection == 0)
        {
            return StandardTables.getTable(1).decode(subInputStream);
        }
        else if (sdHuffAggInstanceSelection == 1)
        {
            if (aggInstTable == null)
            {
                int aggregationInstanceNumber = 0;

                if (sdHuffDecodeHeightSelection == 3)
                {
                    aggregationInstanceNumber++;
                }
                if (sdHuffDecodeWidthSelection == 3)
                {
                    aggregationInstanceNumber++;
                }
                if (sdHuffBMSizeSelection == 3)
                {
                    aggregationInstanceNumber++;
                }

                aggInstTable = getUserTable(aggregationInstanceNumber);
            }
            return aggInstTable.decode(subInputStream);
        }
        return 0;
    }

    private void decodeThroughTextRegion(final int symbolWidth, final int heightClassHeight,
            final long amountOfRefinementAggregationInstances)
            throws IOException, IntegerMaxValueException, InvalidHeaderValueException
    {
        if (textRegion == null)
        {
            textRegion = new TextRegion(subInputStream, null);

            textRegion.setContexts(cx, // default context
                    new CX(512, 1), // IADT
                    new CX(512, 1), // IAFS
                    new CX(512, 1), // IADS
                    new CX(512, 1), // IAIT
                    cxIAID, // IAID
                    new CX(512, 1), // IARDW
                    new CX(512, 1), // IARDH
                    new CX(512, 1), // IARDX
                    new CX(512, 1) // IARDY
            );
        }

        // 6.5.8.2.4 Concatenating the array used as parameter later.
        setSymbolsArray();

        // 6.5.8.2 2) Parameters set according to Table 17, page 36
        textRegion.setParameters(arithmeticDecoder, iDecoder, isHuffmanEncoded, true, symbolWidth,
                heightClassHeight, amountOfRefinementAggregationInstances, 1,
                (amountOfImportedSymbols + amountOfDecodedSymbols), (short) 0, (short) 0,
                (short) 0, (short) 1, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, sdrTemplate, sdrATX, sdrATY, sbSymbols,
                sbSymCodeLen);

        addSymbol(textRegion);
    }

    private void decodeRefinedSymbol(final int symbolWidth, final int heightClassHeight)
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {

        final int id;
        final int rdx;
        final int rdy;
        long symInRefSize = 0;
        long streamPosition0 = 0;
        if (isHuffmanEncoded)
        {
            /* 2) - 4) */
            id = (int) subInputStream.readBits(sbSymCodeLen);
            rdx = (int) StandardTables.getTable(15).decode(subInputStream);
            rdy = (int) StandardTables.getTable(15).decode(subInputStream);

            /* 5) a) */
            symInRefSize = StandardTables.getTable(1).decode(subInputStream);

            /* 5) b) - Skip over remaining bits */
            subInputStream.skipBits();

            streamPosition0 = subInputStream.getStreamPosition();

            // 5) c) - Initialize arithmetic decoder for refinement bitmap
            // Note that the same subInputStream is used for both symbol dictionary decoding
            // and refinement bitmap decoding.
            arithmeticDecoder = new ArithmeticDecoder(subInputStream);
        }
        else
        {
            /* 2) - 4) */
            id = iDecoder.decodeIAID(cxIAID, sbSymCodeLen);
            rdx = (int) iDecoder.decode(cxIARDX);
            rdy = (int) iDecoder.decode(cxIARDY);
        }

        /* 6) */
        setSymbolsArray();
        final Bitmap ibo = sbSymbols.get(id);
        decodeNewSymbols(symbolWidth, heightClassHeight, ibo, rdx, rdy);

        /* 7) */
        if (isHuffmanEncoded)
        {
            // Make sure that the processed bytes are not more than symInRefSize
            if (subInputStream.getStreamPosition() > streamPosition0 + symInRefSize)
            {
                throw new IOException("Refinement bitmap bytes expected: " + symInRefSize +
                        ", bytes read: " + (subInputStream.getStreamPosition() - streamPosition0));
            }
            subInputStream.seek(streamPosition0 + symInRefSize); // needed if less
        }
    }

    /**
     * Decodes a new symbol using the provided parameters.
     *
     * @param symWidth The width of the symbol.
     * @param hcHeight The height of the symbol.
     * @param ibo The input bitmap object.
     * @param rdx The x-offset for refinement.
     * @param rdy The y-offset for refinement.
     * @throws IllegalStateException if {@code cx} or {@code arithmeticDecoder} is not initialized.
     * @throws IOException if an I/O error occurs during decoding.
     * @throws InvalidHeaderValueException if an invalid header value is encountered.
     * @throws IntegerMaxValueException if an integer value exceeds its maximum allowed value
     * 
     */
    private void decodeNewSymbols(final int symWidth, final int hcHeight, final Bitmap ibo,
            final int rdx, final int rdy)
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {
        // cx (bitmap coding context) must already be initialized via ensureBitmapCxInitialized()
        // in getDictionary(). It is required by GenericRefinementRegionDecodingProcedure.decode
        // and provides the arithmetic decoder statistics for bitmap decoding.
        if (cx == null)
        {
            throw new IllegalStateException("CX not initialized (bug in initialization order)");
        }

        // arithmeticDecoder must already be initialized for the current bitstream context.
        // It is required by GenericRefinementRegionDecodingProcedure.decode and is
        // normally set during segment decoding (e.g., refinement or integer-coded paths).
        if (arithmeticDecoder == null) {
            throw new IllegalStateException("ArithmeticDecoder not initialized");
        }

        // Parameters as shown in Table 18, page 36
        final Bitmap symbol = GenericRefinementRegionDecodingProcedure.decode(
                arithmeticDecoder, cx, symWidth, hcHeight,
                sdrTemplate, false, ibo, rdx, rdy, sdrATX, sdrATY);

        newSymbols[amountOfDecodedSymbols] = symbol;
        sbSymbols.add(symbol);
    }

    private void decodeDirectlyThroughGenericRegion(final int symWidth, final int hcHeight)
            throws IOException, IntegerMaxValueException, InvalidHeaderValueException
    {
        if (genericRegion == null)
        {
            genericRegion = new GenericRegion(subInputStream);
        }

        // Parameters set according to Table 16, page 35
        genericRegion.setParameters(false, sdTemplate, false, false, sdATX, sdATY, symWidth,
                hcHeight, cx, arithmeticDecoder);

        addSymbol(genericRegion);
    }

    private void addSymbol(final Region region)
            throws IntegerMaxValueException, InvalidHeaderValueException, IOException
    {
        final Bitmap symbol = region.getRegionBitmap();
        newSymbols[amountOfDecodedSymbols] = symbol;
        sbSymbols.add(symbol);
    }

    private long decodeDifferenceWidth() throws IOException, InvalidHeaderValueException
    {
        if (isHuffmanEncoded)
        {
            switch (sdHuffDecodeWidthSelection)
            {
            case 0:
                return StandardTables.getTable(2).decode(subInputStream);
            case 1:
                return StandardTables.getTable(3).decode(subInputStream);
            case 3:
                if (dwTable == null)
                {
                    int dwNr = 0;

                    if (sdHuffDecodeHeightSelection == 3)
                    {
                        dwNr++;
                    }
                    dwTable = getUserTable(dwNr);
                }

                return dwTable.decode(subInputStream);
            }
        }
        else
        {
            return iDecoder.decode(cxIADW);
        }
        return 0;
    }

    private long decodeHeightClassDeltaHeight()
            throws IOException, InvalidHeaderValueException
    {
        if (isHuffmanEncoded)
        {
            return decodeHeightClassDeltaHeightWithHuffman();
        }
        else
        {
            return iDecoder.decode(cxIADH);
        }
    }

    /**
     * 6.5.6 if isHuffmanEncoded
     * 
     * @return long - Result of decoding HCDH
     * @throws IOException
     * @throws InvalidHeaderValueException
     */
    private long decodeHeightClassDeltaHeightWithHuffman()
            throws IOException, InvalidHeaderValueException
    {
        switch (sdHuffDecodeHeightSelection)
        {
        case 0:
            return StandardTables.getTable(4).decode(subInputStream);
        case 1:
            return StandardTables.getTable(5).decode(subInputStream);
        case 3:
            if (dhTable == null)
            {
                dhTable = getUserTable(0);
            }
            return dhTable.decode(subInputStream);
        }

        return 0;
    }

    private Bitmap decodeHeightClassCollectiveBitmap(final long bmSize,
            final int heightClassHeight, final int totalWidth) throws IOException
    {
        if (bmSize == 0)
        {
            final Bitmap heightClassCollectiveBitmap = new Bitmap(totalWidth, heightClassHeight);

            for (int i = 0; i < heightClassCollectiveBitmap.getLength(); i++)
            {
                heightClassCollectiveBitmap.setByte(i, subInputStream.readByte());
            }

            return heightClassCollectiveBitmap;
        }
        else
        {
            if (genericRegion == null)
            {
                genericRegion = new GenericRegion(subInputStream);
            }

            genericRegion.setParameters(true, subInputStream.getStreamPosition(), bmSize,
                    heightClassHeight, totalWidth);

            return genericRegion.getRegionBitmap();
        }
    }

    private void setExportedSymbols(final int[] toExportFlags)
    {
        exportSymbols = new ArrayList<Bitmap>(amountOfExportSymbolss);

        for (int i = 0; i < amountOfImportedSymbols + amountOfNewSymbols; i++)
        {

            if (toExportFlags[i] == 1)
            {
                if (i < amountOfImportedSymbols)
                {
                    exportSymbols.add(importSymbols.get(i));
                }
                else
                {
                    exportSymbols.add(newSymbols[i - amountOfImportedSymbols]);
                }
            }
        }
    }

    private int[] getToExportFlags() throws IOException, InvalidHeaderValueException
    {
        // the validation could be placed a little earlier but it is needed here before the array creation
        if (amountOfImportedSymbols < 0 || amountOfNewSymbols < 0 
                || (long) amountOfImportedSymbols + amountOfNewSymbols > Integer.MAX_VALUE) {
            throw new InvalidHeaderValueException(" Invalid number of symbols: imported=" + amountOfImportedSymbols + ", new=" + amountOfNewSymbols);
        }

        int exIndex = 0;
        int curExFlag = 0;
        final int total = amountOfImportedSymbols + amountOfNewSymbols;
        final int[] exportFlags = new int[total];

        while (exIndex < total)
        {
            long exRunLength;

            if (isHuffmanEncoded) {
                exRunLength = StandardTables.getTable(1).decode(subInputStream);
            } else {
                exRunLength = iDecoder.decode(cxIAEX);
            }

            if (exRunLength < 0 || exRunLength > total - exIndex) {
                throw new InvalidHeaderValueException("Invalid EXRUNLENGTH: " + exRunLength);
            }

            for (int i = exIndex; i < exIndex + exRunLength; i++)
            {
                exportFlags[i] = curExFlag;
            }

            exIndex += (int) exRunLength;
            curExFlag = (curExFlag == 0) ? 1 : 0;
        }

        return exportFlags;
    }

    private long huffDecodeBmSize() throws IOException, InvalidHeaderValueException
    {
        if (bmSizeTable == null)
        {
            int bmNr = 0;

            if (sdHuffDecodeHeightSelection == 3)
            {
                bmNr++;
            }

            if (sdHuffDecodeWidthSelection == 3)
            {
                bmNr++;
            }

            bmSizeTable = getUserTable(bmNr);
        }
        return bmSizeTable.decode(subInputStream);
    }

    /**
     * 6.5.8.2.3 - Setting SBSYMCODES and SBSYMCODELEN
     * 
     * @return Result of computing SBSYMCODELEN
     * @throws IOException
     */
    private int getSbSymCodeLen() throws IOException
    {
        if (isHuffmanEncoded)
        {
            return Math.max(
                    (int) (Math.ceil(
                            Math.log(amountOfImportedSymbols + amountOfNewSymbols) / Math.log(2))),
                    1);
        }
        else
        {
            return (int) (Math
                    .ceil(Math.log(amountOfImportedSymbols + amountOfNewSymbols) / Math.log(2)));
        }
    }

    /**
     * 6.5.8.2.4 - Setting SBSYMS
     * 
     * @throws IOException
     * @throws InvalidHeaderValueException
     * @throws IntegerMaxValueException
     */
    private void setSymbolsArray()
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {
        if (importSymbols == null)
        {
            retrieveImportSymbols();
        }

        if (sbSymbols == null)
        {
            sbSymbols = new ArrayList<Bitmap>();
            sbSymbols.addAll(importSymbols);
        }
    }

    /**
     * Concatenates symbols from all referred-to segments.
     * 
     * @throws IOException
     * @throws InvalidHeaderValueException
     * @throws IntegerMaxValueException
     */
    private void retrieveImportSymbols()
            throws IOException, InvalidHeaderValueException, IntegerMaxValueException
    {
        importSymbols = new ArrayList<Bitmap>();
        for (final SegmentHeader referredToSegmentHeader : segmentHeader.getRtSegments())
        {
            if (referredToSegmentHeader.getSegmentType() == 0)
            {
                final SymbolDictionary sd = (SymbolDictionary) referredToSegmentHeader
                        .getSegmentData();
                importSymbols.addAll(sd.getDictionary());
                amountOfImportedSymbols += sd.amountOfExportSymbolss;
            }
        }
    }

    private HuffmanTable getUserTable(final int tablePosition)
            throws InvalidHeaderValueException, IOException
    {
        int tableCounter = 0;

        for (final SegmentHeader referredToSegmentHeader : segmentHeader.getRtSegments())
        {
            if (referredToSegmentHeader.getSegmentType() == 53)
            {
                if (tableCounter == tablePosition)
                {
                    final Table t = (Table) referredToSegmentHeader.getSegmentData();
                    return new EncodedTable(t);
                }
                else
                {
                    tableCounter++;
                }
            }
        }
        return null;
    }

    public void init(final SegmentHeader header, final SubInputStream sis)
            throws InvalidHeaderValueException, IntegerMaxValueException, IOException
    {
        this.subInputStream = sis;
        this.segmentHeader = header;
        parseHeader();
    }
}
