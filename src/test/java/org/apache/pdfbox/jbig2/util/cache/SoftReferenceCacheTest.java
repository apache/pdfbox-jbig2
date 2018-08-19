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

package org.apache.pdfbox.jbig2.util.cache;

import org.junit.Assert;
import org.junit.Test;

public class SoftReferenceCacheTest
{
    private static final int KB = 1024;
    private static final int MB = KB * KB;

    /**
     * Should not throw an OutOfMemoryError
     */
    @Test
    public void putDoesNotLeakMemory()
    {
        Cache cache = CacheFactory.getCache();
        cache.clear();
        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        int halfEntrySizeEstimate = 8 * MB;

        for (int i = 0; i < (maxHeapBytes / halfEntrySizeEstimate) * 2; i++)
        {
            cache.put(new Long[KB][KB], new Long[KB][KB], halfEntrySizeEstimate);
        }
    }

    @Test
    public void putAndGet()
    {
        Cache cache = CacheFactory.getCache();
        cache.clear();
        Object key = new Object();
        Object value = new Object();

        cache.put(key, value, 0);

        Assert.assertEquals(value, cache.get(key));
    }
}
