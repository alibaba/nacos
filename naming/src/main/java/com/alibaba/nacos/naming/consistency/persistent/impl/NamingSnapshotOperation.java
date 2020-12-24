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
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.core.utils.TimerContext;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alipay.sofa.jraft.util.CRC64;

import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.zip.Checksum;

/**
 * Snapshot processing of persistent service data for accelerated Raft protocol recovery and data synchronization.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NamingSnapshotOperation implements SnapshotOperation {
    
    private static final String NAMING_SNAPSHOT_SAVE = NamingSnapshotOperation.class.getSimpleName() + ".SAVE";
    
    private static final String NAMING_SNAPSHOT_LOAD = NamingSnapshotOperation.class.getSimpleName() + ".LOAD";
    
    private final String snapshotDir = "naming_persistent";
    
    private final String snapshotArchive = "naming_persistent.zip";
    
    private final String checkSumKey = "checkSum";
    
    private final KvStorage storage;
    
    private final ReentrantReadWriteLock.WriteLock writeLock;
    
    public NamingSnapshotOperation(KvStorage storage, ReentrantReadWriteLock lock) {
        this.storage = storage;
        this.writeLock = lock.writeLock();
    }
    
    @Override
    public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
        RaftExecutor.doSnapshot(() -> {
            TimerContext.start(NAMING_SNAPSHOT_SAVE);
            
            final Lock lock = writeLock;
            lock.lock();
            try {
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
                meta.append(checkSumKey, Long.toHexString(checksum.getValue()));
                
                callFinally.accept(writer.addFile(snapshotArchive, meta), null);
            } catch (Throwable t) {
                Loggers.RAFT.error("Fail to compress snapshot, path={}, file list={}, {}.", writer.getPath(),
                        writer.listFiles(), t);
                callFinally.accept(false, t);
            } finally {
                lock.unlock();
                TimerContext.end(NAMING_SNAPSHOT_SAVE, Loggers.RAFT);
            }
        });
    }
    
    @Override
    public boolean onSnapshotLoad(Reader reader) {
        final String readerPath = reader.getPath();
        final String sourceFile = Paths.get(readerPath, snapshotArchive).toString();
        
        TimerContext.start(NAMING_SNAPSHOT_LOAD);
        final Lock lock = writeLock;
        lock.lock();
        try {
            final Checksum checksum = new CRC64();
            DiskUtils.decompress(sourceFile, readerPath, checksum);
            LocalFileMeta fileMeta = reader.getFileMeta(snapshotArchive);
            if (fileMeta.getFileMeta().containsKey(checkSumKey)) {
                if (!Objects.equals(Long.toHexString(checksum.getValue()), fileMeta.get(checkSumKey))) {
                    throw new IllegalArgumentException("Snapshot checksum failed");
                }
            }
            
            final String loadPath = Paths.get(readerPath, snapshotDir).toString();
            storage.snapshotLoad(loadPath);
            Loggers.RAFT.info("snapshot load from : {}", loadPath);
            DiskUtils.deleteDirectory(loadPath);
            return true;
        } catch (final Throwable t) {
            Loggers.RAFT.error("Fail to load snapshot, path={}, file list={}, {}.",
                    Paths.get(readerPath, snapshotDir).toString(), reader.listFiles(), t);
            return false;
        } finally {
            lock.unlock();
            TimerContext.end(NAMING_SNAPSHOT_LOAD, Loggers.RAFT);
        }
    }
}
