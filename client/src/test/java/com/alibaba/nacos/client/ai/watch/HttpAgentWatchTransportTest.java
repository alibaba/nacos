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

package com.alibaba.nacos.client.ai.watch;

import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentWatchBatchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentHttpWatchClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpAgentWatchTransportTest {
    
    private QueueExecutorService requestExecutor;
    
    private ScheduledExecutorService retryExecutor;
    
    private AtomicReference<Runnable> retryTask;
    
    private ScriptedClient client;
    
    private HttpAgentWatchTransport transport;
    
    private final AtomicLong clock = new AtomicLong();
    
    private final AtomicReference<Runnable> submissionRetry = new AtomicReference<Runnable>();
    
    private AgentWatchTaskScheduler taskScheduler;
    
    @BeforeEach
    void setUp() {
        requestExecutor = new QueueExecutorService();
        retryExecutor = mock(ScheduledExecutorService.class);
        retryTask = new AtomicReference<Runnable>();
        when(retryExecutor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenAnswer(invocation -> {
                retryTask.set(invocation.getArgument(0));
                return mock(ScheduledFuture.class);
            });
        ScheduledExecutorService driver = mock(ScheduledExecutorService.class);
        when(driver.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any()))
            .thenAnswer(invocation -> {
                submissionRetry.set(invocation.getArgument(0));
                return mock(ScheduledFuture.class);
            });
        taskScheduler = new AgentWatchTaskScheduler(driver, clock::get, (count, key) -> 1L);
        client = new ScriptedClient();
        transport = new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
            1000L, 1L, 3, clock::get, taskScheduler);
    }
    
    @AfterEach
    void tearDown() {
        transport.shutdown();
    }
    
    @Test
    void batchesCompleteCurrentSetIntoOneLatestGeneration() throws Exception {
        for (int i = 0; i < 300; i++) {
            transport.start(registration("watch-" + i, "agent-" + i, "fingerprint-" + i),
                new TestCallback());
        }
        requestExecutor.runUntilRequests(client, 1, 400);
        AgentWatchBatchRequest request = client.requests.get(0);
        assertEquals(300L, request.getGeneration());
        assertEquals(300, request.getWatches().size());
        assertEquals(1000L, request.getTimeoutMillis());
        assertEquals(1, client.requests.size());
    }
    
    @Test
    void changedIdsPauseOnlyTheirRefreshAndUpdateRestoresCompleteBatch() throws Exception {
        TestCallback first = new TestCallback();
        TestCallback second = new TestCallback();
        client.responses.add(changed(2L, "watch-a"));
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), first);
        transport.start(registration("watch-b", "agent-b", "fingerprint-b"), second);
        requestExecutor.runUntilRequests(client, 1, 10);
        assertEquals(1, first.invalidations);
        assertEquals(0, second.invalidations);
        
        requestExecutor.runUntilRequests(client, 2, 10);
        assertEquals(1, client.requests.get(1).getWatches().size());
        assertEquals("watch-b",
            client.requests.get(1).getWatches().get(0).getClientWatchId());
        
        transport.update(registration("watch-a", "agent-a", "fingerprint-a2"));
        requestExecutor.runUntilRequests(client, 3, 10);
        AgentWatchBatchRequest restored = client.requests.get(2);
        assertEquals(3L, restored.getGeneration());
        assertEquals(2, restored.getWatches().size());
        
        first.acceptInvalidation = false;
        client.responses.add(changed(3L, "watch-a"));
        requestExecutor.runUntilRequests(client, 4, 10);
        assertEquals(2, first.invalidations);
        requestExecutor.runUntilRequests(client, 5, 10);
        assertEquals(2, client.requests.get(4).getWatches().size());
    }
    
    @Test
    void lateOldGenerationResponseIsIgnoredAfterListMutation() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AgentHttpWatchClient blockingClient = request -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                firstStarted.countDown();
                awaitIgnoringInterrupt(releaseFirst);
                return changed(request.getGeneration(), "watch-a");
            }
            secondStarted.countDown();
            awaitIgnoringInterrupt(releaseSecond);
            return timeout(request.getGeneration());
        };
        ExecutorService concurrentExecutor = Executors.newFixedThreadPool(2);
        HttpAgentWatchTransport concurrent = new HttpAgentWatchTransport(blockingClient,
            concurrentExecutor, retryExecutor, 1000L, 1L, 3);
        TestCallback callback = new TestCallback();
        try {
            concurrent.start(registration("watch-a", "agent-a", "fingerprint-a"), callback);
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            concurrent.start(registration("watch-b", "agent-b", "fingerprint-b"),
                new TestCallback());
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            releaseFirst.countDown();
            waitUntil(() -> calls.get() >= 2, 1000L);
            assertEquals(0, callback.invalidations);
        } finally {
            releaseSecond.countDown();
            concurrent.shutdown();
        }
    }
    
    @Test
    void lateOldGenerationFailureAndCanceledRetryAreIgnored() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AgentHttpWatchClient blockingClient = request -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                awaitIgnoringInterrupt(releaseFirst);
                throw new NacosException(NacosException.SERVER_ERROR, "late failure");
            }
            return timeout(request.getGeneration());
        };
        ExecutorService concurrentExecutor = Executors.newFixedThreadPool(2);
        HttpAgentWatchTransport concurrent = new HttpAgentWatchTransport(blockingClient,
            concurrentExecutor, retryExecutor, 1000L, 1L, 3);
        try {
            concurrent.start(registration("watch-a", "agent-a", "fingerprint-a"),
                new TestCallback());
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            concurrent.start(registration("watch-b", "agent-b", "fingerprint-b"),
                new TestCallback());
            releaseFirst.countDown();
            waitUntil(() -> calls.get() >= 2, 1000L);
            assertTrue(concurrent.isAvailable());
        } finally {
            releaseFirst.countDown();
            concurrent.shutdown();
        }
        
        client.failure = new NacosException(NacosException.SERVER_ERROR, "temporary");
        transport.start(registration("watch-retry", "agent-retry", "fingerprint"),
            new TestCallback());
        requestExecutor.runNext();
        Runnable canceledRetry = retryTask.getAndSet(null);
        assertNotNull(canceledRetry);
        transport.start(registration("watch-new", "agent-new", "fingerprint"),
            new TestCallback());
        canceledRetry.run();
        requestExecutor.runNext();
        assertTrue(transport.isAvailable());
    }
    
    @Test
    void defensiveRetryGuardIsIdempotent() throws Exception {
        Field retryFuture = HttpAgentWatchTransport.class.getDeclaredField("retryFuture");
        retryFuture.setAccessible(true);
        retryFuture.set(transport, mock(ScheduledFuture.class));
        Method scheduleRetry = HttpAgentWatchTransport.class.getDeclaredMethod("scheduleRetry",
            long.class, NacosException.class);
        scheduleRetry.setAccessible(true);
        
        scheduleRetry.invoke(transport, 0L,
            new NacosException(NacosException.SERVER_ERROR, "duplicate"));
        
        assertEquals(null, retryTask.get());
    }
    
    @Test
    void persistentFailureRetainsWatchUntilSameTransportRecovers() throws Exception {
        TestCallback callback = new TestCallback();
        TestLifecycleListener listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        client.failure = new NacosException(NacosException.SERVER_ERROR, "temporary");
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), callback);
        requestExecutor.runNext();
        for (int i = 0; i < 35; i++) {
            runRetry();
            requestExecutor.runNext();
        }
        assertEquals(0, listener.unavailable);
        assertEquals(0, callback.unavailable);
        assertTrue(transport.isAvailable());
        client.failure = null;
        client.responses.add(changed(1L, "watch-a"));
        runRetry();
        requestExecutor.runNext();
        assertEquals(1, callback.invalidations);
    }
    
    @Test
    void onlyUnsupportedBindingFallsBackAndInvalidResponseRetries() throws Exception {
        assertImmediateFallback(new NacosException(NacosException.NOT_FOUND, "missing binding"));
        assertImmediateFallback(
            new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "unsupported"));
        assertImmediateFallback(new NacosException(NacosException.NO_HANDLER, "no handler"));
        transport.shutdown();
        requestExecutor = new QueueExecutorService();
        client = new ScriptedClient();
        retryTask.set(null);
        transport =
            new HttpAgentWatchTransport(client, requestExecutor, retryExecutor, 1000L, 1L, 1);
        TestCallback callback = new TestCallback();
        client.responses.add(timeout(99L));
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), callback);
        requestExecutor.runNext();
        assertTrue(transport.isAvailable());
        assertNotNull(retryTask.get());
        runRetry();
        client.responses.add(changed(1L, "watch-a"));
        requestExecutor.runNext();
        assertEquals(1, callback.invalidations);
        assertEquals(0, callback.unavailable);
    }
    
    @ParameterizedTest
    @ValueSource(ints = {403, 400, 409, 50105})
    void backgroundRejectionRetriesWithoutCallbackOrBindingFallback(int code) throws Exception {
        TestCallback callback = new TestCallback();
        TestLifecycleListener lifecycle = new TestLifecycleListener();
        transport.setLifecycleListener(lifecycle);
        client.failure = new NacosException(code, "rejected");
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), callback);
        requestExecutor.runNext();
        assertEquals(0, callback.unavailable);
        assertEquals(0, lifecycle.unavailable);
        assertNotNull(retryTask.get());
        client.failure = null;
        client.responses.add(changed(1L, "watch-a"));
        runRetry();
        requestExecutor.runNext();
        assertEquals(1, callback.invalidations);
    }
    
    @Test
    void capacityRejectionOfAcknowledgedSetBacksOffWithoutDeletingItems()
        throws Exception {
        TestCallback callback = new TestCallback();
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), callback);
        requestExecutor.runUntilRequests(client, 1, 10);
        client.failure = capacity();
        requestExecutor.runUntilRequests(client, 2, 10);
        assertEquals(0, callback.unavailable);
        assertTrue(transport.isAvailable());
        assertNotNull(retryTask.get());
        client.failure = null;
        runRetry();
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("watch-a");
    }
    
    @Test
    void quotaRollbackRetainsAllAdditionsAndOnlyGrowthSuccessClearsRestriction() throws Exception {
        TestCallback a = new TestCallback();
        TestCallback c = new TestCallback();
        transport.start(registration("a", "agent-a", "a1"), a);
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        client.failure = capacity();
        transport.start(registration("c", "agent-c", "c1"), c);
        transport.start(registration("d", "agent-d", "d1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 2, 10);
        assertIds("a", "b", "c", "d");
        assertEquals(0, c.unavailable);
        assertThrows(NacosException.class,
            () -> transport.start(registration("e", "agent-e", "e1"), new TestCallback()));
        client.failure = null;
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("a", "b");
        transport.start(registration("a", "agent-a", "a2"), a);
        requestExecutor.runUntilRequests(client, 4, 10);
        assertIds("a", "b");
        assertThrows(NacosException.class,
            () -> transport.start(registration("e", "agent-e", "e1"), new TestCallback()));
        runRetry();
        requestExecutor.runUntilRequests(client, 5, 10);
        assertIds("a", "b", "c", "d");
        assertEquals("a2", client.requests.get(4).getWatches().get(0).getMaterializedFingerprint());
        transport.start(registration("e", "agent-e", "e1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 6, 10);
        assertIds("a", "b", "c", "d", "e");
    }
    
    @Test
    void duplicateRuntimeFailureAndRejectedExecutorsAreBounded() throws Exception {
        TestCallback first = new TestCallback();
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), first);
        transport.start(registration("watch-a", "agent-a", "fingerprint-a2"), first);
        requestExecutor.runUntilRequests(client, 1, 10);
        assertEquals("fingerprint-a2",
            client.requests.get(0).getWatches().get(0).getMaterializedFingerprint());
        
        transport.shutdown();
        requestExecutor = new QueueExecutorService();
        client = new ScriptedClient();
        client.runtimeFailure = new IllegalStateException("runtime failure");
        TestLifecycleListener runtimeFailure = new TestLifecycleListener();
        transport = new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
            1000L, 1L, 1);
        transport.setLifecycleListener(runtimeFailure);
        transport.start(registration("watch-runtime", "agent-runtime", "fingerprint"),
            new TestCallback());
        requestExecutor.runNext();
        assertEquals(0, runtimeFailure.unavailable);
        assertNotNull(retryTask.get());
        
        transport.shutdown();
        requestExecutor = new QueueExecutorService();
        requestExecutor.shutdown();
        client = new ScriptedClient();
        TestLifecycleListener requestRejected = new TestLifecycleListener();
        transport = new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
            1000L, 1L, 1);
        transport.setLifecycleListener(requestRejected);
        transport.start(registration("watch-request-rejected", "agent-request", "fingerprint"),
            new TestCallback());
        assertEquals(0, requestRejected.unavailable);
        assertNotNull(retryTask.get());
        
        transport.shutdown();
        requestExecutor = new QueueExecutorService();
        client = new ScriptedClient();
        client.failure = new NacosException(NacosException.SERVER_ERROR, "temporary");
        ScheduledExecutorService rejectedRetry = mock(ScheduledExecutorService.class);
        when(rejectedRetry.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenThrow(new RejectedExecutionException("closed"));
        TestLifecycleListener retryRejected = new TestLifecycleListener();
        transport = new HttpAgentWatchTransport(client, requestExecutor, rejectedRetry,
            1000L, 1L, 3);
        transport.setLifecycleListener(retryRejected);
        transport.start(registration("watch-retry-rejected", "agent-retry", "fingerprint"),
            new TestCallback());
        requestExecutor.runNext();
        assertEquals(0, retryRejected.unavailable);
        assertTrue(transport.isAvailable());
    }
    
    @Test
    void lifecycleValidationUnknownOperationsAndShutdownAreSafe() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
                999L, 1L, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
                1000L, -1L, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
                1000L, 1L, 0));
        transport.update(registration("missing", "agent-a", "fingerprint-a"));
        transport.stop("missing");
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"),
            new TestCallback());
        transport.stop("watch-a");
        transport.shutdown();
        transport.shutdown();
        assertFalse(transport.isAvailable());
        assertEquals(NacosException.CLIENT_DISCONNECT,
            assertThrows(NacosException.class,
                () -> transport.start(
                    registration("watch-b", "agent-b", "fingerprint-b"),
                    new TestCallback()))
                .getErrCode());
    }
    
    @Test
    void cancelAcknowledgedItemAdvancesPendingGrowthWithoutResubscribe() throws Exception {
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        client.failure = capacity();
        requestExecutor.runUntilRequests(client, 2, 10);
        Runnable oldRetry = retryTask.get();
        transport.stop("a");
        oldRetry.run();
        client.failure = null;
        runRetry();
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("b");
    }
    
    @Test
    void cancelAllPendingAllowsOnlyOneRealGrowthProbeAfterCooldown() throws Exception {
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        client.failure = capacity();
        requestExecutor.runUntilRequests(client, 2, 10);
        transport.stop("b");
        assertThrows(NacosException.class,
            () -> transport.start(registration("c", "agent-c", "c1"), new TestCallback()));
        clock.set(TimeUnit.SECONDS.toNanos(60));
        transport.start(registration("c", "agent-c", "c1"), new TestCallback());
        assertThrows(NacosException.class,
            () -> transport.start(registration("d", "agent-d", "d1"), new TestCallback()));
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("a", "c");
        client.failure = null;
        requestExecutor.runUntilRequests(client, 4, 10);
        assertIds("a");
        runRetry();
        requestExecutor.runUntilRequests(client, 5, 10);
        assertIds("a", "c");
        transport.start(registration("d", "agent-d", "d1"), new TestCallback());
    }
    
    @Test
    void neverAcknowledgedQuotaRetriesWholeCurrentSetAndNeverEmptyRequest() throws Exception {
        client.failure = capacity();
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        assertIds("a", "b");
        runRetry();
        requestExecutor.runUntilRequests(client, 2, 10);
        assertIds("a", "b");
        transport.stop("a");
        client.failure = null;
        runRetry();
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("b");
        transport.stop("b");
        while (!requestExecutor.tasks.isEmpty()) {
            requestExecutor.runNext();
        }
        assertEquals(3, client.requests.size());
    }
    
    @Test
    void rejectedRetryExecutorRetainsTaskUntilItCanSubmitAgain() throws Exception {
        client.failure = new NacosException(50105, "migration");
        org.mockito.Mockito.doThrow(new RejectedExecutionException("busy"))
            .when(retryExecutor).schedule(any(Runnable.class), anyLong(), any());
        TestCallback callback = new TestCallback();
        transport.start(registration("a", "agent-a", "a1"), callback);
        requestExecutor.runNext();
        assertNotNull(submissionRetry.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            retryTask.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(retryExecutor).schedule(any(Runnable.class), anyLong(), any());
        clock.set(TimeUnit.SECONDS.toNanos(60));
        submissionRetry.get().run();
        client.failure = null;
        client.responses.add(changed(1L, "a"));
        runRetry();
        requestExecutor.runNext();
        assertEquals(1, callback.invalidations);
        assertEquals(0, callback.unavailable);
    }
    
    @Test
    void growthWaitsForRefreshingAcknowledgedMembersToRejoinTheBatch() throws Exception {
        client.responses.add(changed(2L, "a"));
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        transport.start(registration("c", "agent-c", "c1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 2, 10);
        assertIds("b");
        transport.update(registration("a", "agent-a", "a2"));
        client.failure = capacity();
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("a", "b", "c");
        client.failure = null;
        requestExecutor.runUntilRequests(client, 4, 10);
        assertIds("a", "b");
        transport.stop("b");
        runRetry();
        requestExecutor.runUntilRequests(client, 5, 10);
        assertIds("a", "c");
    }
    
    @Test
    void lateRunningRetryCannotClearNewGenerationWork() throws Exception {
        client.failure = new NacosException(500, "temporary");
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        requestExecutor.runNext();
        Runnable old = retryTask.get();
        Thread executing;
        synchronized (transport) {
            executing = new Thread(old);
            executing.start();
            waitUntil(() -> executing.getState() == Thread.State.BLOCKED, 2000L);
            transport.update(registration("a", "agent-a", "a2"));
            requestExecutor.runUntilRequests(client, 2, 10);
        }
        executing.join(2000L);
        assertFalse(executing.isAlive());
        client.failure = null;
        runRetry();
        requestExecutor.runUntilRequests(client, 3, 10);
        assertEquals("a2", client.requests.get(2).getWatches().get(0).getMaterializedFingerprint());
    }
    
    @Test
    void canceledRunningQuotaRetryCannotOverrideReplacementTimer() throws Exception {
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        transport.start(registration("c", "agent-c", "c1"), new TestCallback());
        client.failure = capacity();
        requestExecutor.runUntilRequests(client, 2, 10);
        Runnable old = retryTask.get();
        Thread executing;
        synchronized (transport) {
            executing = new Thread(old);
            executing.start();
            waitUntil(() -> executing.getState() == Thread.State.BLOCKED, 2000L);
            transport.stop("a");
        }
        executing.join(2000L);
        assertFalse(executing.isAlive());
        client.failure = null;
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("b");
        runRetry();
        requestExecutor.runUntilRequests(client, 4, 10);
        assertIds("b", "c");
    }
    
    @Test
    void repeatedQuotaFailureOfRollbackSetRetainsPendingGrowthAndBacksOff() throws Exception {
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 1, 10);
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        client.failure = capacity();
        requestExecutor.runUntilRequests(client, 2, 10);
        requestExecutor.runUntilRequests(client, 3, 10);
        assertIds("a");
        client.failure = null;
        runRetry();
        requestExecutor.runUntilRequests(client, 4, 10);
        assertIds("a");
        assertTrue(transport.isAvailable());
    }
    
    @Test
    void invalidationFailureDoesNotStrandRefreshOrOtherItems() throws Exception {
        resetLogGate();
        AgentWatchTransportCallback callback = mock(AgentWatchTransportCallback.class);
        when(callback.invalidate(null, true)).thenThrow(new IllegalStateException("first"),
            new IllegalStateException("second")).thenReturn(true);
        transport.start(registration("a", "agent-a", "a1"), callback);
        for (int i = 1; i <= 3; i++) {
            AgentWatchBatchResponse response = changed(1L, "a");
            response.setChangedClientWatchIds(java.util.Arrays.asList("unknown", "a", "a"));
            client.responses.add(response);
            requestExecutor.runUntilRequests(client, i, 10);
        }
        org.mockito.Mockito.verify(callback, org.mockito.Mockito.times(3)).invalidate(null, true);
    }
    
    @Test
    void unsupportedBindingWithoutRouterStillRetainsRetryWork() throws Exception {
        client.failure = new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "unsupported");
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        requestExecutor.runNext();
        assertNotNull(retryTask.get());
        assertTrue(transport.isAvailable());
    }
    
    @Test
    void cancellationBeforeFirstResponseDoesNotAcknowledgeUnsentItems() throws Exception {
        transport.start(registration("a", "agent-a", "a1"), new TestCallback());
        transport.start(registration("b", "agent-b", "b1"), new TestCallback());
        transport.stop("a");
        AgentWatchBatchResponse noIds = changed(3L, "b");
        noIds.setChangedClientWatchIds(null);
        client.responses.add(noIds);
        requestExecutor.runUntilRequests(client, 1, 10);
        assertIds("b");
        transport.start(registration("c", "agent-c", "c1"), new TestCallback());
        requestExecutor.runUntilRequests(client, 2, 10);
        assertIds("b", "c");
    }
    
    private NacosException capacity() {
        return new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, "full");
    }
    
    private void assertIds(String... ids) {
        List<String> actual = new ArrayList<String>();
        for (com.alibaba.nacos.api.ai.model.agent.AgentWatchBatchItem item : client.requests
            .get(client.requests.size() - 1).getWatches()) {
            actual.add(item.getClientWatchId());
        }
        assertEquals(java.util.Arrays.asList(ids), actual);
    }
    
    private void resetLogGate() throws Exception {
        Field field = HttpAgentWatchTransport.class.getDeclaredField("WARN_LOG_LIMITER");
        field.setAccessible(true);
        Object limiter = field.get(null);
        Field next = limiter.getClass().getDeclaredField("nextAllowedNanos");
        next.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) next.get(limiter)).set(Long.MIN_VALUE);
    }
    
    private void assertImmediateFallback(NacosException failure) throws Exception {
        resetLogGate();
        transport.shutdown();
        requestExecutor = new QueueExecutorService();
        client = new ScriptedClient();
        client.failure = failure;
        transport = new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
            1000L, 1L, 3);
        TestLifecycleListener listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"),
            new TestCallback());
        requestExecutor.runNext();
        assertEquals(1, listener.unavailable);
        assertEquals(failure.getErrCode(), listener.exception.getErrCode());
        assertFalse(transport.isAvailable());
        assertEquals(null, retryTask.get());
    }
    
    private void runRetry() {
        clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
        Runnable task = retryTask.getAndSet(null);
        assertNotNull(task);
        task.run();
    }
    
    private AgentWatchRegistration registration(String watchId, String agentName,
        String fingerprint) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(agentName);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        return new AgentWatchRegistration(watchId, request, fingerprint);
    }
    
    private static AgentWatchBatchResponse timeout(long generation) {
        AgentWatchBatchResponse result = new AgentWatchBatchResponse();
        result.setGeneration(generation);
        result.setChanged(false);
        result.setChangedClientWatchIds(Collections.<String>emptyList());
        return result;
    }
    
    private static AgentWatchBatchResponse changed(long generation, String watchId) {
        AgentWatchBatchResponse result = timeout(generation);
        result.setChanged(true);
        result.setChangedClientWatchIds(Collections.singletonList(watchId));
        return result;
    }
    
    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void waitUntil(Check check, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!check.evaluate() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(check.evaluate());
    }
    
    private interface Check {
        
        boolean evaluate();
    }
    
    private static final class ScriptedClient implements AgentHttpWatchClient {
        
        private final List<AgentWatchBatchRequest> requests =
            new ArrayList<AgentWatchBatchRequest>();
        
        private final Queue<AgentWatchBatchResponse> responses =
            new ArrayDeque<AgentWatchBatchResponse>();
        
        private NacosException failure;
        
        private RuntimeException runtimeFailure;
        
        @Override
        public AgentWatchBatchResponse watchAgents(AgentWatchBatchRequest request)
            throws NacosException {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            AgentWatchBatchResponse scripted = responses.poll();
            return scripted == null ? timeout(request.getGeneration()) : scripted;
        }
    }
    
    private static final class TestCallback implements AgentWatchTransportCallback {
        
        private int invalidations;
        
        private boolean acceptInvalidation = true;
        
        private int unavailable;
        
        private boolean terminal;
        
        @Override
        public boolean invalidate(String observedFingerprint, boolean forceRefresh) {
            invalidations++;
            return acceptInvalidation;
        }
        
        @Override
        public void unavailable(int errorCode, String errorMessage, boolean terminal) {
            unavailable++;
            this.terminal = terminal;
        }
    }
    
    private static final class TestLifecycleListener
        implements HttpAgentWatchTransport.WireLifecycleListener {
        
        private int unavailable;
        
        private NacosException exception;
        
        @Override
        public void onWireUnavailable(NacosException exception) {
            unavailable++;
            this.exception = exception;
        }
    }
    
    private static final class QueueExecutorService extends AbstractExecutorService {
        
        private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
        
        private boolean shutdown;
        
        @Override
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> result = new ArrayList<Runnable>(tasks);
            tasks.clear();
            return result;
        }
        
        @Override
        public boolean isShutdown() {
            return shutdown;
        }
        
        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }
        
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
        
        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new java.util.concurrent.RejectedExecutionException();
            }
            tasks.add(command);
        }
        
        private void runNext() {
            Runnable task = tasks.poll();
            assertNotNull(task);
            task.run();
        }
        
        private void runUntilRequests(ScriptedClient client, int expected, int maxTasks) {
            for (int i = 0; client.requests.size() < expected && i < maxTasks; i++) {
                runNext();
            }
            assertEquals(expected, client.requests.size());
        }
    }
}
