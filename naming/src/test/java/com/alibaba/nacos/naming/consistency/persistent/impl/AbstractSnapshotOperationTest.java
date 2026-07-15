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

import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractSnapshotOperationTest {
    
    @BeforeEach
    void setUp() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_CORE_THREAD_NUM, "1");
        config.setVal(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM, "1");
        RaftExecutor.init(config);
    }
    
    @Test
    void testOnSnapshotSaveSuccess(@TempDir Path snapshotPath) throws Exception {
        TestSnapshotOperation operation = new TestSnapshotOperation(true, null);
        AtomicReference<Boolean> successRef = new AtomicReference<>();
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        operation.onSnapshotSave(new Writer(snapshotPath.toString()), (success, throwable) -> {
            successRef.set(success);
            throwableRef.set(throwable);
            latch.countDown();
        });
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(successRef.get());
        assertNull(throwableRef.get());
        assertTrue(operation.writeCalled);
    }
    
    @Test
    void testOnSnapshotSaveFailure(@TempDir Path snapshotPath) throws Exception {
        RuntimeException expected = new RuntimeException("test");
        TestSnapshotOperation operation = new TestSnapshotOperation(false, expected);
        AtomicReference<Boolean> successRef = new AtomicReference<>();
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        operation.onSnapshotSave(new Writer(snapshotPath.toString()), (success, throwable) -> {
            successRef.set(success);
            throwableRef.set(throwable);
            latch.countDown();
        });
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(successRef.get());
        assertSame(expected, throwableRef.get());
        assertTrue(operation.writeCalled);
    }
    
    private static class TestSnapshotOperation extends AbstractSnapshotOperation {
        
        private final boolean writeResult;
        
        private final RuntimeException writeException;
        
        private boolean writeCalled;
        
        TestSnapshotOperation(boolean writeResult, RuntimeException writeException) {
            super(new ReentrantReadWriteLock());
            this.writeResult = writeResult;
            this.writeException = writeException;
        }
        
        @Override
        protected boolean writeSnapshot(Writer writer) {
            writeCalled = true;
            if (null != writeException) {
                throw writeException;
            }
            return writeResult;
        }
        
        @Override
        protected boolean readSnapshot(Reader reader) {
            return true;
        }
        
        @Override
        protected String getSnapshotSaveTag() {
            return "test.save";
        }
        
        @Override
        protected String getSnapshotLoadTag() {
            return "test.load";
        }
    }
}
