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

package com.alibaba.nacos.airegistry.model.ard;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Host metadata for ARD ai-catalog.json.
 *
 * @author nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArdHostInfo {
    
    private String displayName;
    
    private String identifier;
    
    private String documentationUrl;
    
    private Map<String, Object> trustManifest;
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public String getDocumentationUrl() {
        return documentationUrl;
    }
    
    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }
    
    public Map<String, Object> getTrustManifest() {
        return trustManifest;
    }
    
    public void setTrustManifest(Map<String, Object> trustManifest) {
        this.trustManifest = trustManifest;
    }
}
