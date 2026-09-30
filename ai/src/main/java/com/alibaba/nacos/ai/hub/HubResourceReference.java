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
 *
 */

package com.alibaba.nacos.ai.hub;

/**
 * Reference descriptor for a resource shared into Nacos AI Resource Hub.
 *
 * <p>A Hub resource does not copy the source resource content. Instead, it keeps
 * a reference to one exact source resource and version.</p>
 *
 */
public class HubResourceReference {
    
    public static final int CURRENT_SCHEMA_VERSION = 1;
    
    public static final String ENTRY_KIND = "HUB_REFERENCE";
    
    private Integer schemaVersion;
    
    private String entryKind;
    
    private String sourceNamespaceId;
    
    private Long sourceResourceId;
    
    private String sourceName;
    
    private Long sourceVersionId;
    
    private String sourceVersion;
    
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public String getEntryKind() {
        return entryKind;
    }
    
    public void setEntryKind(String entryKind) {
        this.entryKind = entryKind;
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
}
