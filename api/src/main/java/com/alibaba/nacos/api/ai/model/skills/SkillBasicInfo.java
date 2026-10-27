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

package com.alibaba.nacos.api.ai.model.skills;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Skill basic info for list response.
 *
 * @author nacos
 */
public class SkillBasicInfo extends SkillBase {
    
    private Long updateTime;
    
    /** Discovery source; ordinary namespace results keep the original namespace/name identity. */
    private String discoverySource = "NAMESPACE";
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String displayName;
    
    private String sourceDisplayName;
    
    private Long downloadCount;
    
    private String version;
    
    public String getDiscoverySource() {
        return discoverySource;
    }
    
    public void setDiscoverySource(String discoverySource) {
        this.discoverySource = discoverySource;
    }
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getSourceDisplayName() {
        return sourceDisplayName;
    }
    
    public void setSourceDisplayName(String sourceDisplayName) {
        this.sourceDisplayName = sourceDisplayName;
    }
    
    public Long getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public Long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SkillBasicInfo that = (SkillBasicInfo) o;
        return Objects.equals(getNamespaceId(), that.getNamespaceId())
            && Objects.equals(getName(), that.getName())
            && Objects.equals(getDescription(), that.getDescription())
            && Objects.equals(updateTime, that.updateTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getNamespaceId(), getName(), getDescription(), updateTime);
    }
}
