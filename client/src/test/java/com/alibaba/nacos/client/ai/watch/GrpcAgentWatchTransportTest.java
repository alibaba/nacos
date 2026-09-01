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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryNotifyResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.common.remote.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrpcAgentWatchTransportTest {
    
    private AiGrpcClient client;
    
    private DirectExecutorService executor;
    
    private GrpcAgentWatchTransport transport;
    
    @BeforeEach
    void setUp() {
        client = mock(AiGrpcClient.class);
        executor = new DirectExecutorService();
        transport = new GrpcAgentWatchTransport(client, executor);
        verify(client).registerConnectionListener(transport);
        verify(client).registerServerRequestHandler(transport);
    }
    
    @Test
    void negotiatedConnectionSubscribesAndAcceptsDirtyBeforeAck() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(eq("watch-a"), any(AgentDiscoveryRequest.class),
            eq("fingerprint-a"))).thenReturn(response("wire-a", null, false));
        TestCallback callback = new TestCallback();
        transport.start(registration("watch-a", "fingerprint-a"), callback);
        
        AgentDiscoveryNotifyRequest invalidate = notify("wire-a",
            AgentWatchEventType.INVALIDATE);
        invalidate.setObservedFingerprint("fingerprint-b");
        AgentDiscoveryNotifyResponse ack =
            (AgentDiscoveryNotifyResponse) transport.requestReply(invalidate, connection);
        
        assertTrue(ack.isAccepted());
        assertTrue(callback.dirtyRecorded);
        assertEquals("fingerprint-b", callback.observedFingerprint);
        assertFalse(callback.forceRefresh);
        verify(client).subscribeAgentWatch(eq("watch-a"),
            any(AgentDiscoveryRequest.class), eq("fingerprint-a"));
    }
    
    @Test
    void unknownInvalidAndOldConnectionHintsAreRejected() throws Exception {
        Connection first = supportedConnection("connection-a");
        connect(first, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        transport.start(registration("watch-a", null), new TestCallback());
        
        assertFalse(reply(notify("unknown", AgentWatchEventType.REVALIDATE), first).isAccepted());
        AgentDiscoveryNotifyRequest invalid = notify("wire-a", null);
        assertFalse(reply(invalid, first).isAccepted());
        AgentDiscoveryNotifyRequest missingError =
            notify("wire-a", AgentWatchEventType.TERMINATED);
        assertFalse(reply(missingError, first).isAccepted());
        
        Connection second = supportedConnection("connection-b");
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        transport.onDisConnect(first);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-b", null, false));
        transport.onConnected(second);
        
        assertFalse(reply(notify("wire-a", AgentWatchEventType.REVALIDATE), second).isAccepted());
        assertTrue(reply(notify("wire-b", AgentWatchEventType.REVALIDATE), second).isAccepted());
        verify(client, times(2)).subscribeAgentWatch(any(), any(), any());
    }
    
    @Test
    void reconnectUsesLatestFingerprintAndRefreshesOnlyWhenRequired() throws Exception {
        Connection first = supportedConnection("connection-a");
        connect(first, "connection-a");
        when(client.subscribeAgentWatch(eq("watch-a"), any(), eq("fingerprint-a")))
            .thenReturn(response("wire-a", null, false));
        TestCallback callback = new TestCallback();
        transport.start(registration("watch-a", "fingerprint-a"), callback);
        transport.update(registration("watch-a", "fingerprint-b"));
        
        Connection second = supportedConnection("connection-b");
        transport.onDisConnect(first);
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        when(client.subscribeAgentWatch(eq("watch-a"), any(), eq("fingerprint-b")))
            .thenReturn(response("wire-b", "fingerprint-c", true));
        transport.onConnected(second);
        
        assertEquals(1, callback.invalidations);
        assertEquals("fingerprint-c", callback.observedFingerprint);
        assertFalse(callback.forceRefresh);
        assertTrue(reply(notify("wire-b", AgentWatchEventType.REVALIDATE), second).isAccepted());
        assertEquals(2, callback.invalidations);
        assertTrue(callback.forceRefresh);
    }
    
    @Test
    void terminationRemovesWireBeforeUnavailableCallback() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        TestCallback callback = new TestCallback();
        callback.onUnavailable = () -> transport.stop("watch-a");
        transport.start(registration("watch-a", null), callback);
        AgentDiscoveryNotifyRequest terminated =
            notify("wire-a", AgentWatchEventType.TERMINATED);
        terminated.setErrorCode(NacosException.RESOURCE_NOT_FOUND);
        
        AgentDiscoveryNotifyResponse ack = reply(terminated, connection);
        
        assertTrue(ack.isAccepted());
        assertEquals(1, callback.unavailable);
        assertFalse(callback.terminal);
        verify(client, never()).unsubscribeAgentWatch("wire-a");
        assertFalse(reply(notify("wire-a", AgentWatchEventType.REVALIDATE), connection)
            .isAccepted());
    }
    
    @Test
    void missingAbilityAndShutdownRejectNewWireState() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getConnectionId()).thenReturn("connection-a");
        when(connection.getConnectionAbility(AbilityKey.SERVER_RAD_WATCH_V1))
            .thenReturn(AbilityStatus.NOT_SUPPORTED);
        when(client.getCurrentConnectionId()).thenReturn("connection-a");
        when(client.isAgentWatchAvailable()).thenReturn(false);
        transport.onConnected(connection);
        
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> transport.start(registration("watch-a", null), new TestCallback()))
                .getErrCode());
        transport.shutdown();
        transport.shutdown();
        assertEquals(NacosException.CLIENT_DISCONNECT,
            assertThrows(NacosException.class,
                () -> transport.start(registration("watch-a", null), new TestCallback()))
                .getErrCode());
        assertTrue(executor.isShutdown());
    }
    
    @Test
    void duplicateStartUpdatesRegistrationAndUnknownOperationsAreHarmless() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        TestCallback callback = new TestCallback();
        
        transport.start(registration("watch-a", "fingerprint-a"), callback);
        transport.start(registration("watch-a", "fingerprint-b"), new TestCallback());
        transport.update(registration("missing", "fingerprint-c"));
        transport.stop("missing");
        
        assertNull(transport.requestReply(mock(Request.class), connection));
        verify(client).subscribeAgentWatch(eq("watch-a"), any(), eq("fingerprint-a"));
        transport.stop("watch-a");
        verify(client).unsubscribeAgentWatch("wire-a");
    }
    
    @Test
    void invalidAndRuntimeSubscriptionResponsesLeaveNoWireState() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(null)
            .thenReturn(response(null, null, false))
            .thenThrow(new IllegalStateException("broken"))
            .thenReturn(response("wire-a", null, false));
        AgentWatchRegistration registration = registration("watch-a", null);
        TestCallback callback = new TestCallback();
        
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> transport.start(registration, callback)).getErrCode());
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class,
                () -> transport.start(registration, callback)).getErrCode());
        assertEquals("broken",
            assertThrows(IllegalStateException.class,
                () -> transport.start(registration, callback)).getMessage());
        
        transport.start(registration, callback);
        assertTrue(reply(notify("wire-a", AgentWatchEventType.REVALIDATE), connection)
            .isAccepted());
        verify(client, times(4)).subscribeAgentWatch(any(), any(), any());
    }
    
    @Test
    void rejectedRefreshCleansInstalledWire() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", "fingerprint-b", true));
        TestCallback callback = new TestCallback();
        callback.acceptInvalidation = false;
        
        NacosException failure = assertThrows(NacosException.class,
            () -> transport.start(registration("watch-a", "fingerprint-a"), callback));
        
        assertEquals(NacosException.CLIENT_ERROR, failure.getErrCode());
        verify(client).unsubscribeAgentWatch("wire-a");
        assertFalse(reply(notify("wire-a", AgentWatchEventType.REVALIDATE), connection)
            .isAccepted());
    }
    
    @Test
    void connectionChangeDuringSubscribeRejectsAndAllowsCleanRetry() throws Exception {
        AtomicReference<String> current = new AtomicReference<String>("connection-a");
        when(client.getCurrentConnectionId()).thenAnswer(invocation -> current.get());
        when(client.isAgentWatchAvailable()).thenReturn(true);
        Connection first = supportedConnection("connection-a");
        transport.onConnected(first);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenAnswer(invocation -> {
                current.set("connection-b");
                return response("stale-wire", null, false);
            })
            .thenReturn(response("wire-a", null, false));
        
        NacosException failure = assertThrows(NacosException.class,
            () -> transport.start(registration("watch-a", null), new TestCallback()));
        assertEquals(NacosException.CLIENT_DISCONNECT, failure.getErrCode());
        current.set("connection-a");
        transport.start(registration("watch-a", null), new TestCallback());
        
        assertTrue(reply(notify("wire-a", AgentWatchEventType.REVALIDATE), first).isAccepted());
        verify(client, never()).unsubscribeAgentWatch("stale-wire");
    }
    
    @Test
    void reconnectAbilityLossAndSchedulingRejectionNotifyLifecycle() throws Exception {
        Connection first = supportedConnection("connection-a");
        connect(first, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        TestLifecycleListener listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        transport.start(registration("watch-a", null), new TestCallback());
        transport.onDisConnect(first);
        
        Connection unsupported = mock(Connection.class);
        when(unsupported.getConnectionId()).thenReturn("connection-b");
        when(unsupported.getConnectionAbility(AbilityKey.SERVER_RAD_WATCH_V1))
            .thenReturn(AbilityStatus.NOT_SUPPORTED);
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(false);
        transport.onConnected(unsupported);
        
        assertEquals(1, listener.unavailable);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, listener.errorCode);
        assertEquals(0, listener.available);
        
        setUp();
        first = supportedConnection("connection-a");
        connect(first, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        transport.start(registration("watch-a", null), new TestCallback());
        transport.onDisConnect(first);
        executor.shutdown();
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        transport.onConnected(supportedConnection("connection-b"));
        
        assertEquals(1, listener.unavailable);
        assertEquals(NacosException.CLIENT_ERROR, listener.errorCode);
    }
    
    @Test
    void reconnectRuntimeFailureIsTypedAndOldQueuedTaskIsIgnored() throws Exception {
        Connection first = supportedConnection("connection-a");
        connect(first, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false))
            .thenThrow(new IllegalStateException("broken reconnect"));
        TestLifecycleListener listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        transport.start(registration("watch-a", null), new TestCallback());
        transport.onDisConnect(first);
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        transport.onConnected(supportedConnection("connection-b"));
        
        assertEquals(1, listener.unavailable);
        assertEquals(NacosException.CLIENT_ERROR, listener.errorCode);
        
        AiGrpcClient queuedClient = mock(AiGrpcClient.class);
        QueueExecutorService queuedExecutor = new QueueExecutorService();
        GrpcAgentWatchTransport queuedTransport =
            new GrpcAgentWatchTransport(queuedClient, queuedExecutor);
        AtomicReference<String> current = new AtomicReference<String>("connection-a");
        when(queuedClient.getCurrentConnectionId()).thenAnswer(invocation -> current.get());
        when(queuedClient.isAgentWatchAvailable()).thenReturn(true);
        when(queuedClient.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        Connection queuedFirst = supportedConnection("connection-a");
        queuedTransport.onConnected(queuedFirst);
        queuedExecutor.runAll();
        queuedTransport.start(registration("watch-a", null), new TestCallback());
        queuedTransport.onDisConnect(queuedFirst);
        current.set("connection-b");
        queuedTransport.onConnected(supportedConnection("connection-b"));
        current.set("connection-c");
        queuedExecutor.runNext();
        
        verify(queuedClient).subscribeAgentWatch(any(), any(), any());
        assertFalse(queuedTransport.isAvailable());
    }
    
    @Test
    void reconnectErrorFromAbandonedConnectionIsIgnored() throws Exception {
        AtomicReference<String> current = new AtomicReference<String>("connection-a");
        when(client.getCurrentConnectionId()).thenAnswer(invocation -> current.get());
        when(client.isAgentWatchAvailable()).thenReturn(true);
        Connection first = supportedConnection("connection-a");
        transport.onConnected(first);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false))
            .thenAnswer(invocation -> {
                current.set("connection-c");
                throw new NacosException(NacosException.SERVER_ERROR, "stale failure");
            });
        TestLifecycleListener listener = new TestLifecycleListener();
        transport.setLifecycleListener(listener);
        transport.start(registration("watch-a", null), new TestCallback());
        transport.onDisConnect(first);
        current.set("connection-b");
        
        transport.onConnected(supportedConnection("connection-b"));
        
        assertEquals(0, listener.unavailable);
        assertFalse(transport.isAvailable());
    }
    
    @Test
    void lateConnectedEventAndLateHintCannotReplaceCurrentWire() throws Exception {
        AtomicReference<String> current = new AtomicReference<String>("connection-a");
        when(client.getCurrentConnectionId()).thenAnswer(invocation -> current.get());
        when(client.isAgentWatchAvailable()).thenReturn(true);
        Connection first = supportedConnection("connection-a");
        transport.onConnected(first);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false))
            .thenReturn(response("wire-b", null, false));
        transport.start(registration("watch-a", null), new TestCallback());
        transport.onDisConnect(first);
        current.set("connection-b");
        Connection second = supportedConnection("connection-b");
        transport.onConnected(second);
        
        transport.onConnected(first);
        
        assertTrue(reply(notify("wire-b", AgentWatchEventType.REVALIDATE), second).isAccepted());
        current.set("connection-c");
        assertFalse(reply(notify("wire-b", AgentWatchEventType.REVALIDATE), second).isAccepted());
        assertFalse(reply(notify("wire-b", AgentWatchEventType.REVALIDATE), null).isAccepted());
    }
    
    @Test
    void terminalNotificationAndShutdownCleanAllWireKeysBestEffort() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false))
            .thenReturn(response("wire-b", null, false))
            .thenReturn(response("wire-c", null, false));
        TestCallback terminal = new TestCallback();
        transport.start(registration("watch-a", null), terminal);
        AgentDiscoveryNotifyRequest terminated =
            notify("wire-a", AgentWatchEventType.TERMINATED);
        terminated.setErrorCode(NacosException.NO_RIGHT);
        assertTrue(reply(terminated, connection).isAccepted());
        assertTrue(terminal.terminal);
        
        transport.start(registration("watch-b", null), new TestCallback());
        transport.start(registration("watch-c", null), new TestCallback());
        doThrow(new NacosException(NacosException.SERVER_ERROR, "ignored"))
            .when(client).unsubscribeAgentWatch("wire-b");
        transport.shutdown();
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        transport.onConnected(supportedConnection("connection-b"));
        transport.onDisConnect(connection);
        
        verify(client).unsubscribeAgentWatch("wire-b");
        verify(client).unsubscribeAgentWatch("wire-c");
    }
    
    @Test
    void rejectedBestEffortUnsubscribeDoesNotEscape() throws Exception {
        Connection connection = supportedConnection("connection-a");
        connect(connection, "connection-a");
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a", null, false));
        transport.start(registration("watch-a", null), new TestCallback());
        executor.shutdown();
        
        transport.stop("watch-a");
        transport.shutdown();
        
        verify(client, never()).unsubscribeAgentWatch("wire-a");
    }
    
    private Connection supportedConnection(String connectionId) {
        Connection connection = mock(Connection.class);
        when(connection.getConnectionId()).thenReturn(connectionId);
        when(connection.getConnectionAbility(AbilityKey.SERVER_RAD_WATCH_V1))
            .thenReturn(AbilityStatus.SUPPORTED);
        return connection;
    }
    
    private void connect(Connection connection, String connectionId) {
        when(client.getCurrentConnectionId()).thenReturn(connectionId);
        when(client.isAgentWatchAvailable()).thenReturn(true);
        transport.onConnected(connection);
    }
    
    private AgentWatchRegistration registration(String clientWatchId, String fingerprint) {
        AgentReference reference = new AgentReference();
        reference.setAgentName("agent-a");
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        return new AgentWatchRegistration(clientWatchId, request, fingerprint);
    }
    
    private AgentSubscribeRpcResponse response(String watchKey, String fingerprint,
        boolean refreshRequired) {
        AgentSubscribeRpcResponse result = new AgentSubscribeRpcResponse();
        result.setWatchKey(watchKey);
        result.setObservedFingerprint(fingerprint);
        result.setRefreshRequired(refreshRequired);
        return result;
    }
    
    private AgentDiscoveryNotifyRequest notify(String watchKey, AgentWatchEventType eventType) {
        AgentDiscoveryNotifyRequest result = new AgentDiscoveryNotifyRequest();
        result.setWatchKey(watchKey);
        result.setEventType(eventType);
        return result;
    }
    
    private AgentDiscoveryNotifyResponse reply(AgentDiscoveryNotifyRequest request,
        Connection connection) {
        Response response = transport.requestReply(request, connection);
        return (AgentDiscoveryNotifyResponse) response;
    }
    
    private static final class TestCallback implements AgentWatchTransportCallback {
        
        private int invalidations;
        
        private int unavailable;
        
        private boolean dirtyRecorded;
        
        private boolean forceRefresh;
        
        private boolean terminal;
        
        private String observedFingerprint;
        
        private Runnable onUnavailable;
        
        private boolean acceptInvalidation = true;
        
        @Override
        public boolean invalidate(String observedFingerprint, boolean forceRefresh) {
            invalidations++;
            dirtyRecorded = true;
            this.observedFingerprint = observedFingerprint;
            this.forceRefresh = forceRefresh;
            return acceptInvalidation;
        }
        
        @Override
        public void unavailable(int errorCode, String errorMessage, boolean terminal) {
            unavailable++;
            this.terminal = terminal;
            if (onUnavailable != null) {
                onUnavailable.run();
            }
        }
    }
    
    private static final class TestLifecycleListener
        implements GrpcAgentWatchTransport.WireLifecycleListener {
        
        private int available;
        
        private int unavailable;
        
        private int errorCode;
        
        @Override
        public void onWireAvailable() {
            available++;
        }
        
        @Override
        public void onWireUnavailable(String clientWatchId, NacosException exception) {
            unavailable++;
            errorCode = exception.getErrCode();
        }
    }
    
    private static final class DirectExecutorService extends AbstractExecutorService {
        
        private boolean shutdown;
        
        @Override
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }
        
        @Override
        public boolean isShutdown() {
            return shutdown;
        }
        
        @Override
        public boolean isTerminated() {
            return shutdown;
        }
        
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
        
        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new java.util.concurrent.RejectedExecutionException("closed");
            }
            command.run();
        }
    }
    
    private static final class QueueExecutorService extends AbstractExecutorService {
        
        private final LinkedList<Runnable> commands = new LinkedList<Runnable>();
        
        private boolean shutdown;
        
        @Override
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> result = new LinkedList<Runnable>(commands);
            commands.clear();
            return result;
        }
        
        @Override
        public boolean isShutdown() {
            return shutdown;
        }
        
        @Override
        public boolean isTerminated() {
            return shutdown && commands.isEmpty();
        }
        
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
        
        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("closed");
            }
            commands.add(command);
        }
        
        private void runNext() {
            Runnable command = commands.poll();
            if (command != null) {
                command.run();
            }
        }
        
        private void runAll() {
            while (!commands.isEmpty()) {
                runNext();
            }
        }
    }
}
