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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import com.alibaba.nacos.common.notify.Event;

/**
 * Nacos AI module mcp server changed event in nacos- client.
 *
 * @author xiweng.yy
 */
public class McpServerChangedEvent extends Event {
    
    private static final long serialVersionUID = 2010793364377243018L;
    
    private final String mcpName;
    
    private final String version;
    
    private final McpServerDetailInfo mcpServer;
    
    public McpServerChangedEvent(McpServerDetailInfo mcpServer) {
        this.mcpServer = mcpServer;
        this.mcpName = mcpServer.getName();
        this.version = buildVersion(mcpServer);
    }
    
    private String buildVersion(McpServerDetailInfo mcpServer) {
        return mcpServer.getVersionDetail().getIs_latest() ? CacheKeyUtils.LATEST_VERSION
                : mcpServer.getVersionDetail().getVersion();
    }
    
    public String getMcpName() {
        return mcpName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public McpServerDetailInfo getMcpServer() {
        return mcpServer;
    }
}
