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

package com.alibaba.nacos.api.ai.model.skills;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;

/** Public presentation of S3 import origin, without source namespace or database IDs. */
public class SkillOriginSummary implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String sourceName;
    
    private String sourceVersion;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long hubEntryId;
    
    private String importedAt;
    
    private String mode;
    
    public String getSourceName() {
        return sourceName;
    }
    
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
    
    public String getSourceVersion() {
        return sourceVersion;
    }
    
    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }
    
    public Long getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(Long hubEntryId) {
        this.hubEntryId = hubEntryId;
    }
    
    public String getImportedAt() {
        return importedAt;
    }
    
    public void setImportedAt(String importedAt) {
        this.importedAt = importedAt;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
}
