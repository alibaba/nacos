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

import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimeRegistryService;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointDeregisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentSearchRpcRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointOperationResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSearchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentClientRequestHandlerTest {
    
    private AgentDiscoveryApplicationService discoveryService;
    
    private AgentRuntimeRegistryService runtimeRegistryService;
    
    private RequestMeta meta;
    
    @BeforeEach
    void setUp() {
        discoveryService = mock(AgentDiscoveryApplicationService.class);
        runtimeRegistryService = mock(AgentRuntimeRegistryService.class);
        meta = mock(RequestMeta.class);
        when(meta.getConnectionId()).thenReturn("connection");
    }
    
    @Test
    void testSearchHandler() throws NacosException {
        AgentSearchRequest search = new AgentSearchRequest();
        Page page = new Page();
        when(discoveryService.search(search)).thenReturn(page);
        AgentSearchRpcRequest request = new AgentSearchRpcRequest();
        request.setSearchRequest(search);
        
        AgentSearchResponse response =
            new AgentSearchRpcRequestHandler(discoveryService).handle(request, meta);
        
        assertSame(page, response.getPage());
        assertEquals("public", search.getNamespaceId());
        assertInvalid(new AgentSearchRpcRequestHandler(discoveryService)
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
            new AgentDiscoveryRpcRequestHandler(discoveryService).handle(request, meta);
        
        assertSame(result, response.getDiscoveryResult());
        assertEquals("public", discovery.getNamespaceId());
        assertInvalid(new AgentDiscoveryRpcRequestHandler(discoveryService)
            .handle(new AgentDiscoveryRpcRequest(), meta));
    }
    
    @Test
    void testEndpointRegisterHandler() throws NacosException {
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        AgentEndpointRegisterRpcRequest request = new AgentEndpointRegisterRpcRequest();
        request.setRegistrationBatch(batch);
        
        AgentEndpointOperationResponse response =
            new AgentEndpointRegisterRpcRequestHandler(runtimeRegistryService)
                .handle(request, meta);
        
        assertTrue(response.isSuccess());
        assertEquals("public", batch.getNamespaceId());
        verify(runtimeRegistryService).register("connection", batch);
        assertInvalid(new AgentEndpointRegisterRpcRequestHandler(runtimeRegistryService)
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
    
    private void assertInvalid(Response response) {
        assertEquals(20002, response.getErrorCode());
    }
}
