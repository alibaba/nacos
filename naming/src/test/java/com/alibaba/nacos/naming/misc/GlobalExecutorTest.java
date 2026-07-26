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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExecutorTest {
    
    @BeforeAll
    static void setUpBeforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testConstructorAndRegisterServerStatusUpdater() throws Exception {
        assertNotNull(new GlobalExecutor());
        CountDownLatch latch = new CountDownLatch(1);
        
        GlobalExecutor.registerServerStatusUpdater(stoppingRunnable(latch));
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
    
    @Test
    void testExecuteAndInvokeTasks() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        
        GlobalExecutor.executeMysqlCheckTask(latch::countDown);
        GlobalExecutor.executeTcpSuperSense(latch::countDown);
        List<Future<String>> futures = GlobalExecutor.invokeAllTcpSuperSenseTask(
            Collections.singletonList(() -> "ok"));
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("ok", futures.get(0).get());
    }
    
    @Test
    void testScheduleTasks() throws Exception {
        CountDownLatch latch = new CountDownLatch(4);
        
        GlobalExecutor.scheduleTcpSuperSenseTask(latch::countDown, 1, TimeUnit.MILLISECONDS);
        GlobalExecutor.scheduleRetransmitter(latch::countDown, 1, TimeUnit.MILLISECONDS);
        GlobalExecutor.schedulePerformanceLogger(stoppingRunnable(latch), 0, 1, TimeUnit.DAYS);
        GlobalExecutor.scheduleExpiredClientCleaner(stoppingRunnable(latch), 0, 1,
            TimeUnit.DAYS);
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
    
    private Runnable stoppingRunnable(CountDownLatch latch) {
        return () -> {
            latch.countDown();
            throw new IllegalStateException("stop scheduled task");
        };
    }
}
