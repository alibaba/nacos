/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.remote.handler.a2a;

import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEndpointRequestHandlerTest {
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private AgentIdCodecHolder agentIdCodecHolder;
    
    @Mock
    private RequestMeta meta;
    
    private AgentEndpointRequestHandler requestHandler;
    
    @BeforeEach
    void setUp() {
        requestHandler = new AgentEndpointRequestHandler(clientOperationService, agentIdCodecHolder);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithInvalidAgentName() throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `agentName` can't be empty or null");
    }
    
    @Test
    void handleWithNullEndpoint() throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setAgentName("test");
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM, "Required parameter `endpoint` can't be null");
    }
    
    @Test
    void handleWithEmptyEndpointVersion() throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setAgentName("test");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        request.setEndpoint(endpoint);
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `endpoint.version` can't be empty or null");
    }
    
    @Test
    void handleWithInvalidType() throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setAgentName("test");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        request.setEndpoint(endpoint);
        request.setType("INVALID_TYPE");
        when(agentIdCodecHolder.encode("test")).thenReturn("test");
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "parameter `type` should be registerEndpoint or deregisterEndpoint, but was INVALID_TYPE");
    }
    
    @Test
    void handleForRegisterEndpoint() throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setAgentName("test");
        request.setNamespaceId("public");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        endpoint.setPath("/test");
        endpoint.setTransport("JSONRPC");
        endpoint.setSupportTls(false);
        request.setEndpoint(endpoint);
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        when(agentIdCodecHolder.encode("test")).thenReturn("test");
        when(meta.getConnectionId()).thenReturn("TEST_CONNECTION_ID");
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT, response.getType());
        verify(clientOperationService).registerInstance(any(Service.class), any(Instance.class),
                eq("TEST_CONNECTION_ID"));
    }
    
    @Test
    void handleForDeregisterEndpoint() throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setAgentName("test");
        request.setNamespaceId("public");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        endpoint.setVersion("1.0.0");
        endpoint.setPath("/test");
        endpoint.setTransport("JSONRPC");
        endpoint.setSupportTls(false);
        request.setEndpoint(endpoint);
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        when(agentIdCodecHolder.encode("test")).thenReturn("test");
        when(meta.getConnectionId()).thenReturn("TEST_CONNECTION_ID");
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertEquals(AiRemoteConstants.DE_REGISTER_ENDPOINT, response.getType());
        verify(clientOperationService).deregisterInstance(any(Service.class), any(Instance.class),
                eq("TEST_CONNECTION_ID"));
    }
    
    private void assertErrorResponse(AgentEndpointResponse response, int code, String message) {
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(code, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
}