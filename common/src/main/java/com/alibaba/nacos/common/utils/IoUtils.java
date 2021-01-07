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

import com.alibaba.nacos.api.common.Constants;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * IO related tool methods.
 *
 * @author nacos
 */
public class IoUtils {
    
    /**
     * Try decompress by GZIP from stream.
     *
     * @param raw compress stream
     * @return byte array after decompress
     * @throws IOException exception
     */
    public static byte[] tryDecompress(InputStream raw) throws IOException {
        GZIPInputStream gis = null;
        ByteArrayOutputStream out = null;
        try {
            gis = new GZIPInputStream(raw);
            out = new ByteArrayOutputStream();
            copy(gis, out);
            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeQuietly(out);
            closeQuietly(gis);
        }
        
        return null;
    }
    
    /**
     * Try decompress by GZIP from byte array.
     *
     * @param raw compressed byte array
     * @return byte array after decompress
     * @throws Exception exception
     */
    public static byte[] tryDecompress(byte[] raw) throws Exception {
        if (!isGzipStream(raw)) {
            return raw;
        }
        GZIPInputStream gis = null;
        ByteArrayOutputStream out = null;
        
        try {
            gis = new GZIPInputStream(new ByteArrayInputStream(raw));
            out = new ByteArrayOutputStream();
            IoUtils.copy(gis, out);
            return out.toByteArray();
        } finally {
            closeQuietly(out);
            closeQuietly(gis);
        }
    }
    
    /**
     * Try compress by GZIP for string.
     *
     * @param str      strings to be compressed.
     * @param encoding encoding.
     * @return byte[]
     */
    public static byte[] tryCompress(String str, String encoding) {
        if (str == null || str.length() == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(str.getBytes(encoding));
            gzip.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }
    
    private static BufferedReader toBufferedReader(Reader reader) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    }
    
    /**
     * Write string to a file.
     *
     * @param file     file
     * @param data     string
     * @param encoding encoding of string
     * @throws IOException io exception
     */
    public static void writeStringToFile(File file, String data, String encoding) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data.getBytes(encoding));
            os.flush();
        } finally {
            closeQuietly(os);
        }
    }
    
    /**
     * Read lines.
     *
     * @param input reader
     * @return list of line
     * @throws IOException io exception
     */
    public static List<String> readLines(Reader input) throws IOException {
        BufferedReader reader = toBufferedReader(input);
        List<String> list = new ArrayList<String>();
        String line = null;
        for (; ; ) {
            line = reader.readLine();
            if (null != line) {
                if (StringUtils.isNotEmpty(line)) {
                    list.add(line.trim());
                }
            } else {
                break;
            }
        }
        return list;
    }
    
    /**
     * To string from stream.
     *
     * @param input    stream
     * @param encoding charset of stream
     * @return string
     * @throws IOException io exception
     */
    public static String toString(InputStream input, String encoding) throws IOException {
        if (input == null) {
            return StringUtils.EMPTY;
        }
        return (null == encoding) ? toString(new InputStreamReader(input, Constants.ENCODE))
                : toString(new InputStreamReader(input, encoding));
    }
    
    /**
     * To string from reader.
     *
     * @param reader reader
     * @return string
     * @throws IOException io exception
     */
    public static String toString(Reader reader) throws IOException {
        CharArrayWriter sw = new CharArrayWriter();
        copy(reader, sw);
        return sw.toString();
    }
    
    /**
     * Copy data.
     *
     * @param input  source
     * @param output target
     * @return copy size
     * @throws IOException io exception
     */
    public static long copy(Reader input, Writer output) throws IOException {
        char[] buffer = new char[1 << 12];
        long count = 0;
        for (int n = 0; (n = input.read(buffer)) >= 0; ) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
    
    /**
     * Copy data.
     *
     * @param input  source
     * @param output target
     * @return copy size
     * @throws IOException io exception
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        int totalBytes = 0;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            
            totalBytes += bytesRead;
        }
        
        return totalBytes;
    }
    
    /**
     * Delete file or dir.
     *
     * <p>If is dir, clean directory, do not delete dir.
     *
     * <p>If is file, delete file.
     *
     * @param fileOrDir file or dir
     * @throws IOException io exception
     */
    public static void delete(File fileOrDir) throws IOException {
        if (fileOrDir == null) {
            return;
        }
        
        if (fileOrDir.isDirectory()) {
            cleanDirectory(fileOrDir);
        } else {
            if (fileOrDir.exists()) {
                boolean isDeleteOk = fileOrDir.delete();
                if (!isDeleteOk) {
                    throw new IOException("delete fail");
                }
            }
        }
    }
    
    /**
     * 清理目录下的内容. Clean content under directory.
     *
     * @param directory directory
     * @throws IOException io exception
     */
    public static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }
        
        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }
        
        File[] files = directory.listFiles();
        // null if security restricted
        if (files == null) {
            throw new IOException("Failed to list contents of " + directory);
        }
        
        IOException exception = null;
        for (File file : files) {
            try {
                delete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }
        
        if (null != exception) {
            throw exception;
        }
    }
    
    /**
     * Copy File.
     *
     * @param source source file path
     * @param target target file path
     * @throws IOException io exception
     */
    public static void copyFile(String source, String target) throws IOException {
        File sf = new File(source);
        if (!sf.exists()) {
            throw new IllegalArgumentException("source file does not exist.");
        }
        File tf = new File(target);
        if (!tf.getParentFile().mkdirs()) {
            throw new RuntimeException("failed to create parent directory.");
        }
        if (!tf.exists() && !tf.createNewFile()) {
            throw new RuntimeException("failed to create target file.");
        }
        
        FileChannel sc = null;
        FileChannel tc = null;
        try {
            tc = new FileOutputStream(tf).getChannel();
            sc = new FileInputStream(sf).getChannel();
            sc.transferTo(0, sc.size(), tc);
        } finally {
            closeQuietly(sc);
            closeQuietly(tc);
        }
    }
    
    /**
     * Judge whether is Gzip stream.
     *
     * @param bytes byte array
     * @return true if is gzip, otherwise false
     */
    public static boolean isGzipStream(byte[] bytes) {
        
        int minByteArraySize = 2;
        if (bytes == null || bytes.length < minByteArraySize) {
            return false;
        }
        
        return GZIPInputStream.GZIP_MAGIC == ((bytes[1] << 8 | bytes[0]) & 0xFFFF);
    }
    
    /**
     * Close http connection quietly.
     *
     * @param connection http connection
     */
    public static void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                closeQuietly(connection.getInputStream());
            } catch (Exception ignore) {
            }
        }
    }
    
    public static void closeQuietly(InputStream input) {
        closeQuietly((Closeable) input);
    }
    
    public static void closeQuietly(OutputStream output) {
        closeQuietly((Closeable) output);
    }
    
    /**
     * Close closable object quietly.
     *
     * @param closeable http connection
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignored) {
        }
    }
}

