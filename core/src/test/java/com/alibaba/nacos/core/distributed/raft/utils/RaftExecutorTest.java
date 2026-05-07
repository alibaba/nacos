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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftSysConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaftExecutorTest {
    
    @BeforeEach
    void setUp() {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_CORE_THREAD_NUM, "4");
        config.setVal(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM, "2");
        RaftExecutor.init(config);
    }
    
    @Test
    void testInitWithSingleCoreThreadUsesSnapshotExecutor() throws Exception {
        RaftConfig config = new RaftConfig();
        config.setVal(RaftSysConstants.RAFT_CORE_THREAD_NUM, "1");
        config.setVal(RaftSysConstants.RAFT_CLI_SERVICE_THREAD_NUM, "1");
        RaftExecutor.init(config);
        ExecutorService core = RaftExecutor.getRaftCoreExecutor();
        assertNotNull(core);
        CountDownLatch latch = new CountDownLatch(1);
        RaftExecutor.doSnapshot(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
    
    @Test
    void testGetRaftCoreExecutor() throws Exception {
        ExecutorService executor = RaftExecutor.getRaftCoreExecutor();
        assertNotNull(executor);
        assertTrue(executor.submit(() -> {
        }).get(2, TimeUnit.SECONDS) == null);
    }
    
    @Test
    void testGetRaftCliServiceExecutor() {
        ExecutorService executor = RaftExecutor.getRaftCliServiceExecutor();
        assertNotNull(executor);
    }
    
    @Test
    void testGetRaftCommonExecutor() {
        ScheduledExecutorService executor = RaftExecutor.getRaftCommonExecutor();
        assertNotNull(executor);
    }
    
    @Test
    void testExecuteByCommon() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RaftExecutor.executeByCommon(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
    
    @Test
    void testScheduleByCommon() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RaftExecutor.scheduleByCommon(latch::countDown, 10);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
    
    @Test
    void testDoSnapshot() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RaftExecutor.doSnapshot(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
    
    @Test
    void testScheduleRaftMemberRefreshJob() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        RaftExecutor.scheduleRaftMemberRefreshJob(latch::countDown, 20, 50, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }
}
