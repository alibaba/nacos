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

package com.alibaba.nacos.ai.form.agent.client;

import com.alibaba.nacos.ai.form.agent.admin.AgentDraftCreateForm;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;

import java.io.Serial;

/**
 * Form for code-first Agent definition publication.
 *
 * @author Nacos
 */
public class AgentPublishForm extends AgentDraftCreateForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String autoSubmit = Boolean.FALSE.toString();
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Parse JSON-valued fields, validate the complete Form, and build the public request.
     *
     * <p>This method invokes all validation, including the inherited draft validation. Callers
     * should invoke only this method and must not call {@link #validate()} first.</p>
     *
     * @return validated Agent publication request
     * @throws NacosApiException when a JSON-valued Form field is invalid
     */
    @Override
    public AgentPublishRequest toRequest() throws NacosApiException {
        AgentDraftCreateRequest source = super.toRequest();
        AgentPublishRequest result = new AgentPublishRequest();
        result.setAgentName(source.getAgentName());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setIconUrl(source.getIconUrl());
        result.setProvider(source.getProvider());
        result.setTags(source.getTags());
        result.setExtensions(source.getExtensions());
        result.setVersion(source.getVersion());
        result.setCallInterfaces(source.getCallInterfaces());
        result.setAuthor(source.getAuthor());
        result.setChangeDescription(source.getChangeDescription());
        result.setBasedOnVersion(source.getBasedOnVersion());
        result.setAutoSubmit(parseAutoSubmit());
        return result;
    }
    
    public String getAutoSubmit() {
        return autoSubmit;
    }
    
    public void setAutoSubmit(String autoSubmit) {
        this.autoSubmit = autoSubmit;
    }
    
    private boolean parseAutoSubmit() throws NacosApiException {
        if (Boolean.TRUE.toString().equalsIgnoreCase(autoSubmit)) {
            return true;
        }
        if (Boolean.FALSE.toString().equalsIgnoreCase(autoSubmit)) {
            return false;
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Request parameter `autoSubmit` must be `true` or `false`.");
    }
}
