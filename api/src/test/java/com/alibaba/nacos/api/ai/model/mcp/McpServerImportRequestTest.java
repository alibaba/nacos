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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerImportRequestTest extends BasicRequestTest {
    
    @Test
    void testSerializeJsonImport() throws JsonProcessingException {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("json");
        request.setData("{\"servers\":[{\"name\":\"test-server\"}]}");
        request.setOverrideExisting(true);
        request.setValidateOnly(false);
        request.setSelectedServers(new String[]{"server1", "server2"});
        
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"importType\":\"json\""));
        assertTrue(json.contains("\"data\":\"{\\\"servers\\\":[{\\\"name\\\":\\\"test-server\\\"}]}\""));
        assertTrue(json.contains("\"overrideExisting\":true"));
        assertTrue(json.contains("\"validateOnly\":false"));
        assertTrue(json.contains("\"selectedServers\":[\"server1\",\"server2\"]"));
    }
    
    @Test
    void testSerializeFileImport() throws JsonProcessingException {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("file");
        request.setData("/path/to/import/file.json");
        request.setOverrideExisting(false);
        request.setValidateOnly(true);
        
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"importType\":\"file\""));
        assertTrue(json.contains("\"data\":\"/path/to/import/file.json\""));
        assertTrue(json.contains("\"overrideExisting\":false"));
        assertTrue(json.contains("\"validateOnly\":true"));
    }
    
    @Test
    void testSerializeUrlImport() throws JsonProcessingException {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType("url");
        request.setData("https://example.com/mcp-servers.json");
        request.setOverrideExisting(false);
        request.setValidateOnly(false);
        
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"importType\":\"url\""));
        assertTrue(json.contains("\"data\":\"https://example.com/mcp-servers.json\""));
        assertTrue(json.contains("\"overrideExisting\":false"));
        assertTrue(json.contains("\"validateOnly\":false"));
    }
    
    @Test
    void testDeserializeJsonImport() throws JsonProcessingException {
        String json = "{\"importType\":\"json\",\"data\":\"{\\\"servers\\\":[{\\\"name\\\":\\\"test-server\\\"}]}\","
                + "\"overrideExisting\":true,\"validateOnly\":false,\"selectedServers\":[\"server1\",\"server2\"]}";
        
        McpServerImportRequest result = mapper.readValue(json, McpServerImportRequest.class);
        assertNotNull(result);
        assertEquals("json", result.getImportType());
        assertEquals("{\"servers\":[{\"name\":\"test-server\"}]}", result.getData());
        assertTrue(result.isOverrideExisting());
        assertFalse(result.isValidateOnly());
        assertNotNull(result.getSelectedServers());
        assertEquals(2, result.getSelectedServers().length);
        assertEquals("server1", result.getSelectedServers()[0]);
        assertEquals("server2", result.getSelectedServers()[1]);
    }
    
    @Test
    void testDeserializeFileImport() throws JsonProcessingException {
        String json = "{\"importType\":\"file\",\"data\":\"/path/to/import/file.json\","
                + "\"overrideExisting\":false,\"validateOnly\":true}";
        
        McpServerImportRequest result = mapper.readValue(json, McpServerImportRequest.class);
        assertNotNull(result);
        assertEquals("file", result.getImportType());
        assertEquals("/path/to/import/file.json", result.getData());
        assertFalse(result.isOverrideExisting());
        assertTrue(result.isValidateOnly());
    }
    
    @Test
    void testDeserializeUrlImport() throws JsonProcessingException {
        String json = "{\"importType\":\"url\",\"data\":\"https://example.com/mcp-servers.json\","
                + "\"overrideExisting\":false,\"validateOnly\":false}";
        
        McpServerImportRequest result = mapper.readValue(json, McpServerImportRequest.class);
        assertNotNull(result);
        assertEquals("url", result.getImportType());
        assertEquals("https://example.com/mcp-servers.json", result.getData());
        assertFalse(result.isOverrideExisting());
        assertFalse(result.isValidateOnly());
    }
    
    @Test
    void testDefaultValues() throws JsonProcessingException {
        String json = "{\"importType\":\"json\",\"data\":\"{}\"}";
        
        McpServerImportRequest result = mapper.readValue(json, McpServerImportRequest.class);
        assertNotNull(result);
        assertEquals("json", result.getImportType());
        assertEquals("{}", result.getData());
        assertFalse(result.isOverrideExisting());
        assertFalse(result.isValidateOnly());
    }
}