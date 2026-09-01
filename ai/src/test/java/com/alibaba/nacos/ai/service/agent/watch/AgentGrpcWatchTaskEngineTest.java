/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.engine.NacosExecuteTaskExecuteEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentGrpcWatchTaskEngineTest {
    
    @Test
    void testDuplicateScheduleIsMergedAndDifferentWatchesCanRun() throws Exception {
        AtomicInteger firstExecutions = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(2);
        AgentGrpcWatchTaskEngine engine = new AgentGrpcWatchTaskEngine(10L, 2, watchKey -> {
            if ("first".equals(watchKey)) {
                firstExecutions.incrementAndGet();
            }
            completed.countDown();
        });
        try {
            engine.schedule("first");
            engine.schedule("first");
            engine.schedule("second");
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            TimeUnit.MILLISECONDS.sleep(80L);
            assertEquals(1, firstExecutions.get());
            assertEquals(0, engine.pendingTaskCount());
        } finally {
            engine.shutdown();
        }
    }
    
    @Test
    void testExecutionExceptionRetriesFromCurrentFact() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);
        AgentGrpcWatchTaskEngine engine = new AgentGrpcWatchTaskEngine(10L, 1, watchKey -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("injected");
            }
            completed.countDown();
        });
        try {
            engine.schedule("retry");
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertEquals(2, attempts.get());
        } finally {
            engine.shutdown();
        }
    }
    
    @Test
    void testRejectedWorkerAdmissionKeepsDelayTaskRecoverable() throws Exception {
        NacosExecuteTaskExecuteEngine executeEngine = mock(NacosExecuteTaskExecuteEngine.class);
        when(executeEngine.tryAddTask(any(), any(AbstractExecuteTask.class)))
            .thenReturn(false, true);
        AgentGrpcWatchTaskEngine engine = new AgentGrpcWatchTaskEngine(5L, watchKey -> {
        }, executeEngine, 2L);
        try {
            engine.schedule("rejected");
            waitUntil(() -> {
                try {
                    verify(executeEngine, atLeast(2)).tryAddTask(any(),
                        any(AbstractExecuteTask.class));
                    return true;
                } catch (AssertionError ignored) {
                    return false;
                }
            });
            assertEquals(0, engine.pendingTaskCount());
        } finally {
            engine.shutdown();
        }
        verify(executeEngine).shutdown();
    }
    
    @Test
    void testShutdownIsIdempotentAndAdmittedTaskDoesNothingAfterShutdown() throws Exception {
        NacosExecuteTaskExecuteEngine executeEngine = mock(NacosExecuteTaskExecuteEngine.class);
        when(executeEngine.tryAddTask(any(), any(AbstractExecuteTask.class))).thenReturn(true);
        AtomicInteger executions = new AtomicInteger();
        AgentGrpcWatchTaskEngine engine = new AgentGrpcWatchTaskEngine(1L,
            watchKey -> executions.incrementAndGet(), executeEngine, 1L);
        ArgumentCaptor<AbstractExecuteTask> taskCaptor =
            ArgumentCaptor.forClass(AbstractExecuteTask.class);
        engine.schedule("admitted");
        verify(executeEngine, timeout(3000L)).tryAddTask(any(), taskCaptor.capture());
        
        engine.shutdown();
        engine.shutdown();
        engine.schedule("closed");
        engine.retry("closed");
        taskCaptor.getValue().run();
        
        assertEquals(0, executions.get());
        assertEquals(0, engine.pendingTaskCount());
    }
    
    @Test
    void testDelayTaskMergeIgnoresDifferentTaskType() throws Exception {
        Class<?> taskClass = Class.forName(
            AgentGrpcWatchTaskEngine.class.getName() + "$WatchDelayTask");
        Constructor<?> constructor = taskClass.getDeclaredConstructor(String.class, long.class);
        constructor.setAccessible(true);
        AbstractDelayTask task =
            (AbstractDelayTask) constructor.newInstance("watch", 100L);
        AbstractDelayTask differentType = new AbstractDelayTask() {
            
            @Override
            public void merge(AbstractDelayTask ignored) {
            }
        };
        
        task.merge(differentType);
        
        assertEquals(100L, task.getTaskInterval());
    }
    
    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertTrue(condition.getAsBoolean());
    }
}
