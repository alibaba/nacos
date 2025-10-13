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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRegistryServerTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpRegistryServer server = new McpRegistryServer();
        server.setName("testServer");
        server.setDescription("test server description");
        server.setStatus("active");
        server.setVersion("1.0.0");
        server.setWebsiteUrl("https://test.server.com");
        
        Repository repository = new Repository();
        repository.setUrl("https://github.com/test/server");
        repository.setSource("github");
        server.setRepository(repository);
        
        String json = mapper.writeValueAsString(server);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"testServer\""));
        assertTrue(json.contains("\"description\":\"test server description\""));
        assertTrue(json.contains("\"status\":\"active\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"websiteUrl\":\"https://test.server.com\""));
        assertTrue(json.contains("\"repository\":"));
        assertTrue(json.contains("\"url\":\"https://github.com/test/server\""));
        assertTrue(json.contains("\"source\":\"github\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"name\":\"testServer\",\"description\":\"test server description\","
                + "\"status\":\"active\",\"version\":\"1.0.0\",\"websiteUrl\":\"https://test.server.com\","
                + "\"repository\":{\"url\":\"https://github.com/test/server\",\"source\":\"github\"}}";
        
        McpRegistryServer server = mapper.readValue(json, McpRegistryServer.class);
        assertNotNull(server);
        assertEquals("testServer", server.getName());
        assertEquals("test server description", server.getDescription());
        assertEquals("active", server.getStatus());
        assertEquals("1.0.0", server.getVersion());
        assertEquals("https://test.server.com", server.getWebsiteUrl());
        assertNotNull(server.getRepository());
        assertEquals("https://github.com/test/server", server.getRepository().getUrl());
        assertEquals("github", server.getRepository().getSource());
    }
}