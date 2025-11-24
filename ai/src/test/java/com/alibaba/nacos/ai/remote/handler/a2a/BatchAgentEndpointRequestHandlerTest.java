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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.BatchAgentEndpointRequest;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchAgentEndpointRequestHandlerTest {
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private AgentIdCodecHolder agentIdCodecHolder;
    
    @Mock
    private RequestMeta meta;
    
    private BatchAgentEndpointRequestHandler requestHandler;
    
    private List<Instance> capturedInstances;
    
    @BeforeEach
    void setUp() {
        requestHandler = new BatchAgentEndpointRequestHandler(clientOperationService, agentIdCodecHolder);
        capturedInstances = null;
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void handleWithInvalidAgentName() throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `agentName` can't be empty or null");
    }
    
    @Test
    void handleWithNullEndpoints() throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        request.setAgentName("test");
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `endpoints` can't be empty or null, if want to deregister, please use deregister API.");
    }
    
    @Test
    void handleWithEmptyEndpoints() throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        request.setAgentName("test");
        request.setEndpoints(Arrays.asList());
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `endpoints` can't be empty or null, if want to deregister, please use deregister API.");
    }
    
    @Test
    void handleWithEmptyEndpointVersion() throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        request.setAgentName("test");
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("1.1.1.1");
        endpoint.setPort(8080);
        request.setEndpoints(Arrays.asList(endpoint));
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertErrorResponse(response, NacosException.INVALID_PARAM,
                "Required parameter `endpoint.version` can't be empty or null.");
    }
    
    @Test
    void handleWithDifferentVersions() throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        request.setAgentName("test");
        
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("1.1.1.1");
        endpoint1.setPort(8080);
        endpoint1.setVersion("1.0.0");
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("2.2.2.2");
        endpoint2.setPort(9090);
        endpoint2.setVersion("2.0.0");
        
        request.setEndpoints(Arrays.asList(endpoint1, endpoint2));
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
        assertTrue(response.getMessage()
                .startsWith("Required parameter `endpoint.version` can't be different, current includes:"));
        assertTrue(response.getMessage().contains("1.0.0"));
        assertTrue(response.getMessage().contains("2.0.0"));
    }
    
    @Test
    void handleForBatchRegisterEndpoint() throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        request.setAgentName("test");
        request.setNamespaceId("public");
        
        AgentEndpoint endpoint1 = new AgentEndpoint();
        endpoint1.setAddress("1.1.1.1");
        endpoint1.setPort(8080);
        endpoint1.setVersion("1.0.0");
        endpoint1.setPath("/test1");
        endpoint1.setTransport("JSONRPC");
        endpoint1.setSupportTls(false);
        endpoint1.setProtocol("HTTP");
        endpoint1.setQuery("param1=value1");
        
        AgentEndpoint endpoint2 = new AgentEndpoint();
        endpoint2.setAddress("2.2.2.2");
        endpoint2.setPort(9090);
        endpoint2.setVersion("1.0.0");
        endpoint2.setPath("/test2");
        endpoint2.setTransport("GRPC");
        endpoint2.setSupportTls(true);
        endpoint2.setProtocol("HTTPS");
        endpoint2.setQuery("param2=value2");
        
        Collection<AgentEndpoint> endpoints = Arrays.asList(endpoint1, endpoint2);
        request.setEndpoints(endpoints);
        
        when(agentIdCodecHolder.encode("test")).thenReturn("test");
        when(meta.getConnectionId()).thenReturn("TEST_CONNECTION_ID");
        
        // Mock the batchRegisterInstance method to capture the Instance list argument
        doAnswer(invocation -> {
            capturedInstances = invocation.getArgument(1);
            for (Instance instance : capturedInstances) {
                validateInstanceMetadata(instance);
            }
            return null;
        }).when(clientOperationService)
                .batchRegisterInstance(any(Service.class), any(List.class), eq("TEST_CONNECTION_ID"));
        
        AgentEndpointResponse response = requestHandler.handle(request, meta);
        
        assertEquals(AiRemoteConstants.BATCH_REGISTER_ENDPOINT, response.getType());
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        verify(clientOperationService).batchRegisterInstance(any(Service.class), any(List.class),
                eq("TEST_CONNECTION_ID"));
        
        // Verify captured instances
        assertEquals(2, capturedInstances.size());
        Instance instance1 = capturedInstances.get(0);
        assertEquals("1.1.1.1", instance1.getIp());
        assertEquals(8080, instance1.getPort());
        
        Instance instance2 = capturedInstances.get(1);
        assertEquals("2.2.2.2", instance2.getIp());
        assertEquals(9090, instance2.getPort());
    }
    
    private void assertErrorResponse(AgentEndpointResponse response, int code, String message) {
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(code, response.getErrorCode());
        assertEquals(message, response.getMessage());
    }
    
    private void validateInstanceMetadata(Instance instance) {
        Map<String, String> metadata = instance.getMetadata();
        assertTrue(metadata.containsKey(Constants.A2A.AGENT_ENDPOINT_PATH_KEY));
        assertTrue(metadata.containsKey(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY));
        assertTrue(metadata.containsKey(Constants.A2A.NACOS_AGENT_ENDPOINT_SUPPORT_TLS));
        assertTrue(metadata.containsKey(Constants.A2A.NACOS_AGENT_ENDPOINT_PROTOCOL_KEY));
        assertTrue(metadata.containsKey(Constants.A2A.NACOS_AGENT_ENDPOINT_QUERY_KEY));
    }
}