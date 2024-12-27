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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DiskUtilsTest {
    
    private static File testFile;
    
    private static File openTestFile;
    
    private static File testLineFile;
    
    @BeforeAll
    static void setup() throws IOException, URISyntaxException {
        testFile = DiskUtils.createTmpFile("nacostmp", ".ut");
        testLineFile = new File(DiskUtilsTest.class.getClassLoader().getResource("line_iterator_test.txt").toURI());
        openTestFile = new File(testLineFile.getParent(), "temp_open_file");
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        testFile.deleteOnExit();
        openTestFile.deleteOnExit();
    }
    
    @Test
    void testTouch() throws IOException {
        File file = Paths.get(EnvUtil.getNacosTmpDir(), "touch.ut").toFile();
        assertFalse(file.exists());
        DiskUtils.touch(file);
        assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    void testTouchWithFileName() throws IOException {
        File file = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString()).toFile();
        assertFalse(file.exists());
        DiskUtils.touch(file.getParent(), file.getName());
        assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    void testTouchWithIllegalPath() throws IOException {
        File tmpDir = new File(EnvUtil.getNacosTmpDir());
        String fileName = UUID.randomUUID().toString();
        File expectedFile = Paths.get(tmpDir.getParent(), fileName).toFile();
        assertFalse(expectedFile.exists());
        DiskUtils.touch(tmpDir.getAbsolutePath() + "/..", fileName);
        assertFalse(expectedFile.exists());
        expectedFile.deleteOnExit();
    }
    
    @Test
    void testTouchWithIllegalFileName() throws IOException {
        File tmpDir = new File(EnvUtil.getNacosTmpDir());
        String fileName = UUID.randomUUID().toString();
        File expectedFile = Paths.get(tmpDir.getParent(), fileName).toFile();
        assertFalse(expectedFile.exists());
        DiskUtils.touch(tmpDir.getAbsolutePath(), "../" + fileName);
        assertFalse(expectedFile.exists());
        expectedFile.deleteOnExit();
    }
    
    @Test
    void testTouchWithIllegalFileName2() throws IOException {
        String fileName = UUID.randomUUID().toString();
        File expectedFile = Paths.get("/", fileName).toFile();
        assertFalse(expectedFile.exists());
        DiskUtils.touch("", "/" + fileName);
        assertFalse(expectedFile.exists());
        expectedFile.deleteOnExit();
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
            tmpFile = DiskUtils.createTmpFile(EnvUtil.getNacosTmpDir(), "nacos1", ".ut");
            assertEquals(EnvUtil.getNacosTmpDir(), tmpFile.getParent());
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
    void testReadNonExistFile() {
        File file = new File("non-exist");
        assertNull(DiskUtils.readFile(file));
    }
    
    @Test
    void testReadNonExistFile2() {
        File file = new File("non-path/non-exist");
        file.deleteOnExit();
        assertEquals("", DiskUtils.readFile(file.getParentFile().getAbsolutePath(), file.getName()));
    }
    
    @Test
    void testReadFileWithIllegalPath() {
        String path = testFile.getParentFile().getAbsolutePath() + "/../" + testFile.getParentFile().getName();
        assertNull(DiskUtils.readFile(path, testFile.getName()));
    }
    
    @Test
    void testReadFileWithIllegalFileName() {
        String path = testFile.getParentFile().getAbsolutePath();
        String fileName = "../" + testFile.getParentFile().getName() + "/" + testFile.getName();
        assertNull(DiskUtils.readFile(path, fileName));
    }
    
    @Test
    void testReadFileWithInputStream() throws FileNotFoundException {
        assertNotNull(DiskUtils.readFile(new FileInputStream(testFile)));
    }
    
    @Test
    void testReadFileWithInputStreamWithException() {
        InputStream inputStream = mock(InputStream.class);
        assertNull(DiskUtils.readFile(inputStream));
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
    void testReadFileBytesNonExist() {
        assertNull(DiskUtils.readFileBytes(new File("non-exist")));
    }
    
    @Test
    void testReadFileBytesWithPath() {
        assertNotNull(DiskUtils.readFileBytes(testFile.getParent(), testFile.getName()));
    }
    
    @Test
    void testReadFileBytesWithIllegalPath() {
        String path = testFile.getParentFile().getAbsolutePath() + "/../" + testFile.getParentFile().getName();
        assertNull(DiskUtils.readFileBytes(path, testFile.getName()));
    }
    
    @Test
    void testReadFileBytesWithIllegalFileName() {
        String path = testFile.getParentFile().getAbsolutePath();
        String fileName = "/../" + testFile.getParentFile().getName() + "/" + testFile.getName();
        assertNull(DiskUtils.readFileBytes(path, fileName));
    }
    
    @Test
    void writeFile() {
        assertTrue(DiskUtils.writeFile(testFile, "unit test".getBytes(StandardCharsets.UTF_8), false));
        assertEquals("unit test", DiskUtils.readFile(testFile));
    }
    
    @Test
    void writeFileWithNonExist() {
        File file = new File("\u0000non-exist");
        assertFalse(DiskUtils.writeFile(file, "unit test".getBytes(StandardCharsets.UTF_8), false));
    }
    
    @Test
    void deleteQuietly() throws IOException {
        File tmpFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        DiskUtils.deleteQuietly(tmpFile);
        assertFalse(tmpFile.exists());
    }
    
    @Test
    void testDeleteQuietlyWithPath() throws IOException {
        String dir = EnvUtil.getNacosTmpDir() + "/" + "diskutils";
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
    void testDeleteFileIllegalPath() {
        String path = testFile.getParentFile().getAbsolutePath() + "/../" + testFile.getParentFile().getName();
        assertFalse(DiskUtils.deleteFile(path, testFile.getName()));
    }
    
    @Test
    void testDeleteFileIllegalFileName() {
        String path = testFile.getParentFile().getAbsolutePath();
        String fileName = "../" + testFile.getParentFile().getName() + "/" + testFile.getName();
        assertFalse(DiskUtils.deleteFile(path, fileName));
    }
    
    @Test
    void deleteDirectory() throws IOException {
        Path diskutils = Paths.get(EnvUtil.getNacosTmpDir(), "diskutils");
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
        File dir = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .toFile();
        DiskUtils.forceMkdir(dir);
        assertTrue(dir.exists());
        dir.deleteOnExit();
    }
    
    @Test
    void testForceMkdirWithPath() throws IOException {
        Path path = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        DiskUtils.forceMkdir(path.toString());
        File file = path.toFile();
        assertTrue(file.exists());
        file.deleteOnExit();
    }
    
    @Test
    void deleteDirThenMkdir() throws IOException {
        Path path = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString());
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
        Path srcPath = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString());
        DiskUtils.forceMkdir(srcPath.toString());
        File nacos = DiskUtils.createTmpFile(srcPath.toString(), "nacos", ".ut");
        
        Path destPath = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString());
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
    void testOpenFileWithCreateFile() {
        File file = DiskUtils.openFile(openTestFile.getParent(), openTestFile.getName(), true);
        assertNotNull(file);
        assertEquals(openTestFile.getPath(), file.getPath());
        assertEquals(openTestFile.getName(), file.getName());
    }
    
    @Test
    void testOpenFileWithPath() {
        File file = DiskUtils.openFile(testFile.getParent(), testFile.getName(), false);
        assertNotNull(file);
        assertEquals(testFile.getPath(), file.getPath());
        assertEquals(testFile.getName(), file.getName());
    }
    
    @Test
    void testLineIteratorNextLine() throws IOException {
        try (DiskUtils.LineIterator iterator = DiskUtils.lineIterator(testLineFile)) {
            int lineCount = 0;
            while (iterator.hasNext()) {
                String lineContext = iterator.nextLine();
                assertTrue(lineContext.contains("line"));
                lineCount++;
            }
            assertEquals(3, lineCount);
        }
    }
    
    @Test
    void testLineIteratorNext() throws IOException {
        try (DiskUtils.LineIterator iterator = DiskUtils.lineIterator(testLineFile)) {
            int lineCount = 0;
            while (iterator.hasNext()) {
                String lineContext = iterator.next();
                assertTrue(lineContext.contains("line"));
                lineCount++;
            }
            assertEquals(3, lineCount);
        }
    }
    
    @Test
    void testLineIteratorForEachRemaining() throws IOException {
        try (DiskUtils.LineIterator iterator = DiskUtils.lineIterator(testLineFile)) {
            AtomicInteger lineCount = new AtomicInteger();
            iterator.forEachRemaining(s -> {
                if (s.contains("line")) {
                    lineCount.incrementAndGet();
                }
            });
            assertEquals(3, lineCount.get());
        }
    }
    
    @Test
    void testLineIteratorRemove() {
        assertThrows(UnsupportedOperationException.class, () -> {
            try (DiskUtils.LineIterator iterator = DiskUtils.lineIterator(testLineFile, "UTF-8")) {
                iterator.remove();
            }
        });
    }
}
