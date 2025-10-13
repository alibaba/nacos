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
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosMcpRegistryServerDetailTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        McpRegistryServerDetail mcpRegistryServerDetail = new McpRegistryServerDetail();
        mcpRegistryServerDetail.setName("testRegistryServer");
        mcpRegistryServerDetail.setDescription("test mcp registry server object");
        mcpRegistryServerDetail.setRepository(new Repository());
        mcpRegistryServerDetail.setVersion("1.0.0");
        mcpRegistryServerDetail.setSchema("http://example.com/schema");
        
        // Create test packages
        Package pkg = new Package();
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        mcpRegistryServerDetail.setPackages(Arrays.asList(pkg));
        
        Meta meta = new Meta();
        OfficialMeta official = new OfficialMeta();
        official.setPublishedAt("2022-01-01T00:00:00Z");
        meta.setOfficial(official);
        mcpRegistryServerDetail.setMeta(meta);
        mcpRegistryServerDetail.setRemotes(Collections.singletonList(new Remote()));
        mcpRegistryServerDetail.getRemotes().get(0).setUrl("127.0.0.1:8848/sse");
        mcpRegistryServerDetail.getRemotes().get(0).setType("https");
        String json = mapper.writeValueAsString(mcpRegistryServerDetail);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"testRegistryServer\""));
        assertTrue(json.contains("\"description\":\"test mcp registry server object\""));
        assertTrue(json.contains("\"repository\":{}"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"$schema\":\"http://example.com/schema\""));
        assertTrue(json.contains("\"packages\":[{"));
        assertTrue(json.contains("\"identifier\":\"test-package\""));
        assertTrue(json.contains("\"io.modelcontextprotocol.registry/official\""));
        assertTrue(json.contains("\"publishedAt\":\"2022-01-01T00:00:00Z\""));
        assertTrue(json.contains("\"remotes\":[{"));
        assertTrue(json.contains("\"url\":\"127.0.0.1:8848/sse\""));
        assertTrue(json.contains("\"type\":\"https\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"name\":\"testRegistryServer\",\"description\":\"test mcp registry server object\",\"$schema\":\"http://example.com/schema\",\"packages\":[{\"identifier\":\"test-package\",\"version\":\"1.0.0\"}],"
                + "\"repository\":{},\"version\":\"1.0.0\",\"remotes\":[{\"type\":\"https\","
                + "\"url\":\"127.0.0.1:8848/sse\"}],\"_meta\":{\"io.modelcontextprotocol.registry/official\":"
                + "{\"publishedAt\":\"2022-01-01T00:00:00Z\"}}}";
        McpRegistryServerDetail mcpRegistryServerDetail = mapper.readValue(json, McpRegistryServerDetail.class);
        assertNotNull(mcpRegistryServerDetail);
        assertEquals("testRegistryServer", mcpRegistryServerDetail.getName());
        assertEquals("test mcp registry server object", mcpRegistryServerDetail.getDescription());
        assertNotNull(mcpRegistryServerDetail.getRepository());
        assertEquals("1.0.0", mcpRegistryServerDetail.getVersion());
        assertEquals("http://example.com/schema", mcpRegistryServerDetail.getSchema());
        assertNotNull(mcpRegistryServerDetail.getPackages());
        assertEquals(1, mcpRegistryServerDetail.getPackages().size());
        assertEquals("test-package", mcpRegistryServerDetail.getPackages().get(0).getIdentifier());
        assertEquals("1.0.0", mcpRegistryServerDetail.getPackages().get(0).getVersion());
        assertEquals("2022-01-01T00:00:00Z", mcpRegistryServerDetail.getMeta().getOfficial().getPublishedAt());
        assertNotNull(mcpRegistryServerDetail.getRemotes());
        assertEquals(1, mcpRegistryServerDetail.getRemotes().size());
        assertEquals("https", mcpRegistryServerDetail.getRemotes().get(0).getType());
        assertEquals("127.0.0.1:8848/sse", mcpRegistryServerDetail.getRemotes().get(0).getUrl());
    }
}
