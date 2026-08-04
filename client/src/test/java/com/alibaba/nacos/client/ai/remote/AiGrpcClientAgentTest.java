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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSearchRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentPublishRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointOperationResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSearchResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentPublishRpcResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.ai.remote.redo.AiGrpcRedoService;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGrpcClientAgentTest {
    
    private static final String PUBLICATION_KEY = "public@@agent-a@@a2a";
    
    @Mock
    private RpcClient rpcClient;
    
    @Mock
    private AbstractServerListManager serverListManager;
    
    @Mock
    private AiGrpcRedoService redoService;
    
    @Mock
    private SecurityProxy securityProxy;
    
    private AiGrpcClient client;
    
    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        client = new AiGrpcClient("public",
            NacosClientProperties.PROTOTYPE.derive(properties));
        injectMocks();
        lenient().when(securityProxy.getIdentityContext(any(RequestResource.class)))
            .thenReturn(Collections.singletonMap("identity", "alice"));
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        client.shutdown();
    }
    
    @Test
    void searchAndDiscoverUseTypedRequestsAndSecurityIdentity() throws Exception {
        support(AbilityKey.SERVER_AGENT_DISCOVERY_V1);
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        AgentCatalogEntry entry = new AgentCatalogEntry();
        entry.setAgentName("agent-a");
        page.setPageItems(Collections.singletonList(entry));
        AgentSearchResponse searchResponse = new AgentSearchResponse();
        searchResponse.setPage(page);
        AgentDiscoveryResult discoveryResult = new AgentDiscoveryResult();
        discoveryResult.setAgentName("agent-a");
        AgentDiscoveryResponse discoveryResponse = new AgentDiscoveryResponse();
        discoveryResponse.setDiscoveryResult(discoveryResult);
        when(rpcClient.request(any(AgentSearchRpcRequest.class))).thenReturn(searchResponse);
        when(rpcClient.request(any(AgentDiscoveryRpcRequest.class)))
            .thenReturn(discoveryResponse);
        AgentSearchRequest search = new AgentSearchRequest();
        search.setNamespaceId("public");
        AgentDiscoveryRequest discovery = discoveryRequest();
        
        assertSame(page, client.searchAgents(search));
        assertSame(discoveryResult, client.discoverAgent(discovery));
        
        List<Request> requests = rpcClientRequests();
        AgentSearchRpcRequest searchRpcRequest = (AgentSearchRpcRequest) requests.get(0);
        assertSame(search, searchRpcRequest.getSearchRequest());
        assertEquals("alice", searchRpcRequest.getHeader("identity"));
        AgentDiscoveryRpcRequest discoveryRpcRequest =
            (AgentDiscoveryRpcRequest) requests.get(1);
        assertSame(discovery, discoveryRpcRequest.getDiscoveryRequest());
        assertEquals("alice", discoveryRpcRequest.getHeader("identity"));
    }
    
    @Test
    void publishUsesTypedRequestAndDedicatedAbility() throws Exception {
        support(AbilityKey.SERVER_AGENT_PUBLISH_V1);
        AgentPublishRequest publication = new AgentPublishRequest();
        publication.setAgentName("agent-a");
        AgentVersionDetail expected = new AgentVersionDetail();
        AgentPublishRpcResponse response = new AgentPublishRpcResponse();
        response.setVersionDetail(expected);
        when(rpcClient.request(any(AgentPublishRpcRequest.class))).thenReturn(response);
        
        assertSame(expected, client.publishAgent(publication));
        ArgumentCaptor<AgentPublishRpcRequest> request =
            ArgumentCaptor.forClass(AgentPublishRpcRequest.class);
        verify(rpcClient).request(request.capture());
        assertEquals("public", request.getValue().getNamespaceId());
        assertSame(publication, request.getValue().getPublishRequest());
        assertEquals("alice", request.getValue().getHeader("identity"));
    }
    
    @Test
    void publishRequiresExplicitServerAbility() {
        when(rpcClient.isRunning()).thenReturn(true);
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_AGENT_PUBLISH_V1))
            .thenReturn(AbilityStatus.NOT_SUPPORTED);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> client.publishAgent(new AgentPublishRequest())).getErrCode());
    }
    
    @Test
    void strictAbilityRequiresConnectedExplicitSupport() throws Exception {
        when(rpcClient.isRunning()).thenReturn(false);
        assertEquals(NacosException.SERVER_ERROR, assertThrows(NacosException.class,
            () -> client.searchAgents(new AgentSearchRequest())).getErrCode());
        
        when(rpcClient.isRunning()).thenReturn(true);
        when(rpcClient.getConnectionAbility(AbilityKey.SERVER_AGENT_DISCOVERY_V1))
            .thenReturn(AbilityStatus.UNKNOWN, AbilityStatus.NOT_SUPPORTED);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> client.searchAgents(new AgentSearchRequest())).getErrCode());
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED,
            assertThrows(NacosException.class,
                () -> client.searchAgents(new AgentSearchRequest())).getErrCode());
        verify(rpcClient, never()).request(any(AgentSearchRpcRequest.class));
    }
    
    @Test
    void registerCachesCompleteBatchAndMarksItRegistered() throws Exception {
        support(AbilityKey.SERVER_AGENT_ENDPOINT_V1);
        AgentEndpointOperationResponse response = new AgentEndpointOperationResponse();
        when(rpcClient.request(any(AgentEndpointRegisterRpcRequest.class))).thenReturn(response);
        AgentEndpointRegistrationBatch batch = registrationBatch("http://one/a");
        
        assertNull(client.registerAgentEndpoints(batch));
        
        InOrder order = inOrder(redoService, rpcClient);
        order.verify(redoService).cacheAgentEndpointPublication(batch);
        ArgumentCaptor<AgentEndpointRegisterRpcRequest> request =
            ArgumentCaptor.forClass(AgentEndpointRegisterRpcRequest.class);
        order.verify(rpcClient).request(request.capture());
        order.verify(redoService).agentEndpointPublicationRegistered(PUBLICATION_KEY);
        assertSame(batch, request.getValue().getRegistrationBatch());
    }
    
    @Test
    void nonRetryableRegisterFailureRestoresPreviousRedoIntent() throws Exception {
        support(AbilityKey.SERVER_AGENT_ENDPOINT_V1);
        AgentEndpointRegistrationBatch previous = registrationBatch("http://old/a");
        AgentEndpointRegistrationBatch replacement = registrationBatch("http://new/a");
        when(redoService.getAgentEndpointPublication(PUBLICATION_KEY)).thenReturn(previous);
        when(redoService.isAgentEndpointPublicationRegistered(PUBLICATION_KEY)).thenReturn(true);
        when(rpcClient.request(any(AgentEndpointRegisterRpcRequest.class)))
            .thenReturn(error(ErrorCode.PARAMETER_VALIDATE_ERROR));
        
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> client.registerAgentEndpoints(replacement)).getErrCode());
        
        InOrder order = inOrder(redoService);
        order.verify(redoService).cacheAgentEndpointPublication(replacement);
        order.verify(redoService).discardAgentEndpointPublication(PUBLICATION_KEY);
        order.verify(redoService).cacheAgentEndpointPublication(previous);
        order.verify(redoService).agentEndpointPublicationRegistered(PUBLICATION_KEY);
    }
    
    @Test
    void nonRetryableInitialFailureDiscardsAndRetryableFailureKeepsRedoIntent()
        throws Exception {
        support(AbilityKey.SERVER_AGENT_ENDPOINT_V1);
        AgentEndpointRegistrationBatch batch = registrationBatch("http://one/a");
        when(rpcClient.request(any(AgentEndpointRegisterRpcRequest.class)))
            .thenReturn(error(ErrorCode.PARAMETER_VALIDATE_ERROR))
            .thenReturn(error(ErrorCode.SERVER_ERROR));
        
        assertThrows(NacosException.class, () -> client.registerAgentEndpoints(batch));
        verify(redoService).discardAgentEndpointPublication(PUBLICATION_KEY);
        
        assertThrows(NacosException.class, () -> client.registerAgentEndpoints(batch));
        verify(redoService, times(1)).discardAgentEndpointPublication(PUBLICATION_KEY);
    }
    
    @Test
    void deregisterPublishesWholeTombstoneAndRemovesItAfterSuccess() throws Exception {
        support(AbilityKey.SERVER_AGENT_ENDPOINT_V1);
        when(rpcClient.request(any(AgentEndpointDeregisterRpcRequest.class)))
            .thenReturn(new AgentEndpointOperationResponse());
        
        client.deregisterAgentEndpoints("public", "agent-a", "a2a");
        
        InOrder order = inOrder(redoService, rpcClient);
        order.verify(redoService).agentEndpointPublicationDeregistering(PUBLICATION_KEY);
        ArgumentCaptor<AgentEndpointDeregisterRpcRequest> request =
            ArgumentCaptor.forClass(AgentEndpointDeregisterRpcRequest.class);
        order.verify(rpcClient).request(request.capture());
        order.verify(redoService).agentEndpointPublicationDeregistered(PUBLICATION_KEY);
        assertEquals("public", request.getValue().getNamespaceId());
        assertEquals("agent-a", request.getValue().getAgentName());
        assertEquals("a2a", request.getValue().getProtocol());
    }
    
    @Test
    void nonRetryableDeregisterFailureRestoresPreviousRegisteredBatch() throws Exception {
        support(AbilityKey.SERVER_AGENT_ENDPOINT_V1);
        AgentEndpointRegistrationBatch previous = registrationBatch("http://old/a");
        when(redoService.getAgentEndpointPublication(PUBLICATION_KEY)).thenReturn(previous);
        when(redoService.isAgentEndpointPublicationRegistered(PUBLICATION_KEY)).thenReturn(true);
        when(rpcClient.request(any(AgentEndpointDeregisterRpcRequest.class)))
            .thenReturn(error(ErrorCode.RESOURCE_CONFLICT));
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> client.deregisterAgentEndpoints("public", "agent-a", "a2a")).getErrCode());
        
        verify(redoService).discardAgentEndpointPublication(PUBLICATION_KEY);
        verify(redoService).cacheAgentEndpointPublication(previous);
        verify(redoService).agentEndpointPublicationRegistered(PUBLICATION_KEY);
    }
    
    @Test
    void accessDeniedMapsToNoRightAndTriggersRelogin() throws Exception {
        support(AbilityKey.SERVER_AGENT_DISCOVERY_V1);
        when(rpcClient.request(any(AgentSearchRpcRequest.class)))
            .thenReturn(error(ErrorCode.ACCESS_DENIED));
        
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> client.searchAgents(new AgentSearchRequest())).getErrCode());
        verify(securityProxy).reLogin();
    }
    
    @Test
    void everyAgentDetailErrorCategoryMapsToCommonSdkCategory() throws Exception {
        Method mapper =
            AiGrpcClient.class.getDeclaredMethod("mapAgentClientErrorCode", int.class);
        mapper.setAccessible(true);
        assertMapped(mapper, ErrorCode.ACCESS_DENIED, NacosException.NO_RIGHT);
        assertMapped(mapper, ErrorCode.RESOURCE_NOT_FOUND, NacosException.NOT_FOUND);
        assertMapped(mapper, ErrorCode.NAMESPACE_NOT_EXIST, NacosException.NOT_FOUND);
        assertMapped(mapper, ErrorCode.RESOURCE_CONFLICT, NacosException.CONFLICT);
        assertMapped(mapper, ErrorCode.ILLEGAL_STATE, NacosException.CONFLICT);
        assertMapped(mapper, ErrorCode.PARAMETER_MISSING, NacosException.INVALID_PARAM);
        assertMapped(mapper, ErrorCode.PARAMETER_VALIDATE_ERROR, NacosException.INVALID_PARAM);
        assertMapped(mapper, ErrorCode.TENANT_PARAM_ERROR, NacosException.INVALID_PARAM);
        assertMapped(mapper, ErrorCode.MEDIA_TYPE_ERROR, NacosException.INVALID_PARAM);
        assertMapped(mapper, ErrorCode.ILLEGAL_NAMESPACE, NacosException.INVALID_PARAM);
        assertMapped(mapper, ErrorCode.SERVER_ERROR, NacosException.SERVER_ERROR);
        assertMapped(mapper, ErrorCode.DATA_ACCESS_ERROR, NacosException.SERVER_ERROR);
        assertEquals(98765, mapper.invoke(client, 98765));
    }
    
    @Test
    void grpcHeartbeatIsConnectionManaged() {
        assertNull(client.heartbeatAgentEndpoints());
    }
    
    private void support(AbilityKey abilityKey) {
        when(rpcClient.isRunning()).thenReturn(true);
        when(rpcClient.getConnectionAbility(abilityKey)).thenReturn(AbilityStatus.SUPPORTED);
    }
    
    private AgentDiscoveryRequest discoveryRequest() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("agent-a");
        AgentDiscoveryRequest result = new AgentDiscoveryRequest();
        result.setNamespaceId("public");
        result.setReference(reference);
        return result;
    }
    
    private List<Request> rpcClientRequests() throws NacosException {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(rpcClient, times(2)).request(captor.capture());
        return captor.getAllValues();
    }
    
    private AgentEndpointRegistrationBatch registrationBatch(String uri) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport("jsonrpc");
        AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
        result.setNamespaceId("public");
        result.setAgentName("agent-a");
        result.setRuntimeVersion("runtime-1");
        result.setProtocol("a2a");
        result.setEndpoints(Collections.singletonList(endpoint));
        return result;
    }
    
    private Response error(ErrorCode errorCode) {
        return ErrorResponse.build(errorCode.getCode(), errorCode.getMsg());
    }
    
    private void assertMapped(Method mapper, ErrorCode input, int expected) throws Exception {
        assertEquals(expected, mapper.invoke(client, input.getCode()));
    }
    
    private void injectMocks() throws Exception {
        replaceAndShutdown("rpcClient", rpcClient);
        replaceAndShutdown("serverListManager", serverListManager);
        replaceAndShutdown("redoService", redoService);
        injectField("securityProxy", securityProxy);
    }
    
    private void replaceAndShutdown(String fieldName, Object replacement) throws Exception {
        Field field = AiGrpcClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object original = field.get(client);
        field.set(client, replacement);
        if (original instanceof RpcClient) {
            ((RpcClient) original).shutdown();
        } else if (original instanceof AbstractServerListManager) {
            ((AbstractServerListManager) original).shutdown();
        } else if (original instanceof AiGrpcRedoService) {
            ((AiGrpcRedoService) original).shutdown();
        }
    }
    
    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AiGrpcClient.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(client, value);
    }
}
