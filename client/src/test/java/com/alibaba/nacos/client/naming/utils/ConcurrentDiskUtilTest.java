/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.client.utils.ConcurrentDiskUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConcurrentDiskUtilTest {
    
    @Test
    void testReadAndWrite() throws IOException {
        File tempFile = File.createTempFile("aaa", "bbb");
        String fileName = tempFile.getAbsolutePath();
        String content = "hello";
        String charset = "UTF-8";
        ConcurrentDiskUtil.writeFileContent(fileName, content, charset);
        String actualContent = ConcurrentDiskUtil.getFileContent(fileName, charset);
        assertEquals(content, actualContent);
    }
    
    @Test
    void testReadAndWrite2() throws IOException {
        File tempFile = File.createTempFile("aaa", "bbb");
        String content = "hello";
        String charset = "UTF-8";
        ConcurrentDiskUtil.writeFileContent(tempFile, content, charset);
        String actualContent = ConcurrentDiskUtil.getFileContent(tempFile, charset);
        assertEquals(content, actualContent);
    }
    
    @Test
    void testByteBufferToString() throws IOException {
        String msg = "test buff to string";
        ByteBuffer buff = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
        String actual = ConcurrentDiskUtil.byteBufferToString(buff, "UTF-8");
        assertEquals(msg, actual);
    }
    
    @Test
    void testWriteFileContent() throws IOException {
        File file = mock(File.class);
        assertFalse(ConcurrentDiskUtil.writeFileContent(file, "hello", "UTF-8"));
    }
    
    @Test
    void testTryLockFailure() throws Throwable {
        assertThrows(IOException.class, () -> {
            Method method = ConcurrentDiskUtil.class.getDeclaredMethod("tryLock", File.class, FileChannel.class,
                    boolean.class);
            method.setAccessible(true);
            File file = new File("non-exist");
            FileChannel channel = mock(FileChannel.class);
            when(channel.tryLock(anyLong(), anyLong(), anyBoolean())).thenThrow(new RuntimeException());
            try {
                method.invoke(null, file, channel, true);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
    
    @Test
    void testTryLockFailureForIntercept() throws Throwable {
        assertThrows(IOException.class, () -> {
            Method method = ConcurrentDiskUtil.class.getDeclaredMethod("tryLock", File.class, FileChannel.class,
                    boolean.class);
            method.setAccessible(true);
            File file = new File("non-exist");
            FileChannel channel = mock(FileChannel.class);
            Thread.currentThread().interrupt();
            when(channel.tryLock(anyLong(), anyLong(), anyBoolean())).thenThrow(new RuntimeException());
            try {
                method.invoke(null, file, channel, true);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}