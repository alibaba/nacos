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

package com.alibaba.nacos.api.ai.model.prompt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt admin information for control plane fields.
 *
 * @author nacos
 */
public class PromptAdminInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private int schemaVersion = 1;
    
    private String promptKey;
    
    private String description;
    
    private List<String> bizTags = new ArrayList<>();
    
    private Long gmtModified;
    
    public int getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public String getPromptKey() {
        return promptKey;
    }
    
    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getBizTags() {
        return bizTags;
    }
    
    public void setBizTags(List<String> bizTags) {
        this.bizTags = bizTags;
    }
    
    public Long getGmtModified() {
        return gmtModified;
    }
    
    public void setGmtModified(Long gmtModified) {
        this.gmtModified = gmtModified;
    }
}
