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

package com.alibaba.nacos.client.ai;

import java.util.List;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.client.ai.remote.redo.AgentEndpointPublicationRedoData;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AgentTransportType;
import com.alibaba.nacos.client.ai.remote.AgentTransportRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEndpointPublicationManagerTest {
    
    @Mock
    private AgentTransportRouter clientProxy;
    
    @Mock
    private ScheduledExecutorService executor;
    
    @Mock
    private ScheduledFuture<?> future;
    
    private AgentEndpointPublicationManager manager;
    
    @BeforeEach
    void setUp() throws NacosException {
        lenient().doReturn(future).when(executor)
            .schedule(any(Runnable.class), anyLong(), any());
        lenient().when(clientProxy.selectPublicationTransport())
            .thenReturn(AgentTransportType.HTTP);
        lenient()
            .when(clientProxy.registerAgentEndpoints(eq("public"), any(),
                any(AgentTransportType.class)))
            .thenAnswer(invocation -> clientProxy.registerAgentEndpoints(
                invocation.getArgument(0), invocation.getArgument(1)));
        lenient().doAnswer(invocation -> {
            clientProxy.deregisterAgentEndpoints(invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(clientProxy).deregisterAgentEndpoints(any(), any(), any(),
            any(AgentTransportType.class));
        lenient().when(clientProxy.heartbeatAgentEndpoints(any(AgentTransportType.class)))
            .thenAnswer(invocation -> clientProxy.heartbeatAgentEndpoints());
        manager = new AgentEndpointPublicationManager(clientProxy, executor);
    }
    
    @Test
    void registerSchedulesHeartbeatAndCopiesCompleteBatch() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenReturn(liveness(1234));
        AgentEndpointRegistrationBatch source =
            registration("a2a", endpoint("http://one:80/a"), endpoint("http://two:80/b"));
        
        manager.register("public", source);
        source.getEndpoints().clear();
        
        ArgumentCaptor<AgentEndpointRegistrationBatch> captor =
            ArgumentCaptor.forClass(AgentEndpointRegistrationBatch.class);
        verify(clientProxy).registerAgentEndpoints(eq("public"), captor.capture(),
            eq(AgentTransportType.HTTP));
        assertEquals(2, captor.getValue().getEndpoints().size());
        verify(executor).schedule(any(Runnable.class), eq(1234L), any());
    }
    
    @Test
    void defaultConstructorOwnsAndClosesItsExecutor() {
        AgentEndpointPublicationManager defaultManager =
            new AgentEndpointPublicationManager(clientProxy);
        
        defaultManager.shutdown();
    }
    
    @Test
    void grpcRegistrationDoesNotCreateHeartbeatExecutor() throws NacosException {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.GRPC)))
            .thenReturn(liveness(777));
        AgentEndpointPublicationManager grpcManager =
            new AgentEndpointPublicationManager(clientProxy, executor);
        grpcManager.register("public", registration("a2a", endpoint("http://one:80/a")));
        grpcManager.shutdown();
        
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a",
            AgentTransportType.GRPC);
    }
    
    @Test
    void nonPositiveHttpLivenessKeepsTheDefaultMaintenanceInterval()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenReturn(liveness(0));
        
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        
        verify(executor).schedule(any(Runnable.class),
            eq(AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL), any());
    }
    
    @ParameterizedTest
    @EnumSource(AgentTransportType.class)
    void partialDeregisterRegistersCompleteRemainderByNaturalKey(AgentTransportType transport)
        throws NacosException {
        when(clientProxy.selectPublicationTransport()).thenReturn(transport);
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        Endpoint retained = endpoint("http://three:80/c");
        retained.setPriority(7);
        retained.setWeight(2.5D);
        retained.setMetadata(Collections.singletonMap("region", "retained"));
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("2.0.0");
        binding.setVersionRange("[1.0.0,2.0.0]");
        retained.setBindings(Collections.singletonList(binding));
        AgentEndpointRegistrationBatch registration = registration("a2a",
            endpoint("http://LOCALHOST/a"), endpoint("http://two:80/b"), retained);
        registration.setVersionRange("[1.0.0,2.0.0]");
        manager.register("public", registration);
        
        List<Endpoint> removal = Arrays.asList(endpoint("http://two:80/other?ignored=true"),
            endpoint("http://unknown:80/a"), endpoint("http://localhost:80/other"));
        manager.deregister("public", "agent-a", "a2a", removal);
        manager.deregister("public", "agent-a", "a2a", removal);
        
        ArgumentCaptor<AgentEndpointRegistrationBatch> captor =
            ArgumentCaptor.forClass(AgentEndpointRegistrationBatch.class);
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), captor.capture(),
            eq(transport));
        AgentEndpointRegistrationBatch remainder = captor.getAllValues().get(1);
        assertEquals(registration.getAgentName(), remainder.getAgentName());
        assertEquals(registration.getProtocol(), remainder.getProtocol());
        assertEquals(registration.getRuntimeVersion(), remainder.getRuntimeVersion());
        assertEquals(registration.getVersionRange(), remainder.getVersionRange());
        assertEquals(1, remainder.getEndpoints().size());
        Endpoint actual = remainder.getEndpoints().get(0);
        assertEquals(retained.getUri(), actual.getUri());
        assertEquals(retained.getTransport(), actual.getTransport());
        assertEquals(retained.getPriority(), actual.getPriority());
        assertEquals(retained.getWeight(), actual.getWeight());
        assertEquals(retained.getMetadata(), actual.getMetadata());
        assertEquals("2.0.0", actual.getBindings().get(0).getRuntimeVersion());
        assertEquals("[1.0.0,2.0.0]", actual.getBindings().get(0).getVersionRange());
        assertEquals(3, registration.getEndpoints().size());
        assertEquals(3, removal.size());
        verify(clientProxy).selectPublicationTransport();
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any(), any());
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void finalAndRepeatedDeregisterAreIdempotent() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        List<Endpoint> deregistration =
            Arrays.asList(endpoint("http://one:80/different"));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        
        manager.deregister("public", "agent-a", "a2a", deregistration);
        manager.deregister("public", "agent-a", "a2a", deregistration);
        
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
        verify(future).cancel(false);
    }
    
    @Test
    void unknownPublicationAndNaturalKeyAreNoOps() throws NacosException {
        manager.deregister("public", "agent-a", "a2a", Arrays.asList(endpoint("http://one:80/a")));
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.deregister("public", "agent-a", "a2a",
            Arrays.asList(endpoint("http://other:80/a")));
        
        verify(clientProxy).registerAgentEndpoints(eq("public"), any());
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void retryableRegisterFailureKeepsDesiredBatchForMaintenance() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "timeout"))
            .thenReturn(liveness(100));
        Endpoint endpoint = endpoint("http://one:80/a");
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("2.0.0");
        binding.setVersionRange("[1.0.0,2.0.0]");
        endpoint.setBindings(Collections.singletonList(binding));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint)));
        binding.setRuntimeVersion("9.0.0");
        runMaintenance(0);
        ArgumentCaptor<AgentEndpointRegistrationBatch> copy =
            ArgumentCaptor.forClass(AgentEndpointRegistrationBatch.class);
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), copy.capture());
        assertEquals("2.0.0",
            copy.getValue().getEndpoints().get(0).getBindings().get(0).getRuntimeVersion());
        verify(clientProxy).heartbeatAgentEndpoints();
    }
    
    @ParameterizedTest
    @ValueSource(ints = {400, 50105})
    void initialNonRetryableFailureDoesNotRetainDesiredBatch(int code) throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenThrow(new NacosException(code, "rejected"));
        
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://one:80/a"))));
        manager.shutdown();
        
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void localPublicationCapacityAllowsReplacementAndRejectsNewIdentity()
        throws NacosException {
        manager = new AgentEndpointPublicationManager(clientProxy, executor, 1);
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("a2a", endpoint("http://replacement:80/a")));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.register("public", registration("mcp", endpoint("http://two:80/a"))));
        assertEquals(NacosException.CLIENT_OVER_THRESHOLD, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            exception.getDetailErrCode());
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), any());
    }
    
    @Test
    void localPublicationCapacityAdmitsWholeBatchFromBelowWatermark()
        throws NacosException {
        manager = new AgentEndpointPublicationManager(clientProxy, executor, 2);
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/a"),
            endpoint("http://three:80/a"), endpoint("http://four:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/a"),
            endpoint("http://three:80/a"), endpoint("http://four:80/a")));
        
        NacosApiException newIdentity = assertThrows(NacosApiException.class,
            () -> manager.register("public", registration("custom", endpoint("http://five:80/a"))));
        NacosApiException growth = assertThrows(NacosApiException.class,
            () -> manager.register("public", registration("mcp", endpoint("http://two:80/a"),
                endpoint("http://three:80/a"), endpoint("http://four:80/a"),
                endpoint("http://five:80/a"))));
        
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            newIdentity.getDetailErrCode());
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            growth.getDetailErrCode());
        verify(clientProxy, times(3)).registerAgentEndpoints(eq("public"), any());
    }
    
    @Test
    void remotePublicationCapacityRejectRemovesHeartbeatAndRedoIntent()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenThrow(publicationCapacityException());
        
        assertEquals(NacosException.OVER_THRESHOLD, assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://one:80/a"))))
            .getErrCode());
        manager.shutdown();
        
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any());
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void remoteCapacityRejectOfReplacementDiscardsWholePublication()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100))
            .thenThrow(publicationCapacityException());
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://two:80/a"))));
        manager.shutdown();
        
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void delayedCapacityRejectDiscardsIntentAndStopsHttpRedo() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "timeout"))
            .thenThrow(publicationCapacityException());
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://one:80/a"))));
        
        runMaintenance(0);
        runMaintenance(0);
        
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), any());
        verify(clientProxy, never()).heartbeatAgentEndpoints();
        manager.shutdown();
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void genericOverThresholdFailureRemainsRetryable() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenThrow(new NacosException(NacosException.OVER_THRESHOLD, "throttled"))
            .thenReturn(liveness(100));
        Endpoint endpoint = endpoint("http://one:80/a");
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("2.0.0");
        binding.setVersionRange("[1.0.0,2.0.0]");
        endpoint.setBindings(Collections.singletonList(binding));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint)));
        binding.setRuntimeVersion("9.0.0");
        runMaintenance(0);
        ArgumentCaptor<AgentEndpointRegistrationBatch> copy =
            ArgumentCaptor.forClass(AgentEndpointRegistrationBatch.class);
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), copy.capture());
        assertEquals("2.0.0",
            copy.getValue().getEndpoints().get(0).getBindings().get(0).getRuntimeVersion());
        verify(clientProxy).heartbeatAgentEndpoints();
    }
    
    @Test
    void asynchronousRemoteCapacityRejectionDiscardsPublication() throws NacosException {
        AgentEndpointRegistrationBatch batch =
            registration("a2a", endpoint("http://one:80/a"));
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", batch);
        
        manager.discardAfterRemoteCapacityRejection("public", batch);
        manager.shutdown();
        
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void postShutdownCapacityCleanupCannotRestartMaintenance() throws NacosException {
        AgentEndpointRegistrationBatch batch =
            registration("a2a", endpoint("http://one:80/a"));
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", batch);
        manager.shutdown();
        
        manager.discardAfterRemoteCapacityRejection("public", batch);
        
        verify(executor).schedule(any(Runnable.class), eq(100L), any());
        verify(executor).shutdownNow();
    }
    
    @Test
    void invalidLocalPublicationCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new AgentEndpointPublicationManager(clientProxy, executor, 0));
    }
    
    @Test
    void delayedNonRetryableFailureDiscardsInitialIntentAndStopsRedo()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "timeout"))
            .thenThrow(new NacosException(NacosException.INVALID_PARAM, "invalid"));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://one:80/a"))));
        
        runMaintenance(0);
        runMaintenance(0);
        
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), any());
        verify(clientProxy, never()).heartbeatAgentEndpoints();
        verify(executor).schedule(any(Runnable.class), anyLong(), any());
    }
    
    @Test
    void delayedNonRetryableReplacementRestoresPreviousPublication()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "timeout"))
            .thenThrow(new NacosException(NacosException.NO_RIGHT, "denied"));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://two:80/b"))));
        
        runMaintenance(0);
        runMaintenance(1);
        manager.deregister("public", "agent-a", "a2a",
            Arrays.asList(endpoint("http://one:80/other")));
        
        verify(clientProxy, times(3)).registerAgentEndpoints(eq("public"), any());
        verify(clientProxy, times(2)).heartbeatAgentEndpoints();
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
    }
    
    @ParameterizedTest
    @ValueSource(ints = {400, 50105})
    void nonRetryableReplacementFailureRestoresPreviousDesiredBatch(int code)
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100))
            .thenThrow(new NacosException(code, "rejected"));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://two:80/b"))));
        
        manager.deregister("public", "agent-a", "a2a",
            Arrays.asList(endpoint("http://one:80/other")));
        
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
    }
    
    @Test
    void migrationRejectedPartialDeregisterRetainsWholeConfirmedBatch() throws Exception {
        Endpoint first = endpoint("http://one:80/a");
        Endpoint second = endpoint("http://two:80/b");
        when(clientProxy.registerAgentEndpoints(eq("public"), any()))
            .thenReturn(liveness(100))
            .thenThrow(new NacosException(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(),
                "migration"));
        manager.register("public", registration("a2a", first, second));
        assertEquals(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(),
            assertThrows(NacosException.class, () -> manager.deregister("public", "agent-a",
                "a2a", Collections.singletonList(first))).getErrCode());
        manager.redoDirtyHttpPublications();
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), any());
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any());
        manager.deregister("public", "agent-a", "a2a", Arrays.asList(first, second));
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
    }
    
    @Test
    void retryableWholeDeregisterFailureRetainsTombstoneUntilRedo() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        doThrow(new NacosException(NacosException.SERVER_ERROR, "timeout"))
            .doNothing().when(clientProxy)
            .deregisterAgentEndpoints("public", "agent-a", "a2a");
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        
        assertThrows(NacosException.class,
            () -> manager.deregister("public", "agent-a", "a2a",
                Arrays.asList(endpoint("http://one:80/other"))));
        runMaintenance(0);
        
        verify(clientProxy, times(2)).deregisterAgentEndpoints("public", "agent-a", "a2a");
    }
    
    @Test
    void repeatedRetryableDeregisterFailureDoesNotHeartbeatTombstone()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        doThrow(new NacosException(NacosException.SERVER_ERROR, "timeout"))
            .when(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        assertThrows(NacosException.class,
            () -> manager.deregister("public", "agent-a", "a2a",
                Arrays.asList(endpoint("http://one:80/other"))));
        
        runMaintenance(0);
        
        verify(clientProxy, times(2)).deregisterAgentEndpoints("public", "agent-a", "a2a");
        verify(clientProxy, never()).heartbeatAgentEndpoints();
    }
    
    @Test
    void nonRetryableWholeDeregisterFailureRestoresPublication() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .doNothing().when(clientProxy)
            .deregisterAgentEndpoints("public", "agent-a", "a2a");
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        
        assertThrows(NacosException.class,
            () -> manager.deregister("public", "agent-a", "a2a",
                Arrays.asList(endpoint("http://one:80/other"))));
        manager.deregister("public", "agent-a", "a2a",
            Arrays.asList(endpoint("http://one:80/other")));
        
        verify(clientProxy, times(2)).deregisterAgentEndpoints("public", "agent-a", "a2a");
    }
    
    @Test
    void clientNotFoundHeartbeatReplaysEveryCompletePublication() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        when(clientProxy.heartbeatAgentEndpoints()).thenThrow(
            new NacosException(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(), "missing"));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/b")));
        
        runMaintenance(0);
        
        verify(clientProxy, times(4)).registerAgentEndpoints(eq("public"), any());
        verify(clientProxy).heartbeatAgentEndpoints();
    }
    
    @Test
    void transientHeartbeatFailureRetainsPublicationsAndSchedulesAgain()
        throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        when(clientProxy.heartbeatAgentEndpoints())
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        
        runMaintenance(0);
        
        verify(executor, times(2)).schedule(any(Runnable.class), eq(100L), any());
        verify(clientProxy).registerAgentEndpoints(eq("public"), any());
    }
    
    @Test
    void shutdownDeregistersAllAndIsIdempotent() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/b")));
        lenient().doThrow(new NacosException(NacosException.SERVER_ERROR, "failed"))
            .when(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
        
        manager.shutdown();
        manager.shutdown();
        
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a");
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "mcp");
        verify(executor).shutdownNow();
        verify(future).cancel(false);
    }
    
    @Test
    void completedMaintenanceFutureCanBeReplaced() throws NacosException {
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        when(future.isDone()).thenReturn(true);
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/b")));
        verify(executor, times(2)).schedule(any(Runnable.class), eq(100L), any());
    }
    
    @Test
    void replacementKeepsInitialPublicationOwner() throws NacosException {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP,
            AgentTransportType.GRPC);
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("a2a", endpoint("http://two:80/b")));
        
        verify(clientProxy).selectPublicationTransport();
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), any(),
            eq(AgentTransportType.HTTP));
        verify(clientProxy, never()).registerAgentEndpoints(eq("public"), any(),
            eq(AgentTransportType.GRPC));
    }
    
    @Test
    void mixedPublicationOwnersUseIndependentMaintenanceAndShutdown()
        throws NacosException {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP,
            AgentTransportType.GRPC);
        when(clientProxy.registerAgentEndpoints(eq("public"), any())).thenReturn(liveness(100));
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/b")));
        
        runMaintenance(0);
        manager.shutdown();
        
        verify(clientProxy).heartbeatAgentEndpoints(AgentTransportType.HTTP);
        verify(clientProxy, never()).heartbeatAgentEndpoints(AgentTransportType.GRPC);
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a",
            AgentTransportType.HTTP);
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "mcp",
            AgentTransportType.GRPC);
    }
    
    @Test
    void maintenanceClassifiesHttpTombstoneDirtyGrpcAndRegisteredHttpIndependently()
        throws NacosException {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.HTTP,
            AgentTransportType.HTTP, AgentTransportType.GRPC);
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenReturn(liveness(100));
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.GRPC)))
            .thenReturn(null)
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "grpc retryable"));
        doThrow(new NacosException(NacosException.SERVER_ERROR, "deregister retryable"))
            .when(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a",
                AgentTransportType.HTTP);
        doThrow(new NacosException(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(), "missing"))
            .when(clientProxy).heartbeatAgentEndpoints(AgentTransportType.HTTP);
        manager.register("public", registration("a2a", endpoint("http://one:80/a")));
        manager.register("public", registration("mcp", endpoint("http://two:80/b")));
        manager.register("public", registration("custom", endpoint("http://three:80/c")));
        assertThrows(NacosException.class,
            () -> manager.register("public",
                registration("custom", endpoint("http://replacement:80/c"))));
        assertThrows(NacosException.class,
            () -> manager.deregister("public", "agent-a", "a2a",
                Arrays.asList(endpoint("http://one:80/other"))));
        
        runMaintenance(0);
        
        verify(clientProxy).heartbeatAgentEndpoints(AgentTransportType.HTTP);
        verify(clientProxy, never()).heartbeatAgentEndpoints(AgentTransportType.GRPC);
        verify(clientProxy, times(2)).registerAgentEndpoints(eq("public"), any(),
            eq(AgentTransportType.GRPC));
    }
    
    @Test
    void publicationKeyEqualityHandlesIdentityAndForeignTypes() throws Exception {
        Class<?> keyType =
            Class.forName(AgentEndpointPublicationManager.class.getName() + "$PublicationKey");
        Constructor<?> constructor = keyType.getDeclaredConstructor(String.class, String.class,
            String.class);
        constructor.setAccessible(true);
        Object key = constructor.newInstance("public", "agent-a", "a2a");
        
        assertEquals(key, key);
        assertNotEquals(key, "public");
    }
    
    private void runMaintenance(int index) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(index + 1)).schedule(captor.capture(), anyLong(), any());
        captor.getAllValues().get(index).run();
    }
    
    @ParameterizedTest
    @EnumSource(AgentTransportType.class)
    void a2aRegistrationAndWholeVersionRemovalUseOneStickyOwner(AgentTransportType transport)
        throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(transport);
        registerLegacy("1.0.0", "one", "two", "three");
        registerLegacy("1.2.0", "one");
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        assertBinding(lastPublication(transport), "1.2.0", "[1.0.0,1.2.0]");
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        manager.deregisterA2a("public", "agent-a", "1.2.0");
        manager.deregisterA2a("public", "agent-a", "1.2.0");
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a", transport);
        verify(clientProxy).selectPublicationTransport();
    }
    
    @ParameterizedTest
    @EnumSource(AgentTransportType.class)
    void runtimeWriteSourcesConflictBothDirectionsAndReleaseAfterConfirmedCleanup(
        AgentTransportType transport)
        throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(transport);
        registerLegacy("1.0.0", "one");
        AgentEndpointRegistrationBatch nativeBatch =
            registration("a2a", endpoint("http://other:80"));
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> manager.register("public", nativeBatch)).getErrCode());
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> manager.deregister("public", "agent-a", "a2a", nativeBatch.getEndpoints()))
            .getErrCode());
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        manager.register("public", nativeBatch);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> registerLegacy("1.0.0", "one")).getErrCode());
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "1.0.0")).getErrCode());
        manager.deregister("public", "agent-a", "a2a", nativeBatch.getEndpoints());
        registerLegacy("2.0.0", "one");
        assertBinding(lastPublication(transport), "2.0.0", "[2.0.0]");
    }
    
    @ParameterizedTest
    @ValueSource(ints = {400, 403, 409, 501, 50105})
    void knownRejectionPreservesReferencesAndDoesNotClaimInitialSource(int error) throws Exception {
        NacosException rejected = new NacosException(error, "rejected");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenThrow(rejected).thenReturn(liveness(100));
        assertSame(rejected,
            assertThrows(NacosException.class, () -> registerLegacy("1.0.0", "one")));
        AgentEndpointRegistrationBatch nativeBatch =
            registration("a2a", endpoint("http://native:80"));
        manager.register("public", nativeBatch);
        manager.deregister("public", "agent-a", "a2a", nativeBatch.getEndpoints());
        registerLegacy("1.0.0", "one");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenThrow(rejected).thenReturn(liveness(100));
        assertSame(rejected,
            assertThrows(NacosException.class, () -> registerLegacy("3.0.0", "one")));
        registerLegacy("1.2.0", "one");
        assertBinding(lastPublication(AgentTransportType.HTTP), "1.2.0", "[1.0.0,1.2.0]");
    }
    
    @ParameterizedTest
    @ValueSource(ints = {500, 503, -500})
    void unknownWriteKeepsSourceAndHttpRedoReplaysRetainedRange(int error) throws Exception {
        registerLegacy("1.0.0", "one");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenThrow(new NacosException(error, "unknown")).thenReturn(liveness(100));
        assertThrows(NacosException.class, () -> registerLegacy("1.2.0", "one"));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://other:80"))));
        manager.redoDirtyHttpPublications();
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        assertBinding(lastPublication(AgentTransportType.HTTP), "1.2.0", "[1.0.0,1.2.0]");
    }
    
    @Test
    void rejectedPartialLegacyRemovalRetainsWholeVersionAndNoWholeDelete() throws Exception {
        registerLegacy("1.0.0", "one");
        registerLegacy("1.2.0", "two");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenThrow(new NacosException(50105, "migration")).thenReturn(liveness(100));
        assertEquals(50105, assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "1.0.0")).getErrCode());
        manager.deregisterA2a("public", "agent-a", "1.2.0");
        assertBinding(lastPublication(AgentTransportType.HTTP), "1.0.0", "[1.0.0]");
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any(), any());
    }
    
    @Test
    void unknownLastRemovalKeepsOwnerUntilHttpCleanupIsConfirmed() throws Exception {
        registerLegacy("1.0.0", "one");
        org.mockito.Mockito.doThrow(new NacosException(500, "unknown")).doNothing()
            .when(clientProxy)
            .deregisterAgentEndpoints("public", "agent-a", "a2a", AgentTransportType.HTTP);
        assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "1.0.0"));
        assertEquals(409, assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://native:80"))))
            .getErrCode());
        manager.redoDirtyHttpPublications();
        manager.register("public", registration("a2a", endpoint("http://native:80")));
    }
    
    @Test
    void failedHttpRedoRestoresPreviousLegacyRangesAndReferences() throws Exception {
        registerLegacy("1.0.0", "one");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenThrow(new NacosException(500, "unknown"))
            .thenThrow(new NacosException(400, "rejected")).thenReturn(liveness(100));
        assertThrows(NacosException.class, () -> registerLegacy("3.0.0", "one"));
        manager.redoDirtyHttpPublications();
        registerLegacy("1.2.0", "one");
        assertBinding(lastPublication(AgentTransportType.HTTP), "1.2.0", "[1.0.0,1.2.0]");
    }
    
    @Test
    void scopesDoNotShareLegacyReferencesOrRuntimeSource() throws Exception {
        registerLegacy("1.0.0", "one");
        manager.register("public", registration("other", endpoint("http://one:80")));
        manager.register("isolated", registration("a2a", endpoint("http://one:80")));
        AgentEndpointRegistrationBatch other = registration("a2a", endpoint("http://one:80"));
        other.setAgentName("agent-b");
        manager.register("public", other);
        AgentEndpointPublicationManager second =
            new AgentEndpointPublicationManager(clientProxy, executor);
        try {
            second.register("public", registration("a2a", endpoint("http://one:80")));
        } finally {
            second.shutdown();
        }
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        verify(clientProxy, times(2)).deregisterAgentEndpoints("public", "agent-a", "a2a",
            AgentTransportType.HTTP);
    }
    
    @Test
    void capacityRejectDoesNotAddAReferenceAndRemoteEvictionReleasesSource() throws Exception {
        manager = new AgentEndpointPublicationManager(clientProxy, executor, 1);
        registerLegacy("1.0.0", "one");
        assertThrows(NacosException.class, () -> registerLegacy("1.2.0", "two"));
        registerLegacy("1.2.0", "one");
        assertBinding(lastPublication(AgentTransportType.HTTP), "1.2.0", "[1.0.0,1.2.0]");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenThrow(publicationCapacityException()).thenReturn(liveness(100));
        assertThrows(NacosException.class, () -> registerLegacy("1.3.0", "one"));
        manager.register("public", registration("a2a", endpoint("http://native:80")));
    }
    
    @Test
    void closedManagerRejectsNewWritesAndCannotRestartMaintenance() throws Exception {
        manager.shutdown();
        assertEquals(NacosException.CLIENT_DISCONNECT,
            assertThrows(NacosException.class, () -> registerLegacy("1.0.0", "one")).getErrCode());
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://native:80"))));
        assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "1.0.0"));
        verify(clientProxy, never()).registerAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void grpcReplayIgnoresOldSnapshotAfterNewWriteAndKeepsPreservedRange() throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        AgentEndpointPublicationRedoData old = redo(lastPublication(AgentTransportType.GRPC));
        registerLegacy("1.2.0", "one");
        AgentEndpointPublicationRedoData current = redo(lastPublication(AgentTransportType.GRPC));
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        AgentEndpointPublicationRedoData afterRemoval =
            redo(lastPublication(AgentTransportType.GRPC));
        manager.redoGrpcPublication(old, ignored -> {
            throw new AssertionError("stale replay");
        });
        manager.redoGrpcPublication(current, ignored -> {
            throw new AssertionError("stale replay");
        });
        verify(clientProxy, times(3)).registerAgentEndpoints(any(), any(),
            eq(AgentTransportType.GRPC));
        manager.redoGrpcPublication(afterRemoval, ignored -> {
            throw new AssertionError("successful registration");
        });
        assertBinding(lastPublication(AgentTransportType.GRPC), "1.2.0", "[1.0.0,1.2.0]");
        verify(clientProxy, times(4)).registerAgentEndpoints(any(), any(),
            eq(AgentTransportType.GRPC));
    }
    
    @Test
    void grpcUnknownThenRejectedReplayRestoresPriorIntentWithoutReplayingRejectedWrite()
        throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.GRPC)))
            .thenThrow(new NacosException(500, "unknown"))
            .thenThrow(new NacosException(50105, "migration")).thenReturn(null);
        assertThrows(NacosException.class, () -> registerLegacy("3.0.0", "one"));
        AgentEndpointPublicationRedoData pending = redo(lastPublication(AgentTransportType.GRPC));
        AtomicReference<AgentEndpointPublicationRedoData> restored = new AtomicReference<>();
        manager.redoGrpcPublication(pending, restored::set);
        assertBinding(restored.get().get(), "1.0.0", "[1.0.0]");
        assertTrue(restored.get().isRegistered());
        registerLegacy("1.2.0", "one");
        assertBinding(lastPublication(AgentTransportType.GRPC), "1.2.0", "[1.0.0,1.2.0]");
    }
    
    @Test
    void grpcUnknownRemovalIsConfirmedBeforeSourceReleaseEvenIfRedoSaysRemove() throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        AgentEndpointPublicationRedoData pending = redo(lastPublication(AgentTransportType.GRPC));
        pending.setExpectedRegistered(false);
        pending.setUnregistering(true);
        org.mockito.Mockito.doThrow(new NacosException(500, "unknown")).doNothing()
            .when(clientProxy)
            .deregisterAgentEndpoints("public", "agent-a", "a2a", AgentTransportType.GRPC);
        assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "1.0.0"));
        assertThrows(NacosException.class,
            () -> manager.register("public", registration("a2a", endpoint("http://native:80"))));
        AtomicReference<AgentEndpointPublicationRedoData> reconciled =
            new AtomicReference<>(pending);
        manager.redoGrpcPublication(pending, reconciled::set);
        assertNull(reconciled.get());
        manager.register("public", registration("a2a", endpoint("http://native:80")));
        verify(clientProxy, times(2)).deregisterAgentEndpoints("public", "agent-a", "a2a",
            AgentTransportType.GRPC);
    }
    
    @Test
    void concurrentRegistrationAndRemovalCannotPublishAnOlderSnapshotLast() throws Exception {
        registerLegacy("1.0.0", "one");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(clientProxy.registerAgentEndpoints(eq("public"), any(), eq(AgentTransportType.HTTP)))
            .thenAnswer(invocation -> {
                writing.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return liveness(100);
            });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> registration = pool.submit(() -> {
                registerLegacy("1.2.0", "one");
                return null;
            });
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            Future<?> removal = pool.submit(() -> {
                manager.deregisterA2a("public", "agent-a", "1.2.0");
                return null;
            });
            release.countDown();
            registration.get(5, TimeUnit.SECONDS);
            removal.get(5, TimeUnit.SECONDS);
            assertBinding(lastPublication(AgentTransportType.HTTP), "1.0.0", "[1.0.0,1.2.0]");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
    
    @Test
    void invalidLegacyRemovalIsRejectedBeforeAnyRemoteCall() throws Exception {
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "not-a-version")).getErrCode());
        verify(clientProxy, never()).deregisterAgentEndpoints(any(), any(), any(), any());
    }
    
    @Test
    void repeatedPendingLegacyRemovalWaitsForExistingRedo() throws Exception {
        registerLegacy("1.0.0", "one");
        doThrow(new NacosException(500, "response lost")).when(clientProxy)
            .deregisterAgentEndpoints("public", "agent-a", "a2a", AgentTransportType.HTTP);
        assertThrows(NacosException.class,
            () -> manager.deregisterA2a("public", "agent-a", "1.0.0"));
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        verify(clientProxy).deregisterAgentEndpoints("public", "agent-a", "a2a",
            AgentTransportType.HTTP);
        assertTrue(manager.hasHttpPublication());
    }
    
    @Test
    void missingAndClosedGrpcPublicationRemoveRedoWithoutNetwork() throws Exception {
        AgentEndpointPublicationRedoData pending =
            redo(registration("a2a", endpoint("http://one:80")));
        AtomicReference<AgentEndpointPublicationRedoData> reconciled =
            new AtomicReference<>(pending);
        manager.redoGrpcPublication(pending, reconciled::set);
        assertNull(reconciled.get());
        manager.shutdown();
        reconciled.set(pending);
        manager.redoGrpcPublication(pending, reconciled::set);
        assertNull(reconciled.get());
        verify(clientProxy, never()).registerAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void grpcRedoDoesNotReplayHttpOwnerOrAlreadyRegisteredSnapshot() throws Exception {
        registerLegacy("1.0.0", "one");
        manager.redoGrpcPublication(redo(lastPublication(AgentTransportType.HTTP)), ignored -> {
            throw new AssertionError("HTTP owner must be untouched");
        });
        manager.deregisterA2a("public", "agent-a", "1.0.0");
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        AgentEndpointPublicationRedoData completed = redo(lastPublication(AgentTransportType.GRPC));
        completed.registered();
        manager.redoGrpcPublication(completed, ignored -> {
            throw new AssertionError("Completed registration must be untouched");
        });
        verify(clientProxy, times(2)).registerAgentEndpoints(any(), any(), any());
    }
    
    @Test
    void grpcRedoCapacityRejectionEvictsPublicationAndReleasesSource() throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        AgentEndpointPublicationRedoData pending = redo(lastPublication(AgentTransportType.GRPC));
        when(clientProxy.registerAgentEndpoints(any(), any(), eq(AgentTransportType.GRPC)))
            .thenThrow(publicationCapacityException()).thenReturn(null);
        AtomicReference<AgentEndpointPublicationRedoData> reconciled =
            new AtomicReference<>(pending);
        manager.redoGrpcPublication(pending, reconciled::set);
        assertNull(reconciled.get());
        manager.register("public", registration("a2a", endpoint("http://native:80")));
    }
    
    @Test
    void grpcInitialUnknownThenDefinitiveRejectionRemovesItsRedo() throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        when(clientProxy.registerAgentEndpoints(any(), any(), eq(AgentTransportType.GRPC)))
            .thenThrow(new NacosException(500, "unknown"))
            .thenThrow(new NacosException(403, "denied")).thenReturn(null);
        assertThrows(NacosException.class, () -> registerLegacy("1.0.0", "one"));
        AgentEndpointPublicationRedoData pending = redo(lastPublication(AgentTransportType.GRPC));
        AtomicReference<AgentEndpointPublicationRedoData> reconciled =
            new AtomicReference<>(pending);
        manager.redoGrpcPublication(pending, reconciled::set);
        assertNull(reconciled.get());
        manager.register("public", registration("a2a", endpoint("http://native:80")));
    }
    
    @Test
    void grpcRetryableFailureRetainsIntentAndCleanRejectionKeepsAcknowledgedSnapshot()
        throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        AgentEndpointPublicationRedoData pending = redo(lastPublication(AgentTransportType.GRPC));
        when(clientProxy.registerAgentEndpoints(any(), any(), eq(AgentTransportType.GRPC)))
            .thenThrow(new NacosException(500, "unknown"))
            .thenThrow(new NacosException(403, "denied"));
        manager.redoGrpcPublication(pending, ignored -> {
            throw new AssertionError("Transient failure must remain pending");
        });
        AtomicReference<AgentEndpointPublicationRedoData> reconciled = new AtomicReference<>();
        manager.redoGrpcPublication(pending, reconciled::set);
        assertSame(pending.get(), reconciled.get().get());
        assertTrue(reconciled.get().isRegistered());
        assertTrue(reconciled.get().isExpectedRegistered());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void grpcRejectedReplacementRestoresPendingRegistrationOrRemoval(boolean removal)
        throws Exception {
        when(clientProxy.selectPublicationTransport()).thenReturn(AgentTransportType.GRPC);
        registerLegacy("1.0.0", "one");
        if (removal) {
            doThrow(new NacosException(500, "unknown removal")).when(clientProxy)
                .deregisterAgentEndpoints("public", "agent-a", "a2a", AgentTransportType.GRPC);
            assertThrows(NacosException.class,
                () -> manager.deregisterA2a("public", "agent-a", "1.0.0"));
        } else {
            when(clientProxy.registerAgentEndpoints(any(), any(), eq(AgentTransportType.GRPC)))
                .thenThrow(new NacosException(500, "unknown registration"));
            assertThrows(NacosException.class, () -> registerLegacy("1.1.0", "one"));
        }
        doThrow(new NacosException(500, "unknown replacement"))
            .doThrow(new NacosException(403, "denied")).when(clientProxy)
            .registerAgentEndpoints(any(), any(), eq(AgentTransportType.GRPC));
        assertThrows(NacosException.class, () -> registerLegacy("1.2.0", "one"));
        AgentEndpointPublicationRedoData pending = redo(lastPublication(AgentTransportType.GRPC));
        AtomicReference<AgentEndpointPublicationRedoData> reconciled = new AtomicReference<>();
        manager.redoGrpcPublication(pending, reconciled::set);
        assertEquals(!removal, reconciled.get().isExpectedRegistered());
        assertEquals(removal, reconciled.get().isRegistered());
        assertEquals(removal, reconciled.get().isUnregistering());
        assertBinding(reconciled.get().get(), removal ? "1.0.0" : "1.1.0",
            removal ? "[1.0.0]" : "[1.0.0,1.1.0]");
    }
    
    private void registerLegacy(String version, String... hosts) throws NacosException {
        List<AgentEndpoint> endpoints = new ArrayList<>();
        for (String host : hosts) {
            endpoints.add(A2aEndpointIntentTest.endpoint(version, host));
        }
        manager.registerA2a("public", "agent-a", endpoints);
    }
    
    private AgentEndpointRegistrationBatch lastPublication(AgentTransportType transport)
        throws NacosException {
        ArgumentCaptor<AgentEndpointRegistrationBatch> captor =
            ArgumentCaptor.forClass(AgentEndpointRegistrationBatch.class);
        verify(clientProxy, org.mockito.Mockito.atLeastOnce()).registerAgentEndpoints(eq("public"),
            captor.capture(),
            eq(transport));
        List<AgentEndpointRegistrationBatch> batches = captor.getAllValues();
        return batches.get(batches.size() - 1);
    }
    
    private void assertBinding(AgentEndpointRegistrationBatch batch, String runtime, String range) {
        assertEquals(1, batch.getEndpoints().size());
        List<RuntimeVersionBinding> bindings = batch.getEndpoints().get(0).getBindings();
        assertEquals(1, bindings.size());
        assertEquals(runtime, bindings.get(0).getRuntimeVersion());
        assertEquals(range, bindings.get(0).getVersionRange());
    }
    
    private AgentEndpointPublicationRedoData redo(AgentEndpointRegistrationBatch batch) {
        return new AgentEndpointPublicationRedoData("public", batch);
    }
    
    private ClientLivenessInfo liveness(long interval) {
        ClientLivenessInfo result = new ClientLivenessInfo();
        result.setHeartbeatIntervalMillis(interval);
        return result;
    }
    
    private NacosApiException publicationCapacityException() {
        return new NacosApiException(NacosException.OVER_THRESHOLD,
            ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT, "full");
    }
    
    private AgentEndpointRegistrationBatch registration(String protocol,
        Endpoint... endpoints) {
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setAgentName("agent-a");
        result.setRuntimeVersion("1.0.0");
        result.setProtocol(protocol);
        result.setEndpoints(new ArrayList<Endpoint>(Arrays.asList(endpoints)));
        return result;
    }
    
    private Endpoint endpoint(String uri) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport("http");
        result.setPriority(0);
        result.setWeight(1D);
        return result;
    }
}
