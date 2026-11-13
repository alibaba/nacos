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

import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;

/** HTTP transfer form parsing exact IDs without leaking Spring binding failures. */
public class HubSkillTransferForm implements NacosForm {
    
    private static final long serialVersionUID = 1L;
    private String hubEntryId;
    private String sourceNamespaceId;
    private String sourceName;
    private String sourceVersion;
    private String targetNamespaceId;
    private String targetName;
    private String targetVersion;
    @Override
    public void validate() throws NacosApiException {
        prepareRequest();
    }
    
    /** Convert the form, leaving mode-specific validation to the transfer boundary. */
    public HubSkillTransferRequest prepareRequest() throws NacosApiException {
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        if (hubEntryId != null) {
            try {
                request.setHubEntryId(Long.valueOf(hubEntryId));
            } catch (NumberFormatException failure) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "hubEntryId must be a positive long integer");
            }
        }
        request.setSourceNamespaceId(sourceNamespaceId);
        request.setSourceName(sourceName);
        request.setSourceVersion(sourceVersion);
        request.setTargetNamespaceId(targetNamespaceId);
        request.setTargetName(targetName);
        request.setTargetVersion(targetVersion);
        return request;
    }
    
    public String getHubEntryId() {
        return hubEntryId;
    }
    
    public void setHubEntryId(String value) {
        hubEntryId = value;
    }
    
    public String getSourceNamespaceId() {
        return sourceNamespaceId;
    }
    
    public void setSourceNamespaceId(String value) {
        sourceNamespaceId = value;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    public void setSourceName(String value) {
        sourceName = value;
    }
    
    public String getSourceVersion() {
        return sourceVersion;
    }
    
    public void setSourceVersion(String value) {
        sourceVersion = value;
    }
    
    public String getTargetNamespaceId() {
        return targetNamespaceId;
    }
    
    public void setTargetNamespaceId(String value) {
        targetNamespaceId = value;
    }
    
    public String getTargetName() {
        return targetName;
    }
    
    public void setTargetName(String value) {
        targetName = value;
    }
    
    public String getTargetVersion() {
        return targetVersion;
    }
    
    public void setTargetVersion(String value) {
        targetVersion = value;
    }
}
