/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.persistence;

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.sys.utils.TimerContext;
import com.alipay.sofa.jraft.util.CRC64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.zip.Checksum;

/**
 * nacosLock snapshot handler.
 *
 * @author 985492783@qq.com
 * @date 2023/9/7 20:42
 */
public class NacosLockSnapshotOperation implements SnapshotOperation {
    
    protected static final String CHECK_SUM_KEY = "checksum";
    
    private final ReentrantReadWriteLock.WriteLock writeLock;
    
    private final LockManager lockManager;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosLockSnapshotOperation.class);
    
    private static final String LOCK_SNAPSHOT_SAVE = NacosLockSnapshotOperation.class.getSimpleName() + ".SAVE";
    
    private static final String LOCK_SNAPSHOT_LOAD = NacosLockSnapshotOperation.class.getSimpleName() + ".LOAD";
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private static final String SNAPSHOT_ARCHIVE = "nacos_lock.zip";
    
    public NacosLockSnapshotOperation(LockManager lockManager, ReentrantReadWriteLock.WriteLock writeLock) {
        this.lockManager = lockManager;
        this.writeLock = writeLock;
    }
    
    @Override
    public void onSnapshotSave(Writer writer, BiConsumer<Boolean, Throwable> callFinally) {
        RaftExecutor.doSnapshot(() -> {
            TimerContext.start(getSnapshotSaveTag());
            final Lock lock = writeLock;
            lock.lock();
            try {
                callFinally.accept(writeSnapshot(writer), null);
            } catch (Throwable t) {
                Loggers.RAFT.error("Fail to compress snapshot, path={}, file list={}.", writer.getPath(),
                        writer.listFiles(), t);
                callFinally.accept(false, t);
            } finally {
                lock.unlock();
                TimerContext.end(getSnapshotSaveTag(), Loggers.RAFT);
            }
        });
    }
    
    private boolean writeSnapshot(Writer writer) throws IOException {
        final String writePath = writer.getPath();
        final String outputFile = Paths.get(writePath, SNAPSHOT_ARCHIVE).toString();
        final Checksum checksum = new CRC64();
        try (InputStream inputStream = dumpSnapshot()) {
            DiskUtils.compressIntoZipFile("lock", inputStream, outputFile, checksum);
        }
        final LocalFileMeta meta = new LocalFileMeta();
        meta.append(CHECK_SUM_KEY, Long.toHexString(checksum.getValue()));
        return writer.addFile(SNAPSHOT_ARCHIVE, meta);
    }
    
    private InputStream dumpSnapshot() {
        ConcurrentHashMap<LockKey, AtomicLockService> lockMap = lockManager.showLocks();
        return new ByteArrayInputStream(serializer.serialize(lockMap));
    }
    
    @Override
    public boolean onSnapshotLoad(Reader reader) {
        TimerContext.start(getSnapshotLoadTag());
        final Lock lock = writeLock;
        lock.lock();
        try {
            return readSnapshot(reader);
        } catch (final Throwable t) {
            Loggers.RAFT.error("Fail to load snapshot, path={}, file list={}.", reader.getPath(), reader.listFiles(),
                    t);
            return false;
        } finally {
            lock.unlock();
            TimerContext.end(getSnapshotLoadTag(), Loggers.RAFT);
        }
    }
    
    private boolean readSnapshot(Reader reader) throws Exception {
        final String readerPath = reader.getPath();
        Loggers.RAFT.info("snapshot start to load from : {}", readerPath);
        final String sourceFile = Paths.get(readerPath, SNAPSHOT_ARCHIVE).toString();
        final Checksum checksum = new CRC64();
        byte[] snapshotBytes = DiskUtils.decompress(sourceFile, checksum);
        LocalFileMeta fileMeta = reader.getFileMeta(SNAPSHOT_ARCHIVE);
        if (fileMeta.getFileMeta().containsKey(CHECK_SUM_KEY) && !Objects.equals(Long.toHexString(checksum.getValue()),
                fileMeta.get(CHECK_SUM_KEY))) {
            throw new IllegalArgumentException("Snapshot checksum failed");
        }
        loadSnapshot(snapshotBytes);
        Loggers.RAFT.info("snapshot success to load from : {}", readerPath);
        return true;
    }
    
    private void loadSnapshot(byte[] snapshotBytes) {
        ConcurrentHashMap<LockKey, AtomicLockService> newData = serializer.deserialize(snapshotBytes);
        ConcurrentHashMap<LockKey, AtomicLockService> lockMap = lockManager.showLocks();
        //loadSnapshot
        lockMap.putAll(newData);
    }
    
    protected String getSnapshotSaveTag() {
        return LOCK_SNAPSHOT_SAVE;
    }
    
    protected String getSnapshotLoadTag() {
        return LOCK_SNAPSHOT_LOAD;
    }
}
