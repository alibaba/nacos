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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRegistryServerListTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpRegistryServerList mcpRegistryServerList = new McpRegistryServerList();
        // Use detail type to match List<McpRegistryServerDetail> in production code
        mcpRegistryServerList.setServers(Collections.singletonList(new McpRegistryServerDetail()));
        // Set metadata with count and next_cursor
        mcpRegistryServerList.setMetadata(new McpRegistryServerList.Metadata("next", 1));
        String json = mapper.writeValueAsString(mcpRegistryServerList);
        assertTrue(json.contains("\"servers\":[{}]"));
        assertTrue(json.contains("\"metadata\":"));
        assertTrue(json.contains("\"next_cursor\":\"next\""));
        assertTrue(json.contains("\"count\":1"));
    }

    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"servers\":[{}],\"metadata\":{\"next_cursor\":\"next\",\"count\":1}}";
        McpRegistryServerList mcpRegistryServerList = mapper.readValue(json, McpRegistryServerList.class);
        assertEquals(1, mcpRegistryServerList.getServers().size());
        assertEquals(1, mcpRegistryServerList.getMetadata().getCount());
        assertEquals("next", mcpRegistryServerList.getMetadata().getNextCursor());
    }
}