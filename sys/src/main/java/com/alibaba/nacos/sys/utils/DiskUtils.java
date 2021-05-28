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

package com.alibaba.nacos.sys.utils;

import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.Objects;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * IO operates on the utility class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class DiskUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DiskUtils.class);
    
    private static final String NO_SPACE_CN = "设备上没有空间";
    
    private static final String NO_SPACE_EN = "No space left on device";
    
    private static final String DISK_QUATA_CN = "超出磁盘限额";
    
    private static final String DISK_QUATA_EN = "Disk quota exceeded";
    
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    
    private static final CharsetDecoder DECODER = CHARSET.newDecoder();
    
    public static void touch(String path, String fileName) throws IOException {
        FileUtils.touch(Paths.get(path, fileName).toFile());
    }
    
    /**
     * Implements the same behaviour as the "touch" utility on Unix. It creates a new file with size 0 or, if the file
     * exists already, it is opened and closed without modifying it, but updating the file date and time.
     *
     * <p>NOTE: As from v1.3, this method throws an IOException if the last
     * modified date of the file cannot be set. Also, as from v1.3 this method creates parent directories if they do not
     * exist.
     *
     * @param file the File to touch
     * @throws IOException If an I/O problem occurs
     */
    public static void touch(File file) throws IOException {
        FileUtils.touch(file);
    }
    
    /**
     * Creates a new empty file in the specified directory, using the given prefix and suffix strings to generate its
     * name. The resulting {@code Path} is associated with the same {@code FileSystem} as the given directory.
     *
     * <p>The details as to how the name of the file is constructed is
     * implementation dependent and therefore not specified. Where possible the {@code prefix} and {@code suffix} are
     * used to construct candidate names in the same manner as the {@link java.io.File#createTempFile(String, String,
     * File)} method.
     *
     * @param dir    the path to directory in which to create the file
     * @param prefix the prefix string to be used in generating the file's name; may be {@code null}
     * @param suffix the suffix string to be used in generating the file's name; may be {@code null}, in which case
     *               "{@code .tmp}" is used
     * @return the path to the newly created file that did not exist before this method was invoked
     * @throws IllegalArgumentException      if the prefix or suffix parameters cannot be used to generate a candidate
     *                                       file name
     * @throws UnsupportedOperationException if the array contains an attribute that cannot be set atomically when
     *                                       creating the directory
     * @throws IOException                   if an I/O error occurs or {@code dir} does not exist
     * @throws SecurityException             In the case of the default provider, and a security manager is installed,
     *                                       the {@link SecurityManager#checkWrite(String) checkWrite} method is invoked
     *                                       to check write access to the file.
     */
    public static File createTmpFile(String dir, String prefix, String suffix) throws IOException {
        return Files.createTempFile(Paths.get(dir), prefix, suffix).toFile();
    }
    
    /**
     * Creates an empty file in the default temporary-file directory, using the given prefix and suffix to generate its
     * name. The resulting {@code Path} is associated with the default {@code FileSystem}.
     *
     * @param prefix the prefix string to be used in generating the file's name; may be {@code null}
     * @param suffix the suffix string to be used in generating the file's name; may be {@code null}, in which case
     *               "{@code .tmp}" is used
     * @return the path to the newly created file that did not exist before this method was invoked
     * @throws IllegalArgumentException      if the prefix or suffix parameters cannot be used to generate a candidate
     *                                       file name
     * @throws UnsupportedOperationException if the array contains an attribute that cannot be set atomically when
     *                                       creating the directory
     * @throws IOException                   if an I/O error occurs or the temporary-file directory does not exist
     * @throws SecurityException             In the case of the default provider, and a security manager is installed,
     *                                       the {@link SecurityManager#checkWrite(String) checkWrite} method is invoked
     *                                       to check write access to the file.
     */
    public static File createTmpFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix).toFile();
    }
    
    /**
     * read file which under the path.
     *
     * @param path     directory
     * @param fileName filename
     * @return content
     */
    public static String readFile(String path, String fileName) {
        File file = openFile(path, fileName);
        if (file.exists()) {
            return readFile(file);
        }
        return null;
    }
    
    /**
     * read file content by {@link InputStream}.
     *
     * @param is {@link InputStream}
     * @return content
     */
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
     * read this file content then return bytes.
     *
     * @param file {@link File}
     * @return content bytes
     */
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
                if (NO_SPACE_CN.equals(errMsg) || NO_SPACE_EN.equals(errMsg) || errMsg.contains(DISK_QUATA_CN) || errMsg
                        .contains(DISK_QUATA_EN)) {
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
    
    public static void deleteQuietly(Path path) {
        Objects.requireNonNull(path, "path");
        FileUtils.deleteQuietly(path.toFile());
    }
    
    /**
     * delete target file.
     *
     * @param path     directory
     * @param fileName filename
     * @return delete success
     */
    public static boolean deleteFile(String path, String fileName) {
        File file = Paths.get(path, fileName).toFile();
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
    
    /**
     * open file.
     *
     * @param path     directory
     * @param fileName filename
     * @param rewrite  if rewrite is true, will delete old file and create new one
     * @return {@link File}
     */
    public static File openFile(String path, String fileName, boolean rewrite) {
        File directory = new File(path);
        boolean mkdirs = true;
        if (!directory.exists()) {
            mkdirs = directory.mkdirs();
        }
        if (!mkdirs) {
            LOGGER.error("[DiskUtils] can't create directory");
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
    
    /**
     * Compress a folder in a directory.
     *
     * @param rootDir    directory
     * @param sourceDir  folder
     * @param outputFile output file
     * @param checksum   checksum
     * @throws IOException IOException
     */
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
                try (final FileInputStream fis = new FileInputStream(file);
                        final BufferedInputStream bis = new BufferedInputStream(fis)) {
                    compressIntoZipFile(child, bis, zos);
                }
            }
        }
    }
    
    /**
     * Compress an input stream to zip file.
     *
     * @param childName   child name in zip file
     * @param inputStream input stream needed compress
     * @param outputFile  output file
     * @param checksum    check sum
     * @throws IOException IOException during compress
     */
    public static void compressIntoZipFile(final String childName, final InputStream inputStream,
            final String outputFile, final Checksum checksum) throws IOException {
        try (final FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                final CheckedOutputStream checkedOutputStream = new CheckedOutputStream(fileOutputStream, checksum);
                final ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(checkedOutputStream))) {
            compressIntoZipFile(childName, inputStream, zipStream);
            zipStream.flush();
            fileOutputStream.getFD().sync();
        }
    }
    
    private static void compressIntoZipFile(final String childName, final InputStream inputStream,
            final ZipOutputStream zipOutputStream) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(childName));
        IOUtils.copy(inputStream, zipOutputStream);
    }
    
    // copy from sofa-jraft
    
    /**
     * Unzip the target file to the specified folder.
     *
     * @param sourceFile target file
     * @param outputDir  specified folder
     * @param checksum   checksum
     * @throws IOException IOException
     */
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
    
    /**
     * Unzip the target file to byte array.
     *
     * @param sourceFile target file
     * @param checksum   checksum
     * @return decompress byte array
     * @throws IOException IOException during decompress
     */
    public static byte[] decompress(final String sourceFile, final Checksum checksum) throws IOException {
        byte[] result;
        try (final FileInputStream fis = new FileInputStream(sourceFile);
                final CheckedInputStream cis = new CheckedInputStream(fis, checksum);
                final ZipInputStream zis = new ZipInputStream(new BufferedInputStream(cis));
                final ByteArrayOutputStream bos = new ByteArrayOutputStream(1024)) {
            while (zis.getNextEntry() != null) {
                IOUtils.copy(zis, bos);
                bos.flush();
            }
            IOUtils.copy(cis, NullOutputStream.NULL_OUTPUT_STREAM);
            result = bos.toByteArray();
        }
        return result;
    }
    
    /**
     * Returns an Iterator for the lines in a <code>File</code>.
     * <p>
     * This method opens an <code>InputStream</code> for the file. When you have finished with the iterator you should
     * close the stream to free internal resources. This can be done by calling the {@link
     * org.apache.commons.io.LineIterator#close()} or {@link org.apache.commons.io.LineIterator#closeQuietly(org.apache.commons.io.LineIterator)}
     * method.
     * </p>
     * The recommended usage pattern is:
     * <pre>
     * LineIterator it = FileUtils.lineIterator(file, "UTF-8");
     * try {
     *   while (it.hasNext()) {
     *     String line = it.nextLine();
     *     /// do something with line
     *   }
     * } finally {
     *   LineIterator.closeQuietly(iterator);
     * }
     * </pre>
     * <p>
     * If an exception occurs during the creation of the iterator, the underlying stream is closed.
     * </p>
     *
     * @param file     the file to open for input, must not be <code>null</code>
     * @param encoding the encoding to use, <code>null</code> means platform default
     * @return an Iterator of the lines in the file, never <code>null</code>
     * @throws IOException in case of an I/O error (file closed)
     * @since 1.2
     */
    public static LineIterator lineIterator(File file, String encoding) throws IOException {
        return new LineIterator(FileUtils.lineIterator(file, encoding));
    }
    
    /**
     * Returns an Iterator for the lines in a <code>File</code> using the default encoding for the VM.
     *
     * @param file the file to open for input, must not be <code>null</code>
     * @return an Iterator of the lines in the file, never <code>null</code>
     * @throws IOException in case of an I/O error (file closed)
     * @see #lineIterator(File, String)
     * @since 1.3
     */
    public static LineIterator lineIterator(File file) throws IOException {
        return new LineIterator(FileUtils.lineIterator(file, null));
    }
    
    public static class LineIterator implements AutoCloseable {
        
        private final org.apache.commons.io.LineIterator target;
        
        /**
         * Constructs an iterator of the lines for a <code>Reader</code>.
         *
         * @param target {@link org.apache.commons.io.LineIterator}
         */
        LineIterator(org.apache.commons.io.LineIterator target) {
            this.target = target;
        }
        
        public boolean hasNext() {
            return target.hasNext();
        }
        
        public String next() {
            return target.next();
        }
        
        public String nextLine() {
            return target.nextLine();
        }
        
        @Override
        public void close() throws IOException {
            target.close();
        }
        
        public void remove() {
            target.remove();
        }
        
        public void forEachRemaining(Consumer<? super String> action) {
            target.forEachRemaining(action);
        }
    }
    
}
