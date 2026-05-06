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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAgentEndpointRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        String id = UUID.randomUUID().toString();
        request.setRequestId("1");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setAgentName("testAgent");
        
        Collection<AgentEndpoint> endpoints = Arrays.asList(createTestEndpoint1(), createTestEndpoint2());
        request.setEndpoints(endpoints);
        
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"agentName\":\"testAgent\""));
        assertTrue(json.contains("\"type\":\"batchRegisterEndpoint\""));
        assertTrue(json.contains("\"address\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":8848"));
        assertTrue(json.contains("\"address\":\"192.168.1.100\""));
        assertTrue(json.contains("\"port\":9090"));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"namespaceId\":\"public\",\"agentName\":\"testAgent\","
                + "\"endpoints\":[{\"transport\":\"JSONRPC\",\"address\":\"127.0.0.1\",\"port\":8848,\"path\":\"\","
                + "\"supportTls\":false,\"version\":\"1.0.0\"},{\"transport\":\"GRPC\",\"address\":\"192.168.1.100\","
                + "\"port\":9090,\"path\":\"\",\"supportTls\":true,\"version\":\"2.0.0\"}],"
                + "\"type\":\"batchRegisterEndpoint\",\"module\":\"ai\"}";
        BatchAgentEndpointRequest result = mapper.readValue(json, BatchAgentEndpointRequest.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertEquals("testAgent", result.getAgentName());
        assertEquals(AiRemoteConstants.BATCH_REGISTER_ENDPOINT, result.getType());
        
        Collection<AgentEndpoint> endpoints = result.getEndpoints();
        assertNotNull(endpoints);
        assertEquals(2, endpoints.size());
        
        Object[] endpointArray = endpoints.toArray();
        AgentEndpoint endpoint1 = (AgentEndpoint) endpointArray[0];
        assertEquals("127.0.0.1", endpoint1.getAddress());
        assertEquals(8848, endpoint1.getPort());
        assertEquals("1.0.0", endpoint1.getVersion());
        
        AgentEndpoint endpoint2 = (AgentEndpoint) endpointArray[1];
        assertEquals("192.168.1.100", endpoint2.getAddress());
        assertEquals(9090, endpoint2.getPort());
        assertEquals("2.0.0", endpoint2.getVersion());
    }
    
    @Test
    void testGetType() {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        assertEquals(AiRemoteConstants.BATCH_REGISTER_ENDPOINT, request.getType());
    }
    
    private AgentEndpoint createTestEndpoint1() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setTransport("JSONRPC");
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8848);
        endpoint.setVersion("1.0.0");
        return endpoint;
    }
    
    private AgentEndpoint createTestEndpoint2() {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setTransport("GRPC");
        endpoint.setAddress("192.168.1.100");
        endpoint.setPort(9090);
        endpoint.setSupportTls(true);
        endpoint.setVersion("2.0.0");
        return endpoint;
    }
}