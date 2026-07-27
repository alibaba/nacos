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

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Exact Agent version definition for management reads.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentVersionDetail implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String agentName;
    
    private String version;
    
    private String status;
    
    private List<AgentCallInterface> callInterfaces;
    
    private String author;
    
    private String changeDescription;
    
    private String contentDigest;
    
    private Long createTime;
    
    private Long updateTime;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
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
    
    public List<AgentCallInterface> getCallInterfaces() {
        return callInterfaces;
    }
    
    public void setCallInterfaces(List<AgentCallInterface> callInterfaces) {
        this.callInterfaces = callInterfaces;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getChangeDescription() {
        return changeDescription;
    }
    
    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }
    
    public String getContentDigest() {
        return contentDigest;
    }
    
    public void setContentDigest(String contentDigest) {
        this.contentDigest = contentDigest;
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
