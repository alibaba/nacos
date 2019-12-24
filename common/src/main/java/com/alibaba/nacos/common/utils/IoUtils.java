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
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * IO related tool methods
 *
 * @author nacos
 */
public class IoUtils {

    public static byte[] tryDecompress(InputStream raw) throws Exception {
        GZIPInputStream gis = null;
        ByteArrayOutputStream out = null;
        try {
            gis = new GZIPInputStream(raw);
            out = new ByteArrayOutputStream();
            copy(gis, out);
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
            if (gis != null) {
                gis.close();
            }
        }

        return null;
    }

    static private BufferedReader toBufferedReader(Reader reader) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(
            reader);
    }

    public static void writeStringToFile(File file, String data, String encoding)
        throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data.getBytes(encoding));
            os.flush();
        } finally {
            if (null != os) {
                os.close();
            }
        }
    }

    static public List<String> readLines(Reader input) throws IOException {
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

    static public String toString(InputStream input, String encoding) throws IOException {
        if (input == null) {
            return StringUtils.EMPTY;
        }
        return (null == encoding) ? toString(new InputStreamReader(input, Constants.ENCODE))
            : toString(new InputStreamReader(input, encoding));
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
     * 清理目录下的内容
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
        /**
         * null if security restricted
         */
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

    public static void closeQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                closeQuietly(connection.getInputStream());
            } catch (Exception ignore) {
            }
        }
    }

    public static void closeQuietly(InputStream input) {
        closeQuietly((Closeable)input);
    }

    public static void closeQuietly(OutputStream output) {
        closeQuietly((Closeable)output);
    }


    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }

}

