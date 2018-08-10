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

import com.google.common.cache.CacheBuilder;

/**
 * Uses '==' for key equality check, instead of 'equals'.
 */
public class SoftReferenceCache implements Cache {
    private com.google.common.cache.Cache<Object, Object> cache = CacheBuilder.newBuilder().weakKeys().softValues().build();


    public Object put(Object key, Object value, int sizeEstimate) {
        Object previousValue = cache.getIfPresent(key);
        cache.put(key, value);
        return previousValue;
    }

    public Object get(Object key) {
        return cache.getIfPresent(key);
    }

    public void clear() {
        cache.invalidateAll();
    }

    public Object remove(Object key) {
        Object value = cache.getIfPresent(key);
        cache.invalidate(key);
        return value;
    }
}

