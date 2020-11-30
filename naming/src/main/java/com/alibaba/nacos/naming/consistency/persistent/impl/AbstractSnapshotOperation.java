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

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.core.utils.TimerContext;
import com.alibaba.nacos.naming.misc.Loggers;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

/**
 * Abstract snapshot operation.
 *
 * @author xiweng.yy
 */
public abstract class AbstractSnapshotOperation implements SnapshotOperation {
    
    protected static final String CHECK_SUM_KEY = "checksum";
    
    private final ReentrantReadWriteLock.WriteLock writeLock;
    
    public AbstractSnapshotOperation(ReentrantReadWriteLock lock) {
        this.writeLock = lock.writeLock();
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
    
    @Override
    public boolean onSnapshotLoad(Reader reader) {
        TimerContext.start(getSnapshotLoadTag());
        final Lock lock = writeLock;
        lock.lock();
        try {
            return readSnapshot(reader);
        } catch (final Throwable t) {
            Loggers.RAFT
                    .error("Fail to load snapshot, path={}, file list={}.", reader.getPath(), reader.listFiles(), t);
            return false;
        } finally {
            lock.unlock();
            TimerContext.end(getSnapshotLoadTag(), Loggers.RAFT);
        }
    }
    
    /**
     * Write snapshot.
     *
     * @param writer snapshot writer
     * @return {@code true} if write snapshot successfully, otherwise {@code false}
     * @throws Exception any exception during writing
     */
    protected abstract boolean writeSnapshot(Writer writer) throws Exception;
    
    /**
     * Read snapshot.
     *
     * @param reader snapshot reader
     * @return {@code true} if read snapshot successfully, otherwise {@code false}
     * @throws Exception any exception during reading
     */
    protected abstract boolean readSnapshot(Reader reader) throws Exception;
    
    /**
     * Get snapshot save tag. It will be used to see time metric time context.
     *
     * @return snapshot save tag
     */
    protected abstract String getSnapshotSaveTag();
    
    /**
     * Get snapshot load tag. It will be used to see time metric time context.
     *
     * @return snapshot load tag
     */
    protected abstract String getSnapshotLoadTag();
}
