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

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerImportResponseTest extends BasicRequestTest {
    
    @Test
    void testSerializeSuccessResponse() throws JsonProcessingException {
        McpServerImportResponse response = new McpServerImportResponse();
        response.setSuccess(true);
        response.setTotalCount(5);
        response.setSuccessCount(4);
        response.setFailedCount(1);
        response.setSkippedCount(0);
        
        McpServerImportResult result1 = new McpServerImportResult();
        result1.setServerName("server1");
        result1.setServerId("id1");
        result1.setStatus("success");
        
        McpServerImportResult result2 = new McpServerImportResult();
        result2.setServerName("server2");
        result2.setStatus("failed");
        result2.setErrorMessage("Connection failed");
        
        response.setResults(Arrays.asList(result1, result2));
        
        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"totalCount\":5"));
        assertTrue(json.contains("\"successCount\":4"));
        assertTrue(json.contains("\"failedCount\":1"));
        assertTrue(json.contains("\"skippedCount\":0"));
        assertTrue(json.contains("\"results\":["));
        assertTrue(json.contains("\"serverName\":\"server1\""));
        assertTrue(json.contains("\"serverId\":\"id1\""));
        assertTrue(json.contains("\"status\":\"success\""));
        assertTrue(json.contains("\"serverName\":\"server2\""));
        assertTrue(json.contains("\"status\":\"failed\""));
        assertTrue(json.contains("\"errorMessage\":\"Connection failed\""));
    }
    
    @Test
    void testSerializeFailedResponse() throws JsonProcessingException {
        McpServerImportResponse response = new McpServerImportResponse();
        response.setSuccess(false);
        response.setTotalCount(0);
        response.setSuccessCount(0);
        response.setFailedCount(0);
        response.setSkippedCount(0);
        response.setErrorMessage("Invalid import data format");
        response.setResults(Collections.emptyList());
        
        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"totalCount\":0"));
        assertTrue(json.contains("\"successCount\":0"));
        assertTrue(json.contains("\"failedCount\":0"));
        assertTrue(json.contains("\"skippedCount\":0"));
        assertTrue(json.contains("\"errorMessage\":\"Invalid import data format\""));
        assertTrue(json.contains("\"results\":[]"));
    }
    
    @Test
    void testSerializePartialSuccessResponse() throws JsonProcessingException {
        McpServerImportResponse response = new McpServerImportResponse();
        response.setSuccess(true);
        response.setTotalCount(3);
        response.setSuccessCount(2);
        response.setFailedCount(0);
        response.setSkippedCount(1);
        
        McpServerImportResult skippedResult = new McpServerImportResult();
        skippedResult.setServerName("existing-server");
        skippedResult.setStatus("skipped");
        skippedResult.setConflictType("duplicate_name");
        
        response.setResults(Collections.singletonList(skippedResult));
        
        String json = mapper.writeValueAsString(response);
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"totalCount\":3"));
        assertTrue(json.contains("\"successCount\":2"));
        assertTrue(json.contains("\"failedCount\":0"));
        assertTrue(json.contains("\"skippedCount\":1"));
        assertTrue(json.contains("\"serverName\":\"existing-server\""));
        assertTrue(json.contains("\"status\":\"skipped\""));
        assertTrue(json.contains("\"conflictType\":\"duplicate_name\""));
    }
    
    @Test
    void testDeserializeSuccessResponse() throws JsonProcessingException {
        String json = "{\"success\":true,\"totalCount\":5,\"successCount\":4,\"failedCount\":1,\"skippedCount\":0,"
                + "\"results\":[{\"serverName\":\"server1\",\"serverId\":\"id1\",\"status\":\"success\"},"
                + "{\"serverName\":\"server2\",\"status\":\"failed\",\"errorMessage\":\"Connection failed\"}]}";
        
        McpServerImportResponse result = mapper.readValue(json, McpServerImportResponse.class);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(5, result.getTotalCount());
        assertEquals(4, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(0, result.getSkippedCount());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getResults());
        assertEquals(2, result.getResults().size());
        
        McpServerImportResult importResult1 = result.getResults().get(0);
        assertEquals("server1", importResult1.getServerName());
        assertEquals("id1", importResult1.getServerId());
        assertEquals("success", importResult1.getStatus());
        
        McpServerImportResult importResult2 = result.getResults().get(1);
        assertEquals("server2", importResult2.getServerName());
        assertEquals("failed", importResult2.getStatus());
        assertEquals("Connection failed", importResult2.getErrorMessage());
    }
    
    @Test
    void testDeserializeFailedResponse() throws JsonProcessingException {
        String json = "{\"success\":false,\"totalCount\":0,\"successCount\":0,\"failedCount\":0,\"skippedCount\":0,"
                + "\"errorMessage\":\"Invalid import data format\",\"results\":[]}";
        
        McpServerImportResponse result = mapper.readValue(json, McpServerImportResponse.class);
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals("Invalid import data format", result.getErrorMessage());
        assertNotNull(result.getResults());
        assertEquals(0, result.getResults().size());
    }
    
    @Test
    void testDeserializePartialSuccessResponse() throws JsonProcessingException {
        String json = "{\"success\":true,\"totalCount\":3,\"successCount\":2,\"failedCount\":0,\"skippedCount\":1,"
                + "\"results\":[{\"serverName\":\"existing-server\",\"status\":\"skipped\",\"conflictType\":\"duplicate_name\"}]}";
        
        McpServerImportResponse result = mapper.readValue(json, McpServerImportResponse.class);
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(1, result.getSkippedCount());
        assertNotNull(result.getResults());
        assertEquals(1, result.getResults().size());
        
        McpServerImportResult importResult = result.getResults().get(0);
        assertEquals("existing-server", importResult.getServerName());
        assertEquals("skipped", importResult.getStatus());
        assertEquals("duplicate_name", importResult.getConflictType());
    }
}