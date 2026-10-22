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
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentClientProxy;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    
    private final List<Runnable> listenerChecks = new ArrayList<Runnable>();
    
    private final List<ScheduledFuture<?>> listenerCheckFutures = new ArrayList<>();
    
    private FakeTransport transport;
    
    private AgentWatchManager manager;
    
    private final AtomicLong clock = new AtomicLong();
    
    private final AtomicReference<Runnable> submissionRetry = new AtomicReference<Runnable>();
    
    private AgentWatchTaskScheduler taskScheduler;
    
    @BeforeEach
    void setUp() {
        lenient().when(refreshExecutor.schedule(any(Runnable.class), anyLong(),
            any(TimeUnit.class))).thenAnswer(
                invocation -> captureScheduled(
                    invocation.getArgument(0), invocation.getArgument(1)));
        lenient().doAnswer(invocation -> {
            callbacks.add(invocation.getArgument(0));
            return null;
        }).when(callbackExecutor).execute(any(Runnable.class));
        ScheduledExecutorService driver = mock(ScheduledExecutorService.class);
        lenient().when(driver.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
            any(TimeUnit.class))).thenAnswer(invocation -> {
                submissionRetry.set(invocation.getArgument(0));
                return mock(ScheduledFuture.class);
            });
        taskScheduler = new AgentWatchTaskScheduler(driver, clock::get, new FixedRetryPolicy());
        transport = new FakeTransport();
        manager = new AgentWatchManager("public", clientProxy, refreshExecutor,
            callbackExecutor, 300, transport, new FixedRetryPolicy(), taskScheduler);
    }
    
    @AfterEach
    void tearDown() {
        manager.shutdown();
    }
    
    @Test
    void nativeInitialStateIsDeliveredOnceToEachNewListenerOutsideManagerLock() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        AgentReference reference = reference("agent-a");
        TestListener first = new TestListener(null);
        TestListener second = new TestListener(null);
        manager.subscribeAndNotify(reference, null, first);
        manager.subscribeAndNotify(reference, new AgentDiscoveryFilter(), first);
        manager.subscribeAndNotify(reference, null, second);
        assertTrue(first.events.isEmpty());
        drainCallbacks();
        assertEquals(1, first.events.size());
        assertEquals(1, second.events.size());
        assertEquals("1.0.0", first.events.get(0).getAgentDiscoveryResult().getVersion());
        assertNotSame(first.events.get(0).getAgentDiscoveryResult(),
            second.events.get(0).getAgentDiscoveryResult());
        assertEquals(1, transport.startCount);
        AtomicReference<Boolean> lockHeld = new AtomicReference<Boolean>();
        manager.subscribeAndNotify(reference, null, new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                lockHeld.set(Thread.holdsLock(manager));
            }
        });
        drainCallbacks();
        assertEquals(Boolean.FALSE, lockHeld.get());
        assertEquals(1, first.events.size());
    }
    
    @Test
    void newNativeListenerReceivesPendingAbsenceAndThenRecovery() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        AgentReference reference = reference("agent-a");
        TestListener first = new TestListener(null);
        TestListener second = new TestListener(null);
        manager.subscribeAndNotify(reference, null, first);
        manager.subscribeAndNotify(reference, null, second);
        manager.subscribeAndNotify(reference, null, second);
        drainCallbacks();
        assertEquals(1, first.events.size());
        assertEquals(1, second.events.size());
        assertEvent(second, 0, NacosAgentDiscoveryEventType.UNAVAILABLE, NacosException.NOT_FOUND);
        scheduled.remove(0).run();
        drainCallbacks();
        assertEvent(first, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
        assertEvent(second, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @Test
    void activationFingerprintMismatchRefreshesWithoutAnotherBusinessChange() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B),
                result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        transport.startHook = (registration, callback) -> {
            manager.notifyCurrent(reference("agent-a"), null, listener);
            assertTrue(callbacks.isEmpty(), "initialization must wait for activation");
            assertEquals(AgentDiscoveryCanonicalizer.fingerprint(result("1.0.0", DIGEST_A)),
                registration.getMaterializedFingerprint());
            assertTrue(callback.invalidate("new-server-fingerprint", false));
        };
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        manager.markDirty(transport.onlyRegistration().getClientWatchId(), "changed-again", false);
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals("1.0.0", listener.events.get(1).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void refreshBeforeInitializationDoesNotSendDuplicateOrStaleInitialState() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        AgentReference reference = reference("agent-a");
        // Exercise the handoff after subscribe succeeds and before notifyCurrent runs.
        manager.subscribe(reference, null, listener);
        manager.markDirty(transport.onlyRegistration().getClientWatchId(), "newer", false);
        scheduled.remove(0).run();
        drainCallbacks();
        manager.notifyCurrent(reference, null, listener);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void nativeInitialAndLaterEventsStaySerializedWithoutBlockingAnotherListener()
        throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B));
        List<Runnable> slowTasks = new ArrayList<Runnable>();
        TestListener slow = new TestListener(slowTasks::add);
        TestListener normal = new TestListener(null);
        AgentReference reference = reference("agent-a");
        manager.subscribeAndNotify(reference, null, slow);
        manager.subscribeAndNotify(reference, null, normal);
        drainCallbacks();
        assertEquals(1, slowTasks.size());
        manager.markDirty(transport.onlyRegistration().getClientWatchId(), "newer", false);
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(2, normal.events.size());
        assertTrue(slow.events.isEmpty());
        slowTasks.remove(0).run();
        drainCallbacks();
        assertTrue(slowTasks.isEmpty());
        assertEquals(1, slow.events.size());
        assertEquals("2.0.0", slow.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void canceledNativeInitializationCannotReachResubscribedListener() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        AgentReference reference = reference("agent-a");
        manager.subscribeAndNotify(reference, null, listener);
        manager.unsubscribe(reference, null, listener);
        manager.subscribeAndNotify(reference, null, listener);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        TestListener closing = new TestListener(null);
        manager.subscribeAndNotify(reference, null, closing);
        manager.shutdown();
        drainCallbacks();
        assertTrue(closing.events.isEmpty());
    }
    
    @ParameterizedTest
    @ValueSource(ints = {NacosException.NO_RIGHT, NacosException.OVER_THRESHOLD, 50105})
    void rejectedNativeSubscriptionDoesNotInitializeListener(int errorCode) throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(new NacosException(errorCode, "rejected"));
        TestListener listener = new TestListener(null);
        assertEquals(errorCode, assertThrows(NacosException.class,
            () -> manager.subscribeAndNotify(reference("agent-a"), null, listener)).getErrCode());
        assertTrue(callbacks.isEmpty());
        assertEquals(0, manager.subscriptionCount());
    }
    
    @Test
    void absenceBeforeInitializationCannotBeFollowedByStaleInitialSnapshot() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenThrow(notFound("removed"))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        AgentReference reference = reference("agent-a");
        manager.subscribe(reference, null, listener);
        transport.onlyCallback().invalidate(null, true);
        scheduled.remove(0).run();
        manager.notifyCurrent(reference, null, listener);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(2, listener.events.size());
        assertEquals("1.0.0", listener.events.get(1).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void newListenerDoesNotReceiveStaleAbsenceDuringActivationRetry() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        AgentReference reference = reference("agent-a");
        TestListener original = new TestListener(null);
        manager.subscribeAndNotify(reference, null, original);
        drainCallbacks();
        transport.startFailure = new NacosException(NacosException.CLIENT_DISCONNECT, "retry");
        scheduled.remove(0).run();
        TestListener added = new TestListener(null);
        manager.subscribeAndNotify(reference, null, added);
        drainCallbacks();
        assertEquals(1, added.events.size());
        transport.startFailure = null;
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(1, added.events.size());
        assertEvent(added, 0, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    private void drainCallbacks() {
        while (!callbacks.isEmpty()) {
            callbacks.remove(0).run();
        }
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
            .thenThrow(notFound("missing"));
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
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
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
    void absenceRecoveryAndLaterAuthorizationFailureRetainSubscription() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenThrow(notFound("removed"))
            .thenReturn(result("2.0.0", DIGEST_B))
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "revoked"))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        drainCallbacks();
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        runScheduled(1);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
        transport.onlyCallback().invalidate(null, true);
        runScheduled(2);
        drainCallbacks();
        assertEquals(2, listener.events.size());
        assertEquals(1, manager.intentCount());
        assertEquals(1, manager.subscriptionCount());
        runScheduled(3);
        drainCallbacks();
        assertEquals("1.0.0", listener.events.get(2).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void allWireFailuresPreserveDataAndCoalesceRecoveryWithoutCallbacks() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        AgentWatchTransportCallback callback = transport.onlyCallback();
        manager.markUnavailable("unknown", NacosException.NOT_FOUND, "ignored", false);
        callback.unavailable(NacosException.CLIENT_ERROR, "temporary", false);
        callback.unavailable(NacosException.NOT_FOUND, "wire missing", false);
        callback.unavailable(50105, "migration", true);
        callback.unavailable(NacosException.OVER_THRESHOLD, "capacity", true);
        assertEquals(1, manager.intentCount());
        assertEquals(1, scheduled.size());
        assertTrue(callbacks.isEmpty());
        runScheduled(0);
        assertEquals(2, transport.startCount);
        assertTrue(callbacks.isEmpty());
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
    void queuedListenerSelectsLatestStateWhenExecutionStarts()
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
        assertEquals("3.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals(1, callbacks.size());
        assertEquals(1, listener.events.size());
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
    
    @ParameterizedTest
    @ValueSource(ints = {403, 50105})
    void pendingDiscoverRejectionsRetainIntentAndRecover(int code) throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenThrow(new NacosException(code, "denied"))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        drainCallbacks();
        runScheduled(0);
        drainCallbacks();
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        assertEquals(1, listener.events.size());
        assertEquals(1, manager.intentCount());
        runScheduled(1);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @Test
    void initialPendingSubmissionRejectionRecoversWithoutNewSubscribe() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        rejectSubmissions();
        TestListener listener = new TestListener(null);
        assertNull(manager.subscribe(reference("agent-a"), null, listener));
        drainCallbacks();
        assertEquals(1, manager.intentCount());
        resumeSubmissions();
        List<Runnable> retried = new ArrayList<>(scheduled);
        scheduled.clear();
        retried.forEach(Runnable::run);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @Test
    void pendingSubmissionRejectionsDoNotBecomeDataEvents() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenThrow(notFound("still missing"))
            .thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        drainCallbacks();
        rejectSubmissions();
        runScheduled(0);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals(1, manager.intentCount());
        resumeSubmissions();
        runScheduled(1);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @Test
    void refreshSubmissionRepeatedRejectionsEventuallyDeliverChangedData()
        throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"))
            .thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().invalidate(null, true);
        rejectSubmissions();
        runScheduled(0);
        assertTrue(callbacks.isEmpty());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        submissionRetry.get().run();
        assertTrue(callbacks.isEmpty());
        assertEquals(1, manager.intentCount());
        resumeSubmissions();
        runScheduled(1);
        drainCallbacks();
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
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
        drainCallbacks();
        assertEquals(1, transientListener.events.size());
        assertEvent(transientListener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
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
        assertTrue(callbacks.isEmpty());
        transport.startFailure = null;
        runScheduled(0);
        assertEquals(2, transport.startCount);
        assertTrue(callbacks.isEmpty());
    }
    
    @Test
    void activationRetrySubmissionRejectionRetainsConfirmedSnapshotUntilRecovery()
        throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        drainCallbacks();
        transport.startRuntimeFailure = new IllegalStateException("temporarily closed");
        rejectSubmissions();
        runScheduled(0);
        drainCallbacks();
        assertEquals(2, listener.events.size());
        assertEquals(1, manager.intentCount());
        transport.startRuntimeFailure = null;
        resumeSubmissions();
        runScheduled(1);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @Test
    void pendingTransportRejectionRecoversWithoutErrorEvent() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenThrow(notFound("missing")).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        drainCallbacks();
        transport.startFailure =
            new NacosException(NacosException.CLIENT_INVALID_PARAM, "rejected");
        runScheduled(0);
        drainCallbacks();
        assertEquals(2, listener.events.size());
        assertEquals(1, manager.intentCount());
        transport.startFailure = null;
        runScheduled(1);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
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
            NacosException.NOT_FOUND);
        assertEquals(1, manager.intentCount());
        manager.unsubscribe(reference("agent-a"), null, rejected);
        resumeSubmissions();
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
    
    @Test
    void lateEnqueuedSnapshotCannotOverwriteNewerInitialDelivery() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B));
        AgentReference reference = reference("agent-a");
        TestListener listener = new TestListener(null);
        manager.subscribe(reference, null, listener);
        java.lang.reflect.Field field = AgentWatchManager.class.getDeclaredField("intentsById");
        field.setAccessible(true);
        Map<?, ?> intents = (Map<?, ?>) field.get(manager);
        Object intent = intents.values().iterator().next();
        // Capture the handoff between snapshot creation under the manager lock and dispatch.
        java.lang.reflect.Method capture = AgentWatchManager.class.getDeclaredMethod(
            "stateChanged", intent.getClass());
        capture.setAccessible(true);
        List<?> late = (List<?>) capture.invoke(manager, intent);
        manager.markDirty(transport.onlyRegistration().getClientWatchId(), "different", false);
        scheduled.remove(0).run();
        manager.notifyCurrent(reference, null, listener);
        while (!callbacks.isEmpty()) {
            callbacks.remove(0).run();
        }
        int delivered = listener.events.size();
        java.lang.reflect.Field dispatcherField =
            AgentWatchManager.class.getDeclaredField("listenerDispatcher");
        dispatcherField.setAccessible(true);
        AgentWatchListenerDispatcher dispatcher =
            (AgentWatchListenerDispatcher) dispatcherField.get(manager);
        @SuppressWarnings("unchecked")
        List<AgentWatchListenerDispatcher.ListenerRegistration> notifications =
            (List<AgentWatchListenerDispatcher.ListenerRegistration>) late;
        dispatcher.dispatch(notifications);
        while (!callbacks.isEmpty()) {
            callbacks.remove(0).run();
        }
        assertEquals(delivered, listener.events.size());
        assertTrue(listener.events.stream()
            .allMatch(event -> "2.0.0".equals(event.getAgentDiscoveryResult().getVersion())));
    }
    
    @Test
    void initialDeliveryHonorsRemovalAndDoesNotNotifyOtherListener() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A));
        AgentReference reference = reference("agent-a");
        TestListener listener = new TestListener(null);
        TestListener other = new TestListener(null);
        manager.subscribe(reference, null, listener);
        manager.subscribe(reference, null, other);
        manager.notifyCurrent(reference, null, listener);
        manager.unsubscribe(reference, null, listener);
        while (!callbacks.isEmpty()) {
            callbacks.remove(0).run();
        }
        assertTrue(listener.events.isEmpty());
        assertTrue(other.events.isEmpty());
        manager.notifyCurrent(reference, null, listener);
        assertTrue(callbacks.isEmpty());
    }
    
    @Test
    void currentNotificationAndMembershipRespectAbsentPendingAndClosedIntents() throws Exception {
        AgentReference reference = reference("agent-a");
        TestListener listener = new TestListener(null);
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        assertFalse(manager.containsListener(request, listener));
        manager.notifyCurrent(reference, null, listener);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenReturn(null);
        assertNull(manager.subscribe(reference, null, listener));
        assertTrue(manager.containsListener(request, listener));
        assertFalse(manager.containsListener(request, new TestListener(null)));
        int queued = callbacks.size();
        manager.notifyCurrent(reference, null, listener);
        assertEquals(queued, callbacks.size());
        while (!callbacks.isEmpty()) {
            callbacks.remove(0).run();
        }
        assertEquals(1, listener.events.size());
        assertNull(listener.events.get(0).getAgentDiscoveryResult());
        manager.shutdown();
        assertFalse(manager.containsListener(request, listener));
        manager.notifyCurrent(reference, null, listener);
        assertTrue(callbacks.isEmpty());
    }
    
    @Test
    void initialMigrationRejectionDoesNotCreatePendingRetryOrCallback() throws Exception {
        NacosException failure = new NacosException(50105, "migration");
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenThrow(failure);
        assertEquals(50105, assertThrows(NacosException.class,
            () -> manager.subscribe(reference("agent-a"), null, new TestListener(null)))
            .getErrCode());
        assertEquals(0, manager.intentCount());
        assertTrue(scheduled.isEmpty());
        assertTrue(callbacks.isEmpty());
    }
    
    @ParameterizedTest
    @ValueSource(ints = {404, 409, 50105, NacosException.NO_RIGHT, NacosException.OVER_THRESHOLD,
        NacosException.CLIENT_OVER_THRESHOLD, NacosException.INVALID_PARAM,
        NacosException.CLIENT_INVALID_PARAM})
    void backgroundRejectionsDuringRefreshRetryWithoutErrorEvents(int code) throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenThrow(new NacosException(code, "background rejection"))
            .thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        manager.markDirty(transport.onlyRegistration().getClientWatchId(), "different", false);
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(1, manager.intentCount());
        assertTrue(listener.events.isEmpty());
        assertEquals(1, scheduled.size());
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void failedBindingCleanupAndTypedQuotaCannotLoseAcceptedSubscription() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A))
            .thenThrow(new NacosApiException(NacosException.OVER_THRESHOLD,
                ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, "quota"))
            .thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.stopRuntimeFailure = new IllegalStateException("cleanup failed");
        transport.onlyCallback().unavailable(50105, "gate", true);
        runScheduled(0);
        assertTrue(callbacks.isEmpty());
        assertEquals(1, manager.intentCount());
        runScheduled(1);
        drainCallbacks();
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void confirmedDiscoverChangesAreDeliveredEvenWhileBindingRemainsRejected() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B),
                result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        transport.onlyCallback().unavailable(409, "binding ended", true);
        transport.startFailure = new NacosException(403, "binding denied");
        runScheduled(0);
        drainCallbacks();
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        runScheduled(1);
        drainCallbacks();
        assertEquals("1.0.0", listener.events.get(1).getAgentDiscoveryResult().getVersion());
        assertEquals(1, manager.intentCount());
    }
    
    @Test
    void hintDuringFailedTransportUpdateIsRecoveredByOnePendingTask() throws Exception {
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A), result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        AgentWatchTransportCallback callback = transport.onlyCallback();
        transport.updateHook = () -> callback.invalidate(null, true);
        transport.updateRuntimeFailure = new IllegalStateException("update failed");
        callback.invalidate(null, true);
        runScheduled(0);
        assertEquals(2, scheduled.size());
        transport.updateHook = null;
        transport.updateRuntimeFailure = null;
        runScheduled(1);
        drainCallbacks();
        assertEquals(1, listener.events.size());
    }
    
    @Test
    void failedAgentRefreshDoesNotDelayAnotherAcceptedAgent() throws Exception {
        java.util.concurrent.atomic.AtomicInteger callsA =
            new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger callsB =
            new java.util.concurrent.atomic.AtomicInteger();
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenAnswer(invocation -> {
            AgentDiscoveryRequest request = invocation.getArgument(0);
            String name = request.getReference().getAgentName();
            int call = "agent-a".equals(name) ? callsA.incrementAndGet() : callsB.incrementAndGet();
            if ("agent-a".equals(name) && call == 2) {
                throw new NacosException(403, "temporarily denied");
            }
            AgentDiscoveryResult snapshot = call == 1 ? result("1.0.0", DIGEST_A)
                : result("2.0.0", DIGEST_B);
            snapshot.setAgentName(name);
            return snapshot;
        });
        TestListener a = new TestListener(null);
        TestListener b = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, a);
        manager.subscribe(reference("agent-b"), null, b);
        List<AgentWatchTransportCallback> bindings = new ArrayList<>(transport.callbacks.values());
        bindings.get(0).invalidate(null, true);
        bindings.get(1).invalidate(null, true);
        runScheduled(0);
        runScheduled(1);
        drainCallbacks();
        assertTrue(a.events.isEmpty());
        assertEquals("2.0.0", b.events.get(0).getAgentDiscoveryResult().getVersion());
        runScheduled(2);
        drainCallbacks();
        assertEquals("2.0.0", a.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals(2, manager.intentCount());
    }
    
    @Test
    void repeatedBindingFailureDuringPendingDiscoverCannotStrandLaterRecovery() throws Exception {
        AtomicReference<AgentWatchTransportCallback> binding = new AtomicReference<>();
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenAnswer(invocation -> {
                binding.get().unavailable(50105, "repeated late rejection", true);
                binding.get().unavailable(403, "another late rejection", true);
                return result("2.0.0", DIGEST_B);
            }).thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        binding.set(transport.onlyCallback());
        binding.get().unavailable(403, "retry", true);
        runScheduled(0);
        drainCallbacks();
        assertEquals(1, scheduled.size(), "in-flight recovery coalesces repeated rejection");
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        transport.onlyCallback().unavailable(403, "later rejection", true);
        assertEquals(2, scheduled.size(), "later failure must still schedule recovery");
        runScheduled(1);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals(1, manager.intentCount());
        assertNotNull(transport.onlyRegistration());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refreshFromPreviousActivationCannotOverwriteRecoveredState(boolean missing)
        throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls =
            new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<String> watchId = new AtomicReference<>();
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class))).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 2) {
                manager.markUnavailable(watchId.get(), 403, "binding replaced during refresh",
                    true);
                runScheduled(1);
                if (missing) {
                    throw notFound("stale absence");
                }
                return result("1.0.0", DIGEST_A);
            }
            return call == 1 ? result("1.0.0", DIGEST_A) : result("2.0.0", DIGEST_B);
        });
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        watchId.set(transport.onlyRegistration().getClientWatchId());
        manager.markDirty(watchId.get(), null, true);
        runScheduled(0);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
        assertEquals("2.0.0", manager.subscribe(reference("agent-a"), null, listener).getVersion());
        assertEquals(3, calls.get());
        assertEquals(1, manager.intentCount());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateRefreshDuringPendingRecoveryCannotPublishDataOrAbsence(boolean missing)
        throws Exception {
        AtomicReference<String> watchId = new AtomicReference<>();
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(result("1.0.0", DIGEST_A)).thenAnswer(invocation -> {
                manager.markUnavailable(watchId.get(), 403, "binding ended", true);
                if (missing) {
                    throw notFound("late absence from the old binding");
                }
                return result("2.0.0", DIGEST_B);
            }).thenReturn(result("2.0.0", DIGEST_B));
        TestListener listener = new TestListener(null);
        manager.subscribe(reference("agent-a"), null, listener);
        watchId.set(transport.onlyRegistration().getClientWatchId());
        manager.markDirty(watchId.get(), null, true);
        runScheduled(0);
        drainCallbacks();
        assertTrue(listener.events.isEmpty());
        assertEquals(1, manager.intentCount());
        runScheduled(1);
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.0", listener.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void emptyAndUnhealthyEndpointsRemainCompleteSnapshotsAndRepeatedAbsenceIsDeduplicated()
        throws Exception {
        AgentCallInterface call = new AgentCallInterface();
        call.setProtocol("a2a");
        call.setProtocolVersion("0.3.0");
        call.setDescriptorMediaType("application/json");
        call.setNativeDescriptor(Collections.singletonMap("name", "agent-a"));
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://agent.example/rpc");
        endpoint.setTransport("JSONRPC");
        endpoint.setHealthy(false);
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.0");
        binding.setVersionRange("[1.0.0]");
        endpoint.setBindings(Collections.singletonList(binding));
        EndpointSet set = new EndpointSet();
        set.setSource(EndpointSource.RUNTIME);
        set.setSourceRevision("murmur3-x64-128-v1:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        set.setEndpoints(Collections.singletonList(endpoint));
        call.setEndpointSets(Collections.singletonList(set));
        AgentDiscoveryResult unhealthy = result("1.0.0", DIGEST_A);
        unhealthy.setCallInterfaces(Collections.singletonList(call));
        AgentDiscoveryResult empty = result("1.0.0", DIGEST_A);
        when(clientProxy.discoverAgent(any(AgentDiscoveryRequest.class)))
            .thenReturn(unhealthy, empty).thenThrow(notFound("deleted"), notFound("still deleted"))
            .thenReturn(empty);
        TestListener listener = new TestListener(null);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        assertFalse(listener.events.get(0).getAgentDiscoveryResult().getCallInterfaces().get(0)
            .getEndpointSets().get(0).getEndpoints().get(0).getHealthy());
        transport.onlyCallback().invalidate(null, true);
        runScheduled(0);
        drainCallbacks();
        assertEvent(listener, 1, NacosAgentDiscoveryEventType.SNAPSHOT, null);
        assertTrue(listener.events.get(1).getAgentDiscoveryResult().getCallInterfaces().isEmpty());
        transport.onlyCallback().invalidate(null, true);
        runScheduled(1);
        drainCallbacks();
        assertEvent(listener, 2, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        runScheduled(2);
        drainCallbacks();
        assertEquals(3, listener.events.size());
        runScheduled(3);
        drainCallbacks();
        assertEvent(listener, 3, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"callback", "executor", "lookup"})
    void failedInitialDeliveryRecoversWithoutDiscoverOrAnotherChange(String failure)
        throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        AtomicReference<Boolean> reject = new AtomicReference<>(true);
        List<NacosAgentDiscoveryEvent> received = new ArrayList<>();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public Executor getExecutor() {
                if ("lookup".equals(failure) && reject.get()) {
                    throw new IllegalStateException("transient lookup failure");
                }
                return command -> {
                    if ("executor".equals(failure) && reject.get()) {
                        throw new RejectedExecutionException("busy user executor");
                    }
                    command.run();
                };
            }
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                received.add(event);
            }
        };
        doAnswer(call -> {
            if ("callback".equals(failure) && reject.get()) {
                throw new RejectedExecutionException("busy callback executor");
            }
            callbacks.add(call.getArgument(0));
            return null;
        }).when(callbackExecutor).execute(any());
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        assertTrue(received.isEmpty());
        reject.set(false);
        checkListeners();
        drainCallbacks();
        assertEquals(1, received.size());
        assertEquals("1.0.0", received.get(0).getAgentDiscoveryResult().getVersion());
        checkListeners();
        drainCallbacks();
        assertEquals(1, received.size());
        verify(clientProxy, times(1)).discoverAgent(any());
        assertTrue(scheduled.isEmpty());
    }
    
    @Test
    void blockedCallbackCoalescesThousandsOfChangesAndConvergesIndependently()
        throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> received = new ArrayList<>();
        AbstractNacosAgentDiscoveryListener blocked = new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                received.add(event.getAgentDiscoveryResult().getVersion());
                if (received.size() == 1) {
                    entered.countDown();
                    try {
                        assertTrue(release.await(20L, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        };
        manager.subscribeAndNotify(reference("agent-a"), null, blocked);
        Thread callback = new Thread(callbacks.remove(0));
        callback.start();
        try {
            assertTrue(entered.await(2L, TimeUnit.SECONDS));
            TestListener normal = new TestListener(null);
            manager.subscribeAndNotify(reference("agent-a"), null, normal);
            drainCallbacks();
            checkListeners();
            assertTrue(callbacks.isEmpty(),
                "checking an in-flight delivery cannot enqueue another");
            for (int i = 0; i < 2048; i++) {
                refreshTo(result("2.0." + i, DIGEST_B));
                assertEquals(1, callbacks.size(), "only the healthy listener needs a task");
                drainCallbacks();
            }
            assertEquals(2049, normal.events.size());
            assertEquals(1, received.size());
            assertEquals(1, listenerChecks.size(), "one shared delivery check, not one per event");
            clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
            resetLogGate("SLOW_LOG_LIMITER");
            checkListeners();
            checkListeners();
            assertTrue(callbacks.isEmpty());
        } finally {
            release.countDown();
            callback.join(3000L);
        }
        assertFalse(callback.isAlive());
        assertEquals(1, callbacks.size());
        drainCallbacks();
        assertEquals(Arrays.asList("1.0.0", "2.0.2047"), received);
    }
    
    @Test
    void rejectedDeliveryIsRateLimitedAndUsesNewestState() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        doThrow(new RejectedExecutionException("busy")).when(callbackExecutor).execute(any());
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        for (int i = 0; i < 10; i++) {
            refreshTo(result("2.0." + i, DIGEST_B));
        }
        verify(callbackExecutor, times(1)).execute(any());
        doAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return null;
        }).when(callbackExecutor).execute(any());
        checkListeners();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEquals("2.0.9", listener.events.get(0).getAgentDiscoveryResult().getVersion());
    }
    
    @Test
    void rejectedFollowupAfterRunningCallbackRetainsLatestState() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        List<String> received = new ArrayList<>();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                received.add(event.getAgentDiscoveryResult().getVersion());
                if (received.size() == 1) {
                    try {
                        refreshTo(result("2.0.0", DIGEST_B));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    doThrow(new RejectedExecutionException("busy"))
                        .when(callbackExecutor).execute(any());
                }
            }
        };
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        assertEquals(Collections.singletonList("1.0.0"), received);
        doAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return null;
        }).when(callbackExecutor).execute(any());
        checkListeners();
        drainCallbacks();
        assertEquals(Arrays.asList("1.0.0", "2.0.0"), received);
    }
    
    @Test
    void invokedListenerFailureIsNotReplayedButFutureChangesStillArrive() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        listener.fail = true;
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        checkListeners();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        listener.fail = false;
        refreshTo(result("2.0.0", DIGEST_B));
        drainCallbacks();
        assertEquals(2, listener.events.size());
    }
    
    @Test
    void rejectedDeliveryCheckResumesAndCanceledChecksCannotAffectNewSubscription()
        throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        rejectSubmissions();
        TestListener listener = new TestListener(null);
        doThrow(new RejectedExecutionException("busy")).when(callbackExecutor).execute(any());
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        assertTrue(listenerChecks.isEmpty());
        resumeSubmissions();
        doAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return null;
        }).when(callbackExecutor).execute(any());
        // A retried timer whose deadline passed is submitted with zero delay.
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        Runnable obsolete = listenerChecks.remove(0);
        manager.unsubscribe(reference("agent-a"), null, listener);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        obsolete.run();
        drainCallbacks();
        checkListeners();
        drainCallbacks();
        assertEquals(2, listener.events.size());
        assertEquals(1, listenerChecks.size());
    }
    
    @Test
    void queuedAbsenceAndIdenticalRecreationCanCoalesce() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        List<Runnable> held = new ArrayList<>();
        TestListener listener = new TestListener(held::add);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        when(clientProxy.discoverAgent(any())).thenThrow(notFound("deleted"));
        transport.onlyCallback().invalidate(null, true);
        scheduled.remove(0).run();
        doReturn(result("1.0.0", DIGEST_A))
            .when(clientProxy).discoverAgent(any());
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(1, held.size());
        held.remove(0).run();
        assertEquals(1, listener.events.size());
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.SNAPSHOT, null);
    }
    
    @Test
    void executorRunningInlineBeforeThrowingDoesNotReplayCompletedDelivery() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(command -> {
            command.run();
            throw new RejectedExecutionException("thrown after execution");
        });
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        checkListeners();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        refreshTo(result("2.0.0", DIGEST_B));
        drainCallbacks();
        assertEquals(2, listener.events.size());
    }
    
    @Test
    void realDefaultCallbackPoolSaturationDoesNotBlockDiscover() throws Exception {
        manager.shutdown();
        manager = new AgentWatchManager("public", clientProxy, 300, transport);
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        BlockingQueue<NacosAgentDiscoveryEvent> received =
            new LinkedBlockingQueue<>();
        try {
            for (int i = 0; i < 2; i++) {
                manager.subscribeAndNotify(reference("agent-a"), null,
                    new AbstractNacosAgentDiscoveryListener() {
                        
                        @Override
                        public void onEvent(NacosAgentDiscoveryEvent event) {
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
            }
            assertTrue(entered.await(3L, TimeUnit.SECONDS));
            AbstractNacosAgentDiscoveryListener queued = new AbstractNacosAgentDiscoveryListener() {
                
                @Override
                public void onEvent(NacosAgentDiscoveryEvent event) {
                    received.add(event);
                }
            };
            manager.subscribeAndNotify(reference("agent-a"), null, queued);
            CountDownLatch refreshed = new CountDownLatch(1);
            transport.updateHook = refreshed::countDown;
            when(clientProxy.discoverAgent(any())).thenReturn(result("2.0.0", DIGEST_B));
            assertTrue(transport.onlyCallback().invalidate(null, true));
            assertTrue(refreshed.await(3L, TimeUnit.SECONDS),
                "Discover must not wait for user I/O");
            assertEquals("2.0.0",
                manager.subscribe(reference("agent-a"), null, queued).getVersion());
            assertTrue(received.isEmpty(), "the bounded shared pool may delay another listener");
            release.countDown();
            NacosAgentDiscoveryEvent event = received.poll(3L, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals("2.0.0", event.getAgentDiscoveryResult().getVersion());
        } finally {
            release.countDown();
            manager.shutdown();
        }
    }
    
    @Test
    void snapshotPreparationFailureRetainsDeliveryWithoutRequery() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        try (MockedStatic<AgentModelUtils> copier =
            mockStatic(AgentModelUtils.class)) {
            copier.when(() -> AgentModelUtils.copyDiscoveryResult(any()))
                .thenThrow(new IllegalStateException("controlled snapshot copy failure"));
            drainCallbacks();
        }
        assertTrue(listener.events.isEmpty());
        checkListeners();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        verify(clientProxy, times(1)).discoverAgent(any());
    }
    
    @Test
    void cancelDuringExecutorLookupSuppressesLateTaskAndRetainsOtherListener() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        List<Runnable> held = new ArrayList<>();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public Executor getExecutor() {
                try {
                    manager.unsubscribe(reference("agent-a"), null, this);
                } catch (NacosException e) {
                    throw new IllegalStateException(e);
                }
                return held::add;
            }
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                org.junit.jupiter.api.Assertions.fail("canceled listener cannot be invoked");
            }
        };
        TestListener normal = new TestListener(null);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        manager.subscribeAndNotify(reference("agent-a"), null, normal);
        drainCallbacks();
        held.remove(0).run();
        checkListeners();
        drainCallbacks();
        assertEquals(1, normal.events.size());
        assertEquals(1, manager.subscriptionCount());
    }
    
    @Test
    void diagnosticPathsPreserveAbsenceAndCleanupSemantics() throws Exception {
        resetLogGate("BACKGROUND_LOG_LIMITER");
        when(clientProxy.discoverAgent(any())).thenThrow(notFound("missing"));
        TestListener listener = new TestListener(null);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        scheduled.remove(0).run();
        drainCallbacks();
        assertEquals(1, listener.events.size());
        assertEvent(listener, 0, NacosAgentDiscoveryEventType.UNAVAILABLE,
            NacosException.NOT_FOUND);
        resetLogGate("BINDING_LOG_LIMITER");
        transport.stopRuntimeFailure = new IllegalStateException("controlled cleanup failure");
        manager.unsubscribe(reference("agent-a"), null, listener);
        assertEquals(0, manager.subscriptionCount());
    }
    
    private void resetLogGate(String name) throws Exception {
        Class<?> owner = "BACKGROUND_LOG_LIMITER".equals(name)
            || "BINDING_LOG_LIMITER".equals(name) ? AgentWatchManager.class
                : AgentWatchListenerDispatcher.class;
        java.lang.reflect.Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        Object limiter = field.get(null);
        java.lang.reflect.Field next = limiter.getClass().getDeclaredField("nextAllowedNanos");
        next.setAccessible(true);
        ((AtomicLong) next.get(limiter)).set(Long.MIN_VALUE);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"callback", "executor"})
    void staleTaskFromExecutorThatEnqueuesThenThrowsCannotDuplicateDelivery(String stage)
        throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        List<Runnable> held = new ArrayList<>();
        AtomicReference<Boolean> rejecting = new AtomicReference<>(true);
        Executor executor = command -> {
            held.add(command);
            if (rejecting.get()) {
                throw new RejectedExecutionException("accepted before failure");
            }
        };
        if ("callback".equals(stage)) {
            doAnswer(call -> {
                executor.execute(call.getArgument(0));
                return null;
            }).when(callbackExecutor).execute(any());
        }
        TestListener listener = new TestListener("executor".equals(stage) ? executor : null);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        assertEquals(1, held.size());
        rejecting.set(false);
        checkListeners();
        drainCallbacks();
        assertEquals(2, held.size());
        held.get(0).run();
        assertTrue(listener.events.isEmpty(), "old attempt cannot consume a new delivery slot");
        held.get(1).run();
        held.get(1).run();
        assertEquals(1, listener.events.size());
    }
    
    @Test
    void lateExecutorFailureCannotResetFollowupAlreadyQueuedByCallback() throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        List<String> received = new ArrayList<>();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            
            @Override
            public Executor getExecutor() {
                return command -> {
                    command.run();
                    throw new RejectedExecutionException("late failure");
                };
            }
            
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                received.add(event.getAgentDiscoveryResult().getVersion());
                if (received.size() == 1) {
                    try {
                        refreshTo(result("2.0.0", DIGEST_B));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        };
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        assertEquals(Arrays.asList("1.0.0", "2.0.0"), received);
        checkListeners();
        drainCallbacks();
        assertEquals(2, received.size());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelOrShutdownDuringRetrySubmissionLeavesNoDelivery(boolean shutdown) throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        doThrow(new RejectedExecutionException("busy")).when(callbackExecutor).execute(any());
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        doAnswer(call -> {
            if (shutdown) {
                manager.shutdown();
            } else {
                manager.unsubscribe(reference("agent-a"), null, listener);
            }
            throw new RejectedExecutionException("canceled during submission");
        }).when(callbackExecutor).execute(any());
        checkListeners();
        assertTrue(listener.events.isEmpty());
        assertEquals(0, manager.intentCount());
        assertEquals(1, listenerCheckFutures.size() - listenerChecks.size());
        if (!listenerChecks.isEmpty()) {
            checkListeners();
        }
        assertTrue(callbacks.isEmpty());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void alreadyRunningDeliveryCheckCannotRescheduleAfterCancellation(boolean shutdown)
        throws Exception {
        when(clientProxy.discoverAgent(any())).thenReturn(result("1.0.0", DIGEST_A));
        TestListener listener = new TestListener(null);
        manager.subscribeAndNotify(reference("agent-a"), null, listener);
        drainCallbacks();
        Thread staleCheck = new Thread(listenerChecks.remove(0));
        synchronized (manager) {
            staleCheck.start();
            assertTrue(awaitState(staleCheck, Thread.State.BLOCKED));
            if (shutdown) {
                manager.shutdown();
            } else {
                manager.unsubscribe(reference("agent-a"), null, listener);
                manager.subscribeAndNotify(reference("agent-a"), null, listener);
            }
        }
        staleCheck.join(3000L);
        assertFalse(staleCheck.isAlive());
        drainCallbacks();
        if (shutdown) {
            assertTrue(listenerChecks.isEmpty());
            assertEquals(1, listener.events.size());
        } else {
            assertEquals(1, listenerChecks.size());
            checkListeners();
            drainCallbacks();
            assertEquals(2, listener.events.size());
            assertEquals(1, listenerChecks.size());
        }
    }
    
    private void refreshTo(AgentDiscoveryResult latest) throws NacosException {
        when(clientProxy.discoverAgent(any())).thenReturn(latest);
        transport.onlyCallback().invalidate(null, true);
        scheduled.remove(0).run();
    }
    
    private void rejectSubmissions() {
        when(refreshExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RejectedExecutionException("busy"));
    }
    
    private void resumeSubmissions() {
        lenient().doAnswer(invocation -> captureScheduled(
            invocation.getArgument(0), invocation.getArgument(1)))
            .when(refreshExecutor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
        submissionRetry.get().run();
    }
    
    private ScheduledFuture<?> captureScheduled(Runnable command, long delay) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        if (delay == AgentWatchListenerDispatcher.LISTENER_CHECK_INTERVAL_MILLIS) {
            listenerChecks.add(command);
            listenerCheckFutures.add(future);
        } else {
            scheduled.add(command);
            delays.add(delay);
            futures.add(future);
        }
        return future;
    }
    
    private void checkListeners() {
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1L));
        listenerChecks.remove(0).run();
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
        return awaitState(thread, Thread.State.WAITING);
    }
    
    private boolean awaitState(Thread thread, Thread.State state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == state) {
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
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            message);
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
        
        private RuntimeException stopRuntimeFailure;
        
        private Runnable updateHook;
        
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
            if (updateHook != null) {
                updateHook.run();
            }
            updateCount++;
            if (updateRuntimeFailure != null) {
                throw updateRuntimeFailure;
            }
            registrations.put(registration.getClientWatchId(), registration);
        }
        
        @Override
        public void stop(String clientWatchId) {
            if (stopRuntimeFailure != null) {
                throw stopRuntimeFailure;
            }
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
