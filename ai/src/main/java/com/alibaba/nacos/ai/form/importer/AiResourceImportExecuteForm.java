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

package com.alibaba.nacos.ai.form.importer;

import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Form for executing AI resource import.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class AiResourceImportExecuteForm extends AbstractAiResourceImportForm {
    
    private static final long serialVersionUID = 1L;
    
    private String selectedItems;
    
    private boolean overwriteExisting;
    
    private boolean skipInvalid;
    
    private String validationToken;
    
    @Override
    public void validate() throws NacosApiException {
        validateSource();
        if (StringUtils.isBlank(selectedItems)) {
            throw missingParameter("selectedItems");
        }
    }
    
    /**
     * Convert form data to import execute request.
     *
     * @return import execute request
     * @throws NacosApiException if form JSON fields can't be parsed
     */
    public AiResourceImportExecuteRequest toRequest() throws NacosApiException {
        AiResourceImportExecuteRequest request = new AiResourceImportExecuteRequest();
        request.setNamespaceId(getNamespaceId());
        request.setResourceType(getResourceType());
        request.setSourceId(getSourceId());
        request.setSelectedItems(parseSelectedItems(selectedItems));
        request.setOverwriteExisting(overwriteExisting);
        request.setSkipInvalid(skipInvalid);
        request.setValidationToken(validationToken);
        request.setOptions(parseOptions());
        return request;
    }
    
    public String getSelectedItems() {
        return selectedItems;
    }
    
    public void setSelectedItems(String selectedItems) {
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
}
