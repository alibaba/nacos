/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.task.engine;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.NacosTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TaskExecuteWorker.
 *
 * @author nacos
 */
class TaskExecuteWorkerTest {
    
    private TaskExecuteWorker worker;
    
    @BeforeEach
    void setUp() {
        worker = new TaskExecuteWorker("test-worker", 0, 1);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        if (worker != null) {
            worker.shutdown();
        }
    }
    
    @Test
    @DisplayName("getName should return correct worker name")
    void testGetName() {
        assertEquals("test-worker_0%1", worker.getName());
    }
    
    @Test
    @DisplayName("pendingTaskCount should return queue size")
    void testPendingTaskCount() {
        assertEquals(0, worker.pendingTaskCount());
    }
    
    @Test
    @DisplayName("status should return worker status string")
    void testStatus() {
        String status = worker.status();
        assertTrue(status.contains("test-worker"));
        assertTrue(status.contains("pending tasks"));
    }
    
    @Test
    @DisplayName("process with AbstractExecuteTask should add task to queue")
    void testProcessWithAbstractExecuteTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger(0);
        
        AbstractExecuteTask task = new AbstractExecuteTask() {
            
            @Override
            public void run() {
                counter.incrementAndGet();
                latch.countDown();
            }
        };
        
        worker.process(task);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, counter.get());
    }
    
    @Test
    @DisplayName("tryProcess should reject without blocking when queue is full or closed")
    void testTryProcessAdmission() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TaskExecuteWorker boundedWorker =
            new TaskExecuteWorker("bounded", 0, 1, null, 1);
        try {
            AbstractExecuteTask blocking = new AbstractExecuteTask() {
                
                @Override
                public void run() {
                    executing.countDown();
                    try {
                        release.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            assertTrue(boundedWorker.tryProcess(blocking));
            assertTrue(executing.await(2, TimeUnit.SECONDS));
            assertTrue(boundedWorker.tryProcess(new NoopExecuteTask()));
            assertFalse(boundedWorker.tryProcess(new NoopExecuteTask()));
            release.countDown();
            boundedWorker.shutdown();
            assertFalse(boundedWorker.tryProcess(new NoopExecuteTask()));
        } finally {
            release.countDown();
            boundedWorker.shutdown();
        }
    }
    
    @Test
    @DisplayName("tryProcess should reject tasks unsupported by execute workers")
    void testTryProcessRejectsUnsupportedTask() {
        NacosTask unsupported = () -> true;
        assertFalse(worker.tryProcess(unsupported));
    }
    
    @Test
    @DisplayName("shutdown should clear queue and set closed flag")
    void testShutdown() throws NacosException {
        worker.shutdown();
        assertEquals(0, worker.pendingTaskCount());
    }
    
    @Test
    @DisplayName("worker with custom logger should use provided logger")
    void testWorkerWithCustomLogger() throws NacosException {
        Logger customLogger = LoggerFactory.getLogger("custom-worker-logger");
        TaskExecuteWorker customWorker = new TaskExecuteWorker("custom", 0, 1, customLogger);
        assertEquals("custom_0%1", customWorker.getName());
        customWorker.shutdown();
    }
    
    private static class NoopExecuteTask extends AbstractExecuteTask {
        
        @Override
        public void run() {
        }
    }
}
