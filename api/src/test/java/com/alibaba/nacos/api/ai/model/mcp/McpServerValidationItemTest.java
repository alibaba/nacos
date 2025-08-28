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
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerValidationItemTest extends BasicRequestTest {
    
    @Test
    void testSerializeValidItem() throws JsonProcessingException {
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerName("test-server");
        item.setServerId("server-123");
        item.setStatus("valid");
        item.setExists(false);
        item.setSelected(true);
        
        McpServerDetailInfo server = new McpServerDetailInfo();
        server.setName("test-server");
        server.setId("server-123");
        server.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        server.setDescription("Test server");
        item.setServer(server);
        
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"serverName\":\"test-server\""));
        assertTrue(json.contains("\"serverId\":\"server-123\""));
        assertTrue(json.contains("\"status\":\"valid\""));
        assertTrue(json.contains("\"exists\":false"));
        assertTrue(json.contains("\"selected\":true"));
        assertTrue(json.contains("\"server\":{"));
        assertTrue(json.contains("\"name\":\"test-server\""));
        assertTrue(json.contains("\"protocol\":\"stdio\""));
    }
    
    @Test
    void testSerializeInvalidItem() throws JsonProcessingException {
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerName("invalid-server");
        item.setStatus("invalid");
        item.setErrors(Arrays.asList("Missing protocol", "Invalid port", "Empty name"));
        item.setExists(false);
        item.setSelected(false);
        
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"serverName\":\"invalid-server\""));
        assertTrue(json.contains("\"status\":\"invalid\""));
        assertTrue(json.contains("\"errors\":[\"Missing protocol\",\"Invalid port\",\"Empty name\"]"));
        assertTrue(json.contains("\"exists\":false"));
        assertTrue(json.contains("\"selected\":false"));
    }
    
    @Test
    void testSerializeDuplicateItem() throws JsonProcessingException {
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerName("existing-server");
        item.setServerId("existing-id");
        item.setStatus("duplicate");
        item.setExists(true);
        item.setSelected(false);
        item.setErrors(Collections.singletonList("Server already exists"));
        
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"serverName\":\"existing-server\""));
        assertTrue(json.contains("\"serverId\":\"existing-id\""));
        assertTrue(json.contains("\"status\":\"duplicate\""));
        assertTrue(json.contains("\"exists\":true"));
        assertTrue(json.contains("\"selected\":false"));
        assertTrue(json.contains("\"errors\":[\"Server already exists\"]"));
    }
    
    @Test
    void testSerializeDefaultSelectedValue() throws JsonProcessingException {
        McpServerValidationItem item = new McpServerValidationItem();
        item.setServerName("default-server");
        item.setStatus("valid");
        
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"serverName\":\"default-server\""));
        assertTrue(json.contains("\"status\":\"valid\""));
        assertTrue(json.contains("\"selected\":true"));
    }
    
    @Test
    void testDeserializeValidItem() throws JsonProcessingException {
        String json = "{\"serverName\":\"test-server\",\"serverId\":\"server-123\",\"status\":\"valid\","
                + "\"exists\":false,\"selected\":true,\"server\":{\"name\":\"test-server\",\"id\":\"server-123\","
                + "\"protocol\":\"stdio\",\"description\":\"Test server\"}}";
        
        McpServerValidationItem result = mapper.readValue(json, McpServerValidationItem.class);
        assertNotNull(result);
        assertEquals("test-server", result.getServerName());
        assertEquals("server-123", result.getServerId());
        assertEquals("valid", result.getStatus());
        assertNull(result.getErrors());
        assertFalse(result.isExists());
        assertTrue(result.isSelected());
        
        assertNotNull(result.getServer());
        assertEquals("test-server", result.getServer().getName());
        assertEquals("server-123", result.getServer().getId());
        assertEquals(AiConstants.Mcp.MCP_PROTOCOL_STDIO, result.getServer().getProtocol());
        assertEquals("Test server", result.getServer().getDescription());
    }
    
    @Test
    void testDeserializeInvalidItem() throws JsonProcessingException {
        String json = "{\"serverName\":\"invalid-server\",\"status\":\"invalid\","
                + "\"errors\":[\"Missing protocol\",\"Invalid port\",\"Empty name\"],\"exists\":false,\"selected\":false}";
        
        McpServerValidationItem result = mapper.readValue(json, McpServerValidationItem.class);
        assertNotNull(result);
        assertEquals("invalid-server", result.getServerName());
        assertNull(result.getServerId());
        assertEquals("invalid", result.getStatus());
        assertNotNull(result.getErrors());
        assertEquals(3, result.getErrors().size());
        assertEquals("Missing protocol", result.getErrors().get(0));
        assertEquals("Invalid port", result.getErrors().get(1));
        assertEquals("Empty name", result.getErrors().get(2));
        assertFalse(result.isExists());
        assertFalse(result.isSelected());
        assertNull(result.getServer());
    }
    
    @Test
    void testDeserializeDuplicateItem() throws JsonProcessingException {
        String json = "{\"serverName\":\"existing-server\",\"serverId\":\"existing-id\",\"status\":\"duplicate\","
                + "\"exists\":true,\"selected\":false,\"errors\":[\"Server already exists\"]}";
        
        McpServerValidationItem result = mapper.readValue(json, McpServerValidationItem.class);
        assertNotNull(result);
        assertEquals("existing-server", result.getServerName());
        assertEquals("existing-id", result.getServerId());
        assertEquals("duplicate", result.getStatus());
        assertNotNull(result.getErrors());
        assertEquals(1, result.getErrors().size());
        assertEquals("Server already exists", result.getErrors().get(0));
        assertTrue(result.isExists());
        assertFalse(result.isSelected());
        assertNull(result.getServer());
    }
    
    @Test
    void testDeserializeDefaultSelectedValue() throws JsonProcessingException {
        String json = "{\"serverName\":\"default-server\",\"status\":\"valid\"}";
        
        McpServerValidationItem result = mapper.readValue(json, McpServerValidationItem.class);
        assertNotNull(result);
        assertEquals("default-server", result.getServerName());
        assertEquals("valid", result.getStatus());
        assertTrue(result.isSelected());
        assertFalse(result.isExists());
    }
    
    @Test
    void testDeserializeMinimalItem() throws JsonProcessingException {
        String json = "{\"serverName\":\"minimal-server\",\"status\":\"unknown\",\"exists\":false,\"selected\":true}";
        
        McpServerValidationItem result = mapper.readValue(json, McpServerValidationItem.class);
        assertNotNull(result);
        assertEquals("minimal-server", result.getServerName());
        assertNull(result.getServerId());
        assertEquals("unknown", result.getStatus());
        assertNull(result.getErrors());
        assertFalse(result.isExists());
        assertTrue(result.isSelected());
        assertNull(result.getServer());
    }
    
    @Test
    void testDeserializeWithEmptyErrors() throws JsonProcessingException {
        String json = "{\"serverName\":\"server-with-empty-errors\",\"status\":\"valid\",\"errors\":[]}";
        
        McpServerValidationItem result = mapper.readValue(json, McpServerValidationItem.class);
        assertNotNull(result);
        assertEquals("server-with-empty-errors", result.getServerName());
        assertEquals("valid", result.getStatus());
        assertNotNull(result.getErrors());
        assertEquals(0, result.getErrors().size());
    }
}