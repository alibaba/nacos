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

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpTool mcpTool = new McpTool();
        mcpTool.setName("testTool");
        mcpTool.setDescription("A test tool for MCP");
        
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, String> paramA = new HashMap<>();
        paramA.put("type", "string");
        paramA.put("description", "Parameter A");
        properties.put("a", paramA);
        
        inputSchema.put("properties", properties);
        mcpTool.setInputSchema(inputSchema);
        
        String json = mapper.writeValueAsString(mcpTool);
        assertTrue(json.contains("\"name\":\"testTool\""));
        assertTrue(json.contains("\"description\":\"A test tool for MCP\""));
        assertTrue(json.contains("\"inputSchema\":{"));
        assertTrue(json.contains("\"type\":\"object\""));
        assertTrue(json.contains("\"properties\":{"));
        assertTrue(json.contains("\"a\":{"));
        assertTrue(json.contains("\"type\":\"string\""));
        assertTrue(json.contains("\"description\":\"Parameter A\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"name\":\"testTool\",\"description\":\"A test tool for MCP\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\","
                + "\"description\":\"Parameter A\"}}}}";
        
        McpTool result = mapper.readValue(json, McpTool.class);
        assertNotNull(result);
        assertEquals("testTool", result.getName());
        assertEquals("A test tool for MCP", result.getDescription());
        assertNotNull(result.getInputSchema());
        assertEquals("object", result.getInputSchema().get("type"));
        assertNotNull(result.getInputSchema().get("properties"));
        Map<String, Object> properties = (Map<String, Object>) result.getInputSchema().get("properties");
        assertNotNull(properties.get("a"));
        Map<String, String> paramA = (Map<String, String>) properties.get("a");
        assertEquals("string", paramA.get("type"));
        assertEquals("Parameter A", paramA.get("description"));
    }
}