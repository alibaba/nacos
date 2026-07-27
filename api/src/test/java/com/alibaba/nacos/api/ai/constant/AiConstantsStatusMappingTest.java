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

package com.alibaba.nacos.api.ai.constant;

import com.alibaba.nacos.api.ai.model.mcp.registry.McpServerStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiConstantsStatusMappingTest {
    
    @Test
    void testStatusConstantsAlignedWithEnum() {
        // Verify that AiConstants.Mcp status constants match McpServerStatusEnum values
        assertEquals(McpServerStatusEnum.ACTIVE.getName(), AiConstants.Mcp.MCP_STATUS_ACTIVE);
        assertEquals(McpServerStatusEnum.DEPRECATED.getName(),
            AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        assertEquals(McpServerStatusEnum.DELETED.getName(), AiConstants.Mcp.MCP_STATUS_DELETED);
    }
    
    @Test
    void testStatusConstantValues() {
        assertEquals("active", AiConstants.Mcp.MCP_STATUS_ACTIVE);
        assertEquals("deprecated", AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        assertEquals("deleted", AiConstants.Mcp.MCP_STATUS_DELETED);
        assertEquals("enable", AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        assertEquals("disable", AiConstants.Agent.RESOURCE_STATUS_DISABLE);
        assertEquals("draft", AiConstants.Agent.VERSION_STATUS_DRAFT);
        assertEquals("reviewing", AiConstants.Agent.VERSION_STATUS_REVIEWING);
        assertEquals("reviewed", AiConstants.Agent.VERSION_STATUS_REVIEWED);
        assertEquals("online", AiConstants.Agent.VERSION_STATUS_ONLINE);
        assertEquals("offline", AiConstants.Agent.VERSION_STATUS_OFFLINE);
    }
    
}
