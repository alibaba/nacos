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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Exact MCP Version content and lifecycle metadata for management reads.
 *
 * <p>The nested Server specification deliberately suppresses the historical internal
 * {@code id} coordinate. New lifecycle APIs identify MCP resources only by namespace, name, and
 * exact Version.</p>
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpLifecycleVersionDetail extends McpLifecycleVersionSummary {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String mcpName;
    
    @JsonIgnoreProperties(value = "id", allowSetters = true)
    private McpServerBasicInfo serverSpecification;
    
    private McpToolSpecification toolSpecification;
    
    private McpResourceSpecification resourceSpecification;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getMcpName() {
        return mcpName;
    }
    
    public void setMcpName(String mcpName) {
        this.mcpName = mcpName;
    }
    
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
}
