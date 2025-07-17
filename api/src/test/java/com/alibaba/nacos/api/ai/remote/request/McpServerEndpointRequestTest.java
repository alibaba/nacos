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
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerEndpointRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        String id = UUID.randomUUID().toString();
        request.setRequestId("1");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setMcpName("testMcpName");
        request.setMcpId(id);
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        request.setAddress("1.1.1.1");
        request.setPort(3306);
        request.setVersion("1.0.0");
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"mcpName\":\"testMcpName\""));
        assertTrue(json.contains(String.format("\"mcpId\":\"%s\"", id)));
        assertTrue(json.contains(String.format("\"type\":\"%s\"", AiRemoteConstants.REGISTER_ENDPOINT)));
        assertTrue(json.contains("\"address\":\"1.1.1.1\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"port\":3306"));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json =
                "{\"headers\":{},\"requestId\":\"1\",\"namespaceId\":\"public\",\"mcpId\":\"2aaebf2d-4b7b-4ab9-9ad2-1e60355ae041\","
                        + "\"mcpName\":\"testMcpName\",\"address\":\"1.1.1.1\",\"port\":3306,\"version\":\"1.0.0\","
                        + "\"type\":\"registerEndpoint\",\"module\":\"ai\"}";
        McpServerEndpointRequest result = mapper.readValue(json, McpServerEndpointRequest.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertEquals("testMcpName", result.getMcpName());
        assertEquals("2aaebf2d-4b7b-4ab9-9ad2-1e60355ae041", result.getMcpId());
        assertEquals("1.1.1.1", result.getAddress());
        assertEquals(3306, result.getPort());
        assertEquals("1.0.0", result.getVersion());
        assertEquals(AiRemoteConstants.REGISTER_ENDPOINT, result.getType());
    }
}