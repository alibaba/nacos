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

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
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
        client = new ScriptedClient();
        transport = new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
            1000L, 1L, 3);
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
    void persistentTransientFailureFallsBackAfterBoundedRetries() throws Exception {
        TestLifecycleListener listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        client.failure = new NacosException(NacosException.SERVER_ERROR, "temporary");
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"),
            new TestCallback());
        
        requestExecutor.runNext();
        assertEquals(0, listener.unavailable);
        runRetry();
        requestExecutor.runNext();
        assertEquals(0, listener.unavailable);
        runRetry();
        requestExecutor.runNext();
        assertEquals(1, listener.unavailable);
        assertFalse(transport.isAvailable());
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> transport.start(
                    registration("watch-b", "agent-b", "fingerprint-b"),
                    new TestCallback()))
                .getErrCode());
    }
    
    @Test
    void unsupportedDeniedCapacityAndMismatchedGenerationLeaveNoRetryLoop() throws Exception {
        assertImmediateFallback(new NacosException(NacosException.NOT_FOUND, "missing"));
        assertImmediateFallback(new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "unsupported"));
        assertImmediateFallback(new NacosException(NacosException.NO_RIGHT, "denied"));
        
        transport.shutdown();
        requestExecutor = new QueueExecutorService();
        client = new ScriptedClient();
        retryTask.set(null);
        TestLifecycleListener mismatch = new TestLifecycleListener();
        transport = new HttpAgentWatchTransport(client, requestExecutor, retryExecutor,
            1000L, 1L, 1);
        transport.setLifecycleListener(mismatch);
        AgentWatchBatchResponse invalid = timeout(99L);
        client.responses.add(invalid);
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"),
            new TestCallback());
        requestExecutor.runNext();
        assertEquals(1, mismatch.unavailable);
    }
    
    @Test
    void capacityRejectsOnlyLatestAdditionAndKeepsExistingBatchAvailable() throws Exception {
        TestCallback first = new TestCallback();
        TestCallback rejected = new TestCallback();
        transport.start(registration("watch-a", "agent-a", "fingerprint-a"), first);
        requestExecutor.runUntilRequests(client, 1, 10);
        
        client.failure = new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, "full");
        transport.start(registration("watch-b", "agent-b", "fingerprint-b"), rejected);
        requestExecutor.runUntilRequests(client, 2, 10);
        assertEquals(1, rejected.unavailable);
        assertTrue(rejected.terminal);
        assertEquals(0, first.unavailable);
        assertTrue(transport.isAvailable());
        
        client.failure = null;
        requestExecutor.runUntilRequests(client, 3, 10);
        AgentWatchBatchRequest retained = client.requests.get(client.requests.size() - 1);
        assertEquals(1, retained.getWatches().size());
        assertEquals("watch-a", retained.getWatches().get(0).getClientWatchId());
        
        TestCallback replacement = new TestCallback();
        transport.start(registration("watch-c", "agent-c", "fingerprint-c"), replacement);
        requestExecutor.runUntilRequests(client, 4, 10);
        AgentWatchBatchRequest reused = client.requests.get(client.requests.size() - 1);
        assertEquals(2, reused.getWatches().size());
        assertEquals(0, replacement.unavailable);
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
        assertEquals(1, runtimeFailure.unavailable);
        assertEquals(NacosException.SERVER_ERROR, runtimeFailure.exception.getErrCode());
        
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
        assertEquals(1, requestRejected.unavailable);
        assertEquals(NacosException.CLIENT_ERROR, requestRejected.exception.getErrCode());
        
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
        assertEquals(1, retryRejected.unavailable);
        assertEquals(NacosException.CLIENT_ERROR, retryRejected.exception.getErrCode());
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
    
    private void assertImmediateFallback(NacosException failure) throws Exception {
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
