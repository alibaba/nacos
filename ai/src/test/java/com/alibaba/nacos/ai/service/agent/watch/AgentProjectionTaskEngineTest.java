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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.AbstractExecuteTask;
import com.alibaba.nacos.common.task.engine.NacosExecuteTaskExecuteEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentProjectionTaskEngineTest {
    
    @Test
    void testDelayWindowCoalescesAtoBtoAIntoCurrentFact() throws Exception {
        double coalescedBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_COALESCE,
            AgentWatchMetrics.Result.SUCCESS);
        AgentProjectionKey key = AgentProjectionTestFixtures.key("coalesced");
        AtomicReference<String> current = new AtomicReference<String>("A");
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<String> observed = new AtomicReference<String>();
        AtomicReference<Set<AgentProjectionChangeReason>> observedReasons =
            new AtomicReference<Set<AgentProjectionChangeReason>>();
        CountDownLatch completed = new CountDownLatch(1);
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(60L, 20L, 1,
            (ignored, reasons) -> {
                executions.incrementAndGet();
                observed.set(current.get());
                observedReasons.set(reasons);
                completed.countDown();
                return true;
            });
        try {
            engine.markDirty(key, AgentProjectionChangeReason.INITIAL);
            current.set("B");
            engine.markDirty(key, AgentProjectionChangeReason.DEFINITION);
            current.set("A");
            engine.markDirty(key, AgentProjectionChangeReason.RUNTIME);
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            TimeUnit.MILLISECONDS.sleep(120L);
            assertEquals(1, executions.get());
            assertEquals("A", observed.get());
            assertEquals(EnumSet.of(AgentProjectionChangeReason.INITIAL,
                AgentProjectionChangeReason.DEFINITION,
                AgentProjectionChangeReason.RUNTIME), observedReasons.get());
            assertEquals(coalescedBefore + 2D, AgentWatchMetrics.eventCount(
                AgentWatchMetrics.Event.PROJECTION_COALESCE,
                AgentWatchMetrics.Result.SUCCESS));
        } finally {
            engine.shutdown();
        }
    }
    
    @Test
    void testChangeArrivingDuringExecuteRunsSeriallyAfterCurrentExecute() throws Exception {
        AgentProjectionKey key = AgentProjectionTestFixtures.key("serial");
        AtomicReference<String> current = new AtomicReference<String>("B");
        List<String> observations = Collections.synchronizedList(new ArrayList<String>());
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(5L, 10L, 1,
            (ignored, reasons) -> {
                int concurrent = active.incrementAndGet();
                maxActive.accumulateAndGet(concurrent, Math::max);
                String value = current.get();
                observations.add(value);
                if (observations.size() == 1) {
                    firstStarted.countDown();
                    await(releaseFirst);
                }
                active.decrementAndGet();
                completed.countDown();
                return true;
            });
        try {
            engine.markDirty(key, AgentProjectionChangeReason.DEFINITION);
            assertTrue(firstStarted.await(3, TimeUnit.SECONDS));
            current.set("A");
            engine.markDirty(key, AgentProjectionChangeReason.RUNTIME);
            releaseFirst.countDown();
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertEquals(java.util.Arrays.asList("B", "A"), observations);
            assertEquals(1, maxActive.get());
        } finally {
            releaseFirst.countDown();
            engine.shutdown();
        }
    }
    
    @Test
    void testDifferentStripesCanExecuteInParallel() throws Exception {
        AgentProjectionKey first = AgentProjectionTestFixtures.key("parallel-0");
        AgentProjectionKey second = keyOnDifferentStripe(first, 2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(5L, 10L, 2,
            (ignored, reasons) -> {
                bothStarted.countDown();
                await(release);
                completed.countDown();
                return true;
            });
        try {
            engine.markDirty(first, AgentProjectionChangeReason.RUNTIME);
            engine.markDirty(second, AgentProjectionChangeReason.RUNTIME);
            assertTrue(bothStarted.await(3, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(completed.await(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            engine.shutdown();
        }
    }
    
    @Test
    void testExecutionExceptionRetriesFromCurrentFact() throws Exception {
        double retriesBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RETRY,
            AgentWatchMetrics.Result.SCHEDULED);
        AgentProjectionKey key = AgentProjectionTestFixtures.key("retry");
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Set<AgentProjectionChangeReason>> retryReasons =
            new AtomicReference<Set<AgentProjectionChangeReason>>();
        CountDownLatch completed = new CountDownLatch(1);
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(5L, 10L, 1,
            (ignored, reasons) -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("injected");
                }
                retryReasons.set(reasons);
                completed.countDown();
                return true;
            });
        try {
            engine.markDirty(key, AgentProjectionChangeReason.DEFINITION);
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertEquals(2, attempts.get());
            assertEquals(Collections.singleton(AgentProjectionChangeReason.RETRY),
                retryReasons.get());
            assertEquals(retriesBefore + 1D, AgentWatchMetrics.eventCount(
                AgentWatchMetrics.Event.PROJECTION_RETRY,
                AgentWatchMetrics.Result.SCHEDULED));
        } finally {
            engine.shutdown();
        }
    }
    
    @Test
    void testRejectedWorkerAdmissionKeepsDelayTaskRecoverable() throws Exception {
        NacosExecuteTaskExecuteEngine executeEngine =
            mock(NacosExecuteTaskExecuteEngine.class);
        when(executeEngine.tryAddTask(any(), any(AbstractExecuteTask.class)))
            .thenReturn(false, true);
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(5L, 10L,
            (ignored, reasons) -> true, executeEngine, 2L);
        try {
            engine.markDirty(AgentProjectionTestFixtures.key("rejected"),
                AgentProjectionChangeReason.RUNTIME);
            waitUntil(() -> {
                try {
                    verify(executeEngine, atLeast(2)).tryAddTask(any(),
                        any(AbstractExecuteTask.class));
                    return true;
                } catch (AssertionError ignored) {
                    return false;
                }
            });
            assertEquals(0, engine.pendingDelayTaskCount());
        } finally {
            engine.shutdown();
        }
        verify(executeEngine).shutdown();
    }
    
    @Test
    void testShutdownIsIdempotentAndIgnoresNewDirtyState() throws NacosException {
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(5L, 10L, 1,
            (ignored, reasons) -> true);
        engine.shutdown();
        engine.shutdown();
        engine.markDirty(AgentProjectionTestFixtures.key("closed"),
            AgentProjectionChangeReason.RUNTIME);
        engine.retry(AgentProjectionTestFixtures.key("closed"),
            AgentProjectionChangeReason.RETRY);
        assertEquals(0, engine.pendingDelayTaskCount());
    }
    
    @Test
    void testAdmittedExecuteTaskDoesNothingAfterShutdown() throws Exception {
        NacosExecuteTaskExecuteEngine executeEngine =
            mock(NacosExecuteTaskExecuteEngine.class);
        when(executeEngine.tryAddTask(any(), any(AbstractExecuteTask.class))).thenReturn(true);
        AtomicInteger executions = new AtomicInteger();
        AgentProjectionTaskEngine engine = new AgentProjectionTaskEngine(1L, 10L,
            (ignored, reasons) -> {
                executions.incrementAndGet();
                return true;
            }, executeEngine, 1L);
        ArgumentCaptor<AbstractExecuteTask> taskCaptor =
            ArgumentCaptor.forClass(AbstractExecuteTask.class);
        engine.markDirty(AgentProjectionTestFixtures.key("shutdown-admitted"),
            AgentProjectionChangeReason.RUNTIME);
        verify(executeEngine, timeout(3000L)).tryAddTask(any(), taskCaptor.capture());
        
        engine.shutdown();
        taskCaptor.getValue().run();
        
        assertEquals(0, executions.get());
    }
    
    @Test
    void testDelayTaskIgnoresDifferentTaskType() throws Exception {
        Class<?> taskClass = Class.forName(
            AgentProjectionTaskEngine.class.getName() + "$ProjectionDelayTask");
        Constructor<?> constructor = taskClass.getDeclaredConstructor(AgentProjectionKey.class,
            AgentProjectionChangeReason.class, long.class);
        constructor.setAccessible(true);
        AbstractDelayTask task = (AbstractDelayTask) constructor.newInstance(
            AgentProjectionTestFixtures.key("different-type"),
            AgentProjectionChangeReason.RUNTIME, 100L);
        AbstractDelayTask differentType = new AbstractDelayTask() {
            
            @Override
            public void merge(AbstractDelayTask ignored) {
            }
        };
        
        task.merge(differentType);
        
        assertEquals(100L, task.getTaskInterval());
    }
    
    private AgentProjectionKey keyOnDifferentStripe(AgentProjectionKey first, int stripes) {
        int firstStripe = stripe(first, stripes);
        for (int i = 1; i < 100; i++) {
            AgentProjectionKey candidate = AgentProjectionTestFixtures.key("parallel-" + i);
            if (stripe(candidate, stripes) != firstStripe) {
                return candidate;
            }
        }
        throw new AssertionError("Unable to find a key on a different stripe");
    }
    
    private int stripe(AgentProjectionKey key, int stripes) {
        return (key.hashCode() & Integer.MAX_VALUE) % stripes;
    }
    
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
    
    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertTrue(condition.getAsBoolean());
    }
}
