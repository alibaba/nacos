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

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.common.utils.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * concurrent disk util;op file with file lock.
 *
 * @author configCenter
 */
public class ConcurrentDiskUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentDiskUtil.class);
    
    static final int RETRY_COUNT = 10;
    
    /**
     * ms.
     */
    static final int SLEEP_BASETIME = 10;
    
    private static final String READ_ONLY = "r";
    
    private static final String READ_WRITE = "rw";
    
    /**
     * get file content.
     *
     * @param path        file path
     * @param charsetName charsetName
     * @return content
     * @throws IOException IOException
     */
    public static String getFileContent(String path, String charsetName) throws IOException {
        File file = new File(path);
        return getFileContent(file, charsetName);
    }
    
    /**
     * get file content.
     *
     * @param file        file
     * @param charsetName charsetName
     * @return content
     * @throws IOException IOException
     */
    public static String getFileContent(File file, String charsetName) throws IOException {
        RandomAccessFile fis = null;
        FileLock rlock = null;
        try {
            fis = new RandomAccessFile(file, READ_ONLY);
            FileChannel fcin = fis.getChannel();
            int i = 0;
            do {
                try {
                    rlock = fcin.tryLock(0L, Long.MAX_VALUE, true);
                } catch (Exception e) {
                    ++i;
                    if (i > RETRY_COUNT) {
                        LOGGER.error("read {} fail;retryed time:{}", file.getName(), i);
                        throw new IOException("read " + file.getAbsolutePath() + " conflict");
                    }
                    sleep(SLEEP_BASETIME * i);
                    LOGGER.warn("read {} conflict;retry time:{}", file.getName(), i);
                }
            } while (null == rlock);
            int fileSize = (int) fcin.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize);
            fcin.read(byteBuffer);
            byteBuffer.flip();
            return byteBufferToString(byteBuffer, charsetName);
        } finally {
            if (rlock != null) {
                rlock.release();
                rlock = null;
            }
            if (fis != null) {
                IoUtils.closeQuietly(fis);
                fis = null;
            }
        }
    }
    
    /**
     * write file content.
     *
     * @param path        file path
     * @param content     content
     * @param charsetName charsetName
     * @return whether write ok
     * @throws IOException IOException
     */
    public static Boolean writeFileContent(String path, String content, String charsetName) throws IOException {
        File file = new File(path);
        return writeFileContent(file, content, charsetName);
    }
    
    /**
     * write file content.
     *
     * @param file        file
     * @param content     content
     * @param charsetName charsetName
     * @return whether write ok
     * @throws IOException IOException
     */
    public static Boolean writeFileContent(File file, String content, String charsetName) throws IOException {
        if (!file.exists()) {
            boolean isCreateOk = file.createNewFile();
            if (!isCreateOk) {
                return false;
            }
        }
        FileChannel channel = null;
        FileLock lock = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, READ_WRITE);
            channel = raf.getChannel();
            int i = 0;
            do {
                try {
                    lock = channel.tryLock();
                } catch (Exception e) {
                    ++i;
                    if (i > RETRY_COUNT) {
                        LOGGER.error("write {} fail;retryed time:{}", file.getName(), i);
                        throw new IOException("write " + file.getAbsolutePath() + " conflict");
                    }
                    sleep(SLEEP_BASETIME * i);
                    LOGGER.warn("write {} conflict;retry time:{}", file.getName(), i);
                }
            } while (null == lock);
            
            ByteBuffer sendBuffer = ByteBuffer.wrap(content.getBytes(charsetName));
            while (sendBuffer.hasRemaining()) {
                channel.write(sendBuffer);
            }
            channel.truncate(content.length());
        } catch (FileNotFoundException e) {
            throw new IOException("file not exist");
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                    lock = null;
                } catch (IOException e) {
                    LOGGER.warn("close wrong", e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                    channel = null;
                } catch (IOException e) {
                    LOGGER.warn("close wrong", e);
                }
            }
            if (raf != null) {
                try {
                    raf.close();
                    raf = null;
                } catch (IOException e) {
                    LOGGER.warn("close wrong", e);
                }
            }
            
        }
        return true;
    }
    
    /**
     * transfer ByteBuffer to String.
     *
     * @param buffer      buffer
     * @param charsetName charsetName
     * @return String
     * @throws IOException IOException
     */
    public static String byteBufferToString(ByteBuffer buffer, String charsetName) throws IOException {
        Charset charset = null;
        CharsetDecoder decoder = null;
        CharBuffer charBuffer = null;
        charset = Charset.forName(charsetName);
        decoder = charset.newDecoder();
        charBuffer = decoder.decode(buffer.asReadOnlyBuffer());
        return charBuffer.toString();
    }
    
    private static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            LOGGER.warn("sleep wrong", e);
        }
    }
}
