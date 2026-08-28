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
import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.ai.remote.AgentHttpWatchClient;
import com.alibaba.nacos.common.remote.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWatchTransportRouterTest {
    
    private AiGrpcClient client;
    
    private GrpcAgentWatchTransport grpc;
    
    private FakeTransport polling;
    
    private HttpAgentWatchTransport http;
    
    private ScheduledExecutorService pollingExecutor;
    
    @BeforeEach
    void setUp() {
        client = mock(AiGrpcClient.class);
        grpc = new GrpcAgentWatchTransport(client, new DirectExecutorService());
        http = mock(HttpAgentWatchTransport.class);
        when(http.isAvailable()).thenReturn(true);
        polling = new FakeTransport();
        pollingExecutor = mock(ScheduledExecutorService.class);
    }
    
    @Test
    void constructorsBuildDefaultPollingAndShutdownCleanly() {
        AgentWatchTransportRouter publicRouter =
            new AgentWatchTransportRouter(AgentTransportMode.AUTO, client, 1000L);
        publicRouter.shutdown();
        AgentWatchTransportRouter httpRouter = new AgentWatchTransportRouter(
            AgentTransportMode.HTTP, client, mock(AgentHttpWatchClient.class), 1000L);
        httpRouter.shutdown();
        
        AgentWatchTransportRouter packageRouter =
            new AgentWatchTransportRouter(AgentTransportMode.AUTO, grpc, pollingExecutor, 1000L);
        packageRouter.shutdown();
    }
    
    @Test
    void explicitHttpAndAutoWithoutGrpcUseBatchHttpOwner() throws Exception {
        AgentWatchTransportRouter explicit = routerWithHttp(AgentTransportMode.HTTP);
        AgentWatchRegistration first = registration("watch-http");
        TestCallback callback = new TestCallback();
        explicit.start(first, callback);
        verify(http).start(first, callback);
        assertEquals(0, polling.startCount);
        explicit.update(registration("watch-http"));
        verify(http).update(any(AgentWatchRegistration.class));
        explicit.stop("watch-http");
        verify(http).stop("watch-http");
        explicit.shutdown();
        verify(http).shutdown();
        
        setUp();
        AgentWatchTransportRouter automatic = routerWithHttp(AgentTransportMode.AUTO);
        AgentWatchRegistration second = registration("watch-auto");
        automatic.start(second, new TestCallback());
        verify(http).start(eq(second), any(AgentWatchTransportCallback.class));
        assertEquals(0, polling.startCount);
        automatic.shutdown();
    }
    
    @Test
    void autoMigratesBetweenHttpAndNegotiatedGrpcWithoutDuplicateOwner() throws Exception {
        AgentWatchTransportRouter router = routerWithHttp(AgentTransportMode.AUTO);
        AgentWatchRegistration registration = registration("watch-a");
        router.start(registration, new TestCallback());
        verify(http).start(eq(registration), any(AgentWatchTransportCallback.class));
        
        when(client.getCurrentConnectionId()).thenReturn("connection-a");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        when(client.subscribeAgentWatch(any(), any(), any())).thenReturn(response("wire-a"));
        grpc.onConnected(connection("connection-a", AbilityStatus.SUPPORTED));
        verify(http).stop("watch-a");
        verify(client).subscribeAgentWatch(any(), any(), any());
        
        grpc.onDisConnect(connection("connection-a", AbilityStatus.SUPPORTED));
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(false);
        grpc.onConnected(connection("connection-b", AbilityStatus.NOT_SUPPORTED));
        verify(http, times(2)).start(eq(registration),
            any(AgentWatchTransportCallback.class));
        router.shutdown();
    }
    
    @Test
    void httpBindingFailuresFallbackOrTerminateAccordingToCategory() throws Exception {
        AgentWatchTransportRouter fallback = routerWithHttp(AgentTransportMode.HTTP);
        AgentWatchRegistration registration = registration("watch-a");
        TestCallback callback = new TestCallback();
        fallback.start(registration, callback);
        fallback.onWireUnavailable(
            new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "unsupported"));
        assertEquals(1, polling.startCount);
        verify(http).stop("watch-a");
        assertEquals(0, callback.unavailable);
        fallback.shutdown();
        
        setUp();
        AgentWatchTransportRouter terminal = routerWithHttp(AgentTransportMode.HTTP);
        TestCallback rejected = new TestCallback();
        terminal.start(registration("watch-full"), rejected);
        terminal.onWireUnavailable(new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, "full"));
        assertEquals(1, rejected.unavailable);
        assertTrue(rejected.terminal);
        assertEquals(0, polling.startCount);
        terminal.shutdown();
    }
    
    @Test
    void httpFallbackCancellationAndFailuresDoNotLeakRoutes() throws Exception {
        AgentWatchTransportRouter canceled = routerWithHttp(AgentTransportMode.HTTP);
        canceled.start(registration("watch-canceled"), new TestCallback());
        polling.startHook = () -> canceled.stop("watch-canceled");
        canceled.onWireUnavailable(
            new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "unsupported"));
        assertFalse(polling.registrations.containsKey("watch-canceled"));
        assertEquals(1, polling.stopCount);
        verify(http, times(2)).stop("watch-canceled");
        canceled.stop("missing");
        verify(http).stop("missing");
        canceled.shutdown();
        canceled.onWireUnavailable(new NacosException(NacosException.SERVER_ERROR, "late"));
        
        setUp();
        AgentWatchTransportRouter checked = routerWithHttp(AgentTransportMode.HTTP);
        TestCallback checkedFailure = new TestCallback();
        checked.start(registration("watch-checked"), checkedFailure);
        polling.startFailure = new NacosException(NacosException.CLIENT_ERROR, "failed");
        checked.onWireUnavailable(
            new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "unsupported"));
        assertEquals(1, checkedFailure.unavailable);
        assertTrue(checkedFailure.terminal);
        checked.shutdown();
        
        setUp();
        AgentWatchTransportRouter runtime = routerWithHttp(AgentTransportMode.HTTP);
        TestCallback runtimeFailure = new TestCallback();
        runtime.start(registration("watch-runtime"), runtimeFailure);
        polling.runtimeStartFailure = new IllegalStateException("failed");
        runtime.onWireUnavailable(
            new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "unsupported"));
        assertEquals(1, runtimeFailure.unavailable);
        assertEquals(NacosException.CLIENT_ERROR, runtimeFailure.errorCode);
        assertTrue(runtimeFailure.terminal);
        runtime.shutdown();
        
        setUp();
        AgentWatchTransportRouter noHttp = router(AgentTransportMode.HTTP);
        noHttp.onWireUnavailable(new NacosException(NacosException.SERVER_ERROR, "ignored"));
        polling.startFailure = new NacosException(NacosException.CLIENT_ERROR, "failed");
        assertEquals(NacosException.CLIENT_ERROR,
            assertThrows(NacosException.class,
                () -> noHttp.start(registration("watch-polling"), new TestCallback()))
                .getErrCode());
        noHttp.shutdown();
    }
    
    @Test
    void synchronousHttpActivationFailureFallsBackToPolling() throws Exception {
        doThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"))
            .when(http).start(any(), any());
        AgentWatchTransportRouter router = routerWithHttp(AgentTransportMode.HTTP);
        router.start(registration("watch-a"), new TestCallback());
        assertEquals(1, polling.startCount);
        router.shutdown();
    }
    
    @Test
    void grpcRequiresNegotiatedAbilityAndUpgradesPollingWithoutDuplicateOwner()
        throws Exception {
        AgentWatchTransportRouter router = router(AgentTransportMode.GRPC);
        TestCallback callback = new TestCallback();
        AgentWatchRegistration registration = registration("watch-a");
        router.start(registration, callback);
        assertEquals(1, polling.startCount);
        
        Connection connection = connection("connection-a", AbilityStatus.SUPPORTED);
        when(client.getCurrentConnectionId()).thenReturn("connection-a");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a"));
        grpc.onConnected(connection);
        
        assertEquals(1, polling.stopCount);
        verify(client).subscribeAgentWatch(any(), any(), any());
        router.update(registration("watch-a"));
        router.stop("watch-a");
        verify(client).unsubscribeAgentWatch("wire-a");
        router.shutdown();
    }
    
    @Test
    void explicitHttpAndMissingWatchAbilityStayOnPolling() throws Exception {
        AgentWatchTransportRouter http = router(AgentTransportMode.HTTP);
        when(client.getCurrentConnectionId()).thenReturn("connection-a");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a"));
        grpc.onConnected(connection("connection-a", AbilityStatus.SUPPORTED));
        http.start(registration("watch-http"), new TestCallback());
        assertEquals(1, polling.startCount);
        verify(client, never()).subscribeAgentWatch(any(), any(), any());
        http.shutdown();
        
        setUp();
        AgentWatchTransportRouter missing = router(AgentTransportMode.GRPC);
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(false);
        grpc.onConnected(connection("connection-b", AbilityStatus.NOT_SUPPORTED));
        missing.start(registration("watch-missing"), new TestCallback());
        assertEquals(1, polling.startCount);
        missing.shutdown();
    }
    
    @Test
    void transientSubscribeFailureFallsBackButBusinessRejectionsEscape() throws Exception {
        connectSupported();
        AgentWatchTransportRouter transientRouter = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"));
        transientRouter.start(registration("watch-transient"), new TestCallback());
        assertEquals(1, polling.startCount);
        transientRouter.shutdown();
        
        setUp();
        connectSupported();
        AgentWatchTransportRouter deniedRouter = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "denied"));
        assertEquals(NacosException.NO_RIGHT,
            assertThrows(NacosException.class,
                () -> deniedRouter.start(registration("watch-denied"), new TestCallback()))
                .getErrCode());
        assertEquals(0, polling.startCount);
        
        doThrow(new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT, "full"))
            .when(client).subscribeAgentWatch(any(), any(), any());
        NacosApiException capacity = assertThrows(NacosApiException.class,
            () -> deniedRouter.start(registration("watch-full"), new TestCallback()));
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            capacity.getDetailErrCode());
        assertEquals(0, polling.startCount);
        deniedRouter.shutdown();
    }
    
    @Test
    void reconnectAbilityLossMovesExistingGrpcRouteToPolling() throws Exception {
        connectSupported();
        AgentWatchTransportRouter router = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a"));
        router.start(registration("watch-a"), new TestCallback());
        assertEquals(0, polling.startCount);
        
        Connection first = connection("connection-a", AbilityStatus.SUPPORTED);
        grpc.onDisConnect(first);
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(false);
        grpc.onConnected(connection("connection-b", AbilityStatus.NOT_SUPPORTED));
        
        assertEquals(1, polling.startCount);
        router.shutdown();
        router.onWireAvailable();
        assertTrue(polling.closed);
        verify(pollingExecutor).shutdownNow();
    }
    
    @Test
    void reconnectNotFoundAndTerminalFailuresAreNotRetriedAsPolling() throws Exception {
        connectSupported();
        AgentWatchTransportRouter router = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a"))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "gone"));
        TestCallback missing = new TestCallback();
        router.start(registration("watch-a"), missing);
        grpc.onDisConnect(connection("connection-a", AbilityStatus.SUPPORTED));
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        grpc.onConnected(connection("connection-b", AbilityStatus.SUPPORTED));
        assertEquals(1, missing.unavailable);
        assertEquals(NacosException.NOT_FOUND, missing.errorCode);
        assertEquals(0, polling.startCount);
        
        router.shutdown();
    }
    
    @Test
    void duplicateStartUpdatesSingleOwnerAndUnknownOperationsAreHarmless() throws Exception {
        AgentWatchTransportRouter router = router(AgentTransportMode.HTTP);
        router.start(registration("watch-a"), new TestCallback());
        
        router.start(registration("watch-a"), new TestCallback());
        router.update(registration("watch-a"));
        router.update(registration("missing"));
        router.stop("missing");
        
        assertEquals(1, polling.startCount);
        assertEquals(2, polling.updateCount);
        router.stop("watch-a");
        assertEquals(1, polling.stopCount);
        router.shutdown();
        router.shutdown();
        assertEquals(NacosException.CLIENT_DISCONNECT,
            assertThrows(NacosException.class,
                () -> router.start(registration("closed"), new TestCallback())).getErrCode());
        router.onWireAvailable();
    }
    
    @Test
    void fallbackFailuresRemoveRouteAndRuntimeFailuresDoNotLeak() throws Exception {
        connectSupported();
        AgentWatchTransportRouter router = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "temporary"));
        polling.startFailure =
            new NacosException(NacosException.CLIENT_ERROR, "polling failed");
        
        assertEquals(NacosException.CLIENT_ERROR,
            assertThrows(NacosException.class,
                () -> router.start(registration("watch-a"), new TestCallback())).getErrCode());
        polling.startFailure = null;
        router.start(registration("watch-a"), new TestCallback());
        assertEquals(2, polling.startCount);
        
        polling.runtimeStartFailure = new IllegalStateException("polling broken");
        assertEquals("polling broken",
            assertThrows(IllegalStateException.class,
                () -> router.start(registration("watch-b"), new TestCallback())).getMessage());
        polling.runtimeStartFailure = null;
        router.start(registration("watch-b"), new TestCallback());
        router.shutdown();
        
        setUp();
        AgentWatchTransportRouter httpRouter = router(AgentTransportMode.HTTP);
        polling.runtimeStartFailure = new IllegalStateException("initial broken");
        assertEquals("initial broken",
            assertThrows(IllegalStateException.class,
                () -> httpRouter.start(registration("watch-c"), new TestCallback()))
                .getMessage());
        polling.runtimeStartFailure = null;
        httpRouter.start(registration("watch-c"), new TestCallback());
        httpRouter.shutdown();
    }
    
    @Test
    void canceledInitialStartDoesNotRetainOwner() throws Exception {
        AgentWatchTransportRouter router = router(AgentTransportMode.HTTP);
        polling.startHook = () -> router.stop("watch-a");
        
        router.start(registration("watch-a"), new TestCallback());
        
        assertFalse(polling.registrations.containsKey("watch-a"));
        assertEquals(1, polling.stopCount);
        polling.startHook = null;
        router.start(registration("watch-a"), new TestCallback());
        assertTrue(polling.registrations.containsKey("watch-a"));
        router.shutdown();
    }
    
    @Test
    void wireUnavailableClassifiesTerminalAndPollingFailure() throws Exception {
        connectSupported();
        AgentWatchTransportRouter router = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a"));
        TestCallback denied = new TestCallback();
        router.start(registration("watch-a"), denied);
        
        router.onWireUnavailable("unknown",
            new NacosException(NacosException.SERVER_ERROR, "ignored"));
        router.onWireUnavailable("watch-a",
            new NacosException(NacosException.NO_RIGHT, "denied"));
        assertEquals(1, denied.unavailable);
        assertTrue(denied.terminal);
        router.stop("watch-a");
        
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-b"));
        TestCallback transientFailure = new TestCallback();
        router.start(registration("watch-b"), transientFailure);
        polling.startFailure =
            new NacosException(NacosException.CLIENT_ERROR, "polling failed");
        router.onWireUnavailable("watch-b",
            new NacosException(NacosException.SERVER_ERROR, "temporary"));
        assertEquals(1, transientFailure.unavailable);
        assertTrue(transientFailure.terminal);
        router.stop("watch-b");
        
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-c"));
        TestCallback runtimeFailure = new TestCallback();
        router.start(registration("watch-c"), runtimeFailure);
        polling.startFailure = null;
        polling.runtimeStartFailure = new IllegalStateException("polling broken");
        router.onWireUnavailable("watch-c",
            new NacosException(NacosException.SERVER_ERROR, "temporary"));
        assertEquals(1, runtimeFailure.unavailable);
        assertEquals(NacosException.CLIENT_ERROR, runtimeFailure.errorCode);
        assertTrue(runtimeFailure.terminal);
        router.stop("watch-c");
        router.shutdown();
    }
    
    @Test
    void canceledFallbackAndUpgradeDoNotLeaveSecondOwner() throws Exception {
        connectSupported();
        AgentWatchTransportRouter router = router(AgentTransportMode.GRPC);
        when(client.subscribeAgentWatch(any(), any(), any()))
            .thenReturn(response("wire-a"));
        router.start(registration("watch-a"), new TestCallback());
        polling.startHook = () -> router.stop("watch-a");
        
        router.onWireUnavailable("watch-a",
            new NacosException(NacosException.SERVER_ERROR, "temporary"));
        
        assertFalse(polling.registrations.containsKey("watch-a"));
        assertEquals(1, polling.stopCount);
        router.shutdown();
        
        setUp();
        AgentWatchTransportRouter upgradeRouter = router(AgentTransportMode.AUTO);
        upgradeRouter.start(registration("watch-b"), new TestCallback());
        AtomicReference<AgentWatchTransportRouter> routerReference =
            new AtomicReference<AgentWatchTransportRouter>(upgradeRouter);
        when(client.subscribeAgentWatch(any(), any(), any())).thenAnswer(invocation -> {
            routerReference.get().stop("watch-b");
            return response("wire-b");
        });
        when(client.getCurrentConnectionId()).thenReturn("connection-b");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        grpc.onConnected(connection("connection-b", AbilityStatus.SUPPORTED));
        
        assertFalse(polling.registrations.containsKey("watch-b"));
        verify(client).unsubscribeAgentWatch("wire-b");
        upgradeRouter.shutdown();
    }
    
    @Test
    void grpcUpgradeFailureKeepsPollingAndClassifiesErrors() throws Exception {
        AgentWatchTransportRouter router = router(AgentTransportMode.AUTO);
        TestCallback missing = new TestCallback();
        TestCallback denied = new TestCallback();
        TestCallback temporary = new TestCallback();
        TestCallback runtime = new TestCallback();
        router.start(registration("watch-missing"), missing);
        router.start(registration("watch-denied"), denied);
        router.start(registration("watch-temporary"), temporary);
        router.start(registration("watch-runtime"), runtime);
        when(client.subscribeAgentWatch(any(), any(), any())).thenAnswer(invocation -> {
            String watchId = invocation.getArgument(0);
            if ("watch-missing".equals(watchId)) {
                throw new NacosException(NacosException.NOT_FOUND, "missing");
            }
            if ("watch-denied".equals(watchId)) {
                throw new NacosException(NacosException.NO_RIGHT, "denied");
            }
            if ("watch-runtime".equals(watchId)) {
                throw new IllegalStateException("grpc broken");
            }
            throw new NacosException(NacosException.SERVER_ERROR, "temporary");
        });
        when(client.getCurrentConnectionId()).thenReturn("connection-a");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        
        grpc.onConnected(connection("connection-a", AbilityStatus.SUPPORTED));
        
        assertEquals(1, missing.unavailable);
        assertFalse(missing.terminal);
        assertEquals(1, denied.unavailable);
        assertTrue(denied.terminal);
        assertEquals(0, temporary.unavailable);
        assertEquals(0, runtime.unavailable);
        assertEquals(4, polling.registrations.size());
        router.shutdown();
    }
    
    private AgentWatchTransportRouter router(AgentTransportMode mode) {
        return new AgentWatchTransportRouter(mode, grpc, polling, pollingExecutor);
    }
    
    private AgentWatchTransportRouter routerWithHttp(AgentTransportMode mode) {
        return new AgentWatchTransportRouter(mode, grpc, http, polling, pollingExecutor);
    }
    
    private void connectSupported() {
        when(client.getCurrentConnectionId()).thenReturn("connection-a");
        when(client.isAgentWatchAvailable()).thenReturn(true);
        grpc.onConnected(connection("connection-a", AbilityStatus.SUPPORTED));
    }
    
    private Connection connection(String connectionId, AbilityStatus abilityStatus) {
        Connection result = mock(Connection.class);
        when(result.getConnectionId()).thenReturn(connectionId);
        when(result.getConnectionAbility(AbilityKey.SERVER_RAD_WATCH_V1))
            .thenReturn(abilityStatus);
        return result;
    }
    
    private AgentWatchRegistration registration(String clientWatchId) {
        AgentReference reference = new AgentReference();
        reference.setAgentName("agent-a");
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        return new AgentWatchRegistration(clientWatchId, request, "fingerprint-a");
    }
    
    private AgentSubscribeRpcResponse response(String watchKey) {
        AgentSubscribeRpcResponse response = new AgentSubscribeRpcResponse();
        response.setWatchKey(watchKey);
        return response;
    }
    
    private static final class FakeTransport implements AgentWatchTransport {
        
        private final Map<String, AgentWatchRegistration> registrations =
            new LinkedHashMap<String, AgentWatchRegistration>();
        
        private int startCount;
        
        private int stopCount;
        
        private int updateCount;
        
        private boolean closed;
        
        private NacosException startFailure;
        
        private RuntimeException runtimeStartFailure;
        
        private Runnable startHook;
        
        @Override
        public void start(AgentWatchRegistration registration,
            AgentWatchTransportCallback callback) throws NacosException {
            startCount++;
            if (startFailure != null) {
                throw startFailure;
            }
            if (runtimeStartFailure != null) {
                throw runtimeStartFailure;
            }
            registrations.put(registration.getClientWatchId(), registration);
            if (startHook != null) {
                startHook.run();
            }
        }
        
        @Override
        public void update(AgentWatchRegistration registration) {
            updateCount++;
            registrations.put(registration.getClientWatchId(), registration);
        }
        
        @Override
        public void stop(String clientWatchId) {
            if (registrations.remove(clientWatchId) != null) {
                stopCount++;
            }
        }
        
        @Override
        public void shutdown() {
            closed = true;
            registrations.clear();
        }
    }
    
    private static final class TestCallback implements AgentWatchTransportCallback {
        
        private int unavailable;
        
        private int errorCode;
        
        private boolean terminal;
        
        @Override
        public boolean invalidate(String observedFingerprint, boolean forceRefresh) {
            return true;
        }
        
        @Override
        public void unavailable(int errorCode, String errorMessage, boolean terminal) {
            unavailable++;
            this.errorCode = errorCode;
            this.terminal = terminal;
        }
    }
    
    private static final class DirectExecutorService extends AbstractExecutorService {
        
        private boolean shutdown;
        
        @Override
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return java.util.Collections.emptyList();
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
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            return shutdown;
        }
        
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
