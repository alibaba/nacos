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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.common.remote.client.InitialConnectionFailureListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentGrpcTransportTest {
    
    @Mock
    private AiGrpcClient client;
    
    @Mock
    private NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    @Mock
    private NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private List<InitialConnectionFailureListener> listeners;
    
    @BeforeEach
    void setUp() {
        when(client.getRetryTimes()).thenReturn(2);
        listeners = new ArrayList<InitialConnectionFailureListener>();
        doAnswer(invocation -> {
            listeners.add(invocation.getArgument(0));
            return null;
        }).when(client).registerInitialConnectionFailureListener(any());
    }
    
    @Test
    void configuredGrpcAndAutoStartSynchronouslyOnce() throws NacosException {
        AgentGrpcTransport grpc = transport(AgentTransportMode.GRPC);
        grpc.startConfiguredTransport();
        grpc.startConfiguredTransport();
        verify(client).start(mcpServerCacheHolder, agentCardCacheHolder);
        assertTrue(grpc.isAvailable(AgentGrpcTransport.Resource.AGENT));
        assertEquals(AgentTransportType.GRPC, grpc.getType());
        when(client.isEnable()).thenReturn(true);
        assertTrue(grpc.isConnected());
    }
    
    @Test
    void configuredHttpSkipsInitialGrpcAndStartsOnLegacyFeatureDemand()
        throws NacosException {
        AgentGrpcTransport transport = transport(AgentTransportMode.HTTP);
        transport.startConfiguredTransport();
        verify(client, never()).start(any(), any());
        
        assertSame(client, transport.requireGrpcClient());
        assertSame(client, transport.requireGrpcClient());
        verify(client).start(mcpServerCacheHolder, agentCardCacheHolder);
        verify(client, times(2)).resumeInitialReconnect();
    }
    
    @Test
    void autoUsesGrpcOnlyWhenRunningAndAbilityIsSupported() {
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        when(client.isEnable()).thenReturn(true);
        when(client.isAbilitySupportedByServer(AbilityKey.SERVER_RAD_V1)).thenReturn(true);
        assertTrue(transport.isAvailable(AgentGrpcTransport.Resource.AGENT));
        
        when(client.isAbilitySupportedByServer(AbilityKey.SERVER_RAD_V1)).thenReturn(false);
        assertFalse(transport.isAvailable(AgentGrpcTransport.Resource.AGENT));
        when(client.isEnable()).thenReturn(false);
        assertFalse(transport.isAvailable(AgentGrpcTransport.Resource.AGENT));
        assertFalse(
            transport(AgentTransportMode.HTTP).isAvailable(AgentGrpcTransport.Resource.AGENT));
        assertTrue(
            transport(AgentTransportMode.GRPC).isAvailable(AgentGrpcTransport.Resource.AGENT));
    }
    
    @Test
    void autoSettlesOnlyAfterProbeBudgetAndSuccessfulHttp() {
        when(client.suspendInitialReconnect()).thenReturn(true);
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        InitialConnectionFailureListener listener = capturedListener();
        
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        listener.onFailure(1);
        verify(client, never()).suspendInitialReconnect();
        listener.onFailure(2);
        verify(client).suspendInitialReconnect();
        assertTrue(transport.isAutoHttpStable());
        
        listener.onFailure(3);
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        verify(client).suspendInitialReconnect();
        assertFalse(transport.isAvailable(AgentGrpcTransport.Resource.AGENT));
    }
    
    @Test
    void autoCanSettleWhenHttpSucceedsAfterProbeBudget() {
        when(client.getInitialConnectionFailureCount()).thenReturn(2);
        when(client.suspendInitialReconnect()).thenReturn(true);
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        capturedListener().onFailure(2);
        verify(client, never()).suspendInitialReconnect();
        
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        verify(client).suspendInitialReconnect();
        assertTrue(transport.isAutoHttpStable());
    }
    
    @Test
    void autoRemainsUnsettledWhenInitialReconnectCannotBeSuspended() {
        when(client.getInitialConnectionFailureCount()).thenReturn(2);
        when(client.suspendInitialReconnect()).thenReturn(false);
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        
        verify(client).suspendInitialReconnect();
        assertFalse(transport.isAutoHttpStable());
    }
    
    @Test
    void explicitGrpcAndHttpNeverUseAutoSettlement() {
        AgentGrpcTransport grpc = transport(AgentTransportMode.GRPC);
        capturedListener().onFailure(100);
        grpc.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        
        AgentGrpcTransport http = transport(AgentTransportMode.HTTP);
        capturedListener().onFailure(100);
        http.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        verify(client, never()).suspendInitialReconnect();
    }
    
    @Test
    void legacyGrpcDemandPreventsAutoReconnectSuspension() throws NacosException {
        when(client.getInitialConnectionFailureCount()).thenReturn(2);
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        transport.requireGrpcClient();
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        capturedListener().onFailure(2);
        
        verify(client, never()).suspendInitialReconnect();
        verify(client).resumeInitialReconnect();
        assertFalse(transport.isAutoHttpStable());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void agentOperationsDelegateToGrpcClient() throws NacosException {
        AgentGrpcTransport transport = transport(AgentTransportMode.GRPC);
        AgentPublishRequest publishRequest = new AgentPublishRequest();
        AgentVersionDetail version = new AgentVersionDetail();
        AgentSearchRequest searchRequest = new AgentSearchRequest();
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        when(client.publishAgent(publishRequest)).thenReturn(version);
        when(client.searchAgents(searchRequest)).thenReturn(page);
        when(client.discoverAgent(discoveryRequest)).thenReturn(discovery);
        when(client.registerAgentEndpoints(batch)).thenReturn(liveness);
        when(client.heartbeatAgentEndpoints()).thenReturn(liveness);
        
        assertSame(version, transport.publishAgent(publishRequest));
        assertSame(page, transport.searchAgents(searchRequest));
        assertSame(discovery, transport.discoverAgent(discoveryRequest));
        assertSame(liveness, transport.registerAgentEndpoints(batch));
        transport.deregisterAgentEndpoints("public", "agent", "a2a");
        assertSame(liveness, transport.heartbeatAgentEndpoints());
        verify(client).deregisterAgentEndpoints("public", "agent", "a2a");
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void requiredProxyAcquiresGrpcForEveryLegacyOperation() throws NacosException {
        AgentGrpcTransport transport = transport(AgentTransportMode.HTTP);
        AiClientProxy proxy = transport.requiredProxy();
        AgentPublishRequest publishRequest = new AgentPublishRequest();
        publishRequest.setAgentName("agent");
        publishRequest.setVersion("1.0.0");
        publishRequest.setCallInterfaces(
            Collections.singletonList(new AgentCallInterface()));
        AgentVersionDetail version = new AgentVersionDetail();
        Page page = new Page();
        AgentDiscoveryResult discovery = new AgentDiscoveryResult();
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        Prompt prompt = new Prompt();
        SkillQueryResponse skill = new SkillQueryResponse(new byte[] {0x01}, "md5", "1.0.0");
        AgentSpecQueryResponse agentSpec =
            new AgentSpecQueryResponse(new AgentSpec(), "md5", "1.0.0");
        when(client.publishAgent(any())).thenReturn(version);
        when(client.searchAgents(any())).thenReturn(page);
        when(client.discoverAgent(any())).thenReturn(discovery);
        when(client.registerAgentEndpoints(any())).thenReturn(liveness);
        when(client.heartbeatAgentEndpoints()).thenReturn(liveness);
        when(client.queryPrompt("prompt", "1", "latest", "md5")).thenReturn(prompt);
        when(client.querySkill("skill", "1", "latest", "md5")).thenReturn(skill);
        when(client.queryAgentSpec("spec", "1", "latest", "md5")).thenReturn(agentSpec);
        
        assertSame(version, proxy.publishAgent(publishRequest));
        assertSame(page, proxy.searchAgents(new AgentSearchRequest()));
        assertSame(discovery, proxy.discoverAgent(new AgentDiscoveryRequest()));
        assertSame(liveness, proxy.registerAgentEndpoints(batch));
        proxy.deregisterAgentEndpoints("public", "agent", "a2a");
        assertSame(liveness, proxy.heartbeatAgentEndpoints());
        assertSame(prompt, proxy.queryPrompt("prompt", "1", "latest", "md5"));
        assertSame(skill, proxy.querySkill("skill", "1", "latest", "md5"));
        assertSame(agentSpec, proxy.queryAgentSpec("spec", "1", "latest", "md5"));
        proxy.shutdown();
        
        verify(client, times(9)).resumeInitialReconnect();
        verify(client).deregisterAgentEndpoints("public", "agent", "a2a");
    }
    
    @Test
    void resourcesNegotiateIndependentlyAndPromptNeedsOnlyConnection() {
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        when(client.isEnable()).thenReturn(true);
        when(client.isAbilitySupportedByServer(AbilityKey.SERVER_RAD_V1)).thenReturn(false);
        when(client.isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)).thenReturn(true);
        assertFalse(transport.isAvailable(AgentGrpcTransport.Resource.AGENT));
        assertTrue(transport.isAvailable(AgentGrpcTransport.Resource.MCP));
        assertTrue(transport.isAvailable(AgentGrpcTransport.Resource.PROMPT));
    }
    
    @Test
    void allUsedAutoResourcesMustSucceedBeforeSuspension() {
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        transport.isAvailable(AgentGrpcTransport.Resource.AGENT);
        transport.isAvailable(AgentGrpcTransport.Resource.MCP);
        when(client.getInitialConnectionFailureCount()).thenReturn(2);
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        capturedListener().onFailure(3);
        verify(client, never()).suspendInitialReconnect();
        when(client.suspendInitialReconnect()).thenReturn(true);
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.MCP);
        assertTrue(transport.isAutoHttpStable());
        verify(client).suspendInitialReconnect();
    }
    
    @Test
    void unusedResourceResumesItsProbeWithoutChangingSettledResource() {
        AgentGrpcTransport transport = transport(AgentTransportMode.AUTO);
        when(client.getInitialConnectionFailureCount()).thenReturn(2);
        when(client.suspendInitialReconnect()).thenReturn(true);
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        assertTrue(transport.isAutoHttpStable());
        transport.isAvailable(AgentGrpcTransport.Resource.PROMPT);
        verify(client).resumeInitialReconnect();
        assertFalse(transport.isAutoHttpStable());
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
        verify(client).suspendInitialReconnect();
        capturedListener().onFailure(3);
        verify(client).suspendInitialReconnect();
        capturedListener().onFailure(4);
        verify(client, times(2)).suspendInitialReconnect();
        assertTrue(transport.isAutoHttpStable());
    }
    
    @Test
    void explicitGrpcResourcePinsSharedProbeEvenWhenAnotherAutoSucceeds() throws Exception {
        AgentGrpcTransport transport = new AgentGrpcTransport(AgentTransportMode.AUTO,
            AgentTransportMode.GRPC, AgentTransportMode.HTTP, client, mcpServerCacheHolder,
            agentCardCacheHolder);
        transport.startConfiguredTransport();
        transport.recordHttpSuccess(AgentGrpcTransport.Resource.AGENT);
        capturedListener().onFailure(10);
        verify(client).start(mcpServerCacheHolder, agentCardCacheHolder);
        verify(client, never()).suspendInitialReconnect();
        assertTrue(transport.isAvailable(AgentGrpcTransport.Resource.MCP));
        assertFalse(transport.isAvailable(AgentGrpcTransport.Resource.PROMPT));
    }
    
    private AgentGrpcTransport transport(AgentTransportMode mode) {
        AgentGrpcTransport result = new AgentGrpcTransport(mode, mode, mode, client,
            mcpServerCacheHolder,
            agentCardCacheHolder);
        return result;
    }
    
    private InitialConnectionFailureListener capturedListener() {
        return listeners.get(listeners.size() - 1);
    }
}
