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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskUtilsTest {
    
    private static final String TMP_PATH = EnvUtils.getNacosHome() + File.separator + "data" + File.separator + "tmp" + File.separator;
    
    private static File testFile;
    
    @BeforeAll
    static void setup() throws IOException {
        testFile = DiskUtils.createTmpFile("nacostmp", ".ut");
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        testFile.deleteOnExit();
    }
    
    @Test
    void testTouch() throws IOException {
        File file = Paths.get(TMP_PATH, "touch.ut").toFile();
        assertFalse(file.exists());
        DiskUtils.touch(file);
        assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    void testTouchWithFileName() throws IOException {
        File file = Paths.get(TMP_PATH, UUID.randomUUID().toString()).toFile();
        assertFalse(file.exists());
        DiskUtils.touch(file.getParent(), file.getName());
        assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    void testCreateTmpFile() throws IOException {
        File tmpFile = null;
        try {
            tmpFile = DiskUtils.createTmpFile("nacos1", ".ut");
            assertTrue(tmpFile.getName().startsWith("nacos1"));
            assertTrue(tmpFile.getName().endsWith(".ut"));
        } finally {
            if (tmpFile != null) {
                tmpFile.deleteOnExit();
            }
        }
    }
    
    @Test
    void testCreateTmpFileWithPath() throws IOException {
        File tmpFile = null;
        try {
            tmpFile = DiskUtils.createTmpFile(TMP_PATH, "nacos1", ".ut");
            assertEquals(TMP_PATH, tmpFile.getParent() + File.separator);
            assertTrue(tmpFile.getName().startsWith("nacos1"));
            assertTrue(tmpFile.getName().endsWith(".ut"));
        } finally {
            if (tmpFile != null) {
                tmpFile.deleteOnExit();
            }
        }
    }
    
    @Test
    void testReadFile() {
        assertNotNull(DiskUtils.readFile(testFile));
    }
    
    @Test
    void testReadFileWithInputStream() throws FileNotFoundException {
        assertNotNull(DiskUtils.readFile(new FileInputStream(testFile)));
    }
    
    @Test
    void testReadFileWithPath() {
        assertNotNull(DiskUtils.readFile(testFile.getParent(), testFile.getName()));
    }
    
    @Test
    void testReadFileBytes() {
        assertNotNull(DiskUtils.readFileBytes(testFile));
    }
    
    @Test
    void testReadFileBytesWithPath() {
        assertNotNull(DiskUtils.readFileBytes(testFile.getParent(), testFile.getName()));
    }
    
    @Test
    void writeFile() {
        assertTrue(DiskUtils.writeFile(testFile, "unit test".getBytes(StandardCharsets.UTF_8), false));
        assertEquals("unit test", DiskUtils.readFile(testFile));
    }
    
    @Test
    void deleteQuietly() throws IOException {
        File tmpFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        DiskUtils.deleteQuietly(tmpFile);
        assertFalse(tmpFile.exists());
    }
    
    @Test
    void testDeleteQuietlyWithPath() throws IOException {
        String dir = TMP_PATH + "/" + "diskutils";
        DiskUtils.forceMkdir(dir);
        DiskUtils.createTmpFile(dir, "nacos", ".ut");
        Path path = Paths.get(dir);
        DiskUtils.deleteQuietly(path);
        
        assertFalse(path.toFile().exists());
    }
    
    @Test
    void testDeleteFile() throws IOException {
        File tmpFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        assertTrue(DiskUtils.deleteFile(tmpFile.getParent(), tmpFile.getName()));
        assertFalse(DiskUtils.deleteFile(tmpFile.getParent(), tmpFile.getName()));
    }
    
    @Test
    void deleteDirectory() throws IOException {
        Path diskutils = Paths.get(TMP_PATH, "diskutils");
        File file = diskutils.toFile();
        if (!file.exists()) {
            file.mkdir();
        }
        
        assertTrue(file.exists());
        DiskUtils.deleteDirectory(diskutils.toString());
        assertFalse(file.exists());
    }
    
    @Test
    void testForceMkdir() throws IOException {
        File dir = Paths.get(TMP_PATH, UUID.randomUUID().toString(), UUID.randomUUID().toString()).toFile();
        DiskUtils.forceMkdir(dir);
        assertTrue(dir.exists());
        dir.deleteOnExit();
    }
    
    @Test
    void testForceMkdirWithPath() throws IOException {
        Path path = Paths.get(TMP_PATH, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        DiskUtils.forceMkdir(path.toString());
        File file = path.toFile();
        assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    void deleteDirThenMkdir() throws IOException {
        Path path = Paths.get(TMP_PATH, UUID.randomUUID().toString());
        DiskUtils.forceMkdir(path.toString());
        
        DiskUtils.createTmpFile(path.toString(), UUID.randomUUID().toString(), ".ut");
        DiskUtils.createTmpFile(path.toString(), UUID.randomUUID().toString(), ".ut");
        
        DiskUtils.deleteDirThenMkdir(path.toString());
        
        File file = path.toFile();
        assertTrue(file.exists());
        assertTrue(file.isDirectory());
        assertTrue(file.list() == null || file.list().length == 0);
        
        file.deleteOnExit();
    }
    
    @Test
    void testCopyDirectory() throws IOException {
        Path srcPath = Paths.get(TMP_PATH, UUID.randomUUID().toString());
        DiskUtils.forceMkdir(srcPath.toString());
        File nacos = DiskUtils.createTmpFile(srcPath.toString(), "nacos", ".ut");
        
        Path destPath = Paths.get(TMP_PATH, UUID.randomUUID().toString());
        DiskUtils.copyDirectory(srcPath.toFile(), destPath.toFile());
        
        File file = Paths.get(destPath.toString(), nacos.getName()).toFile();
        assertTrue(file.exists());
        
        DiskUtils.deleteDirectory(srcPath.toString());
        DiskUtils.deleteDirectory(destPath.toString());
    }
    
    @Test
    void testCopyFile() throws IOException {
        File nacos = DiskUtils.createTmpFile("nacos", ".ut");
        DiskUtils.copyFile(testFile, nacos);
        
        assertEquals(DiskUtils.readFile(testFile), DiskUtils.readFile(nacos));
        
        nacos.deleteOnExit();
    }
    
    @Test
    void openFile() {
        File file = DiskUtils.openFile(testFile.getParent(), testFile.getName());
        assertNotNull(file);
        assertEquals(testFile.getPath(), file.getPath());
        assertEquals(testFile.getName(), file.getName());
    }
    
    @Test
    void testOpenFileWithPath() {
        File file = DiskUtils.openFile(testFile.getParent(), testFile.getName(), false);
        assertNotNull(file);
        assertEquals(testFile.getPath(), file.getPath());
        assertEquals(testFile.getName(), file.getName());
    }
    
}
