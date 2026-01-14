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

        Map<String, Object> outputSchema = new HashMap<>();
        outputSchema.put("type", "object");
        Map<String, Object> outputProperties = new HashMap<>();
        Map<String, String> resultSchema = new HashMap<>();
        resultSchema.put("type", "string");
        resultSchema.put("description", "Result");
        outputProperties.put("result", resultSchema);
        outputSchema.put("properties", outputProperties);
        mcpTool.setOutputSchema(outputSchema);

        // Set meta field (_meta in JSON)
        Map<String, Object> meta = new HashMap<>();
        meta.put("hint", "This is a test tool");
        meta.put("version", 1);
        mcpTool.setMeta(meta);

        // Set annotations
        McpToolAnnotations annotations = new McpToolAnnotations();
        annotations.setTitle("Test Tool Title");
        annotations.setReadOnlyHint(true);
        annotations.setDestructiveHint(false);
        annotations.setIdempotentHint(true);
        annotations.setOpenWorldHint(false);
        mcpTool.setAnnotations(annotations);
        
        String json = mapper.writeValueAsString(mcpTool);
        assertTrue(json.contains("\"name\":\"testTool\""));
        assertTrue(json.contains("\"description\":\"A test tool for MCP\""));
        assertTrue(json.contains("\"inputSchema\":{"));
        assertTrue(json.contains("\"type\":\"object\""));
        assertTrue(json.contains("\"properties\":{"));
        assertTrue(json.contains("\"a\":{"));
        assertTrue(json.contains("\"type\":\"string\""));
        assertTrue(json.contains("\"description\":\"Parameter A\""));

        assertTrue(json.contains("\"outputSchema\":{"));
        assertTrue(json.contains("\"result\":{"));
        assertTrue(json.contains("\"description\":\"Result\""));

        // Verify _meta field serialization
        assertTrue(json.contains("\"_meta\":{"));
        assertTrue(json.contains("\"hint\":\"This is a test tool\""));
        assertTrue(json.contains("\"version\":1"));

        // Verify annotations field serialization
        assertTrue(json.contains("\"annotations\":{"));
        assertTrue(json.contains("\"title\":\"Test Tool Title\""));
        assertTrue(json.contains("\"readOnlyHint\":true"));
        assertTrue(json.contains("\"destructiveHint\":false"));
        assertTrue(json.contains("\"idempotentHint\":true"));
        assertTrue(json.contains("\"openWorldHint\":false"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"name\":\"testTool\",\"description\":\"A test tool for MCP\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\","
                + "\"description\":\"Parameter A\"}}},"
                + "\"outputSchema\":{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\","
                + "\"description\":\"Result\"}}},"
                + "\"_meta\":{\"hint\":\"This is a test tool\",\"version\":1},"
                + "\"annotations\":{\"title\":\"Test Tool Title\",\"readOnlyHint\":true,"
                + "\"destructiveHint\":false,\"idempotentHint\":true,\"openWorldHint\":false}}";

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

        assertNotNull(result.getOutputSchema());
        assertEquals("object", result.getOutputSchema().get("type"));
        Map<String, Object> outProps = (Map<String, Object>) result.getOutputSchema().get("properties");
        assertNotNull(outProps.get("result"));

        // Verify _meta field deserialization
        assertNotNull(result.getMeta());
        assertEquals("This is a test tool", result.getMeta().get("hint"));
        assertEquals(1, result.getMeta().get("version"));

        // Verify annotations field deserialization
        assertNotNull(result.getAnnotations());
        assertEquals("Test Tool Title", result.getAnnotations().getTitle());
        assertEquals(true, result.getAnnotations().getReadOnlyHint());
        assertEquals(false, result.getAnnotations().getDestructiveHint());
        assertEquals(true, result.getAnnotations().getIdempotentHint());
        assertEquals(false, result.getAnnotations().getOpenWorldHint());
    }
}