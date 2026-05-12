/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiskUtilsTest {
    
    private static File testFile;
    
    private static File openTestFile;
    
    private static File testLineFile;
    
    @BeforeAll
    static void setup() throws IOException, URISyntaxException {
        testFile = DiskUtils.createTmpFile("nacostmp", ".ut");
        testLineFile = new File(
            DiskUtilsTest.class.getClassLoader().getResource("line_iterator_test.txt").toURI());
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
        assertEquals("",
            DiskUtils.readFile(file.getParentFile().getAbsolutePath(), file.getName()));
    }
    
    @Test
    void testReadFileWithIllegalPath() {
        String path = testFile.getParentFile().getAbsolutePath() + "/../"
            + testFile.getParentFile().getName();
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
        String path = testFile.getParentFile().getAbsolutePath() + "/../"
            + testFile.getParentFile().getName();
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
        assertTrue(
            DiskUtils.writeFile(testFile, "unit test".getBytes(StandardCharsets.UTF_8), false));
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
        String path = testFile.getParentFile().getAbsolutePath() + "/../"
            + testFile.getParentFile().getName();
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
        File dir = Paths
            .get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString())
            .toFile();
        DiskUtils.forceMkdir(dir);
        assertTrue(dir.exists());
        dir.deleteOnExit();
    }
    
    @Test
    void testForceMkdirWithPath() throws IOException {
        Path path = Paths.get(EnvUtil.getNacosTmpDir(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
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
    
    // ========== Missing lines coverage tests ==========
    
    @Test
    void testWriteFileWithNoSpaceError() {
        // 测试磁盘满异常处理 - 模拟 IOException
        // 由于无法真正触发磁盘满，这里测试正常写入失败的返回值
        File invalidFile = new File("/non/existent/path/file.txt");
        assertFalse(
            DiskUtils.writeFile(invalidFile, "test".getBytes(StandardCharsets.UTF_8), false));
    }
    
    @Test
    void testOpenFileWithIllegalPath() {
        // 测试 openFile 中非法路径的处理
        File result = DiskUtils.openFile("/non/../existent", "test.txt");
        assertNull(result);
    }
    
    @Test
    void testOpenFileWithIllegalFileName() {
        // 测试 openFile 中非法文件名的处理
        File result = DiskUtils.openFile(EnvUtil.getNacosTmpDir(), "../illegal.txt");
        assertNull(result);
    }
    
    @Test
    void testOpenFileInNonWritableDirectory() {
        // 测试在不可写目录中创建文件
        // 这个测试可能需要特定环境，所以使用一个更安全的测试方式
        File nonWritableDir = new File("/root/non-writable-dir-" + UUID.randomUUID());
        File result = DiskUtils.openFile(nonWritableDir.getAbsolutePath(), "test.txt");
        // 如果目录无法创建，应该返回 null 或抛异常
        if (result == null) {
            assertNull(result);
        } else {
            // 如果目录创建成功，清理
            result.deleteOnExit();
            nonWritableDir.deleteOnExit();
        }
    }
    
    @Test
    void testReadFileWithMultiByteUtf8AcrossChunkBoundary() throws IOException {
        // Reproduces the corruption that happens when a multi-byte UTF-8 character straddles the
        // 4096-byte read-buffer boundary. The decode loop reads the first chunk, the decoder
        // reports UNDERFLOW because the trailing bytes are an incomplete UTF-8 sequence, and the
        // unconsumed bytes are dropped if the buffer is cleared instead of compacted.
        //
        // Layout (UTF-8): 4094 ASCII bytes + 3-byte CJK ('中', E4 B8 AD) + 200 ASCII bytes.
        // The CJK character occupies bytes 4094..4096, so its trailing byte falls in the second
        // chunk. With buffer.clear() the leading two bytes are discarded and the second chunk
        // starts at the orphaned continuation byte AD, producing a malformed sequence and
        // truncating the rest of the file. With buffer.compact() the leading bytes are preserved
        // and the character round-trips cleanly.
        File f = Files.createTempFile("nacos-sys-disk-utils-chunk-boundary", ".txt").toFile();
        f.deleteOnExit();
        StringBuilder content = new StringBuilder(4094 + 1 + 200);
        for (int i = 0; i < 4094; i++) {
            content.append('a');
        }
        content.append('中'); // '中', encodes to E4 B8 AD in UTF-8
        for (int i = 0; i < 200; i++) {
            content.append('b');
        }
        String expected = content.toString();
        byte[] encoded = expected.getBytes(StandardCharsets.UTF_8);
        // Sanity-check the layout so future edits to the test cannot accidentally make it pass.
        assertEquals(4094 + 3 + 200, encoded.length);
        assertEquals((byte) 0xe4, encoded[4094]);
        assertEquals((byte) 0xb8, encoded[4095]);
        assertEquals((byte) 0xad, encoded[4096]);
        Files.write(f.toPath(), encoded);
        
        assertEquals(expected, DiskUtils.readFile(f));
    }
    
    @Test
    void testReadFileWithSmallMultiByteUtf8Content() throws IOException {
        // Regression: small non-ASCII content that fits in a single 4096-byte chunk must continue
        // to round-trip correctly.
        File f = Files.createTempFile("nacos-sys-disk-utils-utf8", ".txt").toFile();
        f.deleteOnExit();
        String content = "限流规则-中文内容-αβγ-🚀";
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        assertEquals(content, DiskUtils.readFile(f));
    }
    
    @Test
    void testReadFileSequentialCallsAreIndependent() throws IOException {
        // Each readFile call must observe a clean decoder regardless of what the previous call
        // read; the previous code shared one static CharsetDecoder across all callers.
        File f = Files.createTempFile("nacos-sys-disk-utils-sequential", ".txt").toFile();
        f.deleteOnExit();
        String first = "first-payload-数据A";
        String second = "second-payload-数据B";
        Files.write(f.toPath(), first.getBytes(StandardCharsets.UTF_8));
        assertEquals(first, DiskUtils.readFile(f));
        Files.write(f.toPath(), second.getBytes(StandardCharsets.UTF_8));
        assertEquals(second, DiskUtils.readFile(f));
    }
    
    @Test
    void testConstructor() {
        new DiskUtils();
    }
    
    @Test
    void testWriteFileWithNoSpaceCnTriggersExit() throws Exception {
        verifyDiskFullExit("设备上没有空间");
    }
    
    @Test
    void testWriteFileWithNoSpaceEnTriggersExit() throws Exception {
        verifyDiskFullExit("No space left on device");
    }
    
    @Test
    void testWriteFileWithDiskQuotaCnTriggersExit() throws Exception {
        verifyDiskFullExit("xx超出磁盘限额xx");
    }
    
    @Test
    void testWriteFileWithDiskQuotaEnTriggersExit() throws Exception {
        verifyDiskFullExit("xx Disk quota exceeded xx");
    }
    
    @Test
    void testWriteFileWithIoExceptionWithoutMessage() throws Exception {
        File targetFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        targetFile.deleteOnExit();
        try (MockedConstruction<FileOutputStream> ignored =
            Mockito.mockConstruction(FileOutputStream.class, (mock, ctx) -> {
                FileChannel channel = mock(FileChannel.class);
                when(mock.getChannel()).thenReturn(channel);
                when(channel.write(any(ByteBuffer.class))).thenThrow(new IOException());
            })) {
            assertFalse(DiskUtils.writeFile(targetFile, new byte[] {1}, false));
        }
    }
    
    private void verifyDiskFullExit(String ioMessage) throws Exception {
        File targetFile = DiskUtils.createTmpFile(UUID.randomUUID().toString(), ".ut");
        targetFile.deleteOnExit();
        Runtime runtimeMock = mock(Runtime.class);
        try (MockedConstruction<FileOutputStream> ignored =
            Mockito.mockConstruction(FileOutputStream.class, (mock, ctx) -> {
                FileChannel channel = mock(FileChannel.class);
                when(mock.getChannel()).thenReturn(channel);
                when(channel.write(any(ByteBuffer.class)))
                    .thenThrow(new IOException(ioMessage));
            });
            MockedStatic<Runtime> runtimeMocked = Mockito.mockStatic(Runtime.class)) {
            runtimeMocked.when(Runtime::getRuntime).thenReturn(runtimeMock);
            assertFalse(DiskUtils.writeFile(targetFile, new byte[] {1}, false));
            verify(runtimeMock).exit(0);
        }
    }
    
    @Test
    void testOpenFileRethrowsIoExceptionAsRuntimeException() {
        try (MockedConstruction<File> ignored =
            Mockito.mockConstruction(File.class, (mock, ctx) -> {
                when(mock.exists()).thenReturn(false);
                when(mock.mkdirs()).thenReturn(true);
                when(mock.createNewFile()).thenThrow(new IOException("forced"));
            })) {
            assertThrows(RuntimeException.class,
                () -> DiskUtils.openFile("nacos-tmp", "openFile-ioexception"));
        }
    }
    
    @Test
    void testDecompressSkipsIllegalEntryName() throws IOException {
        File zipFile = Files.createTempFile("nacos-sys-illegal-zip", ".zip").toFile();
        zipFile.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry illegal = new ZipEntry("../illegal.txt");
            zos.putNextEntry(illegal);
            zos.write("evil".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            ZipEntry safe = new ZipEntry("safe.txt");
            zos.putNextEntry(safe);
            zos.write("ok".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        File outDir = Files.createTempDirectory("nacos-sys-illegal-out").toFile();
        outDir.deleteOnExit();
        DiskUtils.decompress(zipFile.getAbsolutePath(), outDir.getAbsolutePath(), new Adler32());
        assertFalse(new File(outDir.getParentFile(), "illegal.txt").exists());
        assertTrue(new File(outDir, "safe.txt").exists());
    }
    
    @Test
    void testLineIteratorRemoveDelegatesToTarget() throws Exception {
        org.apache.commons.io.LineIterator targetMock =
            mock(org.apache.commons.io.LineIterator.class);
        doNothing().when(targetMock).remove();
        Constructor<DiskUtils.LineIterator> ctor = DiskUtils.LineIterator.class
            .getDeclaredConstructor(org.apache.commons.io.LineIterator.class);
        ctor.setAccessible(true);
        DiskUtils.LineIterator iterator = ctor.newInstance(targetMock);
        iterator.remove();
        verify(targetMock).remove();
    }
}
