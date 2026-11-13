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

package com.alibaba.nacos.ai.form.hub;

import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.model.hub.HubShareResourceRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;

/** Management form for a concrete published version of a non-Skill resource. */
public class HubShareResourceForm implements NacosForm {
    
    private static final long serialVersionUID = 1L;
    
    private String sourceNamespaceId;
    private String sourceName;
    private String sourceVersion;
    private String expectedSourceResourceId;
    
    @Override
    public void validate() throws NacosApiException {
        prepareRequest();
    }
    
    /** Validate before loading source metadata or evaluating source authority. */
    public HubShareResourceRequest prepareRequest() throws NacosApiException {
        requireText(sourceNamespaceId, 128);
        if (sourceName == null || sourceName.isBlank() || sourceName.length() > 256) {
            throw invalid();
        }
        requireText(sourceVersion, 64);
        HubNamespaceUtils.rejectIfHubNamespace(sourceNamespaceId);
        if ("latest".equalsIgnoreCase(sourceVersion)) {
            throw invalid();
        }
        HubShareResourceRequest result = new HubShareResourceRequest();
        result.setSourceNamespaceId(sourceNamespaceId);
        result.setSourceName(sourceName);
        result.setSourceVersion(sourceVersion);
        if (expectedSourceResourceId != null && !expectedSourceResourceId.isBlank()) {
            result.setExpectedSourceResourceId(
                HubResourceNameUtils.parseEntryId(expectedSourceResourceId));
        }
        return result;
    }
    
    private void requireText(String value, int limit) throws NacosApiException {
        if (value == null || value.isBlank() || value.length() > limit
            || !value.equals(value.trim())) {
            throw invalid();
        }
    }
    
    private NacosApiException invalid() {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "A concrete source namespace, name and version are required");
    }
    
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
    
    public String getExpectedSourceResourceId() {
        return expectedSourceResourceId;
    }
    
    public void setExpectedSourceResourceId(String expectedSourceResourceId) {
        this.expectedSourceResourceId = expectedSourceResourceId;
    }
}
