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
 * Request to share one explicitly named source Skill version.
 *
 * @author nacos
 */
public class HubShareSkillRequest {
    
    private String sourceNamespaceId;
    
    private String sourceName;
    
    private String sourceVersion;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long expectedSourceResourceId;
    
    public String getSourceNamespaceId() {
        return sourceNamespaceId;
    }
    
    public void setSourceNamespaceId(String sourceNamespaceId) {
        this.sourceNamespaceId = sourceNamespaceId;
    }
    
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
    
    public Long getExpectedSourceResourceId() {
        return expectedSourceResourceId;
    }
    
    public void setExpectedSourceResourceId(Long expectedSourceResourceId) {
        this.expectedSourceResourceId = expectedSourceResourceId;
    }
    
}
