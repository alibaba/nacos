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

package com.alibaba.nacos.core.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.alibaba.nacos.common.utils.ByteUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DiskUtils {

    private static final Logger logger = LoggerFactory.getLogger(DiskUtils.class);

    private final static String NO_SPACE_CN = "设备上没有空间";
    private final static String NO_SPACE_EN = "No space left on device";
    private final static String DISK_QUATA_CN = "超出磁盘限额";
    private final static String DISK_QUATA_EN = "Disk quota exceeded";

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final CharsetDecoder DECODER = CHARSET.newDecoder();

    public static void touch(String path, String fileName) throws IOException {
        FileUtils.touch(Paths.get(path, fileName).toFile());
    }

    public static void touch(File file) throws IOException {
        FileUtils.touch(file);
    }

    public static String readFile(String path, String fileName) {
        File file = openFile(path, fileName);
        if (file.exists()) {
            return readFile(file);
        }
        return null;
    }

    public static String readFile(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder textBuilder = new StringBuilder();
            String lineTxt = null;
            while ((lineTxt = reader.readLine()) != null) {
                textBuilder.append(lineTxt);
            }
            return textBuilder.toString();
        } catch (IOException e) {
            return null;
        }
    }

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

    public static byte[] readFileBytes(File file) {
        if (file.exists()) {
            String result = readFile(file);
            if (result != null) {
                return ByteUtils.toBytes(result);
            }
        }
        return null;
    }

    public static byte[] readFileBytes(String path, String fileName) {
        File file = openFile(path, fileName);
        return readFileBytes(file);
    }

    public static boolean writeFile(File file, byte[] content, boolean append) {
        try (FileChannel fileChannel = new FileOutputStream(file, append).getChannel()) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            fileChannel.write(buffer);
            return true;
        } catch (IOException ioe) {
            if (ioe.getMessage() != null) {
                String errMsg = ioe.getMessage();
                if (NO_SPACE_CN.equals(errMsg) || NO_SPACE_EN.equals(errMsg)
                        || errMsg.contains(DISK_QUATA_CN)
                        || errMsg.contains(DISK_QUATA_EN)) {
                    logger.warn("磁盘满，自杀退出");
                    System.exit(0);
                }
            }
        }
        return false;
    }

    public static boolean deleteFile(String path, String fileName) {
        File file = openFile(path, fileName);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static void deleteDirectory(String path) throws IOException {
        FileUtils.deleteDirectory(new File(path));
    }

    public static void forceMkdir(String path) throws IOException {
        FileUtils.forceMkdir(new File(path));
    }

    public static void forceMkdir(File file) throws IOException {
        FileUtils.forceMkdir(file);
    }

    public static void deleteDirThenMkdir(String path) throws IOException {
        deleteDirectory(path);
        forceMkdir(path);
    }

    public static void copyDirectory(File srcDir, File destDir) throws IOException {
        FileUtils.copyDirectory(srcDir, destDir);
    }

    public static void copyFile(File src, File target) throws IOException {
        FileUtils.copyFile(src, target);
    }

    public static File openFile(String path, String fileName) {
        return openFile(path, fileName, false);
    }

    public static File openFile(String path, String fileName, boolean rewrite) {
        File directory = new File(path);
        boolean mkdirs = true;
        if (!directory.exists()) {
            mkdirs = directory.mkdirs();
        }
        if (!mkdirs) {
            logger.error("[DiskUtils] can't create directory");
            return null;
        }
        File file = new File(path, fileName);
        try {
            boolean create = true;
            if (!file.exists()) {
                file.createNewFile();
            }
            if (file.exists()) {
                if (rewrite) {
                    file.delete();
                } else {
                    create = false;
                }
            }
            if (create) {
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }

    // copy from sofa-jraft

    public static void compress(final String rootDir, final String sourceDir, final String outputFile,
                                final Checksum checksum) throws IOException {
        try (final FileOutputStream fos = new FileOutputStream(outputFile);
             final CheckedOutputStream cos = new CheckedOutputStream(fos, checksum);
             final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(cos))) {
            compressDirectoryToZipFile(rootDir, sourceDir, zos);
            zos.flush();
            fos.getFD().sync();
        }
    }

    // copy from sofa-jraft

    private static void compressDirectoryToZipFile(final String rootDir, final String sourceDir,
                                                   final ZipOutputStream zos) throws IOException {
        final String dir = Paths.get(rootDir, sourceDir).toString();
        final File[] files = Objects.requireNonNull(new File(dir).listFiles(), "files");
        for (final File file : files) {
            final String child = Paths.get(sourceDir, file.getName()).toString();
            if (file.isDirectory()) {
                compressDirectoryToZipFile(rootDir, child, zos);
            } else {
                zos.putNextEntry(new ZipEntry(child));
                try (final FileInputStream fis = new FileInputStream(file);
                     final BufferedInputStream bis = new BufferedInputStream(fis)) {
                    IOUtils.copy(bis, zos);
                }
            }
        }
    }

    // copy from sofa-jraft

    public static void decompress(final String sourceFile, final String outputDir, final Checksum checksum)
            throws IOException {
        try (final FileInputStream fis = new FileInputStream(sourceFile);
             final CheckedInputStream cis = new CheckedInputStream(fis, checksum);
             final ZipInputStream zis = new ZipInputStream(new BufferedInputStream(cis))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final String fileName = entry.getName();
                final File entryFile = new File(Paths.get(outputDir, fileName).toString());
                FileUtils.forceMkdir(entryFile.getParentFile());
                try (final FileOutputStream fos = new FileOutputStream(entryFile);
                     final BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    IOUtils.copy(zis, bos);
                    bos.flush();
                    fos.getFD().sync();
                }
            }
            // Continue to read all remaining bytes(extra metadata of ZipEntry) directly from the checked stream,
            // Otherwise, the checksum value maybe unexpected.
            //
            // See https://coderanch.com/t/279175/java/ZipInputStream
            IOUtils.copy(cis, NullOutputStream.NULL_OUTPUT_STREAM);
        }
    }

}
