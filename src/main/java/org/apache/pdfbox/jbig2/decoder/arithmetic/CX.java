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
 * CX represents the context used by arithmetic decoding and arithmetic integer decoding. It selects the probability
 * estimate and statistics used during decoding procedure.
 */
public final class CX
{
    private int index;

    private final byte cx[];
    private final byte mps[];

    /**
     * @param size - Amount of context values.
     * @param index - Start index.
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
