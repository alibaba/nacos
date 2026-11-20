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
import com.fasterxml.jackson.annotation.JsonInclude;

/** Public discovery coordinates of a fixed Hub share; no source IDs or storage descriptors. */
public class HubResourceDiscoveryInfo {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String resourceType;
    
    private String resourceName;
    
    private String version;
    
    private String sourceDisplayName;
    
    private String author;
    
    private Long downloadCount;
    
    /** Modification time of the Hub entry itself, in Unix epoch milliseconds. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long updateTime;
    
    private boolean sourceReadable;
    
    private String sourceNamespaceId;
    
    private String sourceName;
    
    public boolean isSourceReadable() {
        return sourceReadable;
    }
    
    public void setSourceReadable(boolean sourceReadable) {
        this.sourceReadable = sourceReadable;
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSourceNamespaceId() {
        return sourceReadable ? sourceNamespaceId : null;
    }
    
    public void setSourceNamespaceId(String sourceNamespaceId) {
        this.sourceNamespaceId = sourceNamespaceId;
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSourceName() {
        return sourceReadable ? sourceName : null;
    }
    
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
    
    public Long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
    
    public Long getDownloadCount() {
        return downloadCount;
    }
    
    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
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
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getSourceDisplayName() {
        return sourceDisplayName;
    }
    
    public void setSourceDisplayName(String sourceDisplayName) {
        this.sourceDisplayName = sourceDisplayName;
    }
}
