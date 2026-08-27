/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpLifecycleVersionDetailTest {
    
    @Test
    void shouldExposeLifecycleMetadataWithoutNestedCompatibilityId() throws Exception {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setId("internal-id");
        server.setName("weather");
        McpLifecycleVersionDetail detail = new McpLifecycleVersionDetail();
        detail.setNamespaceId("public");
        detail.setMcpName("weather");
        detail.setVersion("1.0.0");
        detail.setStatus("draft");
        detail.setAuthor("nacos");
        detail.setDescription("weather server");
        detail.setLatest(false);
        detail.setCreateTime(1L);
        detail.setUpdateTime(2L);
        detail.setServerSpecification(server);
        detail.setToolSpecification(new McpToolSpecification());
        detail.setResourceSpecification(new McpResourceSpecification());
        
        String json = new ObjectMapper().writeValueAsString(detail);
        
        assertFalse(json.contains("internal-id"));
        assertFalse(json.contains("\"id\""));
        assertTrue(json.contains("\"mcpName\":\"weather\""));
        assertEquals("public", detail.getNamespaceId());
        assertEquals("weather", detail.getMcpName());
        assertEquals("1.0.0", detail.getVersion());
        assertEquals("draft", detail.getStatus());
        assertEquals("nacos", detail.getAuthor());
        assertEquals("weather server", detail.getDescription());
        assertFalse(detail.getLatest());
        assertEquals(1L, detail.getCreateTime());
        assertEquals(2L, detail.getUpdateTime());
        assertEquals(server, detail.getServerSpecification());
        assertTrue(detail.getToolSpecification().getTools().isEmpty());
        assertTrue(detail.getResourceSpecification().getResources().isEmpty());
    }
}
