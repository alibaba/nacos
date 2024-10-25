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

package com.alibaba.nacos.plugin.control.utils;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * IO operates on the utility class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DiskUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DiskUtils.class);
    
    private static final String NO_SPACE_CN = "设备上没有空间";
    
    private static final String NO_SPACE_EN = "No space left on device";
    
    private static final String DISK_QUOTA_CN = "超出磁盘限额";
    
    private static final String DISK_QUOTA_EN = "Disk quota exceeded";
    
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    
    private static final CharsetDecoder DECODER = CHARSET.newDecoder();
    
    /**
     * read this file content.
     *
     * @param file {@link File}
     * @return content
     */
    public static String readFile(File file) {
        try (FileChannel fileChannel = new FileInputStream(file).getChannel()) {
            StringBuilder text = new StringBuilder();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            CharBuffer charBuffer = CharBuffer.allocate(4096);
            while (fileChannel.read(buffer) != -1) {
                buffer.flip();
                DECODER.decode(buffer, charBuffer, false);
                charBuffer.flip();
                while (charBuffer.hasRemaining()) {
                    text.append(charBuffer.get());
                }
                buffer.clear();
                charBuffer.clear();
            }
            return text.toString();
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Writes the contents to the target file.
     *
     * @param file    target file
     * @param content content
     * @param append  write append mode
     * @return write success
     */
    public static boolean writeFile(File file, byte[] content, boolean append) {
        try (FileChannel fileChannel = new FileOutputStream(file, append).getChannel()) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            fileChannel.write(buffer);
            return true;
        } catch (IOException ioe) {
            if (ioe.getMessage() != null) {
                String errMsg = ioe.getMessage();
                if (NO_SPACE_CN.equals(errMsg) || NO_SPACE_EN.equals(errMsg) || errMsg.contains(DISK_QUOTA_CN) || errMsg
                        .contains(DISK_QUOTA_EN)) {
                    LOGGER.warn("磁盘满，自杀退出");
                    System.exit(0);
                }
            }
        }
        return false;
    }
    
    public static void deleteQuietly(File file) {
        Objects.requireNonNull(file, "file");
        FileUtils.deleteQuietly(file);
    }
    
}
