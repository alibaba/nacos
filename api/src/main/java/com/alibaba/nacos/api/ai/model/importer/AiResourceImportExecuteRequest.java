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

package com.alibaba.nacos.api.ai.model.importer;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Request for executing AI resource import.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportExecuteRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String namespaceId;
    
    private String resourceType;
    
    private String sourceId;
    
    private List<AiResourceImportItem> selectedItems;
    
    private boolean overwriteExisting;
    
    private boolean skipInvalid;
    
    private String validationToken;
    
    private Map<String, String> options;
    
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
    
    public String getSourceId() {
        return sourceId;
    }
    
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }
    
    public List<AiResourceImportItem> getSelectedItems() {
        return selectedItems;
    }
    
    public void setSelectedItems(List<AiResourceImportItem> selectedItems) {
        this.selectedItems = selectedItems;
    }
    
    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }
    
    public void setOverwriteExisting(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }
    
    public boolean isSkipInvalid() {
        return skipInvalid;
    }
    
    public void setSkipInvalid(boolean skipInvalid) {
        this.skipInvalid = skipInvalid;
    }
    
    public String getValidationToken() {
        return validationToken;
    }
    
    public void setValidationToken(String validationToken) {
        this.validationToken = validationToken;
    }
    
    public Map<String, String> getOptions() {
        return options;
    }
    
    public void setOptions(Map<String, String> options) {
        this.options = options;
    }
}
