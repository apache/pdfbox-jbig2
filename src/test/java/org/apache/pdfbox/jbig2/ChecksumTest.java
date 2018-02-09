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

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;

import javax.imageio.stream.ImageInputStream;

import junit.framework.Assert;

import org.apache.pdfbox.jbig2.Bitmap;
import org.apache.pdfbox.jbig2.JBIG2Document;
import org.apache.pdfbox.jbig2.io.DefaultInputStreamFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ChecksumTest
{

    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                { "target/images/042_1.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_2.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_3.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_4.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_5.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_6.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_7.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_8.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_9.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_10.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_11.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_12.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                // { "/images/042_13.jb2",
                // "69-26-6629-1793-107941058147-58-79-37-31-79" },
                // { "/images/042_14.jb2",
                // "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_15.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_16.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_17.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_18.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_19.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_20.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_21.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_22.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_23.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_24.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/042_25.jb2", "69-26-6629-1793-107941058147-58-79-37-31-79" },
                { "target/images/amb_1.jb2", "58311272494-318210035-125100-344625-126-79" },
                { "target/images/amb_2.jb2", "58311272494-318210035-125100-344625-126-79" },
                { "src/test/resources/images/002.jb2",
                        "-12713-4587-92-651657111-57121-1582564895" },
                { "src/test/resources/images/003.jb2",
                        "-37-108-89-33-78-5019-966-96-124-9675-1-108-24" },
                { "target/images/004.jb2", "-10709436-24-59-48-217114-37-85-3126-24" },
                { "src/test/resources/images/005.jb2",
                        "712610586-1224021396100112-102-77-1177851" },
                { "src/test/resources/images/006.jb2",
                        "-8719-116-83-83-35-3425-64-528667602154-25" },
                { "target/images/007.jb2", "6171-125-109-20-128-71925295955793-127-41-122" },
                { "target/images/sampledata_page1.jb2",
                        "104-68-555325117-4757-48527676-9775-8432" },
                { "target/images/sampledata_page2.jb2",
                        "104-68-555325117-4757-48527676-9775-8432" },
                { "target/images/sampledata_page3.jb2",
                        "-7825-56-41-30-19-719536-3678580-61-2586" },
                { "src/test/resources/images/20123110001.jb2",
                        "60-96-101-2458-3335024-5468-5-11068-78-80" },
                { "src/test/resources/images/20123110002.jb2",
                        "-28-921048181-117-48-96126-110-9-2865611113" },
                { "src/test/resources/images/20123110003.jb2",
                        "-3942-239351-28-56-729169-5839122-439231" },
                { "src/test/resources/images/20123110004.jb2",
                        "-49-101-28-20-57-4-24-17-9352104-106-118-122-122" },
                { "src/test/resources/images/20123110005.jb2",
                        "-48221261779-94-838820-127-114110-2-88-80-106" },
                { "src/test/resources/images/20123110006.jb2",
                        "81-11870-63-30124-1614-45838-53-123-41639" },
                { "src/test/resources/images/20123110007.jb2",
                        "12183-49124728346-29-124-9-10775-63-44116103" },
                { "src/test/resources/images/20123110008.jb2",
                        "15-74-49-45958458-67-2545-96-119-122-60100-35" },
                { "src/test/resources/images/20123110009.jb2",
                        "36115-114-28-123-3-70-87-113-4197-8512396113-65" },
                { "src/test/resources/images/20123110010.jb2",
                        "-109-1069-61-1576-67-43122406037-75-1091115" } });
    }

    private final String filepath;
    private final String checksum;

    public ChecksumTest(String filepath, String cksum)
    {
        this.filepath = filepath;
        this.checksum = cksum;
    }

    @Test
    public void compareChecksum() throws Throwable
    {
        int imageIndex = 1;

        final File inputFile = new File(filepath);
        // skip test if input stream isn't available
        assumeTrue(inputFile.exists());

        final InputStream inputStream = new FileInputStream(inputFile);

        DefaultInputStreamFactory disf = new DefaultInputStreamFactory();
        ImageInputStream iis = disf.getInputStream(inputStream);

        JBIG2Document doc = new JBIG2Document(iis);

        Bitmap b = doc.getPage(imageIndex).getBitmap();

        byte[] digest = MessageDigest.getInstance("MD5").digest(b.getByteArray());

        StringBuilder stringBuilder = new StringBuilder();
        for (byte toAppend : digest)
        {
            stringBuilder.append(toAppend);
        }

        Assert.assertEquals(checksum, stringBuilder.toString());
    }
}
