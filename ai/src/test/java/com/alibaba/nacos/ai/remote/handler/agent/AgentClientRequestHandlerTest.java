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

package com.alibaba.nacos.ai.remote.handler.agent;

import com.alibaba.nacos.ai.service.agent.AgentClientMigrationGuard;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import static org.mockito.Mockito.verifyNoInteractions;
import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.ai.service.agent.AgentPublishApplicationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.ai.service.agent.watch.AgentGrpcWatchService;
import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSearchRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentUnsubscribeRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentPublishRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointOperationResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSearchResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentUnsubscribeRpcResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentPublishRpcResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentClientRequestHandlerTest {
    
    private final AgentClientMigrationGuard migrationGuard = mock(AgentClientMigrationGuard.class);
    
    private AgentDiscoveryApplicationService discoveryService;
    
    private AgentRuntimeRegistryService runtimeRegistryService;
    
    private AgentPublishApplicationService publishService;
    
    private AgentGrpcWatchService watchService;
    
    private RequestMeta meta;
    
    @BeforeEach
    void setUp() {
        discoveryService = mock(AgentDiscoveryApplicationService.class);
        runtimeRegistryService = mock(AgentRuntimeRegistryService.class);
        publishService = mock(AgentPublishApplicationService.class);
        watchService = mock(AgentGrpcWatchService.class);
        meta = mock(RequestMeta.class);
        when(meta.getConnectionId()).thenReturn("connection");
        when(meta.getConnectionAbility(AbilityKey.SDK_RAD_WATCH_V1))
            .thenReturn(AbilityStatus.SUPPORTED);
    }
    
    @Test
    void testMigrationDetailPreservedBeforeBusinessCalls() throws Exception {
        doThrow(new NacosApiException(NacosException.CONFLICT,
            ErrorCode.AGENT_MIGRATION_IN_PROGRESS, "migration"))
            .when(migrationGuard).checkReady();
        AgentPublishRpcRequest publish = new AgentPublishRpcRequest();
        publish.setPublishRequest(new AgentPublishRequest());
        AgentSearchRpcRequest search = new AgentSearchRpcRequest();
        search.setSearchRequest(new AgentSearchRequest());
        AgentDiscoveryRpcRequest discover = new AgentDiscoveryRpcRequest();
        discover.setDiscoveryRequest(new AgentDiscoveryRequest());
        AgentEndpointRegisterRpcRequest register = new AgentEndpointRegisterRpcRequest();
        register.setRegistrationBatch(new AgentEndpointRegistrationBatch());
        Response[] responses = {
            new AgentPublishRpcRequestHandler(publishService, migrationGuard).handle(publish, meta),
            new AgentSearchRpcRequestHandler(discoveryService, migrationGuard).handle(search, meta),
            new AgentDiscoveryRpcRequestHandler(discoveryService, migrationGuard).handle(discover,
                meta),
            new AgentEndpointRegisterRpcRequestHandler(runtimeRegistryService, migrationGuard)
                .handle(register, meta)};
        for (Response response : responses) {
            assertEquals(ErrorCode.AGENT_MIGRATION_IN_PROGRESS.getCode(), response.getErrorCode());
        }
        verifyNoInteractions(discoveryService, publishService, runtimeRegistryService);
    }
    
    @Test
    void testPublishHandler() throws NacosException {
        AgentPublishRequest publication = new AgentPublishRequest();
        AgentVersionDetail detail = new AgentVersionDetail();
        AgentPublishRpcRequest request = new AgentPublishRpcRequest();
        request.setPublishRequest(publication);
        when(publishService.publish("public", publication)).thenReturn(detail);
        
        AgentPublishRpcResponse response =
            new AgentPublishRpcRequestHandler(publishService, migrationGuard).handle(request, meta);
        assertSame(detail, response.getVersionDetail());
        assertInvalid(new AgentPublishRpcRequestHandler(publishService, migrationGuard)
            .handle(new AgentPublishRpcRequest(), meta));
    }
    
    @Test
    void testSearchHandler() throws NacosException {
        AgentSearchRequest search = new AgentSearchRequest();
        Page page = new Page();
        when(discoveryService.search("public", search)).thenReturn(page);
        AgentSearchRpcRequest request = new AgentSearchRpcRequest();
        request.setSearchRequest(search);
        
        AgentSearchResponse response =
            new AgentSearchRpcRequestHandler(discoveryService, migrationGuard).handle(request,
                meta);
        
        assertSame(page, response.getPage());
        assertEquals("public", request.getNamespaceId());
        assertInvalid(new AgentSearchRpcRequestHandler(discoveryService, migrationGuard)
            .handle(new AgentSearchRpcRequest(), meta));
    }
    
    @Test
    void testDiscoveryHandler() throws NacosException {
        AgentDiscoveryRequest discovery = new AgentDiscoveryRequest();
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        when(discoveryService.discover(discovery)).thenReturn(result);
        AgentDiscoveryRpcRequest request = new AgentDiscoveryRpcRequest();
        request.setDiscoveryRequest(discovery);
        
        AgentDiscoveryResponse response =
            new AgentDiscoveryRpcRequestHandler(discoveryService, migrationGuard).handle(request,
                meta);
        
        assertSame(result, response.getDiscoveryResult());
        assertEquals("public", discovery.getNamespaceId());
        assertInvalid(new AgentDiscoveryRpcRequestHandler(discoveryService, migrationGuard)
            .handle(new AgentDiscoveryRpcRequest(), meta));
    }
    
    @Test
    void testEndpointRegisterHandler() throws NacosException {
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        AgentEndpointRegisterRpcRequest request = new AgentEndpointRegisterRpcRequest();
        request.setRegistrationBatch(batch);
        
        AgentEndpointOperationResponse response =
            new AgentEndpointRegisterRpcRequestHandler(runtimeRegistryService, migrationGuard)
                .handle(request, meta);
        
        assertTrue(response.isSuccess());
        assertEquals("public", request.getNamespaceId());
        verify(runtimeRegistryService).register("connection", "public", batch);
        assertInvalid(
            new AgentEndpointRegisterRpcRequestHandler(runtimeRegistryService, migrationGuard)
                .handle(new AgentEndpointRegisterRpcRequest(), meta));
    }
    
    @Test
    void testEndpointDeregisterHandler() throws NacosException {
        AgentEndpointDeregisterRpcRequest request = new AgentEndpointDeregisterRpcRequest();
        request.setAgentName("demo");
        request.setProtocol("a2a");
        
        AgentEndpointOperationResponse response =
            new AgentEndpointDeregisterRpcRequestHandler(runtimeRegistryService)
                .handle(request, meta);
        
        assertTrue(response.isSuccess());
        assertEquals("public", request.getNamespaceId());
        verify(runtimeRegistryService).deregisterPublisher("connection", "public", "demo", "a2a");
        
        AgentEndpointDeregisterRpcRequest invalidRequest =
            new AgentEndpointDeregisterRpcRequest();
        invalidRequest.setAgentName("demo");
        doThrow(new IllegalArgumentException("protocol must not be blank"))
            .when(runtimeRegistryService)
            .deregisterPublisher("connection", "public", "demo", null);
        assertInvalid(new AgentEndpointDeregisterRpcRequestHandler(runtimeRegistryService)
            .handle(invalidRequest, meta));
    }
    
    @Test
    void testSearchAndRegisterUseExplicitEnvelopeNamespace() throws NacosException {
        AgentSearchRequest search = new AgentSearchRequest();
        AgentSearchRpcRequest searchRpc = new AgentSearchRpcRequest();
        searchRpc.setNamespaceId("tenant-b");
        searchRpc.setSearchRequest(search);
        new AgentSearchRpcRequestHandler(discoveryService, migrationGuard).handle(searchRpc, meta);
        verify(discoveryService).search("tenant-b", search);
        
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        AgentEndpointRegisterRpcRequest registration = new AgentEndpointRegisterRpcRequest();
        registration.setNamespaceId("tenant-b");
        registration.setRegistrationBatch(batch);
        new AgentEndpointRegisterRpcRequestHandler(runtimeRegistryService, migrationGuard)
            .handle(registration, meta);
        verify(runtimeRegistryService).register("connection", "tenant-b", batch);
    }
    
    @Test
    void testSubscribeHandler() throws Exception {
        AgentSubscribeRpcRequest request = new AgentSubscribeRpcRequest();
        request.setClientWatchId("client-watch");
        request.setDiscoveryRequest(new AgentDiscoveryRequest());
        AgentSubscribeRpcResponse expected = new AgentSubscribeRpcResponse();
        expected.setWatchKey("watch-key");
        when(watchService.subscribe("connection", request)).thenReturn(expected);
        
        AgentSubscribeRpcResponse actual =
            new AgentSubscribeRpcRequestHandler(watchService).handle(request, meta);
        
        assertSame(expected, actual);
        assertEquals("public", request.getDiscoveryRequest().getNamespaceId());
        verify(watchService).subscribe("connection", request);
        assertInvalid(new AgentSubscribeRpcRequestHandler(watchService)
            .handle(new AgentSubscribeRpcRequest(), meta));
    }
    
    @Test
    void testWatchHandlersRejectClientWithoutHintAbility() throws Exception {
        when(meta.getConnectionAbility(AbilityKey.SDK_RAD_WATCH_V1))
            .thenReturn(AbilityStatus.UNKNOWN);
        AgentSubscribeRpcRequest subscribe = new AgentSubscribeRpcRequest();
        subscribe.setClientWatchId("client-watch");
        subscribe.setDiscoveryRequest(new AgentDiscoveryRequest());
        AgentUnsubscribeRpcRequest unsubscribe = new AgentUnsubscribeRpcRequest();
        unsubscribe.setWatchKey("watch-key");
        
        AgentSubscribeRpcResponse subscribeResponse =
            new AgentSubscribeRpcRequestHandler(watchService).handle(subscribe, meta);
        AgentUnsubscribeRpcResponse unsubscribeResponse =
            new AgentUnsubscribeRpcRequestHandler(watchService).handle(unsubscribe, meta);
        
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, subscribeResponse.getErrorCode());
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, unsubscribeResponse.getErrorCode());
        verify(watchService, never()).subscribe(anyString(), any());
        verify(watchService, never()).unsubscribe(anyString(), anyString());
    }
    
    @Test
    void testUnsubscribeHandlerIsIdempotentAndMapsValidationFailure() throws Exception {
        AgentUnsubscribeRpcRequest request = new AgentUnsubscribeRpcRequest();
        request.setWatchKey("watch-key");
        
        AgentUnsubscribeRpcResponse response =
            new AgentUnsubscribeRpcRequestHandler(watchService).handle(request, meta);
        
        assertTrue(response.isSuccess());
        verify(watchService).unsubscribe("connection", "watch-key");
        
        doThrow(new IllegalArgumentException("invalid watchKey")).when(watchService)
            .unsubscribe("connection", null);
        assertInvalid(new AgentUnsubscribeRpcRequestHandler(watchService)
            .handle(new AgentUnsubscribeRpcRequest(), meta));
    }
    
    @Test
    void testWatchHandlerSecurityAndExtractionAnnotations() throws Exception {
        Method subscribe = AgentSubscribeRpcRequestHandler.class.getMethod("handle",
            AgentSubscribeRpcRequest.class, RequestMeta.class);
        Method unsubscribe = AgentUnsubscribeRpcRequestHandler.class.getMethod("handle",
            AgentUnsubscribeRpcRequest.class, RequestMeta.class);
        
        Secured subscribeSecured = subscribe.getAnnotation(Secured.class);
        Secured unsubscribeSecured = unsubscribe.getAnnotation(Secured.class);
        assertNotNull(subscribeSecured);
        assertEquals(ActionTypes.READ, subscribeSecured.action());
        assertEquals(SignType.AI, subscribeSecured.signType());
        assertNotNull(subscribe.getAnnotation(NamespaceValidation.class));
        assertNotNull(subscribe.getAnnotation(ExtractorManager.Extractor.class));
        assertNotNull(unsubscribeSecured);
        assertEquals(ActionTypes.READ, unsubscribeSecured.action());
        assertEquals(SignType.AI, unsubscribeSecured.signType());
        assertNotNull(unsubscribe.getAnnotation(ExtractorManager.Extractor.class));
    }
    
    private void assertInvalid(Response response) {
        assertEquals(20002, response.getErrorCode());
    }
}
