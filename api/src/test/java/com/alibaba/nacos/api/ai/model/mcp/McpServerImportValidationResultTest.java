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

class McpServerImportValidationResultTest extends BasicRequestTest {
    
    @Test
    void testSerializeValidResult() throws JsonProcessingException {
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        result.setValid(true);
        result.setTotalCount(3);
        result.setValidCount(3);
        result.setInvalidCount(0);
        result.setDuplicateCount(0);
        
        McpServerValidationItem item1 = new McpServerValidationItem();
        item1.setServerName("server1");
        item1.setServerId("id1");
        item1.setStatus("valid");
        item1.setSelected(true);
        
        McpServerValidationItem item2 = new McpServerValidationItem();
        item2.setServerName("server2");
        item2.setServerId("id2");
        item2.setStatus("valid");
        item2.setSelected(false);
        
        result.setServers(Arrays.asList(item1, item2));
        result.setErrors(Collections.emptyList());
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"valid\":true"));
        assertTrue(json.contains("\"totalCount\":3"));
        assertTrue(json.contains("\"validCount\":3"));
        assertTrue(json.contains("\"invalidCount\":0"));
        assertTrue(json.contains("\"duplicateCount\":0"));
        assertTrue(json.contains("\"servers\":["));
        assertTrue(json.contains("\"serverName\":\"server1\""));
        assertTrue(json.contains("\"serverId\":\"id1\""));
        assertTrue(json.contains("\"status\":\"valid\""));
        assertTrue(json.contains("\"selected\":true"));
        assertTrue(json.contains("\"selected\":false"));
        assertTrue(json.contains("\"errors\":[]"));
    }
    
    @Test
    void testSerializeInvalidResult() throws JsonProcessingException {
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        result.setValid(false);
        result.setTotalCount(2);
        result.setValidCount(1);
        result.setInvalidCount(1);
        result.setDuplicateCount(0);
        
        McpServerValidationItem validItem = new McpServerValidationItem();
        validItem.setServerName("valid-server");
        validItem.setStatus("valid");
        validItem.setExists(false);
        
        McpServerValidationItem invalidItem = new McpServerValidationItem();
        invalidItem.setServerName("invalid-server");
        invalidItem.setStatus("invalid");
        invalidItem.setErrors(Arrays.asList("Missing protocol", "Invalid port"));
        
        result.setServers(Arrays.asList(validItem, invalidItem));
        result.setErrors(Arrays.asList("Invalid JSON format", "Missing required fields"));
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"valid\":false"));
        assertTrue(json.contains("\"totalCount\":2"));
        assertTrue(json.contains("\"validCount\":1"));
        assertTrue(json.contains("\"invalidCount\":1"));
        assertTrue(json.contains("\"duplicateCount\":0"));
        assertTrue(json.contains("\"serverName\":\"valid-server\""));
        assertTrue(json.contains("\"status\":\"valid\""));
        assertTrue(json.contains("\"exists\":false"));
        assertTrue(json.contains("\"serverName\":\"invalid-server\""));
        assertTrue(json.contains("\"status\":\"invalid\""));
        assertTrue(json.contains("\"errors\":[\"Missing protocol\",\"Invalid port\"]"));
        assertTrue(json.contains("\"errors\":[\"Invalid JSON format\",\"Missing required fields\"]"));
    }
    
    @Test
    void testSerializeDuplicateResult() throws JsonProcessingException {
        McpServerImportValidationResult result = new McpServerImportValidationResult();
        result.setValid(true);
        result.setTotalCount(2);
        result.setValidCount(1);
        result.setInvalidCount(0);
        result.setDuplicateCount(1);
        
        McpServerValidationItem duplicateItem = new McpServerValidationItem();
        duplicateItem.setServerName("existing-server");
        duplicateItem.setServerId("existing-id");
        duplicateItem.setStatus("duplicate");
        duplicateItem.setExists(true);
        
        result.setServers(Collections.singletonList(duplicateItem));
        result.setErrors(Collections.emptyList());
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"valid\":true"));
        assertTrue(json.contains("\"duplicateCount\":1"));
        assertTrue(json.contains("\"serverName\":\"existing-server\""));
        assertTrue(json.contains("\"status\":\"duplicate\""));
        assertTrue(json.contains("\"exists\":true"));
    }
    
    @Test
    void testDeserializeValidResult() throws JsonProcessingException {
        String json = "{\"valid\":true,\"totalCount\":3,\"validCount\":3,\"invalidCount\":0,\"duplicateCount\":0,"
                + "\"servers\":[{\"serverName\":\"server1\",\"serverId\":\"id1\",\"status\":\"valid\",\"selected\":true,\"exists\":false},"
                + "{\"serverName\":\"server2\",\"serverId\":\"id2\",\"status\":\"valid\",\"selected\":false,\"exists\":false}],"
                + "\"errors\":[]}";
        
        McpServerImportValidationResult result = mapper.readValue(json, McpServerImportValidationResult.class);
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(3, result.getTotalCount());
        assertEquals(3, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        
        assertNotNull(result.getServers());
        assertEquals(2, result.getServers().size());
        
        McpServerValidationItem item1 = result.getServers().get(0);
        assertEquals("server1", item1.getServerName());
        assertEquals("id1", item1.getServerId());
        assertEquals("valid", item1.getStatus());
        assertTrue(item1.isSelected());
        assertFalse(item1.isExists());
        
        McpServerValidationItem item2 = result.getServers().get(1);
        assertEquals("server2", item2.getServerName());
        assertEquals("id2", item2.getServerId());
        assertEquals("valid", item2.getStatus());
        assertFalse(item2.isSelected());
        assertFalse(item2.isExists());
        
        assertNotNull(result.getErrors());
        assertEquals(0, result.getErrors().size());
    }
    
    @Test
    void testDeserializeInvalidResult() throws JsonProcessingException {
        String json = "{\"valid\":false,\"totalCount\":2,\"validCount\":1,\"invalidCount\":1,\"duplicateCount\":0,"
                + "\"servers\":[{\"serverName\":\"valid-server\",\"status\":\"valid\",\"exists\":false},"
                + "{\"serverName\":\"invalid-server\",\"status\":\"invalid\",\"errors\":[\"Missing protocol\",\"Invalid port\"]}],"
                + "\"errors\":[\"Invalid JSON format\",\"Missing required fields\"]}";
        
        McpServerImportValidationResult result = mapper.readValue(json, McpServerImportValidationResult.class);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getValidCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        
        assertNotNull(result.getServers());
        assertEquals(2, result.getServers().size());
        
        McpServerValidationItem validItem = result.getServers().get(0);
        assertEquals("valid-server", validItem.getServerName());
        assertEquals("valid", validItem.getStatus());
        assertFalse(validItem.isExists());
        
        McpServerValidationItem invalidItem = result.getServers().get(1);
        assertEquals("invalid-server", invalidItem.getServerName());
        assertEquals("invalid", invalidItem.getStatus());
        assertNotNull(invalidItem.getErrors());
        assertEquals(2, invalidItem.getErrors().size());
        assertEquals("Missing protocol", invalidItem.getErrors().get(0));
        assertEquals("Invalid port", invalidItem.getErrors().get(1));
        
        assertNotNull(result.getErrors());
        assertEquals(2, result.getErrors().size());
        assertEquals("Invalid JSON format", result.getErrors().get(0));
        assertEquals("Missing required fields", result.getErrors().get(1));
    }
    
    @Test
    void testDeserializeDuplicateResult() throws JsonProcessingException {
        String json = "{\"valid\":true,\"totalCount\":2,\"validCount\":1,\"invalidCount\":0,\"duplicateCount\":1,"
                + "\"servers\":[{\"serverName\":\"existing-server\",\"serverId\":\"existing-id\",\"status\":\"duplicate\",\"exists\":true}],"
                + "\"errors\":[]}";
        
        McpServerImportValidationResult result = mapper.readValue(json, McpServerImportValidationResult.class);
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(1, result.getDuplicateCount());
        
        assertNotNull(result.getServers());
        assertEquals(1, result.getServers().size());
        
        McpServerValidationItem duplicateItem = result.getServers().get(0);
        assertEquals("existing-server", duplicateItem.getServerName());
        assertEquals("existing-id", duplicateItem.getServerId());
        assertEquals("duplicate", duplicateItem.getStatus());
        assertTrue(duplicateItem.isExists());
        
        assertNotNull(result.getErrors());
        assertEquals(0, result.getErrors().size());
    }
    
    @Test
    void testDeserializeMinimalResult() throws JsonProcessingException {
        String json = "{\"valid\":false,\"totalCount\":0,\"validCount\":0,\"invalidCount\":0,\"duplicateCount\":0}";
        
        McpServerImportValidationResult result = mapper.readValue(json, McpServerImportValidationResult.class);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getValidCount());
        assertEquals(0, result.getInvalidCount());
        assertEquals(0, result.getDuplicateCount());
        assertNull(result.getServers());
        assertNull(result.getErrors());
    }
}