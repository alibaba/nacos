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

package com.alibaba.nacos.api.remote;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcScheduledExecutorTest {
    
    private static final String NAME = "test.rpc.thread";
    
    Map<String, String> threadNameMap = new ConcurrentHashMap<>();
    
    @Test
    void testRpcScheduledExecutor() throws InterruptedException {
        RpcScheduledExecutor executor = new RpcScheduledExecutor(2, NAME);
        CountDownLatch latch = new CountDownLatch(2);
        executor.submit(new TestRunner(1, latch));
        executor.submit(new TestRunner(2, latch));
        boolean await = latch.await(1, TimeUnit.SECONDS);
        assertTrue(await);
        assertEquals(2, threadNameMap.size());
    }
    
    private class TestRunner implements Runnable {
        
        int id;
        
        CountDownLatch latch;
        
        public TestRunner(int id, CountDownLatch latch) {
            this.id = id;
            this.latch = latch;
        }
        
        @Override
        public void run() {
            threadNameMap.put(String.valueOf(id), Thread.currentThread().getName());
            latch.countDown();
        }
    }
}