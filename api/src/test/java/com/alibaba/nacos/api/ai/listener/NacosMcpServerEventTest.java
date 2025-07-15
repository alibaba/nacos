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

package com.alibaba.nacos.api.ai.listener;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NacosMcpServerEventTest {
    
    @Test
    void testConstructor() {
        McpServerDetailInfo mcpServerDetailInfo = new McpServerDetailInfo();
        mcpServerDetailInfo.setName("testName");
        mcpServerDetailInfo.setId(UUID.randomUUID().toString());
        mcpServerDetailInfo.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        NacosMcpServerEvent event = new NacosMcpServerEvent(mcpServerDetailInfo);
        assertEquals(mcpServerDetailInfo, event.getMcpServerDetailInfo());
        assertEquals(mcpServerDetailInfo.getId(), event.getMcpId());
        assertEquals(mcpServerDetailInfo.getNamespaceId(), event.getNamespaceId());
        assertEquals(mcpServerDetailInfo.getName(), event.getMcpName());
    }
}