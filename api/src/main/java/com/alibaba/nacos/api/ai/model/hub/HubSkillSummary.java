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

package com.alibaba.nacos.api.ai.model.hub;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Safe public summary of one effective Hub Skill reference.
 *
 * <p>Two entries may have the same display name. Only {@code hubEntryId}
 * identifies a Hub entry; neither internal Hub names nor raw source storage,
 * source primary keys, or reference ext JSON are part of this response.</p>
 *
 * @author nacos
 */
public class HubSkillSummary {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String displayName;
    
    private String sourceDisplayName;
    
    private String author;
    
    private String description;
    
    private String sharedVersion;
    
    private List<String> tags;
    
    private Long sharedAt;
    
    /** Total successful downloads through this Hub entry, independent of source downloads. */
    private Long downloadCount;
    
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getOrigin() {
        return "HUB";
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
    
    public String getSharedVersion() {
        return sharedVersion;
    }
    
    public void setSharedVersion(String sharedVersion) {
        this.sharedVersion = sharedVersion;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public Long getSharedAt() {
        return sharedAt;
    }
    
    public void setSharedAt(Long sharedAt) {
        this.sharedAt = sharedAt;
    }
    
    public Long getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
    
}
