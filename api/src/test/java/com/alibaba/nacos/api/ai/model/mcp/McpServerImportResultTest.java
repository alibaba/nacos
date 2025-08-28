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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerImportResultTest extends BasicRequestTest {
    
    @Test
    void testSerializeSuccessResult() throws JsonProcessingException {
        McpServerImportResult result = new McpServerImportResult();
        result.setServerName("test-server");
        result.setServerId("server-123");
        result.setStatus("success");
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"serverName\":\"test-server\""));
        assertTrue(json.contains("\"serverId\":\"server-123\""));
        assertTrue(json.contains("\"status\":\"success\""));
    }
    
    @Test
    void testSerializeFailedResult() throws JsonProcessingException {
        McpServerImportResult result = new McpServerImportResult();
        result.setServerName("failed-server");
        result.setStatus("failed");
        result.setErrorMessage("Connection timeout");
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"serverName\":\"failed-server\""));
        assertTrue(json.contains("\"status\":\"failed\""));
        assertTrue(json.contains("\"errorMessage\":\"Connection timeout\""));
    }
    
    @Test
    void testSerializeSkippedResult() throws JsonProcessingException {
        McpServerImportResult result = new McpServerImportResult();
        result.setServerName("existing-server");
        result.setStatus("skipped");
        result.setConflictType("duplicate_name");
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"serverName\":\"existing-server\""));
        assertTrue(json.contains("\"status\":\"skipped\""));
        assertTrue(json.contains("\"conflictType\":\"duplicate_name\""));
    }
    
    @Test
    void testSerializeCompleteResult() throws JsonProcessingException {
        McpServerImportResult result = new McpServerImportResult();
        result.setServerName("complete-server");
        result.setServerId("server-456");
        result.setStatus("success");
        result.setErrorMessage("Warning: deprecated config");
        result.setConflictType("version_conflict");
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"serverName\":\"complete-server\""));
        assertTrue(json.contains("\"serverId\":\"server-456\""));
        assertTrue(json.contains("\"status\":\"success\""));
        assertTrue(json.contains("\"errorMessage\":\"Warning: deprecated config\""));
        assertTrue(json.contains("\"conflictType\":\"version_conflict\""));
    }
    
    @Test
    void testDeserializeSuccessResult() throws JsonProcessingException {
        String json = "{\"serverName\":\"test-server\",\"serverId\":\"server-123\",\"status\":\"success\"}";
        
        McpServerImportResult result = mapper.readValue(json, McpServerImportResult.class);
        assertNotNull(result);
        assertEquals("test-server", result.getServerName());
        assertEquals("server-123", result.getServerId());
        assertEquals("success", result.getStatus());
        assertNull(result.getErrorMessage());
        assertNull(result.getConflictType());
    }
    
    @Test
    void testDeserializeFailedResult() throws JsonProcessingException {
        String json = "{\"serverName\":\"failed-server\",\"status\":\"failed\",\"errorMessage\":\"Connection timeout\"}";
        
        McpServerImportResult result = mapper.readValue(json, McpServerImportResult.class);
        assertNotNull(result);
        assertEquals("failed-server", result.getServerName());
        assertNull(result.getServerId());
        assertEquals("failed", result.getStatus());
        assertEquals("Connection timeout", result.getErrorMessage());
        assertNull(result.getConflictType());
    }
    
    @Test
    void testDeserializeSkippedResult() throws JsonProcessingException {
        String json = "{\"serverName\":\"existing-server\",\"status\":\"skipped\",\"conflictType\":\"duplicate_name\"}";
        
        McpServerImportResult result = mapper.readValue(json, McpServerImportResult.class);
        assertNotNull(result);
        assertEquals("existing-server", result.getServerName());
        assertNull(result.getServerId());
        assertEquals("skipped", result.getStatus());
        assertNull(result.getErrorMessage());
        assertEquals("duplicate_name", result.getConflictType());
    }
    
    @Test
    void testDeserializeCompleteResult() throws JsonProcessingException {
        String json = "{\"serverName\":\"complete-server\",\"serverId\":\"server-456\",\"status\":\"success\","
                + "\"errorMessage\":\"Warning: deprecated config\",\"conflictType\":\"version_conflict\"}";
        
        McpServerImportResult result = mapper.readValue(json, McpServerImportResult.class);
        assertNotNull(result);
        assertEquals("complete-server", result.getServerName());
        assertEquals("server-456", result.getServerId());
        assertEquals("success", result.getStatus());
        assertEquals("Warning: deprecated config", result.getErrorMessage());
        assertEquals("version_conflict", result.getConflictType());
    }
    
    @Test
    void testDeserializeMinimalResult() throws JsonProcessingException {
        String json = "{\"serverName\":\"minimal-server\",\"status\":\"unknown\"}";
        
        McpServerImportResult result = mapper.readValue(json, McpServerImportResult.class);
        assertNotNull(result);
        assertEquals("minimal-server", result.getServerName());
        assertNull(result.getServerId());
        assertEquals("unknown", result.getStatus());
        assertNull(result.getErrorMessage());
        assertNull(result.getConflictType());
    }
}