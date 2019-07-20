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

package org.apache.pdfbox.jbig2.image;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;

import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.jbig2.Bitmap;
import org.apache.pdfbox.jbig2.JBIG2DocumentFacade;
import org.apache.pdfbox.jbig2.PreconfiguredImageReadParam;
import org.apache.pdfbox.jbig2.err.JBIG2Exception;
import org.apache.pdfbox.jbig2.io.DefaultInputStreamFactory;
import org.apache.pdfbox.jbig2.io.InputStreamFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BitmapsChecksumTest
{

    private String resourcePath;
    private ImageReadParam param;
    private FilterType filterType;
    private String checksum;
    private int pageNumber;

    @Parameters
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][] {
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Dimension(500, 500)), FilterType.Bessel,
                        "101-6467-126-3534108-8927-58-26-37248672" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Dimension(500, 800)), FilterType.Box,
                        "-748135-126-6412111-11925-1038826-95-32-6-104" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Dimension(4000, 5500)), FilterType.Box,
                        "-646510160-466410970-77-1031184396-8-23-18" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Dimension(600, 300)), FilterType.Bessel,
                        "-69-11478-721003586-100-72-85-1559101-118-24-94" },
                { "target/images/042_1.jb2", 1, new PreconfiguredImageReadParam(2, 2, 0, 0),
                        FilterType.Bessel, "42-7327-48-10105-703710-571171181079448-70" },
                { "target/images/042_1.jb2", 1, new PreconfiguredImageReadParam(2, 2, 0, 0),
                        FilterType.Lanczos, "42-7327-48-10105-703710-571171181079448-70" },
                { "target/images/042_1.jb2", 1, new PreconfiguredImageReadParam(3, 3, 1, 1),
                        FilterType.Lanczos, "-68082-6815-41-95-124101-60-69-106-114-68-81-65" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Rectangle(100, 100, 500, 500)),
                        FilterType.Lanczos, "15-92-12415-6-46-89-78-2070-59-4250-305893" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Rectangle(500, 500, 2000, 2000)),
                        FilterType.Lanczos, "101-49-39-823-579062-4063-84914-9-75-4" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Rectangle(500, 500, 2000, 2000),
                                new Dimension(678, 931)),
                        FilterType.Lanczos, "-17-95-5543-12062-625054-94-88-31-4-120-1971" },
                { "target/images/042_1.jb2", 1,
                        new PreconfiguredImageReadParam(new Rectangle(500, 500, 2000, 2000),
                                new Dimension(678, 931), 3, 3, 1, 1),
                        FilterType.Lanczos, "-109-60118-41999255-94113-5019-2818-10-39-71" } });
    }

    public BitmapsChecksumTest(String resourcePath, int pageNumber, ImageReadParam param,
            FilterType filterType, String checksum)
    {
        this.resourcePath = resourcePath;
        this.pageNumber = pageNumber;
        this.param = param;
        this.filterType = filterType;
        this.checksum = checksum;
    }

    @Test
    public void test() throws IOException, JBIG2Exception, NoSuchAlgorithmException
    {

        final File inputFile = new File(resourcePath);
        // skip test if input stream isn't available
        assumeTrue(inputFile.exists());

        final InputStream inputStream = new FileInputStream(inputFile);

        final InputStreamFactory disf = new DefaultInputStreamFactory();
        final ImageInputStream iis = disf.getInputStream(inputStream);

        final JBIG2DocumentFacade doc = new JBIG2DocumentFacade(iis);
        final Bitmap b = doc.getPageBitmap(pageNumber);
        final WritableRaster raster = Bitmaps.asRaster(b, param, filterType);

        final DataBufferByte dataBufferByte = (DataBufferByte) raster.getDataBuffer();
        final byte[] bytes = dataBufferByte.getData();

        final MessageDigest md = MessageDigest.getInstance("MD5");

        final byte[] digest = md.digest(bytes);
        final StringBuilder sb = new StringBuilder();
        for (byte toAppend : digest)
        {
            sb.append(toAppend);
        }

        assertArrayEquals(checksum.getBytes(), sb.toString().getBytes());
    }

    static class RasterChecksumCalculator
    {
        public static void main(String[] args)
                throws IOException, JBIG2Exception, NoSuchAlgorithmException
        {

            final File inputFile = new File("target/images/042_1.jb2");
            // skip test if input stream isn't available
            assumeTrue(inputFile.exists());

            final int pageNumber = 1;

            final InputStream inputStream = new FileInputStream(inputFile);

            final InputStreamFactory disf = new DefaultInputStreamFactory();
            final ImageInputStream iis = disf.getInputStream(inputStream);

            final JBIG2DocumentFacade doc = new JBIG2DocumentFacade(iis);
            final Bitmap b = doc.getPageBitmap(pageNumber);

            final ImageReadParam param = new PreconfiguredImageReadParam(
                    new Rectangle(100, 100, 500, 500));

            final WritableRaster raster = Bitmaps.asRaster(b, param, FilterType.Lanczos);
            final DataBufferByte dataBufferByte = (DataBufferByte) raster.getDataBuffer();
            final byte[] bytes = dataBufferByte.getData();

            final MessageDigest md = MessageDigest.getInstance("MD5");

            final byte[] digest = md.digest(bytes);
            for (byte d : digest)
            {
                System.out.print(d);
            }
        }
    }
}
