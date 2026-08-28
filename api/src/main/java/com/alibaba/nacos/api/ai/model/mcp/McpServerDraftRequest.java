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

import java.io.Serializable;

/**
 * Complete content request for creating or replacing one MCP draft Version.
 *
 * @author Nacos
 */
public class McpServerDraftRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private McpServerBasicInfo serverSpecification;
    
    private McpToolSpecification toolSpecification;
    
    private McpResourceSpecification resourceSpecification;
    
    private McpEndpointSpec endpointSpecification;
    
    public McpServerBasicInfo getServerSpecification() {
        return serverSpecification;
    }
    
    public void setServerSpecification(McpServerBasicInfo serverSpecification) {
        this.serverSpecification = serverSpecification;
    }
    
    public McpToolSpecification getToolSpecification() {
        return toolSpecification;
    }
    
    public void setToolSpecification(McpToolSpecification toolSpecification) {
        this.toolSpecification = toolSpecification;
    }
    
    public McpResourceSpecification getResourceSpecification() {
        return resourceSpecification;
    }
    
    public void setResourceSpecification(McpResourceSpecification resourceSpecification) {
        this.resourceSpecification = resourceSpecification;
    }
    
    public McpEndpointSpec getEndpointSpecification() {
        return endpointSpecification;
    }
    
    public void setEndpointSpecification(McpEndpointSpec endpointSpecification) {
        this.endpointSpecification = endpointSpecification;
    }
}
