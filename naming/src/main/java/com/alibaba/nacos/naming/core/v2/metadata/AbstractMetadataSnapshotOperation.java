/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.naming.consistency.persistent.impl.AbstractSnapshotOperation;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alipay.sofa.jraft.util.CRC64;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.Checksum;

/**
 * Nacos naming snapshot operation for metadata.
 *
 * @author xiweng.yy
 */
public abstract class AbstractMetadataSnapshotOperation extends AbstractSnapshotOperation {
    
    private static final String METADATA_CHILD_NAME = "metadata";
    
    public AbstractMetadataSnapshotOperation(ReentrantReadWriteLock lock) {
        super(lock);
    }
    
    @Override
    protected boolean writeSnapshot(Writer writer) throws IOException {
        final String writePath = writer.getPath();
        final String outputFile = Paths.get(writePath, getSnapshotArchive()).toString();
        final Checksum checksum = new CRC64();
        try (InputStream inputStream = dumpSnapshot()) {
            DiskUtils.compressIntoZipFile(METADATA_CHILD_NAME, inputStream, outputFile, checksum);
        }
        final LocalFileMeta meta = new LocalFileMeta();
        meta.append(CHECK_SUM_KEY, Long.toHexString(checksum.getValue()));
        return writer.addFile(getSnapshotArchive(), meta);
    }
    
    @Override
    protected boolean readSnapshot(Reader reader) throws Exception {
        final String readerPath = reader.getPath();
        final String sourceFile = Paths.get(readerPath, getSnapshotArchive()).toString();
        final Checksum checksum = new CRC64();
        byte[] snapshotBytes = DiskUtils.decompress(sourceFile, checksum);
        LocalFileMeta fileMeta = reader.getFileMeta(getSnapshotArchive());
        if (fileMeta.getFileMeta().containsKey(CHECK_SUM_KEY)) {
            if (!Objects.equals(Long.toHexString(checksum.getValue()), fileMeta.get(CHECK_SUM_KEY))) {
                throw new IllegalArgumentException("Snapshot checksum failed");
            }
        }
        loadSnapshot(snapshotBytes);
        return true;
    }
    
    /**
     * Get snapshot archive file name.
     *
     * @return snapshot archive
     */
    protected abstract String getSnapshotArchive();
    
    /**
     * Dump snapshot as input stream.
     *
     * @return snapshot
     */
    protected abstract InputStream dumpSnapshot();
    
    /**
     * Load snapshot.
     *
     * @param snapshotBytes snapshot bytes
     */
    protected abstract void loadSnapshot(byte[] snapshotBytes);
}
