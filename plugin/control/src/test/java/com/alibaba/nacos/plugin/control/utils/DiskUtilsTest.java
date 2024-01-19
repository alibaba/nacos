/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.utils;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class DiskUtilsTest {
    private static File testFile;
    
    private static final String TMP_PATH = EnvUtils.getNacosHome() + File.separator + "data" + File.separator + "tmp" + File.separator;
    
    @BeforeClass
    public static void setup() throws IOException {
        testFile = DiskUtils.createTmpFile("nacostmp", ".ut");
    }
    
    @AfterClass
    public static void tearDown() throws IOException {
        testFile.deleteOnExit();
    }
    
    @Test
    public void testTouch() throws IOException {
        File file = Paths.get(TMP_PATH, "touch.ut").toFile();
        Assert.assertFalse(file.exists());
        DiskUtils.touch(file);
        Assert.assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    public void testTouchWithFileName() throws IOException {
        File file = Paths.get(TMP_PATH, UUID.randomUUID().toString()).toFile();
        Assert.assertFalse(file.exists());
        DiskUtils.touch(file.getParent(), file.getName());
        Assert.assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    public void testCreateTmpFile() throws IOException {
        File tmpFile = null;
        try {
            tmpFile = DiskUtils.createTmpFile("nacos1", ".ut");
            Assert.assertTrue(tmpFile.getName().startsWith("nacos1"));
            Assert.assertTrue(tmpFile.getName().endsWith(".ut"));
        } finally {
            if (tmpFile != null) {
                tmpFile.deleteOnExit();
            }
        }
    }
    
    @Test
    public void testCreateTmpFileWithPath() throws IOException {
        File tmpFile = null;
        try {
            tmpFile = DiskUtils.createTmpFile(TMP_PATH, "nacos1", ".ut");
            Assert.assertEquals(TMP_PATH, tmpFile.getParent() + File.separator);
            Assert.assertTrue(tmpFile.getName().startsWith("nacos1"));
            Assert.assertTrue(tmpFile.getName().endsWith(".ut"));
        } finally {
            if (tmpFile != null) {
                tmpFile.deleteOnExit();
            }
        }
    }
    
    @Test
    public void testReadFile() {
        Assert.assertNotNull(DiskUtils.readFile(testFile));
    }
    
    @Test
    public void testReadFileWithInputStream() throws FileNotFoundException {
        Assert.assertNotNull(DiskUtils.readFile(new FileInputStream(testFile)));
    }
    
    @Test
    public void testReadFileWithPath() {
        Assert.assertNotNull(DiskUtils.readFile(testFile.getParent(), testFile.getName()));
    }
    
    @Test
    public void testReadFileBytes() {
        Assert.assertNotNull(DiskUtils.readFileBytes(testFile));
    }
    
    @Test
    public void testReadFileBytesWithPath() {
        Assert.assertNotNull(DiskUtils.readFileBytes(testFile.getParent(), testFile.getName()));
    }
    
    @Test
    public void writeFile() {
        Assert.assertTrue(DiskUtils.writeFile(testFile, "unit test".getBytes(StandardCharsets.UTF_8), false));
        Assert.assertEquals("unit test", DiskUtils.readFile(testFile));
    }
    
    @Test
    public void deleteQuietly() throws IOException {
        File tmpFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        DiskUtils.deleteQuietly(tmpFile);
        Assert.assertFalse(tmpFile.exists());
    }
    
    @Test
    public void testDeleteQuietlyWithPath() throws IOException {
        String dir = TMP_PATH + "/" + "diskutils";
        DiskUtils.forceMkdir(dir);
        DiskUtils.createTmpFile(dir, "nacos", ".ut");
        Path path = Paths.get(dir);
        DiskUtils.deleteQuietly(path);
        
        Assert.assertFalse(path.toFile().exists());
    }
    
    @Test
    public void testDeleteFile() throws IOException {
        File tmpFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        Assert.assertTrue(DiskUtils.deleteFile(tmpFile.getParent(), tmpFile.getName()));
        Assert.assertFalse(DiskUtils.deleteFile(tmpFile.getParent(), tmpFile.getName()));
    }
    
    @Test
    public void deleteDirectory() throws IOException {
        Path diskutils = Paths.get(TMP_PATH, "diskutils");
        File file = diskutils.toFile();
        if (!file.exists()) {
            file.mkdir();
        }
        
        Assert.assertTrue(file.exists());
        DiskUtils.deleteDirectory(diskutils.toString());
        Assert.assertFalse(file.exists());
    }
    
    @Test
    public void testForceMkdir() throws IOException {
        File dir = Paths.get(TMP_PATH, UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .toFile();
        DiskUtils.forceMkdir(dir);
        Assert.assertTrue(dir.exists());
        dir.deleteOnExit();
    }
    
    @Test
    public void testForceMkdirWithPath() throws IOException {
        Path path = Paths.get(TMP_PATH, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        DiskUtils.forceMkdir(path.toString());
        File file = path.toFile();
        Assert.assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    public void deleteDirThenMkdir() throws IOException {
        Path path = Paths.get(TMP_PATH, UUID.randomUUID().toString());
        DiskUtils.forceMkdir(path.toString());
        
        DiskUtils.createTmpFile(path.toString(), UUID.randomUUID().toString(), ".ut");
        DiskUtils.createTmpFile(path.toString(), UUID.randomUUID().toString(), ".ut");
        
        DiskUtils.deleteDirThenMkdir(path.toString());
        
        File file = path.toFile();
        Assert.assertTrue(file.exists());
        Assert.assertTrue(file.isDirectory());
        Assert.assertTrue(file.list() == null || file.list().length == 0);
        
        file.deleteOnExit();
    }
    
    @Test
    public void testCopyDirectory() throws IOException {
        Path srcPath = Paths.get(TMP_PATH, UUID.randomUUID().toString());
        DiskUtils.forceMkdir(srcPath.toString());
        File nacos = DiskUtils.createTmpFile(srcPath.toString(), "nacos", ".ut");
        
        Path destPath = Paths.get(TMP_PATH, UUID.randomUUID().toString());
        DiskUtils.copyDirectory(srcPath.toFile(), destPath.toFile());
        
        File file = Paths.get(destPath.toString(), nacos.getName()).toFile();
        Assert.assertTrue(file.exists());
        
        DiskUtils.deleteDirectory(srcPath.toString());
        DiskUtils.deleteDirectory(destPath.toString());
    }
    
    @Test
    public void testCopyFile() throws IOException {
        File nacos = DiskUtils.createTmpFile("nacos", ".ut");
        DiskUtils.copyFile(testFile, nacos);
        
        Assert.assertEquals(DiskUtils.readFile(testFile), DiskUtils.readFile(nacos));
        
        nacos.deleteOnExit();
    }
    
    @Test
    public void openFile() {
        File file = DiskUtils.openFile(testFile.getParent(), testFile.getName());
        Assert.assertNotNull(file);
        Assert.assertEquals(testFile.getPath(), file.getPath());
        Assert.assertEquals(testFile.getName(), file.getName());
    }
    
    @Test
    public void testOpenFileWithPath() {
        File file = DiskUtils.openFile(testFile.getParent(), testFile.getName(), false);
        Assert.assertNotNull(file);
        Assert.assertEquals(testFile.getPath(), file.getPath());
        Assert.assertEquals(testFile.getName(), file.getName());
    }
    
}
