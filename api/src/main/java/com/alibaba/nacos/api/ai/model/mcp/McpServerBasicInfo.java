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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.ai.constant.AiConstants;

import java.util.List;
import java.util.Map;

/**
 * AI Mcp server spec in nacos.
 *
 * @author xiweng.yy
 */
public class McpServerBasicInfo {
    
    private String name;
    
    /**
     * It should be {@link AiConstants.Mcp#MCP_TYPE_LOCAL}, {@link AiConstants.Mcp#MCP_TYPE_SSE_REMOTE} or
     * {@link AiConstants.Mcp#MCP_TYPE_STREAM_REMOTE}.
     */
    private String type;
    
    private String description;
    
    private String version;
    
    /**
     * Should be set when `type` is {@link AiConstants.Mcp#MCP_TYPE_SSE_REMOTE} or
     * {@link AiConstants.Mcp#MCP_TYPE_STREAM_REMOTE}.
     */
    private McpServerRemoteServiceConfig remoteServerConfig;
    
    /**
     * Should be set when `type` is {@link AiConstants.Mcp#MCP_TYPE_LOCAL}.
     */
    private Map<String, Object> localServerConfig;
    
    private boolean enabled;
    
    /**
     * Auto discovery capabilities by Nacos. No need to set when create or update Mcp server.
     */
    private List<McpCapability> capabilities;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public McpServerRemoteServiceConfig getRemoteServerConfig() {
        return remoteServerConfig;
    }
    
    public void setRemoteServerConfig(McpServerRemoteServiceConfig remoteServerConfig) {
        this.remoteServerConfig = remoteServerConfig;
    }
    
    public Map<String, Object> getLocalServerConfig() {
        return localServerConfig;
    }
    
    public void setLocalServerConfig(Map<String, Object> localServerConfig) {
        this.localServerConfig = localServerConfig;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<McpCapability> getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(List<McpCapability> capabilities) {
        this.capabilities = capabilities;
    }
}
