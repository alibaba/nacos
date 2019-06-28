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
package com.alibaba.nacos.client.naming.utils;


import com.alibaba.nacos.client.utils.StringUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;
import static org.apache.commons.lang3.CharEncoding.UTF_8;

/**
 * @author nkorange
 */
public class IoUtils {

    static public String toString(InputStream input, String encoding) {

        try {
            return (null == encoding) ? toString(new InputStreamReader(input, UTF_8))
                : toString(new InputStreamReader(input, encoding));
        } catch (Exception e) {
            NAMING_LOGGER.error("NA", "read input failed.", e);
            return StringUtils.EMPTY;
        }
    }

    static public String toString(Reader reader) throws IOException {
        CharArrayWriter sw = new CharArrayWriter();
        copy(reader, sw);
        return sw.toString();
    }

    static public long copy(Reader input, Writer output) throws IOException {
        char[] buffer = new char[1 << 12];
        long count = 0;
        for (int n = 0; (n = input.read(buffer)) >= 0; ) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    static public long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        int totalBytes = 0;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);

            totalBytes += bytesRead;
        }

        return totalBytes;
    }

    static public List<String> readLines(Reader input) throws IOException {
        BufferedReader reader = toBufferedReader(input);
        List<String> list = new ArrayList<String>();
        String line = null;
        for (; ; ) {
            line = reader.readLine();
            if (null != line) {
                list.add(line);
            } else {
                break;
            }
        }
        return list;
    }

    static private BufferedReader toBufferedReader(Reader reader) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(
            reader);
    }

    static public void copyFile(String source, String target) throws IOException {
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
            if (null != sc) {
                sc.close();
            }
            if (null != tc) {
                tc.close();
            }
        }
    }

    public static void delete(File fileOrDir) throws IOException {
        if (fileOrDir == null) {
            return;
        }

        if (fileOrDir.isDirectory()) {
            cleanDirectory(fileOrDir);
        }

        if (fileOrDir.delete()) {
            throw new RuntimeException("failed to delete file: " + fileOrDir.getAbsolutePath());
        }
    }

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

    public static boolean isGzipStream(byte[] bytes) {

        int minByteArraySize = 2;
        if (bytes == null || bytes.length < minByteArraySize) {
            return false;
        }

        return GZIPInputStream.GZIP_MAGIC == ((bytes[1] << 8 | bytes[0]) & 0xFFFF);
    }

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
            if (out != null) {
                out.close();
            }
            if (gis != null) {
                gis.close();
            }
        }
    }
}

