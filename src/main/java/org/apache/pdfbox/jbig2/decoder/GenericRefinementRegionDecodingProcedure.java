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

package org.apache.pdfbox.jbig2.decoder;

import java.io.IOException;

import org.apache.pdfbox.jbig2.Bitmap;
import org.apache.pdfbox.jbig2.decoder.arithmetic.ArithmeticDecoder;
import org.apache.pdfbox.jbig2.decoder.arithmetic.CX;

/**
 * Implements the JBIG2 Generic Refinement Region decoding procedure
 * as defined in ITU-T T.88, §6.3 including the core decoding loop
 * defined in §6.3.5.6.
 *
 * <p>This class is purely algorithmic. It has no knowledge of how its inputs
 * are obtained and no dependency on segment headers or input streams.
 * The entry point is the static {@link #decode decode()} method; callers
 * supply the {@link ArithmeticDecoder} and {@link CX} context model directly,
 * so they decide whether to create fresh instances or share existing ones:</p>
 *
 * <ul>
 *   <li><b>{@link @link org.apache.pdfbox.jbig2.segments.GenericRefinementRegion}</b> — creates its own
 *       {@code ArithmeticDecoder} and {@code CX}, then calls {@code decode()}.</li>
 *   <li><b>Symbol dictionary refinement (§6.5.8.2)</b> — passes the
 *       parent dictionary's shared {@code ArithmeticDecoder} and {@code CX}.</li>
 *   <li><b>Text region refinement (§6.4)</b> — passes the parent text
 *       region's shared {@code ArithmeticDecoder} and {@code CX}.</li>
 * </ul>
 *
 * <p>This class cannot be instantiated by callers. The private constructor and
 * the internal {@link #run} method exist solely to let the many private helper
 * methods share state through fields rather than through long parameter lists.</p>
 */
public class GenericRefinementRegionDecodingProcedure
{

    // -------------------------------------------------------------------------
    // Template strategy — bit-context formation for each template type
    // -------------------------------------------------------------------------

    /**
     * Encapsulates the template-specific operations: context bit formation
     * ({@link #form}) and initial CX index selection ({@link #setIndex}).
     *
     * <p>The two concrete implementations ({@link #T0}, {@link #T1}) correspond
     * to GRTEMPLATE values 0 and 1 as defined in §6.3.3 / Figures 14–15.</p>
     */
    public static abstract class Template
    {
        protected abstract short form(short c1, short c2, short c3, short c4, short c5);

        protected abstract void setIndex(CX cx);
    }

    private static class Template0 extends Template
    {
        @Override
        protected short form(short c1, short c2, short c3, short c4, short c5)
        {
            return (short) ((c1 << 10) | (c2 << 7) | (c3 << 4) | (c4 << 1) | c5);
        }

        @Override
        protected void setIndex(CX cx)
        {
            // Figure 14, page 22
            cx.setIndex(0x100);
        }
    }

    private static class Template1 extends Template
    {
        @Override
        protected short form(short c1, short c2, short c3, short c4, short c5)
        {
            return (short) (((c1 & 0x02) << 8) | (c2 << 6) | ((c3 & 0x03) << 4) | (c4 << 1) | c5);
        }

        @Override
        protected void setIndex(CX cx)
        {
            // Figure 15, page 22
            cx.setIndex(0x080);
        }
    }

    /** Singleton template 0 instance (stateless). */
    public static final Template T0 = new Template0();

    /** Singleton template 1 instance (stateless). */
    public static final Template T1 = new Template1();

    // -------------------------------------------------------------------------
    // Decoder state — injected at construction
    // -------------------------------------------------------------------------

    private final ArithmeticDecoder arithDecoder;
    private final CX cx;

    // -------------------------------------------------------------------------
    // Per-decode working state — reset by each decode() call
    // -------------------------------------------------------------------------

    private short templateID;
    private Template template;
    private boolean isTPGROn;
    private Bitmap referenceBitmap;
    private int referenceDX;
    private int referenceDY;
    private short[] grAtX;
    private short[] grAtY;
    private boolean override;
    private boolean[] grAtOverride;

    /** The bitmap being built; held as a field to avoid passing it everywhere. */
    private Bitmap regionBitmap;

    // -------------------------------------------------------------------------
    // Construction — private; callers use the static decode() entry point
    // -------------------------------------------------------------------------

    private GenericRefinementRegionDecodingProcedure(final ArithmeticDecoder arithDecoder, final CX cx)
    {
        this.arithDecoder = arithDecoder;
        this.cx = cx;
    }

    // -------------------------------------------------------------------------
    // Public static API
    // -------------------------------------------------------------------------

    /**
     * Executes the Generic Refinement Region decoding procedure (§6.3.5.6) and
     * returns the decoded bitmap.
     *
     * <p>A short-lived {@code GenericRefinementRegionDecodingProcedure} instance is created
     * internally so that the many private helper methods can share state through
     * fields rather than through long parameter lists. No state survives the
     * return of this method.</p>
     *
     * @param arithDecoder    the arithmetic decoder — shared with the parent
     *                        when called from a symbol dictionary or text region,
     *                        or freshly created when called from a standalone segment
     * @param cx              the context model — shared or fresh, as above
     * @param width           decoded bitmap width  (GRW)
     * @param height          decoded bitmap height (GRH)
     * @param grTemplate      template index: 0 or 1 (GRTEMPLATE)
     * @param isTPGROn        whether typical prediction is enabled (TPGRON)
     * @param referenceBitmap the reference / base bitmap (GRREFERENCE)
     * @param referenceDX     horizontal offset of reference bitmap (GRREFERENCEDX)
     * @param referenceDY     vertical offset of reference bitmap (GRREFERENCEDY)
     * @param grAtX           AT pixel X offsets (only used when grTemplate == 0)
     * @param grAtY           AT pixel Y offsets (only used when grTemplate == 0)
     * @return the decoded bitmap
     * @throws IOException if an underlying I/O operation fails
     */
    public static Bitmap decode(final ArithmeticDecoder arithDecoder, final CX cx,
            final int width, final int height, final short grTemplate,
            final boolean isTPGROn, final Bitmap referenceBitmap,
            final int referenceDX, final int referenceDY,
            final short[] grAtX, final short[] grAtY) throws IOException
    {
        return new GenericRefinementRegionDecodingProcedure(arithDecoder, cx)
                .run(width, height, grTemplate, isTPGROn, referenceBitmap,
                        referenceDX, referenceDY, grAtX, grAtY);
    }

    // -------------------------------------------------------------------------
    // Private execution — all helpers share state through instance fields
    // -------------------------------------------------------------------------

    private Bitmap run(final int width, final int height, final short grTemplate,
            final boolean isTPGROn, final Bitmap referenceBitmap,
            final int referenceDX, final int referenceDY,
            final short[] grAtX, final short[] grAtY) throws IOException
    {
        this.templateID = grTemplate;
        this.template = (grTemplate == 0) ? T0 : T1;
        this.isTPGROn = isTPGROn;
        this.referenceBitmap = referenceBitmap;
        this.referenceDX = referenceDX;
        this.referenceDY = referenceDY;
        this.grAtX = grAtX;
        this.grAtY = grAtY;
        this.override = false;
        this.grAtOverride = null;

        /* 6.3.5.6 - 2) */
        regionBitmap = new Bitmap(width, height);

        if (templateID == 0)
        {
            // AT pixels are only relevant for template 0
            updateOverride();
        }

        final int paddedWidth = (width + 7) & -8;
        final int deltaRefStride = isTPGROn ? -referenceDY * referenceBitmap.getRowStride() : 0;
        final int yOffset = deltaRefStride + 1;

        /* 6.3.5.6 - 1) */
        int isLineTypicalPredicted = 0; // LTP

        /* 6.3.5.6 - 3) */
        for (int y = 0; y < height; y++)
        {
            /* 6.3.5.6 - 3 b) */
            if (isTPGROn)
            {
                isLineTypicalPredicted ^= decodeSLTP();
            }

            if (isLineTypicalPredicted == 0)
            {
                /* 6.3.5.6 - 3 c) */
                decodeOptimized(y, width, regionBitmap.getRowStride(),
                        referenceBitmap.getRowStride(), paddedWidth, deltaRefStride, yOffset);
            }
            else
            {
                /* 6.3.5.6 - 3 d) */
                decodeTypicalPredictedLine(y, width, regionBitmap.getRowStride(),
                        referenceBitmap.getRowStride(), paddedWidth, deltaRefStride);
            }
        }

        /* 6.3.5.6 - 4) */
        return regionBitmap;
    }

    // -------------------------------------------------------------------------
    // Private decoding helpers (§6.3.5.6 sub-steps)
    // -------------------------------------------------------------------------

    private int decodeSLTP() throws IOException
    {
        template.setIndex(cx);
        return arithDecoder.decode(cx);
    }

    private void decodeOptimized(final int lineNumber, final int width, final int rowStride,
            final int refRowStride, final int paddedWidth, final int deltaRefStride,
            final int lineOffset) throws IOException
    {
        // Offset of the reference bitmap with respect to the bitmap being decoded.
        // For example: if referenceDY = -1, y is 1 HIGHER than currY.
        final int currentLine = lineNumber - referenceDY;
        final int referenceByteIndex = referenceBitmap.getByteIndex(Math.max(0, -referenceDX),
                currentLine);
        final int byteIndex = regionBitmap.getByteIndex(Math.max(0, referenceDX), lineNumber);

        switch (templateID)
        {
        case 0:
            decodeTemplate(lineNumber, width, rowStride, refRowStride, paddedWidth, deltaRefStride,
                    lineOffset, byteIndex, currentLine, referenceByteIndex, T0);
            break;
        case 1:
            decodeTemplate(lineNumber, width, rowStride, refRowStride, paddedWidth, deltaRefStride,
                    lineOffset, byteIndex, currentLine, referenceByteIndex, T1);
            break;
        }
    }

    private void decodeTemplate(final int lineNumber, final int width, final int rowStride,
            final int refRowStride, final int paddedWidth, final int deltaRefStride,
            final int lineOffset, int byteIndex, final int currentLine, int refByteIndex,
            Template templateFormation) throws IOException
    {
        short c1, c2, c3, c4, c5;

        int w1, w2, w3, w4;
        w1 = w2 = w3 = w4 = 0;

        if (currentLine >= 1 && (currentLine - 1) < referenceBitmap.getHeight())
            w1 = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride);
        if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
            w2 = referenceBitmap.getByteAsInteger(refByteIndex);
        if (currentLine >= -1 && currentLine + 1 < referenceBitmap.getHeight())
            w3 = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride);
        refByteIndex++;

        if (lineNumber >= 1)
        {
            w4 = regionBitmap.getByteAsInteger(byteIndex - rowStride);
        }
        byteIndex++;

        final int modReferenceDX = referenceDX % 8;
        final int shiftOffset = 6 + modReferenceDX;
        final int modRefByteIdx = refByteIndex % refRowStride;

        if (shiftOffset >= 0)
        {
            c1 = (short) ((shiftOffset >= 8 ? 0 : w1 >>> shiftOffset) & 0x07);
            c2 = (short) ((shiftOffset >= 8 ? 0 : w2 >>> shiftOffset) & 0x07);
            c3 = (short) ((shiftOffset >= 8 ? 0 : w3 >>> shiftOffset) & 0x07);
            if (shiftOffset == 6 && modRefByteIdx > 1)
            {
                if (currentLine >= 1 && (currentLine - 1) < referenceBitmap.getHeight())
                {
                    c1 |= referenceBitmap.getByteAsInteger(refByteIndex - refRowStride - 2) << 2
                            & 0x04;
                }
                if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
                {
                    c2 |= referenceBitmap.getByteAsInteger(refByteIndex - 2) << 2 & 0x04;
                }
                if (currentLine >= -1 && currentLine + 1 < referenceBitmap.getHeight())
                {
                    c3 |= referenceBitmap.getByteAsInteger(refByteIndex + refRowStride - 2) << 2
                            & 0x04;
                }
            }
            if (shiftOffset == 0)
            {
                w1 = w2 = w3 = 0;
                if (modRefByteIdx < refRowStride - 1)
                {
                    if (currentLine >= 1 && (currentLine - 1) < referenceBitmap.getHeight())
                        w1 = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride);
                    if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
                        w2 = referenceBitmap.getByteAsInteger(refByteIndex);
                    if (currentLine >= -1 && currentLine + 1 < referenceBitmap.getHeight())
                        w3 = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride);
                }
                refByteIndex++;
            }
        }
        else
        {
            c1 = (short) ((w1 << 1) & 0x07);
            c2 = (short) ((w2 << 1) & 0x07);
            c3 = (short) ((w3 << 1) & 0x07);
            w1 = w2 = w3 = 0;
            if (modRefByteIdx < refRowStride - 1)
            {
                if (currentLine >= 1 && (currentLine - 1) < referenceBitmap.getHeight())
                    w1 = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride);
                if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
                    w2 = referenceBitmap.getByteAsInteger(refByteIndex);
                if (currentLine >= -1 && currentLine + 1 < referenceBitmap.getHeight())
                    w3 = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride);
                refByteIndex++;
            }
            c1 |= (short) ((w1 >>> 7) & 0x07);
            c2 |= (short) ((w2 >>> 7) & 0x07);
            c3 |= (short) ((w3 >>> 7) & 0x07);
        }

        c4 = (short) (w4 >>> 6);
        c5 = 0;

        final int modBitsToTrim = (2 - modReferenceDX) % 8;
        w1 <<= modBitsToTrim;
        w2 <<= modBitsToTrim;
        w3 <<= modBitsToTrim;

        w4 <<= 2;

        for (int x = 0; x < width; x++)
        {
            final int minorX = x & 0x07;

            final short tval = templateFormation.form(c1, c2, c3, c4, c5);

            if (override)
            {
                cx.setIndex(overrideAtTemplate0(tval, x, lineNumber,
                        regionBitmap.getByte(regionBitmap.getByteIndex(x, lineNumber)), minorX));
            }
            else
            {
                cx.setIndex(tval);
            }
            final int bit = arithDecoder.decode(cx);
            regionBitmap.setPixel(x, lineNumber, (byte) bit);

            c1 = (short) (((c1 << 1) | 0x01 & (w1 >>> 7)) & 0x07);
            c2 = (short) (((c2 << 1) | 0x01 & (w2 >>> 7)) & 0x07);
            c3 = (short) (((c3 << 1) | 0x01 & (w3 >>> 7)) & 0x07);
            c4 = (short) (((c4 << 1) | 0x01 & (w4 >>> 7)) & 0x07);
            c5 = (short) bit;

            if ((x - referenceDX) % 8 == 5)
            {
                if (((x - referenceDX) / 8) + 1 >= referenceBitmap.getRowStride())
                {
                    w1 = w2 = w3 = 0;
                }
                else
                {
                    if (currentLine >= 1 && (currentLine - 1 < referenceBitmap.getHeight()))
                    {
                        w1 = referenceBitmap.getByteAsInteger(refByteIndex - refRowStride);
                    }
                    else
                    {
                        w1 = 0;
                    }
                    if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
                    {
                        w2 = referenceBitmap.getByteAsInteger(refByteIndex);
                    }
                    else
                    {
                        w2 = 0;
                    }
                    if (currentLine >= -1 && (currentLine + 1) < referenceBitmap.getHeight())
                    {
                        w3 = referenceBitmap.getByteAsInteger(refByteIndex + refRowStride);
                    }
                    else
                    {
                        w3 = 0;
                    }
                }
                refByteIndex++;
            }
            else
            {
                w1 <<= 1;
                w2 <<= 1;
                w3 <<= 1;
            }

            if (minorX == 5 && lineNumber >= 1)
            {
                if ((x >> 3) + 1 >= regionBitmap.getRowStride())
                {
                    w4 = 0;
                }
                else
                {
                    w4 = regionBitmap.getByteAsInteger(byteIndex - rowStride);
                }
                byteIndex++;
            }
            else
            {
                w4 <<= 1;
            }
        }
    }

    private void updateOverride()
    {
        if (grAtX == null || grAtY == null)
        {
            return;
        }

        if (grAtX.length != grAtY.length)
        {
            return;
        }

        grAtOverride = new boolean[grAtX.length];

        switch (templateID)
        {
        case 0:
            if (!(grAtX[0] == -1 && grAtY[0] == -1))
            {
                grAtOverride[0] = true;
                override = true;
            }

            if (!(grAtX[1] == -1 && grAtY[1] == -1))
            {
                grAtOverride[1] = true;
                override = true;
            }
            break;
        case 1:
            override = false;
            break;
        }
    }

    private void decodeTypicalPredictedLine(final int lineNumber, final int width,
            final int rowStride, final int refRowStride, final int paddedWidth,
            final int deltaRefStride) throws IOException
    {
        // Offset of the reference bitmap with respect to the bitmap being decoded.
        // For example: if grReferenceDY = -1, y is 1 HIGHER than currY.
        final int currentLine = lineNumber - referenceDY;
        final int refByteIndex = referenceBitmap.getByteIndex(0, currentLine);
        final int byteIndex = regionBitmap.getByteIndex(0, lineNumber);

        switch (templateID)
        {
        case 0:
            decodeTypicalPredictedLineTemplate0(lineNumber, width, rowStride, refRowStride,
                    paddedWidth, deltaRefStride, byteIndex, currentLine, refByteIndex);
            break;
        case 1:
            decodeTypicalPredictedLineTemplate1(lineNumber, width, rowStride, refRowStride,
                    paddedWidth, deltaRefStride, byteIndex, currentLine, refByteIndex);
            break;
        }
    }

    private void decodeTypicalPredictedLineTemplate0(final int lineNumber, final int width,
            final int rowStride, final int refRowStride, final int paddedWidth,
            final int deltaRefStride, int byteIndex, final int currentLine, int refByteIndex)
            throws IOException
    {
        int context;
        int overriddenContext;

        int previousLine;
        int previousReferenceLine;
        int currentReferenceLine;
        int nextReferenceLine;

        if (lineNumber > 0)
        {
            previousLine = regionBitmap.getByteAsInteger(byteIndex - rowStride);
        }
        else
        {
            previousLine = 0;
        }

        if (currentLine > 0 && currentLine <= referenceBitmap.getHeight())
        {
            previousReferenceLine = referenceBitmap
                    .getByteAsInteger(refByteIndex - refRowStride + deltaRefStride) << 4;
        }
        else
        {
            previousReferenceLine = 0;
        }

        if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
        {
            currentReferenceLine = referenceBitmap
                    .getByteAsInteger(refByteIndex + deltaRefStride) << 1;
        }
        else
        {
            currentReferenceLine = 0;
        }

        if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1))
        {
            nextReferenceLine = referenceBitmap
                    .getByteAsInteger(refByteIndex + refRowStride + deltaRefStride);
        }
        else
        {
            nextReferenceLine = 0;
        }

        context = ((previousLine >> 5) & 0x6) | ((nextReferenceLine >> 2) & 0x30)
                | (currentReferenceLine & 0x180) | (previousReferenceLine & 0xc00);

        int nextByte;
        for (int x = 0; x < paddedWidth; x = nextByte)
        {
            byte result = 0;
            nextByte = x + 8;
            final int minorWidth = width - x > 8 ? 8 : width - x;
            final boolean readNextByte = nextByte < width;
            final boolean refReadNextByte = nextByte < referenceBitmap.getWidth();

            final int yOffset = deltaRefStride + 1;

            if (lineNumber > 0)
            {
                previousLine = (previousLine << 8) | (readNextByte
                        ? regionBitmap.getByteAsInteger(byteIndex - rowStride + 1) : 0);
            }

            if (currentLine > 0 && currentLine <= referenceBitmap.getHeight())
            {
                previousReferenceLine = (previousReferenceLine << 8)
                        | (refReadNextByte ? referenceBitmap
                                .getByteAsInteger(refByteIndex - refRowStride + yOffset) << 4 : 0);
            }

            if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
            {
                currentReferenceLine = (currentReferenceLine << 8) | (refReadNextByte
                        ? referenceBitmap.getByteAsInteger(refByteIndex + yOffset) << 1 : 0);
            }

            if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1))
            {
                nextReferenceLine = (nextReferenceLine << 8) | (refReadNextByte
                        ? referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + yOffset)
                        : 0);
            }

            for (int minorX = 0; minorX < minorWidth; minorX++)
            {
                boolean isPixelTypicalPredicted = false;
                int bit = 0;

                // i)
                final int bitmapValue = (context >> 4) & 0x1FF;

                if (bitmapValue == 0x1ff)
                {
                    isPixelTypicalPredicted = true;
                    bit = 1;
                }
                else if (bitmapValue == 0x00)
                {
                    isPixelTypicalPredicted = true;
                    bit = 0;
                }

                if (!isPixelTypicalPredicted)
                {
                    // iii) - is like 3 c) but for one pixel only
                    if (override)
                    {
                        overriddenContext = overrideAtTemplate0(context, x + minorX, lineNumber,
                                result, minorX);
                        cx.setIndex(overriddenContext);
                    }
                    else
                    {
                        cx.setIndex(context);
                    }
                    bit = arithDecoder.decode(cx);
                }

                final int toShift = 7 - minorX;
                result |= bit << toShift;

                context = ((context & 0xdb6) << 1) | bit | ((previousLine >> toShift + 5) & 0x002)
                        | ((nextReferenceLine >> toShift + 2) & 0x010)
                        | ((currentReferenceLine >> toShift) & 0x080)
                        | ((previousReferenceLine >> toShift) & 0x400);
            }
            regionBitmap.setByte(byteIndex++, result);
            refByteIndex++;
        }
    }

    private void decodeTypicalPredictedLineTemplate1(final int lineNumber, final int width,
            final int rowStride, final int refRowStride, final int paddedWidth,
            final int deltaRefStride, int byteIndex, final int currentLine, int refByteIndex)
            throws IOException
    {
        int context;
        int grReferenceValue;

        int previousLine;
        int previousReferenceLine;
        int currentReferenceLine;
        int nextReferenceLine;

        if (lineNumber > 0)
        {
            previousLine = regionBitmap.getByteAsInteger(byteIndex - rowStride);
        }
        else
        {
            previousLine = 0;
        }

        if (currentLine > 0 && currentLine <= referenceBitmap.getHeight())
        {
            previousReferenceLine = referenceBitmap
                    .getByteAsInteger(refByteIndex - refRowStride + deltaRefStride) << 2;
        }
        else
        {
            previousReferenceLine = 0;
        }

        if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
        {
            currentReferenceLine = referenceBitmap.getByteAsInteger(refByteIndex + deltaRefStride);
        }
        else
        {
            currentReferenceLine = 0;
        }

        if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1))
        {
            nextReferenceLine = referenceBitmap
                    .getByteAsInteger(refByteIndex + refRowStride + deltaRefStride);
        }
        else
        {
            nextReferenceLine = 0;
        }

        context = ((previousLine >> 5) & 0x6) | ((nextReferenceLine >> 2) & 0x30)
                | (currentReferenceLine & 0xc0) | (previousReferenceLine & 0x200);

        grReferenceValue = ((nextReferenceLine >> 2) & 0x70) | (currentReferenceLine & 0xc0)
                | (previousReferenceLine & 0x700);

        int nextByte;
        for (int x = 0; x < paddedWidth; x = nextByte)
        {
            byte result = 0;
            nextByte = x + 8;
            final int minorWidth = width - x > 8 ? 8 : width - x;
            final boolean readNextByte = nextByte < width;
            final boolean refReadNextByte = nextByte < referenceBitmap.getWidth();

            final int yOffset = deltaRefStride + 1;

            if (lineNumber > 0)
            {
                previousLine = (previousLine << 8) | (readNextByte
                        ? regionBitmap.getByteAsInteger(byteIndex - rowStride + 1) : 0);
            }

            if (currentLine > 0 && currentLine <= referenceBitmap.getHeight())
            {
                previousReferenceLine = (previousReferenceLine << 8)
                        | (refReadNextByte ? referenceBitmap
                                .getByteAsInteger(refByteIndex - refRowStride + yOffset) << 2 : 0);
            }

            if (currentLine >= 0 && currentLine < referenceBitmap.getHeight())
            {
                currentReferenceLine = (currentReferenceLine << 8) | (refReadNextByte
                        ? referenceBitmap.getByteAsInteger(refByteIndex + yOffset) : 0);
            }

            if (currentLine > -2 && currentLine < (referenceBitmap.getHeight() - 1))
            {
                nextReferenceLine = (nextReferenceLine << 8) | (refReadNextByte
                        ? referenceBitmap.getByteAsInteger(refByteIndex + refRowStride + yOffset)
                        : 0);
            }

            for (int minorX = 0; minorX < minorWidth; minorX++)
            {
                int bit = 0;

                // i)
                final int bitmapValue = (grReferenceValue >> 4) & 0x1ff;

                if (bitmapValue == 0x1ff)
                {
                    bit = 1;
                }
                else if (bitmapValue == 0x00)
                {
                    bit = 0;
                }
                else
                {
                    cx.setIndex(context);
                    bit = arithDecoder.decode(cx);
                }

                final int toShift = 7 - minorX;
                result |= bit << toShift;

                context = ((context & 0x0d6) << 1) | bit | ((previousLine >> toShift + 5) & 0x002)
                        | ((nextReferenceLine >> toShift + 2) & 0x010)
                        | ((currentReferenceLine >> toShift) & 0x040)
                        | ((previousReferenceLine >> toShift) & 0x200);

                grReferenceValue = ((grReferenceValue & 0x0db) << 1)
                        | ((nextReferenceLine >> toShift + 2) & 0x010)
                        | ((currentReferenceLine >> toShift) & 0x080)
                        | ((previousReferenceLine >> toShift) & 0x400);
            }
            regionBitmap.setByte(byteIndex++, result);
            refByteIndex++;
        }
    }

    private int overrideAtTemplate0(int context, final int x, final int y, final int result,
            final int minorX) throws IOException
    {
        if (grAtOverride[0])
        {
            context &= 0xfff7;
            if (grAtY[0] == 0 && grAtX[0] >= -minorX)
            {
                context |= (result >> (7 - (minorX + grAtX[0])) & 0x1) << 3;
            }
            else
            {
                context |= getPixel(regionBitmap, x + grAtX[0], y + grAtY[0]) << 3;
            }
        }

        if (grAtOverride[1])
        {
            context &= 0xefff;
            // 6.3.5.3
            // The AT pixel RA2 can be located anywhere in the range (–128, –128) to (127, 127)
            // in the reference bitmap. Make sure that we do use the reference bitmap.
            context |= getPixel(referenceBitmap, x + grAtX[1] + referenceDX,
                    y + grAtY[1] + referenceDY) << 12;
        }
        return context;
    }

    private byte getPixel(final Bitmap b, final int x, final int y) throws IOException
    {
        if (x < 0 || x >= b.getWidth())
        {
            return 0;
        }
        if (y < 0 || y >= b.getHeight())
        {
            return 0;
        }
        return b.getPixel(x, y);
    }
}
