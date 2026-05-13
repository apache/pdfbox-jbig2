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

package org.apache.pdfbox.jbig2.io;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * @deprecated This factory provides no way to configure its behaviour and 
 * does not document the tradeoff it makes: it defaults to
 * {@link FileCacheImageInputStream} (file-backed, lower memory usage) with
 * {@link MemoryCacheImageInputStream} only as a fallback on I/O failure.
 * Callers should construct the appropriate {@link ImageInputStream} directly
 * based on their own memory and performance requirements.
 * This class will be removed in a future release.
 */
@Deprecated
public class DefaultInputStreamFactory implements InputStreamFactory
{

    @Deprecated
    @Override
    public ImageInputStream getInputStream(InputStream is)
    {
        try
        {
            return new FileCacheImageInputStream(is, null);
        }
        catch (IOException e)
        {
            return new MemoryCacheImageInputStream(is);
        }
    }

}
