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

/**
 * Result of Hub share lifecycle operation; never exposes storage or raw ext JSON.
 *
 * @author nacos
 */
public class HubShareResult {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String displayName;
    
    private String sourceNamespaceId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sourceResourceId;
    
    private String sourceName;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sourceVersionId;
    
    private String sourceVersion;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long metaVersion;
    
    private HubShareOutcome outcome;
    
    private boolean active;
    
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
    
    public String getSourceNamespaceId() {
        return sourceNamespaceId;
    }
    
    public void setSourceNamespaceId(String sourceNamespaceId) {
        this.sourceNamespaceId = sourceNamespaceId;
    }
    
    public Long getSourceResourceId() {
        return sourceResourceId;
    }
    
    public void setSourceResourceId(Long sourceResourceId) {
        this.sourceResourceId = sourceResourceId;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
    
    public Long getSourceVersionId() {
        return sourceVersionId;
    }
    
    public void setSourceVersionId(Long sourceVersionId) {
        this.sourceVersionId = sourceVersionId;
    }
    
    public String getSourceVersion() {
        return sourceVersion;
    }
    
    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }
    
    public Long getMetaVersion() {
        return metaVersion;
    }
    
    public void setMetaVersion(Long metaVersion) {
        this.metaVersion = metaVersion;
    }
    
    public HubShareOutcome getOutcome() {
        return outcome;
    }
    
    public void setOutcome(HubShareOutcome outcome) {
        this.outcome = outcome;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
}
