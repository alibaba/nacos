/*
 *
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
 *
 */

package com.alibaba.nacos.client.naming.cache;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ConcurrentDiskUtilTest {
    
    @Test
    public void testReadAndWrite() throws IOException {
        File tempFile = File.createTempFile("aaa", "bbb");
        String fileName = tempFile.getAbsolutePath();
        String content = "hello";
        String charset = "UTF-8";
        ConcurrentDiskUtil.writeFileContent(fileName, content, charset);
        String actualContent = ConcurrentDiskUtil.getFileContent(fileName, charset);
        Assert.assertEquals(content, actualContent);
    }
    
    @Test
    public void testReadAndWrite2() throws IOException {
        File tempFile = File.createTempFile("aaa", "bbb");
        String content = "hello";
        String charset = "UTF-8";
        ConcurrentDiskUtil.writeFileContent(tempFile, content, charset);
        String actualContent = ConcurrentDiskUtil.getFileContent(tempFile, charset);
        Assert.assertEquals(content, actualContent);
    }
    
    @Test
    public void testByteBufferToString() throws IOException {
        String msg = "test buff to string";
        ByteBuffer buff = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
        String actual = ConcurrentDiskUtil.byteBufferToString(buff, "UTF-8");
        Assert.assertEquals(msg, actual);
        
    }
}