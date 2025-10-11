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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEndpointRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        AgentEndpointRequest request = new AgentEndpointRequest();
        String id = UUID.randomUUID().toString();
        request.setRequestId("1");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setAgentName("testAgent");
        
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setAddress("127.0.0.1");
        endpoint.setPort(8848);
        endpoint.setVersion("1.0.0");
        request.setEndpoint(endpoint);
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"agentName\":\"testAgent\""));
        assertTrue(json.contains("\"type\":\"registerEndpoint\""));
        assertTrue(json.contains("\"address\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":8848"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"namespaceId\":\"public\",\"agentName\":\"testAgent\","
                + "\"endpoint\":{\"transport\":\"JSONRPC\",\"address\":\"127.0.0.1\",\"port\":8848,\"path\":\"\","
                + "\"supportTls\":false,\"version\":\"1.0.0\"},\"type\":\"registerEndpoint\",\"module\":\"ai\"}";
        AgentEndpointRequest result = mapper.readValue(json, AgentEndpointRequest.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertEquals("testAgent", result.getAgentName());
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT, result.getType());
        
        AgentEndpoint endpoint = result.getEndpoint();
        assertNotNull(endpoint);
        assertEquals("127.0.0.1", endpoint.getAddress());
        assertEquals(8848, endpoint.getPort());
        assertEquals("1.0.0", endpoint.getVersion());
    }
}