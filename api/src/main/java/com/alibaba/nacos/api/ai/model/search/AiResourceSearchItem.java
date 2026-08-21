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

package com.alibaba.nacos.api.ai.model.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One protocol-neutral AI Resource Search result.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiResourceSearchItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String resourceType;
    
    private String resourceName;
    
    private String resourceVersion;
    
    private String displayName;
    
    private String description;
    
    private List<String> tags = Collections.emptyList();
    
    private List<String> capabilities = Collections.emptyList();
    
    private List<String> representativeQueries = Collections.emptyList();
    
    private Map<String, Object> metadata = Collections.emptyMap();
    
    private Long createTime;
    
    private Long updateTime;
    
    private Integer score;
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }
    
    public String getResourceName() {
        return resourceName;
    }
    
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    
    public String getResourceVersion() {
        return resourceVersion;
    }
    
    public void setResourceVersion(String resourceVersion) {
        this.resourceVersion = resourceVersion;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags == null ? Collections.emptyList() : tags;
    }
    
    public List<String> getCapabilities() {
        return capabilities;
    }
    
    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities == null ? Collections.emptyList() : capabilities;
    }
    
    public List<String> getRepresentativeQueries() {
        return representativeQueries;
    }
    
    public void setRepresentativeQueries(List<String> representativeQueries) {
        this.representativeQueries = representativeQueries == null ? Collections.emptyList()
            : representativeQueries;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? Collections.emptyMap() : metadata;
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
    
    public Integer getScore() {
        return score;
    }
    
    public void setScore(Integer score) {
        this.score = score;
    }
}
