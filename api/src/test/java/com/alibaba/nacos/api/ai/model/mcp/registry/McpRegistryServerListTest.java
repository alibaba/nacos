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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpRegistryServerListTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpRegistryServerList mcpRegistryServerList = new McpRegistryServerList();
        // Use detail type to match List<ServerResponse> in production code
        mcpRegistryServerList.setServers(Collections.singletonList(new ServerResponse()));
        // Set metadata with count and nextCursor (now camelCase)
        mcpRegistryServerList.setMetadata(new McpRegistryServerList.Metadata("next", 1));
        String json = mapper.writeValueAsString(mcpRegistryServerList);
        assertTrue(json.contains("\"servers\":["));
        assertTrue(json.contains("\"metadata\":"));
        // Primary format: camelCase
        assertTrue(json.contains("\"nextCursor\":\"next\""));
        assertTrue(json.contains("\"count\":1"));
    }

    @Test
    void testDeserialize() throws JsonProcessingException {
        // Test with new camelCase format (primary)
        String jsonCamelCase = "{\"servers\":[],\"metadata\":{\"nextCursor\":\"next\",\"count\":1}}";
        McpRegistryServerList result1 = mapper.readValue(jsonCamelCase, McpRegistryServerList.class);
        assertEquals(0, result1.getServers().size());
        assertEquals(1, result1.getMetadata().getCount());
        assertEquals("next", result1.getMetadata().getNextCursor());
        
        // Test with old snake_case format (backward compatibility)
        String jsonSnakeCase = "{\"servers\":[],\"metadata\":{\"next_cursor\":\"next\",\"count\":1}}";
        McpRegistryServerList result2 = mapper.readValue(jsonSnakeCase, McpRegistryServerList.class);
        assertEquals(0, result2.getServers().size());
        assertEquals(1, result2.getMetadata().getCount());
        assertEquals("next", result2.getMetadata().getNextCursor());
    }
    
    @Test
    void testSerializeWithMultipleServers() throws JsonProcessingException {
        McpRegistryServerList list = new McpRegistryServerList();
        
        ServerResponse sr1 = new ServerResponse();
        McpRegistryServerDetail server1 = new McpRegistryServerDetail();
        server1.setName("Server1");
        sr1.setServer(server1);
        
        ServerResponse sr2 = new ServerResponse();
        McpRegistryServerDetail server2 = new McpRegistryServerDetail();
        server2.setName("Server2");
        sr2.setServer(server2);
        
        list.setServers(Arrays.asList(sr1, sr2));
        list.setMetadata(new McpRegistryServerList.Metadata("cursor2", 2));
        
        String json = mapper.writeValueAsString(list);
        assertTrue(json.contains("\"servers\":["));
        assertTrue(json.contains("\"name\":\"Server1\""));
        assertTrue(json.contains("\"name\":\"Server2\""));
        assertTrue(json.contains("\"count\":2"));
    }
    
    @Test
    void testDeserializeWithMultipleServers() throws JsonProcessingException {
        String json = "{\"servers\":["
                + "{\"server\":{\"name\":\"Server1\",\"version\":\"1.0.0\"}},"
                + "{\"server\":{\"name\":\"Server2\",\"version\":\"2.0.0\"}}"
                + "],\"metadata\":{\"nextCursor\":\"cursor2\",\"count\":2}}";
        
        McpRegistryServerList list = mapper.readValue(json, McpRegistryServerList.class);
        
        assertEquals(2, list.getServers().size());
        assertEquals("Server1", list.getServers().get(0).getServer().getName());
        assertEquals("Server2", list.getServers().get(1).getServer().getName());
        assertEquals("cursor2", list.getMetadata().getNextCursor());
        assertEquals(2, list.getMetadata().getCount());
    }
    
    @Test
    void testMetadataConstructor() throws JsonProcessingException {
        McpRegistryServerList.Metadata metadata = new McpRegistryServerList.Metadata("test_cursor", 5);
        
        assertEquals("test_cursor", metadata.getNextCursor());
        assertEquals(5, metadata.getCount());
        
        String json = mapper.writeValueAsString(metadata);
        assertTrue(json.contains("\"nextCursor\":\"test_cursor\""));
        assertTrue(json.contains("\"count\":5"));
    }
    
    @Test
    void testEmptyServerList() throws JsonProcessingException {
        McpRegistryServerList list = new McpRegistryServerList();
        list.setServers(Collections.emptyList());
        list.setMetadata(new McpRegistryServerList.Metadata(null, 0));
        
        String json = mapper.writeValueAsString(list);
        assertTrue(json.contains("\"servers\":[]"));
        assertTrue(json.contains("\"metadata\":{"));
    }
    
    @Test
    void testNullNextCursorHandling() throws JsonProcessingException {
        McpRegistryServerList list = new McpRegistryServerList();
        list.setServers(Collections.emptyList());
        list.setMetadata(new McpRegistryServerList.Metadata(null, 0));
        
        String json = mapper.writeValueAsString(list);
        McpRegistryServerList parsed = mapper.readValue(json, McpRegistryServerList.class);
        
        assertNull(parsed.getMetadata().getNextCursor());
        assertEquals(0, parsed.getMetadata().getCount());
    }
    
    @Test
    void testBackwardCompatibilitySnakeCaseAlias() throws JsonProcessingException {
        // Ensure @JsonAlias works for next_cursor -> nextCursor
        String jsonSnakeCase = "{\"servers\":[],\"metadata\":{\"next_cursor\":\"pagination_cursor\",\"count\":10}}";
        McpRegistryServerList list = mapper.readValue(jsonSnakeCase, McpRegistryServerList.class);
        
        assertEquals("pagination_cursor", list.getMetadata().getNextCursor());
        assertEquals(10, list.getMetadata().getCount());
    }
    
    @Test
    void testPrimaryFormatCamelCase() throws JsonProcessingException {
        // Ensure camelCase is the primary serialization format
        McpRegistryServerList list = new McpRegistryServerList();
        list.setServers(Collections.emptyList());
        list.setMetadata(new McpRegistryServerList.Metadata("cursor", 5));
        
        String json = mapper.writeValueAsString(list);
        // Primary format should be camelCase
        assertTrue(json.contains("\"nextCursor\":\"cursor\""));
        // Should NOT use snake_case in serialization
        assertTrue(!json.contains("\"next_cursor\""));
    }
}