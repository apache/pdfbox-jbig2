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

import org.junit.Assert;
import org.junit.Test;

public class CXTest
{

    @Test
    public void testInitialization()
    {
        // Test that CX is initialized with correct size and index
        CX cx = new CX(512, 5);
        
        Assert.assertEquals(5, cx.getIndex());
        // After initialization, cx value should be 0 (unset)
        Assert.assertEquals(0, cx.cx());
        // After initialization, mps should be 0
        Assert.assertEquals(0, cx.mps());
    }

    @Test
    public void testSetAndGetIndex()
    {
        CX cx = new CX(100, 0);
        
        cx.setIndex(50);
        Assert.assertEquals(50, cx.getIndex());
        
        cx.setIndex(99);
        Assert.assertEquals(99, cx.getIndex());
        
        cx.setIndex(0);
        Assert.assertEquals(0, cx.getIndex());
    }

    @Test
    public void testSetAndGetCx()
    {
        CX cx = new CX(512, 0);
        
        cx.setCx(42);
        Assert.assertEquals(42, cx.cx());
        
        cx.setIndex(1);
        cx.setCx(127); // Maximum value (7 bits)
        Assert.assertEquals(127, cx.cx());
        
        // Verify that different indices have independent values
        cx.setIndex(0);
        Assert.assertEquals(42, cx.cx());
    }

    @Test
    public void testCxMasking()
    {
        // CX should mask to 7 bits (0-127)
        CX cx = new CX(512, 0);
        
        cx.setCx(0xFF); // Set with all bits set
        Assert.assertEquals(0x7F, cx.cx()); // Should be masked to 7 bits
        
        cx.setCx(0x80); // Bit 7 set
        Assert.assertEquals(0, cx.cx()); // Should be masked out
    }

    @Test
    public void testToggleMps()
    {
        CX cx = new CX(512, 0);
        
        // Initial state should be 0
        Assert.assertEquals(0, cx.mps());
        
        // Toggle to 1
        cx.toggleMps();
        Assert.assertEquals(1, cx.mps());
        
        // Toggle back to 0
        cx.toggleMps();
        Assert.assertEquals(0, cx.mps());
        
        // Toggle again
        cx.toggleMps();
        Assert.assertEquals(1, cx.mps());
    }

    @Test
    public void testMpsIndependent()
    {
        CX cx = new CX(512, 0);
        
        cx.toggleMps();
        Assert.assertEquals(1, cx.mps());
        
        // Change index and verify MPS is independent
        cx.setIndex(1);
        Assert.assertEquals(0, cx.mps());
        
        cx.toggleMps();
        Assert.assertEquals(1, cx.mps());
        
        // Back to index 0, should still be 1
        cx.setIndex(0);
        Assert.assertEquals(1, cx.mps());
    }

    @Test
    public void testCopyDeepCopy()
    {
        CX original = new CX(512, 10);
        original.setCx(50);
        original.toggleMps();
        
        CX copy = original.copy();
        
        // Verify copy has same state
        Assert.assertEquals(10, copy.getIndex());
        Assert.assertEquals(50, copy.cx());
        Assert.assertEquals(1, copy.mps());
    }

    @Test
    public void testCopyIndependence()
    {
        CX original = new CX(512, 0);
        original.setCx(42);
        original.toggleMps();
        
        CX copy = original.copy();
        
        // Modify original
        original.setCx(100);
        original.toggleMps();
        original.setIndex(5);
        
        // Copy should be unaffected
        Assert.assertEquals(42, copy.cx());
        Assert.assertEquals(1, copy.mps());
        Assert.assertEquals(0, copy.getIndex());
    }

    @Test
    public void testCopyMultipleIndices()
    {
        CX original = new CX(512, 0);
        
        // Set values at different indices
        original.setCx(10);
        original.toggleMps();
        
        original.setIndex(1);
        original.setCx(20);
        
        original.setIndex(2);
        original.setCx(30);
        original.toggleMps();
        
        // Create copy
        CX copy = original.copy();
        
        // Verify all indices were copied
        copy.setIndex(0);
        Assert.assertEquals(10, copy.cx());
        Assert.assertEquals(1, copy.mps());
        
        copy.setIndex(1);
        Assert.assertEquals(20, copy.cx());
        Assert.assertEquals(0, copy.mps());
        
        copy.setIndex(2);
        Assert.assertEquals(30, copy.cx());
        Assert.assertEquals(1, copy.mps());
    }

    @Test
    public void testLargeSizeContext()
    {
        // Test with large context size (typical for bitmap decoding)
        CX cx = new CX(65536, 0);
        
        cx.setIndex(65535);
        cx.setCx(127);
        Assert.assertEquals(127, cx.cx());
        
        cx.toggleMps();
        Assert.assertEquals(1, cx.mps());
    }
}
