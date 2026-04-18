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

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.jbig2.err.JBIG2Exception;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * PDFBOX-6151: Run tests on the Serenity test files from Nico Weber.
 * 
 * @author Tilman Hausherr
 */
public class SerenityTests
{
    @Test
    public void testSerenity() throws IOException, JBIG2Exception
    {
        String targetDir = "target/output";
        new File(targetDir).mkdirs();
        File inputDir = new File("target/images");
        InputStream is = new FileInputStream(new File(inputDir,"bitmap.jbig2"));
        ImageInputStream imageIIS = ImageIO.createImageInputStream(is);
        JBIG2Document doc = new JBIG2Document(imageIIS);
        JBIG2Page page = doc.getPage(1);
        Bitmap expectedBitmap = page.getBitmap();
        is.close();
        File[] files = inputDir.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.endsWith(".jbig2") && !name.equals("bitmap.jbig2");
            }
        });
        
        for (File file : files)
        {
            String name = file.getName();
            // Files that are not properly decoded yet
            if (name.equals("bitmap-refine-template1-tpgron.jbig2") ||
                name.equals("bitmap-symbol-context-reuse.jbig2"))
            {
                continue;
            }
            imageIIS = ImageIO.createImageInputStream(file);
            doc = new JBIG2Document(imageIIS);
            page = doc.getPage(1);
            Bitmap bitmap = page.getBitmap();
            assertEquals("image not equal to epected: " + name, expectedBitmap, bitmap);
            imageIIS.close();
        }
    }
}
