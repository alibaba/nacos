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

import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;

import java.util.List;

/**
 * AI Mcp server spec in nacos.
 *
 * @author xiweng.yy
 */
public class McpServerDetailInfo extends McpServerBasicInfo {
    
    private List<McpEndpointInfo> backendEndpoints;
    
    private List<McpEndpointInfo> frontendEndpoints;
    
    private McpToolSpecification toolSpec;
    
    private List<ServerVersionDetail> allVersions;
    
    public List<McpEndpointInfo> getBackendEndpoints() {
        return backendEndpoints;
    }
    
    public void setBackendEndpoints(List<McpEndpointInfo> backendEndpoints) {
        this.backendEndpoints = backendEndpoints;
    }
    
    public List<McpEndpointInfo> getFrontendEndpoints() {
        return frontendEndpoints;
    }
    
    public void setFrontendEndpoints(List<McpEndpointInfo> frontendEndpoints) {
        this.frontendEndpoints = frontendEndpoints;
    }
    
    public McpToolSpecification getToolSpec() {
        return toolSpec;
    }
    
    public void setToolSpec(McpToolSpecification toolSpec) {
        this.toolSpec = toolSpec;
    }

    public List<ServerVersionDetail> getAllVersions() {
        return allVersions;
    }

    public void setAllVersions(List<ServerVersionDetail> allVersions) {
        this.allVersions = allVersions;
    }
}
