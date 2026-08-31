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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class McpServerVersionDetailTest {
    
    @Test
    void shouldExposeVersionMetadataAndContent() {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName("weather");
        McpServerVersionDetail detail = new McpServerVersionDetail();
        detail.setNamespaceId("public");
        detail.setMcpName("weather");
        detail.setVersion("1.0.0");
        detail.setStatus("draft");
        detail.setPublishPipelineInfo("{\"status\":\"REJECTED\"}");
        detail.setAuthor("nacos");
        detail.setDescription("weather server");
        detail.setLatest(false);
        detail.setCreateTime(1L);
        detail.setUpdateTime(2L);
        detail.setServerSpecification(server);
        detail.setToolSpecification(new McpToolSpecification());
        detail.setResourceSpecification(new McpResourceSpecification());
        detail.setResourceStatus("enable");
        detail.setOwner("nacos");
        detail.setScope("PUBLIC");
        detail.setLabels(Map.of("latest", "1.0.0"));
        detail.setEditingVersion("1.1.0");
        detail.setReviewingVersion("1.2.0");
        detail.setOnlineCount(1);
        
        assertEquals("public", detail.getNamespaceId());
        assertEquals("weather", detail.getMcpName());
        assertEquals("1.0.0", detail.getVersion());
        assertEquals("draft", detail.getStatus());
        assertEquals("{\"status\":\"REJECTED\"}", detail.getPublishPipelineInfo());
        assertEquals("nacos", detail.getAuthor());
        assertEquals("weather server", detail.getDescription());
        assertFalse(detail.getLatest());
        assertEquals(1L, detail.getCreateTime());
        assertEquals(2L, detail.getUpdateTime());
        assertSame(server, detail.getServerSpecification());
        assertEquals(0, detail.getToolSpecification().getTools().size());
        assertEquals(0, detail.getResourceSpecification().getResources().size());
        assertEquals("enable", detail.getResourceStatus());
        assertEquals("nacos", detail.getOwner());
        assertEquals("PUBLIC", detail.getScope());
        assertEquals("1.0.0", detail.getLabels().get("latest"));
        assertEquals("1.1.0", detail.getEditingVersion());
        assertEquals("1.2.0", detail.getReviewingVersion());
        assertEquals(1, detail.getOnlineCount());
    }
}
