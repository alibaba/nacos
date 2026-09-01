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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.api.remote.PushCallBack;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentGrpcWatchServiceTest {
    
    private static final String CONNECTION_ID = "connection";
    
    private AgentProjectionService projectionService;
    
    private AgentDiscoveryApplicationService discoveryService;
    
    private AgentWatchOwnerEligibilityChecker ownerEligibilityChecker;
    
    private RpcPushService rpcPushService;
    
    private ConnectionManager connectionManager;
    
    private AgentGrpcWatchService service;
    
    private final AtomicReference<Optional<AgentProjectionState>> projectionState =
        new AtomicReference<Optional<AgentProjectionState>>(Optional.empty());
    
    private final BlockingQueue<PushRecord> pushes = new LinkedBlockingQueue<PushRecord>();
    
    @BeforeEach
    void setUp() throws Exception {
        projectionService = mock(AgentProjectionService.class);
        discoveryService = mock(AgentDiscoveryApplicationService.class);
        ownerEligibilityChecker = mock(AgentWatchOwnerEligibilityChecker.class);
        rpcPushService = mock(RpcPushService.class);
        connectionManager = mock(ConnectionManager.class);
        when(connectionManager.checkValid(anyString())).thenReturn(true);
        when(ownerEligibilityChecker.evaluate(any(), any()))
            .thenReturn(AgentWatchOwnerEligibility.ALLOWED);
        when(projectionService.retain(any()))
            .thenAnswer(invocation -> AgentProjectionKey.of(invocation.getArgument(0)));
        when(projectionService.refreshNow(any()))
            .thenAnswer(invocation -> projectionState.get().orElse(null));
        when(projectionService.getState(any())).thenAnswer(invocation -> projectionState.get());
        doAnswer(invocation -> {
            pushes.add(new PushRecord(invocation.getArgument(1), invocation.getArgument(2)));
            return null;
        }).when(rpcPushService).pushWithCallback(anyString(),
            any(AgentDiscoveryNotifyRequest.class), any(PushCallBack.class), any());
        createService(2);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (service != null) {
            service.shutdown();
        }
    }
    
    @Test
    void testSubscribeIsIdempotentAndUnsubscribeIsConnectionOwned() throws Exception {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        String fingerprint = AgentDiscoveryCanonicalizer.fingerprint(snapshot);
        projectionState.set(Optional.of(AgentProjectionTestFixtures.available(fingerprint, 1L)));
        when(discoveryService.discover(any())).thenReturn(snapshot);
        AgentSubscribeRpcRequest request = request("watch-1", "agent", fingerprint);
        
        AgentSubscribeRpcResponse first = service.subscribe(CONNECTION_ID, request);
        AgentSubscribeRpcResponse duplicate = service.subscribe(CONNECTION_ID, request);
        
        assertTrue(first.isSuccess());
        assertEquals(first.getWatchKey(), duplicate.getWatchKey());
        assertEquals(fingerprint, first.getObservedFingerprint());
        assertFalse(first.isRefreshRequired());
        assertEquals(1, service.size());
        assertEquals(1, service.connectionSize(CONNECTION_ID));
        verify(discoveryService).discover(any());
        verify(projectionService).retain(any());
        verify(projectionService).refreshNow(AgentProjectionTestFixtures.key("agent"));
        
        service.unsubscribe("other", first.getWatchKey());
        assertEquals(1, service.size());
        service.unsubscribe(CONNECTION_ID, first.getWatchKey());
        service.unsubscribe(CONNECTION_ID, first.getWatchKey());
        assertEquals(0, service.size());
        verify(projectionService).release(AgentProjectionTestFixtures.key("agent"));
        assertThrows(IllegalArgumentException.class,
            () -> service.unsubscribe(CONNECTION_ID, "invalid/key"));
    }
    
    @Test
    void testSubscribeValidationConflictAndAdmissionRollback() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> service.subscribe(CONNECTION_ID, null));
        assertThrows(IllegalArgumentException.class,
            () -> service.subscribe(CONNECTION_ID, request("bad/id", "agent", null)));
        AgentSubscribeRpcRequest missingDiscovery = new AgentSubscribeRpcRequest();
        missingDiscovery.setClientWatchId("watch");
        assertThrows(IllegalArgumentException.class,
            () -> service.subscribe(CONNECTION_ID, missingDiscovery));
        assertThrows(IllegalArgumentException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "agent", "invalid")));
        verify(discoveryService, never()).discover(any());
        
        doThrow(new NacosException(NacosException.RESOURCE_NOT_FOUND, "missing"))
            .when(discoveryService).discover(any());
        assertThrows(NacosException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "agent", null)));
        assertEquals(0, service.size());
        
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        doReturn(snapshot).when(discoveryService).discover(any());
        when(connectionManager.checkValid(CONNECTION_ID)).thenReturn(false);
        NacosException disconnected = assertThrows(NacosException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "agent", null)));
        assertEquals(NacosException.CLIENT_DISCONNECT, disconnected.getErrCode());
        assertEquals(0, service.size());
        verify(projectionService, never()).retain(any());
        
        when(connectionManager.checkValid(CONNECTION_ID)).thenReturn(true);
        doThrow(new IllegalStateException("retain failed")).when(projectionService).retain(any());
        assertThrows(IllegalStateException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "agent", null)));
        assertEquals(0, service.size());
        
        reset(projectionService);
        doAnswer(invocation -> AgentProjectionKey.of(invocation.getArgument(0)))
            .when(projectionService).retain(any());
        doThrow(new IllegalStateException("refresh failed"))
            .when(projectionService).refreshNow(any());
        assertThrows(IllegalStateException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "agent", null)));
        assertEquals(0, service.size());
        verify(projectionService).release(AgentProjectionTestFixtures.key("agent"));
    }
    
    @Test
    void testRetainKeyMismatchRollsBackAndCapacityHasNoPartialState() throws Exception {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        when(discoveryService.discover(any())).thenReturn(snapshot);
        doReturn(AgentProjectionTestFixtures.key("different"))
            .when(projectionService).retain(any());
        
        assertThrows(IllegalStateException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "agent", null)));
        assertEquals(0, service.size());
        verify(projectionService).release(AgentProjectionTestFixtures.key("agent"));
        
        service.shutdown();
        reset(projectionService);
        doAnswer(invocation -> AgentProjectionKey.of(invocation.getArgument(0)))
            .when(projectionService).retain(any());
        when(projectionService.refreshNow(any())).thenReturn(null);
        when(projectionService.getState(any())).thenReturn(Optional.empty());
        createService(1);
        when(discoveryService.discover(any())).thenReturn(snapshot);
        service.subscribe(CONNECTION_ID, request("watch-1", "agent", null));
        
        NacosApiException overLimit = assertThrows(NacosApiException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch-2", "other", null)));
        assertEquals(NacosException.OVER_THRESHOLD, overLimit.getErrCode());
        assertEquals(1, service.size());
        assertEquals(1, service.connectionSize(CONNECTION_ID));
        assertThrows(IllegalArgumentException.class,
            () -> new AgentGrpcWatchService(projectionService, discoveryService,
                ownerEligibilityChecker, rpcPushService, connectionManager,
                new AgentGrpcWatchRegistry(), 0, 10L, 1, Runnable::run));
    }
    
    @Test
    void testConflictingClientWatchIdAndSubscribeRaceUseCurrentProjection() throws Exception {
        double bytesBefore = AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.GRPC);
        double pushesBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.GRPC_PUSH, AgentWatchMetrics.Result.SCHEDULED);
        double acknowledgementsBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.GRPC_ACK, AgentWatchMetrics.Result.SUCCESS);
        AgentDiscoveryResult initial = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        String initialFingerprint = AgentDiscoveryCanonicalizer.fingerprint(initial);
        String currentFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(currentFingerprint, 2L)));
        when(discoveryService.discover(any())).thenReturn(initial);
        
        AgentSubscribeRpcResponse response = service.subscribe(CONNECTION_ID,
            request("watch", "agent", initialFingerprint));
        
        assertEquals(currentFingerprint, response.getObservedFingerprint());
        assertTrue(response.isRefreshRequired());
        PushRecord raceHint = awaitPush();
        assertInvalidation(raceHint.request, currentFingerprint);
        assertTrue(AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.GRPC) > bytesBefore);
        assertEquals(pushesBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.GRPC_PUSH, AgentWatchMetrics.Result.SCHEDULED));
        raceHint.callback.onSuccess();
        assertEquals(acknowledgementsBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.GRPC_ACK, AgentWatchMetrics.Result.SUCCESS));
        
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> service.subscribe(CONNECTION_ID, request("watch", "other", null)));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
        assertEquals(1, service.size());
    }
    
    @Test
    void testConcurrentDuplicateSubscribeConvergesToOneWatch() throws Exception {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        String fingerprint = AgentDiscoveryCanonicalizer.fingerprint(snapshot);
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(fingerprint, 1L)));
        CountDownLatch discovering = new CountDownLatch(2);
        CountDownLatch releaseDiscovery = new CountDownLatch(1);
        doAnswer(invocation -> {
            discovering.countDown();
            assertTrue(releaseDiscovery.await(3, TimeUnit.SECONDS));
            return snapshot;
        }).when(discoveryService).discover(any());
        AgentSubscribeRpcRequest request = request("watch", "agent", fingerprint);
        
        CompletableFuture<AgentSubscribeRpcResponse> first = subscribeAsync(request);
        CompletableFuture<AgentSubscribeRpcResponse> second = subscribeAsync(request);
        assertTrue(discovering.await(3, TimeUnit.SECONDS));
        releaseDiscovery.countDown();
        
        assertEquals(first.get(3, TimeUnit.SECONDS).getWatchKey(),
            second.get(3, TimeUnit.SECONDS).getWatchKey());
        assertEquals(1, service.size());
        verify(discoveryService, times(2)).discover(any());
        verify(projectionService).retain(any());
    }
    
    @Test
    void testProjectionStatusesProduceHintOnlyFieldInvariants() throws Exception {
        subscribeWithInitialState("agent", "watch");
        String nextFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(nextFingerprint, 2L)));
        fireUpdate("agent");
        PushRecord invalidation = awaitPush();
        assertInvalidation(invalidation.request, nextFingerprint);
        invalidation.callback.onSuccess();
        
        assertRevalidate("agent", AgentProjectionStatus.ACCESS_UNCERTAIN);
        assertRevalidate("agent", AgentProjectionStatus.CONFLICT);
        assertRevalidate("agent", AgentProjectionStatus.TRANSIENT_FAILURE);
        
        projectionState.set(Optional.of(AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, NacosException.RESOURCE_NOT_FOUND, "missing", 6L)));
        fireUpdate("agent");
        PushRecord terminated = awaitPush();
        assertEquals(AgentWatchEventType.TERMINATED, terminated.request.getEventType());
        assertNull(terminated.request.getObservedFingerprint());
        assertEquals(NacosException.RESOURCE_NOT_FOUND, terminated.request.getErrorCode());
        terminated.callback.onSuccess();
        waitUntil(() -> service.size() == 0);
        verify(projectionService).release(AgentProjectionTestFixtures.key("agent"));
    }
    
    @Test
    void testOwnerRecheckNeverLeaksFingerprintWhenDeniedOrUncertain() throws Exception {
        subscribeWithInitialState("denied-agent", "denied-watch");
        projectionState.set(Optional.of(AgentProjectionTestFixtures.available(
            AgentDiscoveryCanonicalizer.fingerprint(
                AgentProjectionTestFixtures.snapshot("denied-agent", "a2a", "mcp")),
            2L)));
        when(ownerEligibilityChecker.evaluate(any(), any()))
            .thenReturn(AgentWatchOwnerEligibility.DENIED);
        fireUpdate("denied-agent");
        PushRecord denied = awaitPush();
        assertEquals(AgentWatchEventType.TERMINATED, denied.request.getEventType());
        assertNull(denied.request.getObservedFingerprint());
        denied.callback.onSuccess();
        
        when(ownerEligibilityChecker.evaluate(any(), any()))
            .thenReturn(AgentWatchOwnerEligibility.ALLOWED);
        subscribeWithInitialState("uncertain-agent", "uncertain-watch");
        projectionState.set(Optional.of(AgentProjectionTestFixtures.available(
            AgentDiscoveryCanonicalizer.fingerprint(
                AgentProjectionTestFixtures.snapshot("uncertain-agent", "a2a", "mcp")),
            3L)));
        when(ownerEligibilityChecker.evaluate(any(), any()))
            .thenReturn(AgentWatchOwnerEligibility.UNCERTAIN);
        fireUpdate("uncertain-agent");
        PushRecord uncertain = awaitPush();
        assertEquals(AgentWatchEventType.REVALIDATE, uncertain.request.getEventType());
        assertNull(uncertain.request.getObservedFingerprint());
        assertNull(uncertain.request.getErrorCode());
        uncertain.callback.onSuccess();
    }
    
    @Test
    void testRejectedOrFailedAckRetriesCurrentFactAndMissingStateRevalidates()
        throws Exception {
        double failedAcknowledgementsBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.GRPC_ACK, AgentWatchMetrics.Result.FAILED);
        subscribeWithInitialState("agent", "watch");
        String nextFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(nextFingerprint, 2L)));
        fireUpdate("agent");
        PushRecord first = awaitPush();
        first.callback.onFail(new NacosException(NacosException.SERVER_ERROR, "rejected"));
        assertEquals(failedAcknowledgementsBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.GRPC_ACK, AgentWatchMetrics.Result.FAILED));
        
        PushRecord retried = awaitPush();
        assertInvalidation(retried.request, nextFingerprint);
        verify(projectionService).revalidate(AgentProjectionTestFixtures.key("agent"));
        retried.callback.onSuccess();
        
        fireUpdate("agent");
        assertNull(pushes.poll(150L, TimeUnit.MILLISECONDS));
        
        projectionState.set(Optional.empty());
        fireUpdate("agent");
        waitUntil(() -> {
            try {
                verify(projectionService, atLeast(2))
                    .revalidate(AgentProjectionTestFixtures.key("agent"));
                return true;
            } catch (AssertionError ignored) {
                return false;
            }
        });
        String recoveredFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp", "custom"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(recoveredFingerprint, 3L)));
        PushRecord recovered = awaitPush();
        assertInvalidation(recovered.request, recoveredFingerprint);
        recovered.callback.onSuccess();
    }
    
    @Test
    void testSynchronousPushAndRevalidationFailureDoNotStallWatch() throws Exception {
        subscribeWithInitialState("agent", "watch");
        String nextFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(nextFingerprint, 2L)));
        doThrow(new IllegalStateException("projection unavailable")).doNothing()
            .when(projectionService).revalidate(AgentProjectionTestFixtures.key("agent"));
        doThrow(new IllegalStateException("push unavailable")).doAnswer(invocation -> {
            pushes.add(new PushRecord(invocation.getArgument(1), invocation.getArgument(2)));
            return null;
        }).when(rpcPushService).pushWithCallback(anyString(),
            any(AgentDiscoveryNotifyRequest.class), any(PushCallBack.class), any());
        
        fireUpdate("agent");
        
        PushRecord retried = awaitPush();
        assertInvalidation(retried.request, nextFingerprint);
        retried.callback.onSuccess();
        verify(projectionService, atLeast(1))
            .revalidate(AgentProjectionTestFixtures.key("agent"));
        waitUntil(() -> service.pendingTaskCount() == 0);
    }
    
    @Test
    void testDirtyDuringInflightPushSchedulesLatestFactAndDisconnectFailureCleansState()
        throws Exception {
        subscribeWithInitialState("agent", "watch");
        String secondFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(secondFingerprint, 2L)));
        fireUpdate("agent");
        PushRecord first = awaitPush();
        assertInvalidation(first.request, secondFingerprint);
        
        String thirdFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp", "custom"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(thirdFingerprint, 3L)));
        fireUpdate("agent");
        first.callback.onSuccess();
        
        PushRecord latest = awaitPush();
        assertInvalidation(latest.request, thirdFingerprint);
        when(connectionManager.checkValid(CONNECTION_ID)).thenReturn(false);
        latest.callback.onFail(new NacosException(NacosException.CLIENT_DISCONNECT,
            "disconnected"));
        assertEquals(0, service.size());
        verify(projectionService).release(AgentProjectionTestFixtures.key("agent"));
    }
    
    @Test
    void testQueuedDeliveryAfterUnsubscribeIsIgnored() throws Exception {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        String fingerprint = AgentDiscoveryCanonicalizer.fingerprint(snapshot);
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(fingerprint, 1L)));
        when(discoveryService.discover(any())).thenReturn(snapshot);
        AgentSubscribeRpcResponse response = service.subscribe(CONNECTION_ID,
            request("watch", "agent", fingerprint));
        String changedFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(changedFingerprint, 2L)));
        
        fireUpdate("agent");
        service.unsubscribe(CONNECTION_ID, response.getWatchKey());
        
        waitUntil(() -> service.pendingTaskCount() == 0);
        assertNull(pushes.poll(100L, TimeUnit.MILLISECONDS));
    }
    
    @Test
    void testPublicConstructorUsesConfiguredCapacity() throws Exception {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot("agent", "a2a");
        String fingerprint = AgentDiscoveryCanonicalizer.fingerprint(snapshot);
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(fingerprint, 1L)));
        when(discoveryService.discover(any())).thenReturn(snapshot);
        try (MockedStatic<EnvUtil> env = mockStatic(EnvUtil.class)) {
            env.when(() -> EnvUtil.getProperty(
                Constants.Agent.MAX_WATCHES_PER_CLIENT_CONFIG_KEY, Integer.class,
                Constants.Agent.DEFAULT_MAX_WATCHES_PER_CLIENT)).thenReturn(7);
            AgentGrpcWatchService configured = new AgentGrpcWatchService(projectionService,
                discoveryService, ownerEligibilityChecker, rpcPushService, connectionManager);
            try {
                for (int index = 0; index < 7; index++) {
                    configured.subscribe(CONNECTION_ID,
                        request("watch-" + index, "agent", fingerprint));
                }
                NacosApiException overLimit = assertThrows(NacosApiException.class,
                    () -> configured.subscribe(CONNECTION_ID,
                        request("watch-7", "agent", fingerprint)));
                assertEquals(NacosException.OVER_THRESHOLD, overLimit.getErrCode());
                assertEquals(0, configured.pendingTaskCount());
            } finally {
                configured.shutdown();
            }
        }
    }
    
    @Test
    void testSlowWatchDoesNotBlockAnotherAndConnectionLossCleansAllState()
        throws Exception {
        subscribeWithInitialState("first", "watch-1");
        subscribeWithInitialState("second", "watch-2");
        String changedFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("changed", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(changedFingerprint, 2L)));
        
        fireUpdate("first");
        fireUpdate("second");
        PushRecord first = awaitPush();
        PushRecord second = awaitPush();
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.request.getWatchKey().equals(second.request.getWatchKey()));
        
        Connection connection = mock(Connection.class);
        ConnectionMeta meta = mock(ConnectionMeta.class);
        when(connection.getMetaInfo()).thenReturn(meta);
        when(meta.getConnectionId()).thenReturn(CONNECTION_ID);
        service.clientConnected(connection);
        service.clientDisConnected(connection);
        assertEquals(0, service.size());
        verify(projectionService).release(AgentProjectionTestFixtures.key("first"));
        verify(projectionService).release(AgentProjectionTestFixtures.key("second"));
        
        first.callback.onFail(new IllegalStateException("late"));
        second.callback.onSuccess();
        assertEquals(0, service.size());
    }
    
    @Test
    void testInvalidConnectionDuringDeliveryRemovesConnectionState() throws Exception {
        subscribeWithInitialState("agent", "watch");
        String changedFingerprint = AgentDiscoveryCanonicalizer.fingerprint(
            AgentProjectionTestFixtures.snapshot("agent", "a2a", "mcp"));
        projectionState.set(Optional.of(
            AgentProjectionTestFixtures.available(changedFingerprint, 2L)));
        when(connectionManager.checkValid(CONNECTION_ID)).thenReturn(false);
        
        fireUpdate("agent");
        
        waitUntil(() -> service.size() == 0);
        assertNull(pushes.poll(100L, TimeUnit.MILLISECONDS));
        verify(projectionService).release(AgentProjectionTestFixtures.key("agent"));
    }
    
    private void createService(int capacity) {
        service = new AgentGrpcWatchService(projectionService, discoveryService,
            ownerEligibilityChecker, rpcPushService, connectionManager,
            new AgentGrpcWatchRegistry(), capacity, 10L, 2, Runnable::run);
        service.start();
    }
    
    private CompletableFuture<AgentSubscribeRpcResponse> subscribeAsync(
        AgentSubscribeRpcRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return service.subscribe(CONNECTION_ID, request);
            } catch (NacosException e) {
                throw new CompletionException(e);
            }
        });
    }
    
    private String subscribeWithInitialState(String agentName, String clientWatchId)
        throws Exception {
        AgentDiscoveryResult snapshot = AgentProjectionTestFixtures.snapshot(agentName, "a2a");
        String fingerprint = AgentDiscoveryCanonicalizer.fingerprint(snapshot);
        projectionState.set(Optional.of(AgentProjectionTestFixtures.available(fingerprint, 1L)));
        when(discoveryService.discover(any())).thenReturn(snapshot);
        AgentSubscribeRpcResponse response = service.subscribe(CONNECTION_ID,
            request(clientWatchId, agentName, fingerprint));
        assertFalse(response.isRefreshRequired());
        return fingerprint;
    }
    
    private AgentSubscribeRpcRequest request(String clientWatchId, String agentName,
        String materializedFingerprint) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(agentName);
        AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
        discoveryRequest.setNamespaceId("public");
        discoveryRequest.setReference(reference);
        AgentSubscribeRpcRequest result = new AgentSubscribeRpcRequest();
        result.setClientWatchId(clientWatchId);
        result.setDiscoveryRequest(discoveryRequest);
        result.setMaterializedFingerprint(materializedFingerprint);
        return result;
    }
    
    private void fireUpdate(String agentName) {
        AgentProjectionKey key = AgentProjectionTestFixtures.key(agentName);
        AgentProjectionState current = projectionState.get().orElse(
            AgentProjectionState.failure(AgentProjectionStatus.TRANSIENT_FAILURE,
                NacosException.SERVER_ERROR, "missing state", 1L));
        service.onProjectionUpdate(new AgentProjectionUpdate(key, null, current,
            Collections.singleton(AgentProjectionChangeReason.RUNTIME)));
    }
    
    private void assertRevalidate(String agentName, AgentProjectionStatus status)
        throws Exception {
        projectionState.set(Optional.of(AgentProjectionState.failure(status,
            NacosException.SERVER_ERROR, status.name(), System.nanoTime())));
        fireUpdate(agentName);
        PushRecord record = awaitPush();
        assertEquals(AgentWatchEventType.REVALIDATE, record.request.getEventType());
        assertNull(record.request.getObservedFingerprint());
        assertNull(record.request.getErrorCode());
        record.callback.onSuccess();
    }
    
    private void assertInvalidation(AgentDiscoveryNotifyRequest request,
        String expectedFingerprint) {
        assertEquals(AgentWatchEventType.INVALIDATE, request.getEventType());
        assertEquals(expectedFingerprint, request.getObservedFingerprint());
        assertNull(request.getErrorCode());
    }
    
    private PushRecord awaitPush() throws InterruptedException {
        PushRecord result = pushes.poll(3, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(3000L, result.callback.getTimeout());
        return result;
    }
    
    private void waitUntil(Check condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.evaluate() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertTrue(condition.evaluate());
    }
    
    private interface Check {
        
        boolean evaluate();
    }
    
    private static class PushRecord {
        
        private final AgentDiscoveryNotifyRequest request;
        
        private final PushCallBack callback;
        
        PushRecord(AgentDiscoveryNotifyRequest request, PushCallBack callback) {
            this.request = request;
            this.callback = callback;
        }
    }
}
