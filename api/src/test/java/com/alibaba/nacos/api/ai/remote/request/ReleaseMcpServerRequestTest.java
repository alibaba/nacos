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
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseMcpServerRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        String id = UUID.randomUUID().toString();
        request.setRequestId("1");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setMcpName("testMcpName");
        request.setMcpId(id);
        McpServerBasicInfo serverSpecification = new McpServerBasicInfo();
        serverSpecification.setName("testServerName");
        serverSpecification.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        serverSpecification.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        request.setServerSpecification(serverSpecification);
        McpToolSpecification toolSpecification = new McpToolSpecification();
        request.setToolSpecification(toolSpecification);
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"mcpName\":\"testMcpName\""));
        assertTrue(json.contains("\"serverSpecification\":{"));
        assertTrue(json.contains("\"toolSpecification\":{"));
        assertTrue(json.contains("\"tools\":[]"));
        assertTrue(json.contains("\"toolsMeta\":{}"));
        assertTrue(json.contains(String.format("\"mcpId\":\"%s\"", id)));
        assertTrue(json.contains(String.format("\"protocol\":\"%s\"", AiConstants.Mcp.MCP_PROTOCOL_STDIO)));
        assertTrue(json.contains(String.format("\"frontProtocol\":\"%s\"", AiConstants.Mcp.MCP_PROTOCOL_STDIO)));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json =
                "{\"headers\":{},\"requestId\":\"1\",\"namespaceId\":\"public\",\"mcpId\":\"5fe08d1c-2b34-45a3-b050-9b5aa2f59400\","
                        + "\"mcpName\":\"testMcpName\",\"serverSpecification\":{\"name\":\"testServerName\",\"protocol\":\"stdio\","
                        + "\"frontProtocol\":\"stdio\",\"enabled\":true},\"toolSpecification\":{\"tools\":[],\"toolsMeta\":{}},\"module\":\"ai\"}";
        ReleaseMcpServerRequest result = mapper.readValue(json, ReleaseMcpServerRequest.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertEquals("testMcpName", result.getMcpName());
        assertEquals("5fe08d1c-2b34-45a3-b050-9b5aa2f59400", result.getMcpId());
        McpServerBasicInfo serverSpecification = result.getServerSpecification();
        assertEquals("testServerName", serverSpecification.getName());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, serverSpecification.getProtocol());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, serverSpecification.getFrontProtocol());
        McpToolSpecification toolSpec = result.getToolSpecification();
        assertNotNull(toolSpec);
    }
}