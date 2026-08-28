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
 * Canonical management summary for one exact MCP Server Version.
 *
 * <p>This single-Version model is distinct from {@link McpServerVersionInfo}, which is the legacy
 * serving Manifest aggregate, and from the Registry-compatible
 * {@link com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail} descriptor.</p>
 *
 * @author Nacos
 * @see McpServerVersionDetail
 * @see McpServerVersionInfo
 */
public class McpServerVersionSummary implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String version;
    
    private String status;
    
    private String author;
    
    private String description;
    
    private Boolean latest;
    
    private Long createTime;
    
    private Long updateTime;
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Boolean getLatest() {
        return latest;
    }
    
    public void setLatest(Boolean latest) {
        this.latest = latest;
    }
    
    public Long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
    
    public Long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
