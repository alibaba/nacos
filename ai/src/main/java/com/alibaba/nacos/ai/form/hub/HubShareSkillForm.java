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

import com.alibaba.nacos.ai.service.hub.HubSkillRequestValidator;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * HTTP form for the Hub Skill share operation.
 *
 * @author nacos
 */
public class HubShareSkillForm implements NacosForm {
    
    private static final long serialVersionUID = 1L;
    
    private String sourceNamespaceId;
    
    private String sourceName;
    
    private String sourceVersion;
    
    private String expectedSourceResourceId;
    
    @Override
    public void validate() throws NacosApiException {
        prepareRequest();
    }
    
    /**
     * Prepare and validate the service request from HTTP parameters.
     *
     * @return validated request
     * @throws NacosApiException if the request is invalid
     */
    public HubShareSkillRequest prepareRequest() throws NacosApiException {
        HubShareSkillRequest request = new HubShareSkillRequest();
        request.setSourceNamespaceId(sourceNamespaceId);
        request.setSourceName(sourceName);
        request.setSourceVersion(sourceVersion);
        request.setExpectedSourceResourceId(StringUtils.isBlank(expectedSourceResourceId) ? null
            : Long.valueOf(expectedSourceResourceId));
        HubSkillRequestValidator.validate(request);
        return request;
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
