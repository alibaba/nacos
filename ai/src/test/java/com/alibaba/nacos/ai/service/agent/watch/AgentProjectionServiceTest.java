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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentProjectionServiceTest {
    
    private AgentProjectionService service;
    
    @AfterEach
    void tearDown() throws NacosException {
        if (service != null) {
            service.shutdown();
        }
    }
    
    @Test
    void testRetainSharesProjectionAcrossOwnersAndReleaseCleansState() throws Exception {
        AgentProjectionProjector projector = mock(AgentProjectionProjector.class);
        AtomicInteger projects = new AtomicInteger();
        when(projector.project(any())).thenAnswer(invocation -> {
            AgentProjectionKey key = invocation.getArgument(0);
            projects.incrementAndGet();
            return AgentProjectionTestFixtures.available("fp", 1L,
                AgentProjectionTestFixtures.service(key.getAgentName(), "a2a"));
        });
        service = newService(projector, 5L, 10L, 0L, 10, 1);
        
        AgentProjectionKey first = service.retain(
            AgentProjectionTestFixtures.request("shared"));
        AgentProjectionKey second = service.retain(
            AgentProjectionTestFixtures.request("shared"));
        waitUntil(() -> service.getState(first).isPresent());
        
        assertEquals(first, second);
        assertEquals(1, projects.get());
        assertEquals(2, service.getReferenceCount(first));
        assertEquals(1, service.size());
        assertFalse(service.release(first));
        assertEquals(1, service.getReferenceCount(first));
        assertTrue(service.release(second));
        assertEquals(0, service.size());
        assertFalse(service.getState(first).isPresent());
    }
    
    @Test
    void testDefinitionAndRuntimeChangesRefreshOnlyDependentProjections() throws Exception {
        AgentProjectionProjector projector = mock(AgentProjectionProjector.class);
        AtomicInteger projects = new AtomicInteger();
        when(projector.project(any())).thenAnswer(invocation -> {
            AgentProjectionKey key = invocation.getArgument(0);
            projects.incrementAndGet();
            return AgentProjectionTestFixtures.available("fp-" + projects.get(),
                projects.get(), AgentProjectionTestFixtures.service(key.getAgentName(), "a2a"));
        });
        service = newService(projector, 5L, 10L, 0L, 10, 2);
        AgentProjectionKey alpha = service.retain(AgentProjectionTestFixtures.request("alpha"));
        service.retain(AgentProjectionTestFixtures.request("beta"));
        waitUntil(() -> projects.get() == 2);
        
        service.onAgentChanged("public", "missing");
        service.onRuntimeServiceChanged(AgentProjectionTestFixtures.service("missing", "a2a"));
        TimeUnit.MILLISECONDS.sleep(40L);
        assertEquals(2, projects.get());
        
        service.onAgentChanged("public", "alpha");
        waitUntil(() -> projects.get() == 3);
        service.onRuntimeServiceChanged(AgentProjectionTestFixtures.service("alpha", "a2a"));
        waitUntil(() -> service.getState(alpha)
            .map(state -> "fp-4".equals(state.getFingerprint())).orElse(false));
        assertEquals(4, projects.get());
        assertEquals("fp-4", service.getState(alpha).get().getFingerprint());
    }
    
    @Test
    void testTransientSynchronousRefreshSchedulesCurrentFactRetry() throws Exception {
        AgentProjectionProjector projector = mock(AgentProjectionProjector.class);
        AgentProjectionState transientFailure = AgentProjectionState.failure(
            AgentProjectionStatus.TRANSIENT_FAILURE, NacosException.SERVER_ERROR,
            "temporary", 1L);
        AgentProjectionState recovered = AgentProjectionTestFixtures.available("recovered", 2L);
        when(projector.project(any())).thenReturn(transientFailure, recovered);
        service = newService(projector, 10000L, 5L, 0L, 10, 1);
        AgentProjectionKey key = service.retain(AgentProjectionTestFixtures.request("retry"));
        
        AgentProjectionState immediate = service.refreshNow(key);
        assertEquals(AgentProjectionStatus.TRANSIENT_FAILURE, immediate.getStatus());
        waitUntil(() -> service.getState(key).map(
            state -> "recovered".equals(state.getFingerprint())).orElse(false));
        assertEquals(AgentProjectionStatus.AVAILABLE, service.getState(key).get().getStatus());
    }
    
    @Test
    void testProjectionFailureAndNotFoundRecordCurrentFactOutcomes() throws Exception {
        double failures = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.FAILED);
        AgentProjectionProjector failing = key -> {
            throw new IllegalStateException("projection failure");
        };
        service = newService(failing, 10000L, 10L, 0L, 10, 1);
        AgentProjectionKey failedKey = service.retain(
            AgentProjectionTestFixtures.request("failed"));
        assertThrows(IllegalStateException.class, () -> service.refreshNow(failedKey));
        assertEquals(failures + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.FAILED));
        service.shutdown();
        
        double notFound = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.NOT_FOUND);
        service = newService(key -> AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, NacosException.NOT_FOUND, "missing", 1L),
            10000L, 10L, 0L, 10, 1);
        AgentProjectionKey missingKey = service.retain(
            AgentProjectionTestFixtures.request("missing"));
        AgentProjectionState result = service.refreshNow(missingKey);
        assertEquals(AgentProjectionStatus.NOT_FOUND, result.getStatus());
        assertEquals(notFound + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.NOT_FOUND));
    }
    
    @Test
    void testExplicitRevalidationOnlySchedulesActiveProjection() throws Exception {
        AtomicInteger projects = new AtomicInteger();
        service = newService(key -> AgentProjectionTestFixtures.available(
            "fp-" + projects.incrementAndGet(), projects.get()), 5L, 5L, 0L, 10, 1);
        AgentProjectionKey key = service.retain(
            AgentProjectionTestFixtures.request("revalidate"));
        waitUntil(() -> service.getState(key)
            .map(state -> "fp-1".equals(state.getFingerprint())).orElse(false));
        
        service.revalidate(key);
        waitUntil(() -> projects.get() == 2);
        assertEquals("fp-2", service.getState(key).get().getFingerprint());
        
        assertTrue(service.release(key));
        service.revalidate(key);
        TimeUnit.MILLISECONDS.sleep(40L);
        assertEquals(2, projects.get());
        service.shutdown();
        service.revalidate(key);
    }
    
    @Test
    void testListenerFailureIsIsolatedAndRemovalStopsDelivery() throws Exception {
        AgentProjectionProjector projector =
            key -> AgentProjectionTestFixtures.available("listener", 1L);
        service = newService(projector, 5L, 10L, 0L, 10, 1);
        AtomicInteger healthyCalls = new AtomicInteger();
        AgentProjectionUpdateListener failing = update -> {
            throw new IllegalStateException("listener failure");
        };
        AgentProjectionUpdateListener healthy = update -> healthyCalls.incrementAndGet();
        service.addUpdateListener(failing);
        service.addUpdateListener(healthy);
        AgentProjectionKey key = service.retain(AgentProjectionTestFixtures.request("listener"));
        waitUntil(() -> healthyCalls.get() == 1);
        service.removeUpdateListener(failing);
        service.removeUpdateListener(healthy);
        service.onAgentChanged("public", "listener");
        TimeUnit.MILLISECONDS.sleep(40L);
        assertEquals(1, healthyCalls.get());
        assertTrue(service.getState(key).isPresent());
    }
    
    @Test
    void testReconciliationIsBoundedAndDoesNotScanWithoutActiveProjection()
        throws Exception {
        final double reconciledBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.RECONCILIATION,
            AgentWatchMetrics.Result.SCHEDULED);
        List<String> projected = Collections.synchronizedList(new ArrayList<String>());
        AgentProjectionProjector projector = key -> {
            projected.add(key.getAgentName());
            return AgentProjectionTestFixtures.available("fp-" + key.getAgentName(), 1L);
        };
        service = newService(projector, 5L, 10L, 0L, 1, 2);
        service.reconcileBatch();
        TimeUnit.MILLISECONDS.sleep(30L);
        assertTrue(projected.isEmpty());
        
        service.retain(AgentProjectionTestFixtures.request("alpha"));
        service.retain(AgentProjectionTestFixtures.request("beta"));
        waitUntil(() -> projected.size() == 2);
        projected.clear();
        service.reconcileBatch();
        waitUntil(() -> projected.size() == 1);
        String first = projected.get(0);
        service.reconcileBatch();
        waitUntil(() -> projected.size() == 2);
        assertFalse(first.equals(projected.get(1)));
        assertEquals(reconciledBefore + 2D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.RECONCILIATION,
            AgentWatchMetrics.Result.SCHEDULED));
    }
    
    @Test
    void testReconciliationLagTracksSchedulerDelayAndClearsOnShutdown() throws Exception {
        long intervalMillis = 5000L;
        service = newService(key -> AgentProjectionTestFixtures.available("fp", 1L),
            5L, 10L, intervalMillis, 1, 1);
        ReflectionTestUtils.setField(service, "lastReconciliationMillis",
            System.currentTimeMillis() - intervalMillis - 100L);
        
        service.reconcileBatch();
        
        assertTrue(AgentWatchMetrics.reconciliationLagMillis() >= 50L);
        service.shutdown();
        assertEquals(0L, AgentWatchMetrics.reconciliationLagMillis());
    }
    
    @Test
    void testConcurrentReleaseDiscardsLateComputation() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AgentProjectionProjector projector = key -> {
            started.countDown();
            await(release);
            return AgentProjectionTestFixtures.available("late", 1L);
        };
        service = newService(projector, 5L, 10L, 0L, 10, 1);
        AgentProjectionKey key = service.retain(AgentProjectionTestFixtures.request("late"));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        assertTrue(service.release(key));
        release.countDown();
        TimeUnit.MILLISECONDS.sleep(40L);
        assertFalse(service.getState(key).isPresent());
        assertEquals(0, service.size());
    }
    
    @Test
    void testInactiveAndClosedServiceRejectsNewWork() throws NacosException {
        service = newService(key -> AgentProjectionTestFixtures.available("fp", 1L),
            10000L, 10L, 0L, 10, 1);
        AgentProjectionKey missing = AgentProjectionTestFixtures.key("missing");
        assertThrows(IllegalStateException.class, () -> service.refreshNow(missing));
        service.shutdown();
        assertThrows(IllegalStateException.class,
            () -> service.retain(AgentProjectionTestFixtures.request("closed")));
        assertThrows(IllegalStateException.class, () -> service.refreshNow(missing));
        service.shutdown();
    }
    
    @Test
    void testDefaultLifecycleAndClosedReconciliation() throws Exception {
        service = new AgentProjectionService(
            key -> AgentProjectionTestFixtures.available("default", 1L));
        AgentProjectionKey key = service.retain(
            AgentProjectionTestFixtures.request("default-lifecycle"));
        waitUntil(() -> service.getState(key).isPresent());
        assertEquals("default", service.getState(key).get().getFingerprint());
        assertTrue(service.pendingTaskCount() >= 0);
        
        service.shutdown();
        service.reconcileBatch();
    }
    
    @Test
    void testSpringSelectsProductionConstructor() {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.registerBean(AgentProjectionProjector.class,
                () -> key -> AgentProjectionTestFixtures.available("spring", 1L));
            context.register(AgentProjectionService.class);
            context.refresh();
            
            service = context.getBean(AgentProjectionService.class);
            assertTrue(service != null);
        }
    }
    
    @Test
    void testRefreshReleasedWhileProjectingFailsClearly() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releaseProjector = new CountDownLatch(1);
        AgentProjectionProjector projector = key -> {
            started.countDown();
            await(releaseProjector);
            return AgentProjectionTestFixtures.available("released", 1L);
        };
        service = newService(projector, 10000L, 10L, 0L, 10, 1);
        AgentProjectionKey key = service.retain(
            AgentProjectionTestFixtures.request("released-refresh"));
        CompletableFuture<AgentProjectionState> refresh = CompletableFuture.supplyAsync(
            () -> service.refreshNow(key));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        assertTrue(service.release(key));
        releaseProjector.countDown();
        
        CompletionException exception = assertThrows(CompletionException.class, refresh::join);
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }
    
    @Test
    void testReleasedDelayedProjectionIsIgnoredBeforeProjecting() throws Exception {
        AtomicInteger projects = new AtomicInteger();
        service = newService(key -> {
            projects.incrementAndGet();
            return AgentProjectionTestFixtures.available("late", 1L);
        }, 100L, 10L, 0L, 10, 1);
        AgentProjectionKey key = service.retain(
            AgentProjectionTestFixtures.request("released-before-execute"));
        assertTrue(service.release(key));
        
        TimeUnit.MILLISECONDS.sleep(180L);
        assertEquals(0, projects.get());
    }
    
    private AgentProjectionService newService(AgentProjectionProjector projector,
        long changeDelayMillis, long retryDelayMillis, long reconciliationIntervalMillis,
        int batchSize, int workers) {
        return new AgentProjectionService(projector, new AgentProjectionRegistry(),
            changeDelayMillis, retryDelayMillis, reconciliationIntervalMillis, batchSize,
            workers);
    }
    
    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertTrue(condition.getAsBoolean());
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
}
