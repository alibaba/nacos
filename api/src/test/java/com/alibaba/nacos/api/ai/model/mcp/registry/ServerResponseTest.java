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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerResponseTest extends BasicRequestTest {
    
    @Test
    void testSerializeServerResponseBasic() throws JsonProcessingException {
        ServerResponse response = new ServerResponse();
        
        McpRegistryServerDetail server = new McpRegistryServerDetail();
        server.setName("TestServer");
        server.setDescription("Test Server Description");
        server.setVersion("1.0.0");
        response.setServer(server);
        
        OfficialMeta official = new OfficialMeta();
        official.setPublishedAt("2025-01-01T00:00:00Z");
        official.setUpdatedAt("2025-01-15T00:00:00Z");
        official.setIsLatest(true);
        official.setStatus("active");
        
        ServerResponse.Meta meta = new ServerResponse.Meta();
        meta.setOfficial(official);
        response.setMeta(meta);
        
        String json = mapper.writeValueAsString(response);
        
        assertNotNull(json);
        assertTrue(json.contains("\"server\":{"));
        assertTrue(json.contains("\"name\":\"TestServer\""));
        assertTrue(json.contains("\"_meta\":{"));
        assertTrue(json.contains("\"io.modelcontextprotocol.registry/official\":{"));
        assertTrue(json.contains("\"publishedAt\":\"2025-01-01T00:00:00Z\""));
        assertTrue(json.contains("\"status\":\"active\""));
    }
    
    @Test
    void testDeserializeServerResponseBasic() throws JsonProcessingException {
        String json = "{\"server\":{\"name\":\"TestServer\",\"version\":\"1.0.0\"},"
                + "\"_meta\":{\"io.modelcontextprotocol.registry/official\":"
                + "{\"publishedAt\":\"2025-01-01T00:00:00Z\",\"isLatest\":true}}}";
        
        ServerResponse response = mapper.readValue(json, ServerResponse.class);
        
        assertNotNull(response);
        assertNotNull(response.getServer());
        assertEquals("TestServer", response.getServer().getName());
        assertEquals("1.0.0", response.getServer().getVersion());
        assertNotNull(response.getMeta());
        assertNotNull(response.getMeta().getOfficial());
        assertEquals("2025-01-01T00:00:00Z", response.getMeta().getOfficial().getPublishedAt());
        assertEquals(true, response.getMeta().getOfficial().getIsLatest());
    }
    
    @Test
    void testServerResponseWithMetadataExtensions() throws JsonProcessingException {
        Map<String, Object> extensionData = new HashMap<>();
        extensionData.put("customField", "customValue");
        extensionData.put("metadata", new HashMap<String, Object>() {
            {
                put("key1", "value1");
            }
        });
        
        String json = "{\"server\":{\"name\":\"ExtendedServer\"},"
                + "\"_meta\":{"
                + "\"io.modelcontextprotocol.registry/official\":{\"publishedAt\":\"2025-01-01T00:00:00Z\"},"
                + "\"customExtension\":\"extensionValue\"}}";
        
        ServerResponse response = mapper.readValue(json, ServerResponse.class);
        
        assertNotNull(response);
        assertNotNull(response.getServer());
        assertEquals("ExtendedServer", response.getServer().getName());
        assertNotNull(response.getMeta());
    }
    
    @Test
    void testServerResponseMinimal() throws JsonProcessingException {
        ServerResponse response = new ServerResponse();
        McpRegistryServerDetail server = new McpRegistryServerDetail();
        server.setName("MinimalServer");
        response.setServer(server);
        
        String json = mapper.writeValueAsString(response);
        
        assertTrue(json.contains("\"server\":{"));
        assertTrue(json.contains("\"name\":\"MinimalServer\""));
    }
    
    @Test
    void testServerResponseMetaNullSafe() throws JsonProcessingException {
        ServerResponse response = new ServerResponse();
        McpRegistryServerDetail server = new McpRegistryServerDetail();
        server.setName("TestServer");
        response.setServer(server);
        // Meta is not set - should handle null gracefully
        
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"server\":{"));
    }
    
    @Test
    void testServerResponseMetaOfficialNested() throws JsonProcessingException {
        String json = "{"
                + "\"server\":{\"name\":\"NestedServer\"},"
                + "\"_meta\":{"
                + "\"io.modelcontextprotocol.registry/official\":{"
                + "\"publishedAt\":\"2025-01-01T00:00:00Z\","
                + "\"updatedAt\":\"2025-01-15T00:00:00Z\","
                + "\"isLatest\":true,"
                + "\"status\":\"active\"}}}";
        
        ServerResponse response = mapper.readValue(json, ServerResponse.class);
        
        assertEquals("NestedServer", response.getServer().getName());
        assertEquals("2025-01-01T00:00:00Z", response.getMeta().getOfficial().getPublishedAt());
        assertEquals("2025-01-15T00:00:00Z", response.getMeta().getOfficial().getUpdatedAt());
        assertEquals(true, response.getMeta().getOfficial().getIsLatest());
        assertEquals("active", response.getMeta().getOfficial().getStatus());
    }
}
