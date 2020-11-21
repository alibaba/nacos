/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.utils.RaftExecutor;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NamingSnapshotOperationTest {
    
    static {
        RaftExecutor.init(new RaftConfig());
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Mock
    private KvStorage storage;
    
    private final String tmpDir = Paths.get(EnvUtil.getNacosTmpDir(), "rocks_test").toString();
    
    private final String snapshotDir = Paths.get(EnvUtil.getNacosTmpDir(), "rocks_snapshot_test").toString();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    @Before
    public void init() {
        DiskUtils.deleteQuietly(Paths.get(EnvUtil.getNacosTmpDir()));
    }
    
    @After
    public void after() {
        storage.shutdown();
        DiskUtils.deleteQuietly(Paths.get(EnvUtil.getNacosTmpDir()));
    }
    
    @Test
    public void testNamingSnapshot() throws InterruptedException {
        AtomicBoolean result = new AtomicBoolean(false);
        NamingSnapshotOperation operation = new NamingSnapshotOperation(storage, lock);
        final Writer writer = new Writer(snapshotDir);
        final CountDownLatch latch = new CountDownLatch(1);
        
        operation.onSnapshotSave(writer, (isOk, throwable) -> {
            result.set(isOk && throwable == null);
            latch.countDown();
        });
        latch.await(10, TimeUnit.SECONDS);
        Assert.assertTrue(result.get());
        
        final Reader reader = new Reader(snapshotDir, writer.listFiles());
        boolean res = operation.onSnapshotLoad(reader);
        Assert.assertTrue(res);
    }
    
}
