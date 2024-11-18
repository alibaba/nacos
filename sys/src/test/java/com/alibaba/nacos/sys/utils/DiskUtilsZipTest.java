/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.sys.utils;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Modified by sofa jraft ZipTest.
 */
class DiskUtilsZipTest {
    
    private static File sourceDir;
    
    @BeforeAll
    static void setup() throws IOException {
        sourceDir = new File("zip_test");
        if (sourceDir.exists()) {
            FileUtils.forceDelete(sourceDir);
        }
        FileUtils.forceMkdir(sourceDir);
        final File f1 = Paths.get(sourceDir.getAbsolutePath(), "f1").toFile();
        DiskUtils.writeFile(f1, "f1".getBytes(), false);
        final File d1 = Paths.get(sourceDir.getAbsolutePath(), "d1").toFile();
        FileUtils.forceMkdir(d1);
        final File f11 = Paths.get(d1.getAbsolutePath(), "f11").toFile();
        DiskUtils.writeFile(f11, "f11".getBytes(), false);
        
        final File d2 = Paths.get(d1.getAbsolutePath(), "d2").toFile();
        FileUtils.forceMkdir(d2);
        
        final File d3 = Paths.get(d2.getAbsolutePath(), "d3").toFile();
        FileUtils.forceMkdir(d3);
        
        final File f31 = Paths.get(d3.getAbsolutePath(), "f31").toFile();
        DiskUtils.writeFile(f31, "f32".getBytes(), false);
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        FileUtils.forceDelete(sourceDir);
    }
    
    @Test
    public void testCompressAndDecompress() throws IOException {
        final String rootPath = sourceDir.toPath().toAbsolutePath().getParent().toString();
        Path path = Paths.get(rootPath, "test.zip");
        try {
            final Checksum c1 = new CRC32();
            DiskUtils.compress(rootPath, "zip_test", path.toString(), c1);
            
            final Checksum c2 = new CRC32();
            DiskUtils.decompress(path.toString(), rootPath, c2);
            
            assertEquals(c1.getValue(), c2.getValue());
        } finally {
            FileUtils.forceDelete(path.toFile());
        }
    }
    
    @Test
    public void testCompressAndDecompressWithStream() throws IOException {
        final String rootPath = sourceDir.toPath().toAbsolutePath().getParent().toString();
        Path path = Paths.get(rootPath, "test.zip");
        try (InputStream inputStream = new ByteArrayInputStream("test".getBytes())) {
            DiskUtils.compressIntoZipFile("test", inputStream, path.toString(), new CRC32());
            DiskUtils.compressIntoZipFile("../test", inputStream, path.toString(), new CRC32());
            byte[] actual = DiskUtils.decompress(path.toString(), new CRC32());
            assertEquals("test", new String(actual));
        } finally {
            FileUtils.forceDelete(path.toFile());
        }
    }
}
