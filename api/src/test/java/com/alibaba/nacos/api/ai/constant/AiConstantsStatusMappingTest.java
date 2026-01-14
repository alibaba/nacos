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
        assertEquals(McpServerStatusEnum.DEPRECATED.getName(), AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        assertEquals(McpServerStatusEnum.DELETED.getName(), AiConstants.Mcp.MCP_STATUS_DELETED);
    }
    
    @Test
    void testStatusConstantValues() {
        assertEquals("active", AiConstants.Mcp.MCP_STATUS_ACTIVE);
        assertEquals("deprecated", AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        assertEquals("deleted", AiConstants.Mcp.MCP_STATUS_DELETED);
    }
    
    @Test
    void testStatusCanBeParsedFromEnum() {
        // Verify that we can parse the constants back to enum
        McpServerStatusEnum active = McpServerStatusEnum.parseStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        assertEquals(McpServerStatusEnum.ACTIVE, active);
        
        McpServerStatusEnum deprecated = McpServerStatusEnum.parseStatus(AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        assertEquals(McpServerStatusEnum.DEPRECATED, deprecated);
        
        McpServerStatusEnum deleted = McpServerStatusEnum.parseStatus(AiConstants.Mcp.MCP_STATUS_DELETED);
        assertEquals(McpServerStatusEnum.DELETED, deleted);
    }
}
