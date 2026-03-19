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

package com.alibaba.nacos.core.distributed;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProtocolExecutor} unit test.
 */
class ProtocolExecutorTest {
    
    @Test
    void cpMemberChangeSubmitsRunnableRunnableExecuted() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);
        
        ProtocolExecutor.cpMemberChange(() -> {
            executed.set(true);
            latch.countDown();
        });
        
        assertTrue(latch.await(2, TimeUnit.SECONDS), "cpMemberChange should execute runnable");
        assertTrue(executed.get());
    }
    
    @Test
    void apMemberChangeSubmitsRunnableRunnableExecuted() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);
        
        ProtocolExecutor.apMemberChange(() -> {
            executed.set(true);
            latch.countDown();
        });
        
        assertTrue(latch.await(2, TimeUnit.SECONDS), "apMemberChange should execute runnable");
        assertTrue(executed.get());
    }
}
