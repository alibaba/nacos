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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.common.utils.IoUtils;

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

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Concurrent Disk util.
 *
 * @author nkorange
 */
public class ConcurrentDiskUtil {
    
    private static final String READ_ONLY = "r";
    
    private static final String READ_WRITE = "rw";
    
    private static final int RETRY_COUNT = 10;
    
    private static final int SLEEP_BASETIME = 10;
    
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
                        NAMING_LOGGER.error("[NA] read " + file.getName() + " fail;retryed time: " + i, e);
                        throw new IOException("read " + file.getAbsolutePath() + " conflict");
                    }
                    sleep(SLEEP_BASETIME * i);
                    NAMING_LOGGER.warn("read " + file.getName() + " conflict;retry time: " + i);
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
        
        if (!file.exists() && !file.createNewFile()) {
            return false;
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
                        NAMING_LOGGER.error("[NA] write {} fail;retryed time:{}", file.getName(), i);
                        throw new IOException("write " + file.getAbsolutePath() + " conflict", e);
                    }
                    sleep(SLEEP_BASETIME * i);
                    NAMING_LOGGER.warn("write " + file.getName() + " conflict;retry time: " + i);
                }
            } while (null == lock);
            
            byte[] contentBytes = content.getBytes(charsetName);
            ByteBuffer sendBuffer = ByteBuffer.wrap(contentBytes);
            while (sendBuffer.hasRemaining()) {
                channel.write(sendBuffer);
            }
            channel.truncate(contentBytes.length);
        } catch (FileNotFoundException e) {
            throw new IOException("file not exist");
        } finally {
            if (lock != null) {
                try {
                    lock.release();
                    lock = null;
                } catch (IOException e) {
                    NAMING_LOGGER.warn("close wrong", e);
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                    channel = null;
                } catch (IOException e) {
                    NAMING_LOGGER.warn("close wrong", e);
                }
            }
            if (raf != null) {
                try {
                    raf.close();
                    raf = null;
                } catch (IOException e) {
                    NAMING_LOGGER.warn("close wrong", e);
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
        Charset charset = Charset.forName(charsetName);
        CharsetDecoder decoder = charset.newDecoder();
        CharBuffer charBuffer = decoder.decode(buffer.asReadOnlyBuffer());
        return charBuffer.toString();
    }
    
    private static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            NAMING_LOGGER.warn("sleep wrong", e);
        }
    }
    
}
