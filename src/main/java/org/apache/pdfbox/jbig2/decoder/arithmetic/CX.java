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

package org.apache.pdfbox.jbig2.decoder.arithmetic;

/**
 * Arithmetic coding context used during JBIG2 bitstream decoding.
 * 
 * <p>Represents a context in the arithmetic decoder that selects probability
 * estimates and statistics used during decoding procedures, as defined in
 * ITU-T Rec. T.88 (2000 E), ISO/IEC 14492:2001.</p>
 * 
 * <p><b>Context State:</b></p>
 * <ul>
 *   <li>{@code cx} array: Stores the probability estimate index (0-127) for each context state</li>
 *   <li>{@code mps} array: Stores the most probable symbol (0 or 1) for each context state</li>
 *   <li>{@code index}: Current context index, selected based on the neighborhood pattern
 *       (template-specific arrangement of previously decoded pixels)</li>
 * </ul>
 * 
 * <p><b>Usage:</b>
 * The index is set before each decision to select which context state to use.
 * After decoding, the arithmetic decoder updates the selected context's probability
 * estimate and MPS based on the decoded symbol (see Annex A, T.88).</p>
 * 
 * <p><b>Reuse:</b>
 * When arithmetic coding contexts are retained and reused across segments (§7.4.2.2),
 * a {@link #copy()} must be created to avoid sharing mutable probability state
 * between decoders.</p>
 */
public final class CX
{
    private int index;

    private final byte cx[];
    private final byte mps[];

    /**
     * Creates a new context with the specified number of context states.
     * All probability estimates are initialized to 0, and all MPS values are initialized to 0.
     * 
     * @param size the number of context states
     * @param index the initial context index
     */
    public CX(int size, int index)
    {
        this.index = index;
        cx = new byte[size];
        mps = new byte[size];
    }

    protected int cx()
    {
        return cx[index] & 0x7f;
    }

    protected void setCx(int value)
    {
        cx[index] = (byte) (value & 0x7f);
    }

    /**
     * @return The decision. Possible values are {@code 0} or {@code 1}.
     */
    protected byte mps()
    {
        return mps[index];
    }

    /**
     * Flips the bit in actual "more predictable symbol" array element.
     */
    protected void toggleMps()
    {
        mps[index] ^= 1;
    }

    protected int getIndex()
    {
        return index;
    }

    /**
     * Sets the context index used for subsequent decoding decisions.
     * The index selects which context state's probability estimate and MPS will be used.
     * 
     * <p>The index value is typically computed from the neighboring pixels in the template
     * (§6.2.5.1 for generic regions, §6.4.7.1 for text regions).</p>
     * 
     * @param index the context index (0 to size-1)
     */
    public void setIndex(int index)
    {
        this.index = index;
    }

    /**
     * Creates and returns a deep copy of this {@code CX} object.
     * The new instance will have the same context values, probability estimates,
     * and current index as this object, but will be a separate instance.
     * Changes to the copied object will not affect the original, and vice versa.
     *
     * <p>This is required when reusing arithmetic coding contexts across segments,
     * to avoid sharing mutable probability state between decoders.</p>
     *
     * @return A new {@code CX} object with the same internal state as this object.
     */
    public CX copy() {
        CX result = new CX(cx.length, index);
        System.arraycopy(cx, 0, result.cx, 0, cx.length);
        System.arraycopy(mps, 0, result.mps, 0, mps.length);
        return result;
    }
}
