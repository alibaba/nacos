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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpEndpointSpecTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpEndpointSpec mcpEndpointSpec = new McpEndpointSpec();
        mcpEndpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        mcpEndpointSpec.getData().put("address", "127.0.0.1");
        mcpEndpointSpec.getData().put("port", "8080");
        String json = mapper.writeValueAsString(mcpEndpointSpec);
        assertTrue(json.contains("\"type\":\"DIRECT\""));
        assertTrue(json.contains("\"data\":{"));
        assertTrue(json.contains("\"address\":\"127.0.0.1\""));
        assertTrue(json.contains("\"port\":\"8080\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"type\":\"DIRECT\",\"data\":{\"address\":\"127.0.0.1\",\"port\":\"8080\"}}";
        McpEndpointSpec mcpEndpointSpec = mapper.readValue(json, McpEndpointSpec.class);
        assertEquals(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT, mcpEndpointSpec.getType());
        assertEquals("127.0.0.1", mcpEndpointSpec.getData().get("address"));
        assertEquals("8080", mcpEndpointSpec.getData().get("port"));
    }
}