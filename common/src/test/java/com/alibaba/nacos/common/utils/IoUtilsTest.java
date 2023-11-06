/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.apache.commons.io.Charsets;
import org.junit.Assert;
import org.junit.Test;
import sun.security.action.GetPropertyAction;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of IoUtils.
 *
 * @author karsonto
 */
public class IoUtilsTest {
    
    @Test
    public void testTryDecompressForNotGzip() throws Exception {
        byte[] testCase = "123".getBytes(Charsets.toCharset("UTF-8"));
        Assert.assertEquals(testCase, IoUtils.tryDecompress(testCase));
    }
    
    @Test
    public void testTryDecompressForGzip() throws Exception {
        byte[] testCase = IoUtils.tryCompress("123", "UTF-8");
        Assert.assertEquals("123", new String(IoUtils.tryDecompress(testCase), StandardCharsets.UTF_8));
    }
    
    @Test
    public void testTryCompressWithEmptyString() {
        Assert.assertEquals(0, IoUtils.tryCompress("", "UTF-8").length);
        Assert.assertEquals(0, IoUtils.tryCompress(null, "UTF-8").length);
    }
    
    @Test
    public void testWriteStringToFile() throws IOException {
        File file = null;
        try {
            file = File.createTempFile("test_writeStringToFile", ".txt");
            IoUtils.writeStringToFile(file, "123", "UTF-8");
            List<String> actual = IoUtils.readLines(new FileReader(file));
            Assert.assertEquals(1, actual.size());
            Assert.assertEquals("123", actual.get(0));
        } finally {
            if (null != file) {
                file.deleteOnExit();
            }
        }
    }
    
    @Test
    public void testToStringWithNull() throws IOException {
        Assert.assertEquals("", IoUtils.toString(null, "UTF-8"));
    }
    
    @Test
    public void testToStringWithReader() throws IOException {
        String testCase = "123";
        Assert.assertEquals(testCase,
                IoUtils.toString(new ByteArrayInputStream(testCase.getBytes(Charsets.toCharset("UTF-8"))), "UTF-8"));
    }
    
    @Test
    public void testDeleteForNullFile() throws IOException {
        IoUtils.delete(null);
    }
    
    @Test
    public void testDeleteSuccess() throws IOException {
        File file = null;
        try {
            file = File.createTempFile("test_deleteForFile", ".txt");
            Assert.assertTrue(file.exists());
            IoUtils.delete(file);
            Assert.assertFalse(file.exists());
        } finally {
            if (null != file) {
                file.deleteOnExit();
            }
        }
    }
    
    @Test(expected = IOException.class)
    public void testDeleteFileFailure() throws IOException {
        File file = mock(File.class);
        when(file.exists()).thenReturn(true);
        when(file.delete()).thenReturn(false);
        IoUtils.delete(file);
    }
    
    @Test
    public void testDeleteForDirectory() throws IOException {
        File file = null;
        try {
            String tmpDir = AccessController.doPrivileged(new GetPropertyAction("java.io.tmpdir"));
            File tmpDirFile = new File(tmpDir, "IoUtilsTest");
            tmpDirFile.mkdirs();
            file = File.createTempFile("test_deleteForDirectory", ".txt", tmpDirFile);
            Assert.assertTrue(file.exists());
            IoUtils.delete(file.getParentFile());
            Assert.assertTrue(tmpDirFile.exists());
            Assert.assertFalse(file.exists());
        } finally {
            if (null != file) {
                file.getParentFile().deleteOnExit();
                file.deleteOnExit();
            }
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCleanDirectoryForNonExistingDirectory() throws IOException {
        File nonexistentDir = new File("non_exist");
        IoUtils.cleanDirectory(nonexistentDir);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCleanDirectoryForFile() throws IOException {
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        IoUtils.cleanDirectory(mockFile);
    }
    
    @Test(expected = IOException.class)
    public void testCleanDirectoryWithEmptyDirectory() throws IOException {
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.isDirectory()).thenReturn(true);
        IoUtils.cleanDirectory(mockFile);
    }
    
    @Test(expected = IOException.class)
    public void testCleanDirectory() throws IOException {
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.isDirectory()).thenReturn(true);
        File mockSubFile = mock(File.class);
        when(mockSubFile.exists()).thenReturn(true);
        when(mockFile.listFiles()).thenReturn(new File[] {mockSubFile});
        IoUtils.cleanDirectory(mockFile);
    }
    
    @Test
    public void testIsGzipStreamWithNull() {
        Assert.assertFalse(IoUtils.isGzipStream(null));
    }
    
    @Test
    public void testIsGzipStreamWithEmpty() {
        Assert.assertFalse(IoUtils.isGzipStream(new byte[0]));
    }
    
    @Test()
    public void testCloseQuietly() throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream("111".getBytes(Charsets.toCharset("UTF-8")))));
        Assert.assertEquals("111", br.readLine());
        IoUtils.closeQuietly(br);
        try {
            br.readLine();
        } catch (IOException e) {
            Assert.assertNotNull(e);
            return;
        }
        Assert.fail();
    }
    
    @Test()
    public void testCloseQuietly2() throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream("123".getBytes(Charsets.toCharset("UTF-8")))));
        Assert.assertEquals("123", br.readLine());
        BufferedReader br2 = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream("456".getBytes(Charsets.toCharset("UTF-8")))));
        Assert.assertEquals("456", br2.readLine());
        IoUtils.closeQuietly(br, br2);
        try {
            br.readLine();
        } catch (IOException e) {
            Assert.assertNotNull(e);
        }
        try {
            br2.readLine();
        } catch (IOException e) {
            Assert.assertNotNull(e);
            return;
        }
        Assert.fail();
    }
    
    @Test
    public void testCloseQuietlyForHttpConnection() throws IOException {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        InputStream inputStream = mock(InputStream.class);
        when(conn.getInputStream()).thenReturn(inputStream);
        IoUtils.closeQuietly(conn);
        verify(inputStream).close();
    }
    
}
