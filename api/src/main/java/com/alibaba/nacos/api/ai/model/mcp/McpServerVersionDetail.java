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

import java.util.Map;

/**
 * Canonical content and management metadata for one exact MCP Server Version.
 *
 * <p>Canonical APIs identify MCP resources only by namespace, name, and exact Version. Response
 * assembly clears the historical internal {@code id} coordinate from the nested Server
 * specification. This model is distinct from {@link McpServerDetailInfo}, which is the legacy
 * serving projection.</p>
 *
 * @author Nacos
 * @see McpServerVersionSummary
 * @see McpServerDetailInfo
 */
public class McpServerVersionDetail extends McpServerVersionSummary {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String mcpName;
    
    private McpServerBasicInfo serverSpecification;
    
    private McpToolSpecification toolSpecification;
    
    private McpResourceSpecification resourceSpecification;
    
    private String resourceStatus;
    
    private String owner;
    
    private String scope;
    
    private Map<String, String> labels;
    
    private String editingVersion;
    
    private String reviewingVersion;
    
    private Integer onlineCount;
    
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
    
    public String getResourceStatus() {
        return resourceStatus;
    }
    
    public void setResourceStatus(String resourceStatus) {
        this.resourceStatus = resourceStatus;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public String getEditingVersion() {
        return editingVersion;
    }
    
    public void setEditingVersion(String editingVersion) {
        this.editingVersion = editingVersion;
    }
    
    public String getReviewingVersion() {
        return reviewingVersion;
    }
    
    public void setReviewingVersion(String reviewingVersion) {
        this.reviewingVersion = reviewingVersion;
    }
    
    public Integer getOnlineCount() {
        return onlineCount;
    }
    
    public void setOnlineCount(Integer onlineCount) {
        this.onlineCount = onlineCount;
    }
}
