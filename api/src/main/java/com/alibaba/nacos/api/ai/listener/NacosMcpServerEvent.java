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

import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;

/**
 * Nacos AI module event for mcp server.
 *
 * @author xiweng.yy
 */
public class NacosMcpServerEvent implements NacosAiEvent {
    
    private final String mcpId;
    
    private final String namespaceId;
    
    private final String mcpName;
    
    private final McpServerDetailInfo mcpServerDetailInfo;
    
    public NacosMcpServerEvent(McpServerDetailInfo mcpServerDetailInfo) {
        this.mcpServerDetailInfo = mcpServerDetailInfo;
        this.mcpId = mcpServerDetailInfo.getId();
        this.namespaceId = mcpServerDetailInfo.getNamespaceId();
        this.mcpName = mcpServerDetailInfo.getName();
    }
    
    public String getMcpId() {
        return mcpId;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public String getMcpName() {
        return mcpName;
    }
    
    public McpServerDetailInfo getMcpServerDetailInfo() {
        return mcpServerDetailInfo;
    }
}
