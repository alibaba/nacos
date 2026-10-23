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

package com.alibaba.nacos.api.ai.model.agent;

import com.alibaba.nacos.api.ai.model.agent.base.AbstractAgentMetadata;

/**
 * Agent metadata shared by management and discovery catalog views.
 *
 * <p>Management queries include namespace, governance and lifecycle metadata; list queries omit
 * extensions. Discovery catalog queries include only public metadata and online version facts.
 * Fields outside the query projection remain absent.</p>
 *
 * @author Nacos
 * @since 3.3.0
 */
public class AgentSummary extends AbstractAgentMetadata {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String status;
    
    private String owner;
    
    private String scope;
    
    private AgentVersionInfo versionInfo;
    
    private Long metaVersion;
    
    private Long createTime;
    
    private Long updateTime;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
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
    
    public AgentVersionInfo getVersionInfo() {
        return versionInfo;
    }
    
    public void setVersionInfo(AgentVersionInfo versionInfo) {
        this.versionInfo = versionInfo;
    }
    
    public Long getMetaVersion() {
        return metaVersion;
    }
    
    public void setMetaVersion(Long metaVersion) {
        this.metaVersion = metaVersion;
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
