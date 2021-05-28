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

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alipay.sofa.jraft.util.CRC64;

import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.Checksum;

/**
 * Snapshot processing of persistent service data for accelerated Raft protocol recovery and data synchronization.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author xiweng.yy
 */
public class NamingSnapshotOperation extends AbstractSnapshotOperation {
    
    private static final String NAMING_SNAPSHOT_SAVE = NamingSnapshotOperation.class.getSimpleName() + ".SAVE";
    
    private static final String NAMING_SNAPSHOT_LOAD = NamingSnapshotOperation.class.getSimpleName() + ".LOAD";
    
    private final String snapshotDir = "naming_persistent";
    
    private final String snapshotArchive = "naming_persistent.zip";
    
    private final KvStorage storage;
    
    public NamingSnapshotOperation(KvStorage storage, ReentrantReadWriteLock lock) {
        super(lock);
        this.storage = storage;
    }
    
    @Override
    protected boolean writeSnapshot(Writer writer) throws Exception {
        final String writePath = writer.getPath();
        final String parentPath = Paths.get(writePath, snapshotDir).toString();
        DiskUtils.deleteDirectory(parentPath);
        DiskUtils.forceMkdir(parentPath);
        
        storage.doSnapshot(parentPath);
        final String outputFile = Paths.get(writePath, snapshotArchive).toString();
        final Checksum checksum = new CRC64();
        DiskUtils.compress(writePath, snapshotDir, outputFile, checksum);
        DiskUtils.deleteDirectory(parentPath);
        
        final LocalFileMeta meta = new LocalFileMeta();
        meta.append(CHECK_SUM_KEY, Long.toHexString(checksum.getValue()));
        return writer.addFile(snapshotArchive, meta);
    }
    
    @Override
    protected boolean readSnapshot(Reader reader) throws Exception {
        final String readerPath = reader.getPath();
        final String sourceFile = Paths.get(readerPath, snapshotArchive).toString();
        final Checksum checksum = new CRC64();
        DiskUtils.decompress(sourceFile, readerPath, checksum);
        LocalFileMeta fileMeta = reader.getFileMeta(snapshotArchive);
        if (fileMeta.getFileMeta().containsKey(CHECK_SUM_KEY)) {
            if (!Objects.equals(Long.toHexString(checksum.getValue()), fileMeta.get(CHECK_SUM_KEY))) {
                throw new IllegalArgumentException("Snapshot checksum failed");
            }
        }
        final String loadPath = Paths.get(readerPath, snapshotDir).toString();
        storage.snapshotLoad(loadPath);
        Loggers.RAFT.info("snapshot load from : {}", loadPath);
        DiskUtils.deleteDirectory(loadPath);
        return true;
    }
    
    @Override
    protected String getSnapshotSaveTag() {
        return NAMING_SNAPSHOT_SAVE;
    }
    
    @Override
    protected String getSnapshotLoadTag() {
        return NAMING_SNAPSHOT_LOAD;
    }
}
