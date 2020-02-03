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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ZipUtils {

    public static void compressDirectoryToZipFile(final String rootDir,
                                                  final String sourceDir, final ZipOutputStream zos,
                                                  final WritableByteChannel channel) throws IOException {
        final String dir = Paths.get(rootDir, sourceDir).toString();
        final File[] files = new File(dir).listFiles();
        assert files != null;
        for (final File file : files) {
            if (file.isDirectory()) {
                compressDirectoryToZipFile(rootDir,
                        Paths.get(sourceDir, file.getName()).toString(), zos, channel);
            }
            else {
                zos.putNextEntry(
                        new ZipEntry(Paths.get(sourceDir, file.getName()).toString()));
                try (final FileInputStream in = new FileInputStream(
                        Paths.get(rootDir, sourceDir, file.getName()).toString())) {
                    FileChannel fileChannel = in.getChannel();
                    fileChannel.transferTo(0, fileChannel.size(), channel);
                }
            }
        }
    }

    public static void unzipFile(final String sourceFile, final String outputDir)
            throws IOException {
        try (final ZipInputStream zis = new ZipInputStream(
                new FileInputStream(sourceFile))) {
            ReadableByteChannel channel = Channels.newChannel(zis);
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                final String fileName = zipEntry.getName();
                final File entryFile = new File(outputDir + File.separator + fileName);
                FileUtils.forceMkdir(entryFile.getParentFile());
                try (final FileOutputStream fos = new FileOutputStream(entryFile)) {
                    FileChannel fileChannel = fos.getChannel();
                    fileChannel.transferFrom(channel, 0, zipEntry.getSize());
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

}
