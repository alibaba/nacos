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

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEventType;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentWatchManagerTest {
    
    private static final String DIGEST_A =
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    
    private static final String DIGEST_B =
        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    
    @Mock
    private AgentClientProxy clientProxy;
    
    @Mock
    private ScheduledExecutorService refreshExecutor;
    
    @Mock
    private ExecutorService callbackExecutor;
    
    private final List<Runnable> scheduled = new ArrayList<Runnable>();
    
    private final List<Long> delays = new ArrayList<Long>();
    
    private final List<ScheduledFuture<?>> futures =
        new ArrayList<ScheduledFuture<?>>();
    
    private final List<Runnable> callbacks = new ArrayList<Runnable>();
    
    private FakeTransport transport;
    
    private AgentWatchManager manager;
    
    @BeforeEach
    void setUp() {
        lenient().when(refreshExecutor.schedule(any(Runnable.class), anyLong(),
            any(TimeUnit.class))).thenAnswer(invocation -> {
                scheduled.add(invocation.getArgument(0));
                delays.add(invocation.getArgument(1));
                ScheduledFuture<?> future = mock(ScheduledFuture.class);
                futures.add(future);
                return future;
            });
        lenient().doAnswer(invocation -> {
            callbacks.add(invocation.getArgument(0));
            return null;
        }).when(callbackExecutor).execute(any(Runnable.class));
        transport = new FakeTransport();
        manager = new AgentWatchManager("public", clientProxy, refreshExecutor,
            callbackExecutor, 300, transport, new FixedRetryPolicy());
    }
    
    @AfterEach
    void tearDown() {
        manager.shutdown();
    }
    
    @Test
    void metricsTrackActivePendingDirtyRefreshAndCallbackLifecycle() throws Exception {
        double intents = AgentWatchClientMetrics.intentCount();
        double pending = AgentWatchClientMetrics.pendingCount();
        double dirty = AgentWatchClientMetrics.dirtyCount();
        double mismatches = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.FINGERPRINT_MISMATCH,
            AgentWatchClientMetrics.Result.MISMATCH);
        double refreshes = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
            AgentWatchClientMetrics.Result.SUCCESS);
        double callbacksBefore = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.SUCCESS);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        AgentReference reference = reference("agent-a");
        
        manager.subscribe(reference, null, listener);
        assertEquals(intents + 1D, AgentWatchClientMetrics.intentCount());
        assertEquals(pending, AgentWatchClientMetrics.pendingCount());
        String clientWatchId = transport.onlyRegistration().getClientWatchId();
        assertTrue(manager.markDirty(clientWatchId, "different", false));
        assertEquals(dirty + 1D, AgentWatchClientMetrics.dirtyCount());
        assertEquals(mismatches + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.FINGERPRINT_MISMATCH,
            AgentWatchClientMetrics.Result.MISMATCH));
        scheduled.remove(0).run();
        assertEquals(dirty, AgentWatchClientMetrics.dirtyCount());
        assertEquals(refreshes + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
            AgentWatchClientMetrics.Result.SUCCESS));
        callbacks.remove(0).run();
        assertEquals(callbacksBefore + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.SUCCESS));
        manager.unsubscribe(reference, null, listener);
        assertEquals(intents, AgentWatchClientMetrics.intentCount());
        
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"));
        AgentReference pendingReference = reference("agent-pending-metrics");
        TestListener pendingListener = new TestListener(null);
        assertNull(manager.subscribe(pendingReference, null, pendingListener));
        assertEquals(intents + 1D, AgentWatchClientMetrics.intentCount());
        assertEquals(pending + 1D, AgentWatchClientMetrics.pendingCount());
        manager.unsubscribe(pendingReference, null, pendingListener);
        assertEquals(intents, AgentWatchClientMetrics.intentCount());
        assertEquals(pending, AgentWatchClientMetrics.pendingCount());
    }
    
    @Test
    void canonicalListenersShareOneWireIntentAndCloseOnLastRemoval() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener first = new TestListener(null);
        TestListener second = new TestListener(null);
        AgentDiscoveryFilter ordered = filter(Arrays.asList("a2a", "custom"),
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        AgentDiscoveryFilter reordered = filter(Arrays.asList("custom", "a2a"),
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        AgentReference reference = reference("agent-a");
        
        AgentDiscoveryResult firstResult = manager.subscribe(reference, ordered, first);
        AgentDiscoveryResult secondResult = manager.subscribe(reference, reordered, second);
        AgentDiscoveryResult duplicate = manager.subscribe(reference, ordered, first);
        
        assertNotSame(firstResult, secondResult);
        assertNotSame(firstResult, duplicate);
        assertEquals(1, manager.intentCount());
        assertEquals(2, manager.subscriptionCount());
        assertEquals(1, transport.startCount);
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        AgentWatchRegistration registration = transport.onlyRegistration();
        assertEquals("public", registration.getDiscoveryRequest().getNamespaceId());
        AgentDiscoveryRequest isolated = registration.getDiscoveryRequest();
        isolated.getReference().setAgentName("changed");
        assertEquals("agent-a",
            registration.getDiscoveryRequest().getReference().getAgentName());
        
        manager.unsubscribe(reference, reordered, first);
        manager.unsubscribe(reference, ordered, new TestListener(null));
        assertEquals(0, transport.stopCount);
        assertEquals(1, manager.subscriptionCount());
        manager.unsubscribe(reference, ordered, second);
        assertEquals(1, transport.stopCount);
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
    }
    
    @Test
    void concurrentSubscribersWaitForOneSuccessfulActivation() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transport.startHook = blockingHook(entered, release);
        AtomicReference<AgentDiscoveryResult> firstResult = new AtomicReference<>();
        AtomicReference<AgentDiscoveryResult> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = subscribeThread(new TestListener(null), firstResult, firstFailure);
        Thread second = subscribeThread(new TestListener(null), secondResult, secondFailure);
        
        first.start();
        assertTrue(entered.await(2L, TimeUnit.SECONDS));
        second.start();
        assertTrue(awaitWaiting(second));
        release.countDown();
        first.join(2000L);
        second.join(2000L);
        
        assertNull(firstFailure.get());
        assertNull(secondFailure.get());
        assertEquals("1.0.0", firstResult.get().getVersion());
        assertEquals("1.0.0", secondResult.get().getVersion());
        assertEquals(1, transport.startCount);
        assertEquals(1, manager.intentCount());
        assertEquals(2, manager.subscriptionCount());
    }
    
    @Test
    void slowInitialDiscoverDoesNotHoldTheManagerLockOrDuplicateTheIntent() throws Exception {
        CountDownLatch firstDiscoverEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstDiscover = new CountDownLatch(1);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenAnswer(invocation -> {
                AgentDiscoveryRequest request = invocation.getArgument(0);
                String agentName = request.getReference().getAgentName();
                if ("agent-a".equals(agentName)) {
                    firstDiscoverEntered.countDown();
                    assertTrue(releaseFirstDiscover.await(2L, TimeUnit.SECONDS));
                }
                AgentDiscoveryResult discovered = result("1.0.0", DIGEST_A);
                discovered.setAgentName(agentName);
                return discovered;
            });
        AtomicReference<AgentDiscoveryResult> firstResult = new AtomicReference<>();
        AtomicReference<AgentDiscoveryResult> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        CountDownLatch secondCompleted = new CountDownLatch(1);
        Thread first = subscribeThread(new TestListener(null), firstResult, firstFailure);
        Thread second = new Thread(() -> {
            try {
                secondResult.set(manager.subscribe(reference("agent-b"), null,
                    new TestListener(null)));
            } catch (Throwable throwable) {
                secondFailure.set(throwable);
            } finally {
                secondCompleted.countDown();
            }
        });
        
        first.start();
        assertTrue(firstDiscoverEntered.await(2L, TimeUnit.SECONDS));
        second.start();
        boolean completedBeforeRelease;
        try {
            completedBeforeRelease = secondCompleted.await(2L, TimeUnit.SECONDS);
        } finally {
            releaseFirstDiscover.countDown();
        }
        first.join(2000L);
        second.join(2000L);
        
        assertTrue(completedBeforeRelease);
        assertNull(firstFailure.get());
        assertNull(secondFailure.get());
        assertEquals("agent-a", firstResult.get().getAgentName());
        assertEquals("agent-b", secondResult.get().getAgentName());
        assertEquals(2, manager.intentCount());
        assertEquals(2, transport.startCount);
    }
    
    @Test
    void subscribersJoiningDuringInitialDiscoverShareItsResult() throws Exception {
        CountDownLatch discoverEntered = new CountDownLatch(1);
        CountDownLatch releaseDiscover = new CountDownLatch(1);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenAnswer(invocation -> {
                discoverEntered.countDown();
                assertTrue(releaseDiscover.await(2L, TimeUnit.SECONDS));
                return result("1.0.0", DIGEST_A);
            });
        AtomicReference<AgentDiscoveryResult> firstResult = new AtomicReference<>();
        AtomicReference<AgentDiscoveryResult> secondResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = subscribeThread(new TestListener(null), firstResult, firstFailure);
        Thread second = subscribeThread(new TestListener(null), secondResult, secondFailure);
        
        first.start();
        assertTrue(discoverEntered.await(2L, TimeUnit.SECONDS));
        second.start();
        assertTrue(awaitWaiting(second));
        releaseDiscover.countDown();
        first.join(2000L);
        second.join(2000L);
        
        assertNull(firstFailure.get());
        assertNull(secondFailure.get());
        assertEquals("1.0.0", firstResult.get().getVersion());
        assertEquals("1.0.0", secondResult.get().getVersion());
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        assertEquals(1, transport.startCount);
    }
    
    @Test
    void concurrentSubscribersObserveTheSameTerminalActivationFailure() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transport.startHook = blockingHook(entered, release);
        transport.startFailure = new NacosException(NacosException.NO_RIGHT, "denied");
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = subscribeThread(new TestListener(null), new AtomicReference<>(),
            firstFailure);
        Thread second = subscribeThread(new TestListener(null), new AtomicReference<>(),
            secondFailure);
        
        first.start();
        assertTrue(entered.await(2L, TimeUnit.SECONDS));
        second.start();
        assertTrue(awaitWaiting(second));
        release.countDown();
        first.join(2000L);
        second.join(2000L);
        
        assertEquals(NacosException.NO_RIGHT,
            ((NacosException) firstFailure.get()).getErrCode());
        assertEquals(NacosException.NO_RIGHT,
            ((NacosException) secondFailure.get()).getErrCode());
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
    }
    
    @Test
    void interruptedActivationWaitRemovesOnlyTheInterruptedListener() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transport.startHook = blockingHook(entered, release);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = subscribeThread(new TestListener(null), new AtomicReference<>(),
            firstFailure);
        Thread second = subscribeThread(new TestListener(null), new AtomicReference<>(),
            secondFailure);
        
        first.start();
        assertTrue(entered.await(2L, TimeUnit.SECONDS));
        second.start();
        assertTrue(awaitWaiting(second));
        second.interrupt();
        second.join(2000L);
        release.countDown();
        first.join(2000L);
        
        assertNull(firstFailure.get());
        assertEquals(NacosException.CLIENT_ERROR,
            ((NacosException) secondFailure.get()).getErrCode());
        assertEquals(1, manager.intentCount());
        assertEquals(1, manager.subscriptionCount());
    }
    
    @Test
    void cancellationInsideInitialTransportActivationCannotLeakWireState() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        transport.startHook =
            (registration, callback) -> manager.unsubscribe(reference("agent-a"), null, listener);
        
        AgentDiscoveryResult result = manager.subscribe(reference("agent-a"), null, listener);
        
        assertEquals("1.0.0", result.getVersion());
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, transport.registrations.size());
        assertEquals(1, transport.stopCount);
    }
    
    @Test
    void cancellationDuringInitialDiscoverCannotActivateOrScheduleTheIntent() throws Exception {
        TestListener existing = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenAnswer(invocation -> {
                manager.unsubscribe(reference("agent-a"), null, existing);
                return result("1.0.0", DIGEST_A);
            });
        
        AgentDiscoveryResult discovered =
            manager.subscribe(reference("agent-a"), null, existing);
        
        assertEquals("1.0.0", discovered.getVersion());
        assertEquals(0, manager.intentCount());
        assertEquals(0, transport.startCount);
        assertEquals(0, scheduled.size());
        
        TestListener missing = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenAnswer(invocation -> {
                manager.unsubscribe(reference("agent-a"), null, missing);
                throw notFound("removed concurrently");
            });
        
        assertNull(manager.subscribe(reference("agent-a"), null, missing));
        assertEquals(0, manager.intentCount());
        assertEquals(0, scheduled.size());
        assertEquals(0, callbacks.size());
    }
    
    @Test
    void dirtyHintInsideInitialTransportActivationSchedulesRefreshAfterAck() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        transport.startHook = (registration, callback) -> callback.invalidate(null, true);
        
        manager.subscribe(reference("agent-a"), null, listener);
        assertEquals(1, scheduled.size());
        runScheduled(0);
        runCallback(0);
        
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals(1, transport.updateCount);
    }
    
    @Test
    void absentIntentRetriesWithBackoffThenActivatesOnce() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing"))
            .thenThrow(notFound("still missing"))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        
        assertNull(manager.subscribe(reference("agent-a"), null, listener));
        assertEquals(0, transport.startCount);
        assertEquals(Collections.singletonList(25L), delays);
        runCallback(0);
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        
        runScheduled(0);
        assertEquals(Arrays.asList(25L, 50L), delays);
        assertEquals(1, listener.events.size());
        runScheduled(1);
        assertEquals(1, transport.startCount);
        assertEquals(1, manager.intentCount());
        runCallback(1);
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
        assertEquals("1.0.0",
            listener.events.get(1).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void canceledPendingAndShutdownTasksCannotResurrect() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing"));
        AgentReference reference = reference("agent-a");
        TestListener listener = new TestListener(null);
        manager.subscribe(reference, null, listener);
        manager.unsubscribe(reference, null, listener);
        runScheduled(0);
        runCallback(0);
        
        assertEquals(0, transport.startCount);
        assertEquals(0, listener.events.size());
        verify(futures.get(0)).cancel(false);
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        manager.shutdown();
        manager.shutdown();
        assertTrue(transport.closed);
        verify(refreshExecutor).shutdownNow();
        verify(callbackExecutor).shutdownNow();
        assertThrows(NacosException.class,
            () -> manager.subscribe(reference, null, new TestListener(null)));
    }
    
    @Test
    void pendingDiscoverCanceledDuringRemoteCallCannotResurrectIntent() throws Exception {
        TestListener listener = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenAnswer(invocation -> {
                manager.unsubscribe(reference("agent-a"), null, listener);
                return result("1.0.0", DIGEST_A);
            });
        manager.subscribe(reference("agent-a"), null, listener);
        
        runScheduled(0);
        runCallback(0);
        
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, listener.events.size());
        assertEquals(0, transport.startCount);
    }
    
    @Test
    void pendingFailureAfterUnsubscribeCannotRestoreCanceledIntent() throws Exception {
        TestListener listener = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenAnswer(invocation -> {
                manager.unsubscribe(reference("agent-a"), null, listener);
                throw notFound("late missing");
            });
        manager.subscribe(reference("agent-a"), null, listener);
        
        runScheduled(0);
        runCallback(0);
        
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, listener.events.size());
    }
    
    @Test
    void pendingActivationCancellationCannotLeakWireState() throws Exception {
        TestListener listener = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        transport.startHook =
            (registration, callback) -> manager.unsubscribe(reference("agent-a"), null, listener);
        manager.subscribe(reference("agent-a"), null, listener);
        
        runScheduled(0);
        runCallback(0);
        
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, listener.events.size());
        assertEquals(0, transport.registrations.size());
        assertEquals(1, transport.stopCount);
    }
    
    @Test
    void pendingActivationFailureAfterUnsubscribeCannotRestoreIntent() throws Exception {
        TestListener listener = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        transport.startHook =
            (registration, callback) -> manager.unsubscribe(reference("agent-a"), null, listener);
        transport.startRuntimeFailure = new IllegalStateException("late failure");
        manager.subscribe(reference("agent-a"), null, listener);
        
        runScheduled(0);
        runCallback(0);
        
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, listener.events.size());
    }
    
    @Test
    void dirtyHintInsidePendingActivationSchedulesRefreshAfterAck() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A))
            .thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        transport.startHook = (registration, callback) -> callback.invalidate(null, true);
        manager.subscribe(reference("agent-a"), null, listener);
        
        runScheduled(0);
        assertEquals(2, scheduled.size());
        runScheduled(1);
        runCallback(0);
        runCallback(1);
        runCallback(2);
        
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        assertEquals("1.0.0", listener.events.get(1).getAgentDiscoveryResult().getVersion());
        assertEquals("2.0.0", listener.events.get(2).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void dirtyHintsMergeAndCanonicalFingerprintSuppressesDuplicateCallback() throws Exception {
        final double unchangedBefore = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
            AgentWatchClientMetrics.Result.UNCHANGED);
        AgentDiscoveryResult initial = result("1.0.0", DIGEST_A);
        AgentDiscoveryResult changed = result("2.0.0", DIGEST_B);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(initial).thenReturn(changed).thenReturn(changed);
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        AgentWatchTransportCallback signal = transport.onlyCallback();
        
        assertTrue(signal.invalidate("different", false));
        assertTrue(signal.invalidate(null, true));
        assertEquals(1, scheduled.size());
        runScheduled(0);
        assertEquals(1, transport.updateCount);
        runCallback(0);
        assertEquals(1, listener.events.size());
        
        String latestFingerprint = transport.onlyRegistration().getMaterializedFingerprint();
        assertEquals(AgentDiscoveryCanonicalizer.fingerprint(changed), latestFingerprint);
        assertTrue(signal.invalidate(latestFingerprint, false));
        assertEquals(1, scheduled.size());
        assertTrue(signal.invalidate(latestFingerprint, true));
        runScheduled(1);
        assertEquals(1, listener.events.size());
        assertEquals(unchangedBefore + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
            AgentWatchClientMetrics.Result.UNCHANGED));
        verify(clientProxy, times(3)).discoverAgent(any(AgentDiscoveryRequest.class));
    }
    
    @Test
    void concurrentHintDuringRefreshSchedulesOneFollowingRefresh() throws Exception {
        AgentDiscoveryResult initial = result("1.0.0", DIGEST_A);
        AgentDiscoveryResult changed = result("2.0.0", DIGEST_B);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(initial)
            .thenAnswer(invocation -> {
                assertTrue(transport.onlyCallback().invalidate(null, true));
                assertTrue(transport.onlyCallback().invalidate(null, true));
                return changed;
            }).thenReturn(changed);
        manager.subscribe(reference("agent-a"), null, new TestListener(null));
        transport.onlyCallback().invalidate(null, true);
        
        runScheduled(0);
        assertEquals(2, scheduled.size());
        runScheduled(1);
        assertEquals(2, scheduled.size());
    }
    
    @Test
    void transientFailureRetainsSnapshotAndDirtyUntilBoundedRetry() throws Exception {
        double failedRefreshesBefore = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
            AgentWatchClientMetrics.Result.FAILED);
        double retriesBefore = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.RETRY,
            AgentWatchClientMetrics.Result.SCHEDULED);
        AgentDiscoveryResult initial = result("1.0.0", DIGEST_A);
        AgentDiscoveryResult changed = result("2.0.0", DIGEST_B);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(initial)
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"))
            .thenReturn(changed);
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        
        assertEquals(Arrays.asList(0L, 25L), delays);
        assertEquals(failedRefreshesBefore + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.DISCOVER_REFRESH,
            AgentWatchClientMetrics.Result.FAILED));
        assertEquals(retriesBefore + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.RETRY,
            AgentWatchClientMetrics.Result.SCHEDULED));
        AgentDiscoveryResult retained =
            manager.subscribe(reference("agent-a"), null, listener);
        assertEquals("1.0.0", retained.getVersion());
        assertEquals(0, callbacks.size());
        runScheduled(1);
        runCallback(0);
        assertEquals("2.0.0",
            listener.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void notFoundEntersPendingAndTerminalErrorRemovesRecoveredIntent() throws Exception {
        AgentDiscoveryResult initial = result("1.0.0", DIGEST_A);
        AgentDiscoveryResult recovered = result("2.0.0", DIGEST_B);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(initial).thenThrow(notFound("removed")).thenReturn(recovered)
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "revoked"));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        AgentWatchTransportCallback first = transport.onlyCallback();
        first.invalidate(null, true);
        runScheduled(0);
        runCallback(0);
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        assertEquals(1, transport.stopCount);
        assertFalse(first.invalidate(null, true));
        
        runScheduled(1);
        assertEquals(2, transport.startCount);
        runCallback(1);
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(2);
        runCallback(2);
        assertEvent(listener, 2, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NO_RIGHT);
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(2, transport.stopCount);
        assertFalse(first.invalidate(null, true));
    }
    
    @Test
    void transportUnavailableUsesPendingOrTerminalContract() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        AgentWatchTransportCallback callback = transport.onlyCallback();
        manager.markUnavailable("unknown", NacosException.NOT_FOUND, "ignored", false);
        callback.unavailable(NacosException.CLIENT_ERROR, "temporary", false);
        assertEquals(1, manager.intentCount());
        callback.unavailable(NacosException.NOT_FOUND, "missing", false);
        callback.unavailable(NacosException.NOT_FOUND, "still missing", false);
        runCallback(0);
        assertEquals(1, manager.intentCount());
        assertEquals(1, scheduled.size());
        
        callback.unavailable(NacosException.OVER_THRESHOLD, "capacity", true);
        runCallback(1);
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.OVER_THRESHOLD);
        assertEquals(0, manager.intentCount());
    }
    
    @Test
    void listenerExecutorsFailuresAndQueuedShutdownAreIsolated() throws Exception {
        final double failedCallbacksBefore = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.FAILED);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenReturn(result("2.0.0", DIGEST_B));
        HoldingExecutor slowExecutor = new HoldingExecutor();
        Executor rejectedExecutor = mock(Executor.class);
        doThrow(new RejectedExecutionException("rejected")).when(rejectedExecutor)
            .execute(any(Runnable.class));
        TestListener slow = new TestListener(slowExecutor);
        slow.fail = true;
        TestListener rejected = new TestListener(rejectedExecutor);
        TestListener normal = new TestListener(null);
        AgentReference reference = reference("agent-a");
        manager.subscribe(reference, null, slow);
        manager.subscribe(reference, null, rejected);
        manager.subscribe(reference, null, normal);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        
        runCallback(0);
        runCallback(1);
        runCallback(2);
        assertEquals(0, slow.events.size());
        assertEquals(0, rejected.events.size());
        assertEquals(1, normal.events.size());
        slowExecutor.runHeld();
        assertEquals(1, slow.events.size());
        assertEquals(failedCallbacksBefore + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.FAILED));
        
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("3.0.0", DIGEST_A));
        transport.onlyCallback().invalidate(null, true);
        runScheduled(1);
        int queuedBeforeShutdown = callbacks.size();
        manager.shutdown();
        for (int i = 3; i < queuedBeforeShutdown; i++) {
            runCallback(i);
        }
        assertEquals(1, normal.events.size());
    }
    
    @Test
    void listenerNotificationsRemainOrderedUntilThePreviousCallbackCompletes()
        throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenReturn(result("2.0.0", DIGEST_B))
            .thenReturn(result("3.0.0", DIGEST_A));
        HoldingExecutor listenerExecutor = new HoldingExecutor();
        TestListener listener = new TestListener(listenerExecutor);
        manager.subscribe(reference("agent-a"), null, listener);
        
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        runCallback(0);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(1);
        
        assertEquals(0, listener.events.size());
        assertEquals(1, callbacks.size());
        listenerExecutor.runHeld();
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals(2, callbacks.size());
        runCallback(1);
        listenerExecutor.runHeld();
        assertEquals("3.0.0", listener.events.get(1).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void rejectedFollowupCallbackDropsOnlyQueuedNotifications() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenReturn(result("2.0.0", DIGEST_B))
            .thenReturn(result("3.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(1);
        doThrow(new RejectedExecutionException("closed")).when(callbackExecutor)
            .execute(any(Runnable.class));
        
        runCallback(0);
        
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals(1, manager.intentCount());
    }
    
    @Test
    void capacityIsIdempotentAtomicAndReusable() throws Exception {
        double rejectionsBefore = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.CAPACITY_REJECTION,
            AgentWatchClientMetrics.Result.REJECTED);
        manager = new AgentWatchManager("public", clientProxy, refreshExecutor,
            callbackExecutor, 2, transport, new FixedRetryPolicy());
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        AgentReference reference = reference("agent-a");
        TestListener first = new TestListener(null);
        TestListener second = new TestListener(null);
        TestListener third = new TestListener(null);
        manager.subscribe(reference, null, first);
        manager.subscribe(reference, null, second);
        manager.subscribe(reference, null, first);
        
        NacosApiException rejected = assertThrows(NacosApiException.class,
            () -> manager.subscribe(reference, null, third));
        assertEquals(NacosException.CLIENT_OVER_THRESHOLD, rejected.getErrCode());
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            rejected.getDetailErrCode());
        assertEquals(rejectionsBefore + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.CAPACITY_REJECTION,
            AgentWatchClientMetrics.Result.REJECTED));
        verify(clientProxy).discoverAgent(any(AgentDiscoveryRequest.class));
        assertEquals(2, manager.subscriptionCount());
        
        manager.unsubscribe(reference, null, first);
        manager.subscribe(reference, null, third);
        assertEquals(2, manager.subscriptionCount());
        assertEquals(1, manager.intentCount());
    }
    
    @Test
    void activationAndInvalidResultFailuresCleanRejectedState() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        transport.startFailure = new NacosException(NacosException.NO_RIGHT, "denied");
        assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"), null, new TestListener(null)));
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        
        transport.startFailure = null;
        transport.startRuntimeFailure = new IllegalStateException("closed");
        NacosException wrapped = assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"), filter(Collections.singletonList("b"),
                null), new TestListener(null)));
        assertEquals(NacosException.CLIENT_ERROR, wrapped.getErrCode());
        assertEquals(0, manager.intentCount());
        
        transport.startRuntimeFailure = null;
        AgentDiscoveryResult invalid = result("1.0.0", DIGEST_A);
        invalid.setContentDigest("invalid");
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenReturn(invalid);
        assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"), filter(Collections.singletonList("c"),
                null), new TestListener(null)));
        assertEquals(0, manager.intentCount());
        
        AgentDiscoveryResult mismatched = result("1.0.0", DIGEST_A);
        mismatched.setAgentName("agent-other");
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(mismatched);
        NacosException identityMismatch = assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"),
                filter(Collections.singletonList("mismatch"), null),
                new TestListener(null)));
        assertEquals(NacosException.SERVER_ERROR, identityMismatch.getErrCode());
        assertEquals(0, manager.intentCount());
    }
    
    @Test
    void pendingTerminalDiscoverFailureRemovesIntentAndNotifiesListener() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing"))
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "denied"));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        
        runScheduled(0);
        runCallback(0);
        runCallback(1);
        
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NO_RIGHT);
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
    }
    
    @Test
    void initialPendingSchedulingRejectionCleansLocalState() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing"));
        when(refreshExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RejectedExecutionException("closed"));
        
        NacosException initial = assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"), null, new TestListener(null)));
        assertEquals(NacosException.CLIENT_ERROR, initial.getErrCode());
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
    }
    
    @Test
    void pendingRetrySchedulingRejectionEmitsTerminalUnavailable() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenThrow(notFound("still missing"));
        TestListener pending = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, pending);
        when(refreshExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RejectedExecutionException("closed"));
        runScheduled(0);
        runCallback(0);
        runCallback(1);
        assertEvent(pending, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.CLIENT_ERROR);
        assertEquals(0, manager.intentCount());
    }
    
    @Test
    void refreshRetrySchedulingRejectionEmitsUnavailableAndRemovesIntent()
        throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        when(refreshExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RejectedExecutionException("closed"));
        
        runScheduled(0);
        runCallback(0);
        
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.CLIENT_ERROR);
        assertEquals(0, manager.intentCount());
    }
    
    @Test
    void pendingTransportActivationRuntimeFailureReturnsToPending() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A))
            .thenThrow(notFound("missing again"));
        TestListener transientListener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, transientListener);
        transport.startRuntimeFailure = new IllegalStateException("temporarily closed");
        runScheduled(0);
        assertEquals(2, scheduled.size());
        assertEquals(1, manager.intentCount());
        assertEquals(0, transport.registrations.size());
        transport.startRuntimeFailure = null;
        runScheduled(1);
        runCallback(0);
        runCallback(1);
        assertEvent(transientListener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        assertEvent(transientListener, 1, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
    }
    
    @Test
    void initialTransportNotFoundReturnsPendingWithoutLeakingWireState() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        transport.startFailure = notFound("removed between Discover and Watch admission");
        TestListener listener = new TestListener(null);
        
        AgentDiscoveryResult current =
            manager.subscribe(reference("agent-a"), null, listener);
        
        assertNull(current);
        assertEquals(1, manager.intentCount());
        assertEquals(1, manager.subscriptionCount());
        assertEquals(1, scheduled.size());
        assertTrue(transport.registrations.isEmpty());
        assertTrue(transport.callbacks.isEmpty());
        runCallback(0);
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
    }
    
    @Test
    void pendingActivationRetrySchedulingRejectionRemovesIntent() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.startRuntimeFailure = new IllegalStateException("temporarily closed");
        when(refreshExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RejectedExecutionException("closed"));
        
        runScheduled(0);
        runCallback(0);
        runCallback(1);
        
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.CLIENT_ERROR);
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
    }
    
    @Test
    void pendingTransportActivationTerminalFailureRemovesIntent() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        TestListener terminalListener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, terminalListener);
        transport.startFailure =
            new NacosException(NacosException.CLIENT_INVALID_PARAM, "rejected");
        runScheduled(0);
        runCallback(0);
        runCallback(1);
        
        assertEvent(terminalListener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.CLIENT_INVALID_PARAM);
        assertEquals(0, manager.intentCount());
    }
    
    @Test
    void transportUpdateFailureCannotBlockSnapshotDelivery() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.updateRuntimeFailure = new IllegalStateException("update failed");
        
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        runCallback(0);
        
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals(1, manager.intentCount());
    }
    
    @Test
    void refreshSuccessAfterUnsubscribeCannotRestoreCanceledIntent() throws Exception {
        TestListener listener = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenAnswer(invocation -> {
                manager.unsubscribe(reference("agent-a"), null, listener);
                return result("2.0.0", DIGEST_B);
            });
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        
        runScheduled(0);
        
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, callbacks.size());
    }
    
    @Test
    void refreshFailureAfterUnsubscribeCannotRestoreCanceledIntent() throws Exception {
        TestListener listener = new TestListener(null);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenAnswer(invocation -> {
                manager.unsubscribe(reference("agent-a"), null, listener);
                throw new NacosException(NacosException.SERVER_ERROR, "late failure");
            });
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        
        runScheduled(0);
        
        assertEquals(0, manager.intentCount());
        assertEquals(0, manager.subscriptionCount());
        assertEquals(0, callbacks.size());
    }
    
    @Test
    void canceledQueuedRefreshAndRejectedNotFoundRetryStayClosed() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenThrow(notFound("removed"));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        AgentWatchTransportCallback callback = transport.onlyCallback();
        callback.invalidate(null, true);
        manager.unsubscribe(reference("agent-a"), null, listener);
        runScheduled(0);
        assertEquals(0, manager.intentCount());
        
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenThrow(notFound("removed"));
        TestListener rejected = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, rejected);
        transport.onlyCallback().invalidate(null, true);
        when(refreshExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RejectedExecutionException("closed"));
        runScheduled(1);
        runCallback(0);
        
        assertEvent(rejected, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.CLIENT_ERROR);
        assertEquals(0, manager.intentCount());
    }
    
    @Test
    void callbackExecutorRejectionCannotCorruptRefreshedSnapshot() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        doThrow(new RejectedExecutionException("closed")).when(callbackExecutor)
            .execute(any(Runnable.class));
        
        runScheduled(0);
        
        assertEquals(1, manager.intentCount());
        assertEquals(0, listener.events.size());
        assertEquals(1, transport.updateCount);
    }
    
    @Test
    void invalidInputsAndConstructorBoundsAreRejected() throws Exception {
        AgentWatchManager defaultManager = new AgentWatchManager("public", clientProxy);
        defaultManager.shutdown();
        assertThrows(IllegalArgumentException.class,
            () -> new AgentWatchManager("public", clientProxy, refreshExecutor,
                callbackExecutor, 0, transport, new FixedRetryPolicy()));
        assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"), null, null));
        AgentReference invalid = reference("agent-a");
        invalid.setVersion("1.0.0");
        invalid.setLabel("latest");
        assertThrows(NacosException.class,
            () -> manager.unsubscribe(invalid, null, null));
        manager.unsubscribe(reference("agent-a"), null, null);
        manager.unsubscribe(reference("unknown"), null, new TestListener(null));
        assertThrows(NacosApiException.class,
            () -> manager.subscribe(reference("agent-a"),
                filter(Collections.singletonList(null), null), new TestListener(null)));
        
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenReturn(null);
        assertNull(manager.subscribe(reference("agent-a"), null, new TestListener(null)));
        assertEquals(1, manager.intentCount());
    }
    
    private void runScheduled(int index) {
        scheduled.get(index).run();
    }
    
    private void runCallback(int index) {
        callbacks.get(index).run();
    }
    
    private Thread subscribeThread(TestListener listener,
        AtomicReference<AgentDiscoveryResult> result, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                result.set(manager.subscribe(reference("agent-a"), null, listener));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
    }
    
    private FakeTransport.StartHook blockingHook(CountDownLatch entered,
        CountDownLatch release) {
        return (registration, callback) -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NacosException(NacosException.CLIENT_ERROR, "interrupted", e);
            }
        };
    }
    
    private boolean awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING) {
                return true;
            }
            Thread.sleep(5L);
        }
        return false;
    }
    
    private void assertEvent(TestListener listener, int index,
        NacosAgentDiscoveryEventType type, Integer errorCode) {
        NacosAgentDiscoveryEvent event = listener.events.get(index);
        assertEquals(type, event.getType());
        assertEquals(errorCode, event.getErrorCode());
    }
    
    private AgentReference reference(String agentName) {
        AgentReference result = new AgentReference();
        result.setAgentName(agentName);
        return result;
    }
    
    private AgentDiscoveryFilter filter(List<String> protocols,
        List<EndpointSource> sources) {
        AgentDiscoveryFilter result = new AgentDiscoveryFilter();
        result.setProtocols(protocols);
        result.setTransports(Arrays.asList("http", "grpc"));
        result.setEndpointSources(sources);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("zone", "east");
        result.setMetadataSelector(metadata);
        return result;
    }
    
    private AgentDiscoveryResult result(String version, String digest) {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("agent-a");
        result.setVersion(version);
        result.setContentDigest(digest);
        result.setCallInterfaces(Collections.emptyList());
        return result;
    }
    
    private NacosException notFound(String message) {
        return new NacosException(NacosException.NOT_FOUND, message);
    }
    
    private static final class FixedRetryPolicy implements AgentWatchRetryPolicy {
        
        @Override
        public long nextDelayMillis(int failureCount, String watchKey) {
            return 25L * failureCount;
        }
    }
    
    private static final class FakeTransport implements AgentWatchTransport {
        
        private final Map<String, AgentWatchRegistration> registrations =
            new LinkedHashMap<String, AgentWatchRegistration>();
        
        private final Map<String, AgentWatchTransportCallback> callbacks =
            new LinkedHashMap<String, AgentWatchTransportCallback>();
        
        private int startCount;
        
        private int updateCount;
        
        private int stopCount;
        
        private boolean closed;
        
        private NacosException startFailure;
        
        private RuntimeException startRuntimeFailure;
        
        private RuntimeException updateRuntimeFailure;
        
        private StartHook startHook;
        
        @Override
        public void start(AgentWatchRegistration registration,
            AgentWatchTransportCallback callback) throws NacosException {
            startCount++;
            if (startHook != null) {
                startHook.run(registration, callback);
            }
            if (startFailure != null) {
                throw startFailure;
            }
            if (startRuntimeFailure != null) {
                throw startRuntimeFailure;
            }
            registrations.put(registration.getClientWatchId(), registration);
            callbacks.put(registration.getClientWatchId(), callback);
        }
        
        @Override
        public void update(AgentWatchRegistration registration) {
            updateCount++;
            if (updateRuntimeFailure != null) {
                throw updateRuntimeFailure;
            }
            registrations.put(registration.getClientWatchId(), registration);
        }
        
        @Override
        public void stop(String clientWatchId) {
            if (registrations.remove(clientWatchId) != null) {
                stopCount++;
            }
            callbacks.remove(clientWatchId);
        }
        
        @Override
        public void shutdown() {
            closed = true;
            registrations.clear();
            callbacks.clear();
        }
        
        private AgentWatchRegistration onlyRegistration() {
            return registrations.values().iterator().next();
        }
        
        private AgentWatchTransportCallback onlyCallback() {
            return callbacks.values().iterator().next();
        }
        
        private interface StartHook {
            
            void run(AgentWatchRegistration registration,
                AgentWatchTransportCallback callback) throws NacosException;
        }
    }
    
    private static final class TestListener extends AbstractNacosAgentDiscoveryListener {
        
        private final Executor executor;
        
        private final List<NacosAgentDiscoveryEvent> events =
            new ArrayList<NacosAgentDiscoveryEvent>();
        
        private boolean fail;
        
        private TestListener(Executor executor) {
            this.executor = executor;
        }
        
        @Override
        public void onEvent(NacosAgentDiscoveryEvent event) {
            events.add(event);
            if (fail) {
                throw new IllegalStateException("listener failure");
            }
        }
        
        @Override
        public Executor getExecutor() {
            return executor;
        }
    }
    
    private static final class HoldingExecutor implements Executor {
        
        private Runnable held;
        
        @Override
        public void execute(Runnable command) {
            held = command;
        }
        
        private void runHeld() {
            held.run();
        }
    }
}
