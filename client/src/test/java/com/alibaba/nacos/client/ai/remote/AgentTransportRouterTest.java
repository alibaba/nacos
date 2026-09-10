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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTransportRouterTest {
    
    @Mock
    private AgentGrpcTransport grpcTransport;
    
    @Mock
    private AgentHttpTransport httpTransport;
    
    private AgentTransportRouter router;
    
    @BeforeEach
    void setUp() {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
    }
    
    @Test
    void explicitHttpRoutesEveryOperationToHttp() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.HTTP, grpcTransport, httpTransport);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        AgentVersionDetail version = new AgentVersionDetail();
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(httpTransport.publishAgent(any())).thenReturn(version);
        when(httpTransport.searchAgents(any())).thenReturn(page);
        when(httpTransport.discoverAgent(any())).thenReturn(discovery);
        when(httpTransport.registerAgentEndpoints(any())).thenReturn(liveness);
        when(httpTransport.heartbeatAgentEndpoints()).thenReturn(liveness);
        
        assertSame(version, router.publishAgent(new AgentPublishRequest()));
        assertSame(page, router.searchAgents(new AgentSearchRequest()));
        assertSame(discovery, router.discoverAgent(new AgentDiscoveryRequest()));
        assertEquals(AgentTransportType.HTTP, router.selectPublicationTransport());
        assertSame(liveness,
            router.registerAgentEndpoints(new AgentEndpointRegistrationBatch()));
        router.deregisterAgentEndpoints("public", "agent", "a2a");
        assertSame(liveness, router.heartbeatAgentEndpoints());
        
        verify(grpcTransport, times(6)).recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        verify(httpTransport).deregisterAgentEndpoints("public", "agent", "a2a");
        verify(grpcTransport, never()).publishAgent(any());
    }
    
    @Test
    void explicitHttpReadFailuresAreReturnedWithoutCrossTransportRetry()
        throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.HTTP, grpcTransport, httpTransport);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        NacosException searchFailure =
            new NacosException(NacosException.SERVER_ERROR, "search failed");
        NacosException discoverFailure =
            new NacosException(NacosException.SERVER_ERROR, "discover failed");
        when(httpTransport.searchAgents(any())).thenThrow(searchFailure);
        when(httpTransport.discoverAgent(any())).thenThrow(discoverFailure);
        
        assertSame(searchFailure,
            assertThrows(NacosException.class,
                () -> router.searchAgents(new AgentSearchRequest())));
        assertSame(discoverFailure,
            assertThrows(NacosException.class,
                () -> router.discoverAgent(new AgentDiscoveryRequest())));
        verify(grpcTransport, never()).searchAgents(any());
        verify(grpcTransport, never()).discoverAgent(any());
    }
    
    @Test
    void explicitGrpcNeverFallsBackToHttp() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.GRPC, grpcTransport, httpTransport);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        AgentVersionDetail version = new AgentVersionDetail();
        when(grpcTransport.publishAgent(any())).thenReturn(version);
        when(grpcTransport.searchAgents(any())).thenThrow(
            new NacosException(NacosException.SERVER_ERROR, "unavailable"));
        
        assertSame(version, router.publishAgent(new AgentPublishRequest()));
        assertThrows(NacosException.class,
            () -> router.searchAgents(new AgentSearchRequest()));
        assertEquals(AgentTransportType.GRPC, router.selectPublicationTransport());
        verify(httpTransport, never()).searchAgents(any());
        verify(httpTransport, never()).publishAgent(any());
    }
    
    @Test
    void autoUsesAvailableGrpcAndReturnsItsReadResult() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        when(grpcTransport.searchAgents(any())).thenReturn(page);
        when(grpcTransport.discoverAgent(any())).thenReturn(discovery);
        
        assertSame(page, router.searchAgents(new AgentSearchRequest()));
        assertSame(discovery, router.discoverAgent(new AgentDiscoveryRequest()));
        verify(httpTransport, never()).searchAgents(any());
        verify(httpTransport, never()).discoverAgent(any());
    }
    
    @Test
    void autoUsesHttpBeforeGrpcBecomesAvailable() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(false);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        AgentVersionDetail version = new AgentVersionDetail();
        when(httpTransport.searchAgents(any())).thenReturn(page);
        when(httpTransport.discoverAgent(any())).thenReturn(discovery);
        when(httpTransport.publishAgent(any())).thenReturn(version);
        
        assertSame(page, router.searchAgents(new AgentSearchRequest()));
        assertSame(discovery, router.discoverAgent(new AgentDiscoveryRequest()));
        assertSame(version, router.publishAgent(new AgentPublishRequest()));
        assertEquals(AgentTransportType.HTTP, router.selectPublicationTransport());
        verify(grpcTransport, times(3)).recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
    }
    
    @Test
    void autoRereadsThroughHttpAfterGrpcTransportFailure() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        when(grpcTransport.searchAgents(any())).thenThrow(
            new NacosException(NacosException.CLIENT_DISCONNECT, "disconnected"));
        when(grpcTransport.discoverAgent(any())).thenThrow(
            new NacosException(NacosException.UN_REGISTER, "unregistered"));
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        when(httpTransport.searchAgents(any())).thenReturn(page);
        when(httpTransport.discoverAgent(any())).thenReturn(discovery);
        
        assertSame(page, router.searchAgents(new AgentSearchRequest()));
        assertSame(discovery, router.discoverAgent(new AgentDiscoveryRequest()));
        verify(grpcTransport, times(2)).recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
    }
    
    @Test
    void autoRecognizesOnlyConnectionFailuresForReadFallback() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        when(httpTransport.searchAgents(any())).thenReturn(page);
        int[] transportErrors = {NacosException.CLIENT_DISCONNECT, NacosException.UN_REGISTER};
        
        for (int error : transportErrors) {
            doThrow(new NacosException(error, "transport failure")).when(grpcTransport)
                .searchAgents(any());
            assertSame(page, router.searchAgents(new AgentSearchRequest()));
        }
        
        verify(httpTransport, times(transportErrors.length)).searchAgents(any());
    }
    
    @Test
    void autoFallsBackForNestedGrpcUnavailable() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        when(httpTransport.searchAgents(any())).thenReturn(page);
        Throwable[] unavailableFailures = {Status.UNAVAILABLE.asRuntimeException(),
            Status.UNAVAILABLE.asException()};
        
        for (Throwable failure : unavailableFailures) {
            doThrow(new NacosException(NacosException.SERVER_ERROR,
                new IllegalStateException(failure))).when(grpcTransport).searchAgents(any());
            assertSame(page, router.searchAgents(new AgentSearchRequest()));
        }
        
        verify(grpcTransport, times(unavailableFailures.length))
            .recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
    }
    
    @Test
    void autoFallsBackWhenGrpcDisconnectsBetweenSelectionAndInvocation()
        throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        when(grpcTransport.searchAgents(any())).thenThrow(
            new NacosException(NacosException.CLIENT_DISCONNECT, "connection unavailable"));
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        when(httpTransport.searchAgents(any())).thenReturn(page);
        
        assertSame(page, router.searchAgents(new AgentSearchRequest()));
        verify(grpcTransport).recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
    }
    
    @Test
    void autoDoesNotFallbackForGenericGrpcServerFailures() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        int[] serverErrors = {NacosException.SERVER_ERROR, NacosException.BAD_GATEWAY,
            NacosException.SERVER_NOT_IMPLEMENTED, NacosException.NO_HANDLER};
        
        for (int error : serverErrors) {
            doThrow(new NacosException(error, "server failure")).when(grpcTransport)
                .searchAgents(any());
            assertThrows(NacosException.class,
                () -> router.searchAgents(new AgentSearchRequest()));
        }
        
        verify(httpTransport, never()).searchAgents(any());
    }
    
    @Test
    void autoDoesNotFallbackForNonUnavailableGrpcStatus() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        Throwable[] serverFailures = {Status.INTERNAL.asRuntimeException(),
            Status.INTERNAL.asException()};
        
        for (Throwable failure : serverFailures) {
            doThrow(new NacosException(NacosException.SERVER_ERROR,
                new IllegalStateException(failure))).when(grpcTransport).searchAgents(any());
            assertThrows(NacosException.class,
                () -> router.searchAgents(new AgentSearchRequest()));
        }
        
        verify(httpTransport, never()).searchAgents(any());
    }
    
    @Test
    void autoDoesNotFallbackOnBusinessFailure() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        when(grpcTransport.discoverAgent(any())).thenThrow(
            new NacosException(NacosException.INVALID_PARAM, "invalid"));
        
        assertThrows(NacosException.class,
            () -> router.discoverAgent(new AgentDiscoveryRequest()));
        verify(httpTransport, never()).discoverAgent(any());
    }
    
    @Test
    void autoWriteDoesNotCrossFallbackAfterGrpcSend() throws NacosException {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.publishAgent(any())).thenThrow(
            new NacosException(NacosException.SERVER_ERROR, "unknown result"));
        
        assertThrows(NacosException.class,
            () -> router.publishAgent(new AgentPublishRequest()));
        verify(httpTransport, never()).publishAgent(any());
    }
    
    @Test
    void publicationOwnerIsExplicitlySticky() throws NacosException {
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        ClientLivenessInfo grpcLiveness = new ClientLivenessInfo();
        ClientLivenessInfo httpLiveness = new ClientLivenessInfo();
        when(grpcTransport.registerAgentEndpoints(batch)).thenReturn(grpcLiveness);
        when(httpTransport.registerAgentEndpoints(batch)).thenReturn(httpLiveness);
        when(grpcTransport.heartbeatAgentEndpoints()).thenReturn(grpcLiveness);
        when(httpTransport.heartbeatAgentEndpoints()).thenReturn(httpLiveness);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        when(httpTransport.getType()).thenReturn(AgentTransportType.HTTP);
        
        assertSame(grpcLiveness,
            router.registerAgentEndpoints(batch, AgentTransportType.GRPC));
        assertSame(httpLiveness,
            router.registerAgentEndpoints(batch, AgentTransportType.HTTP));
        router.deregisterAgentEndpoints("public", "agent", "a2a", AgentTransportType.GRPC);
        router.deregisterAgentEndpoints("public", "agent", "a2a", AgentTransportType.HTTP);
        assertSame(grpcLiveness, router.heartbeatAgentEndpoints(AgentTransportType.GRPC));
        assertSame(httpLiveness, router.heartbeatAgentEndpoints(AgentTransportType.HTTP));
        
        verify(grpcTransport).deregisterAgentEndpoints("public", "agent", "a2a");
        verify(httpTransport).deregisterAgentEndpoints("public", "agent", "a2a");
        verify(grpcTransport, times(3)).recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
    }
    
    @Test
    void businessErrorsWinOverConcurrentDisconnectAndUnavailableCause() throws Exception {
        router = new AgentTransportRouter(AgentTransportMode.AUTO, grpcTransport, httpTransport);
        when(grpcTransport.isAvailable(AgentGrpcTransport.Resource.AGENT)).thenReturn(true);
        when(grpcTransport.getType()).thenReturn(AgentTransportType.GRPC);
        int[] codes = {401, NacosException.NO_RIGHT, NacosException.INVALID_PARAM,
            NacosException.NOT_FOUND, NacosException.CONFLICT, NacosException.OVER_THRESHOLD,
            NacosException.CLIENT_OVER_THRESHOLD, NacosException.SERVER_NOT_IMPLEMENTED};
        for (int code : codes) {
            NacosException failure = new NacosException(code, "business failure",
                Status.UNAVAILABLE.asRuntimeException());
            org.mockito.Mockito.doThrow(failure).when(grpcTransport).searchAgents(any());
            assertSame(failure, assertThrows(NacosException.class,
                () -> router.searchAgents(new AgentSearchRequest())));
        }
        verify(httpTransport, never()).searchAgents(any());
    }
}
