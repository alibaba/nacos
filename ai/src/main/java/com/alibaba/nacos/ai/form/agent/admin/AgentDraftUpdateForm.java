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

package com.alibaba.nacos.ai.form.agent.admin;

import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.Serial;
import java.util.List;

/**
 * Form for replacing one exact Agent draft.
 *
 * @author Nacos
 */
public class AgentDraftUpdateForm extends AgentVersionForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String callInterfaces;
    
    private String changeDescription;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Validate this form, parse JSON-valued fields, and build the public request model.
     *
     * <p>This method performs both Form and Request validation. Callers should invoke this method
     * directly without calling {@link #validate()} first because {@code validate()} delegates to
     * this method.</p>
     *
     * @return validated Agent draft-update request
     * @throws NacosApiException when the call-interface JSON is invalid
     */
    public AgentDraftUpdateRequest toRequest() throws NacosApiException {
        super.validate();
        AgentDraftUpdateRequest result = new AgentDraftUpdateRequest();
        result.setAgentName(getAgentName());
        result.setVersion(getVersion());
        result.setCallInterfaces(AgentAdminFormJsonParser.parseOptional("callInterfaces",
            callInterfaces, new TypeReference<List<AgentCallInterface>>() {
            }));
        result.setChangeDescription(changeDescription);
        result.validate();
        return result;
    }
    
    public String getCallInterfaces() {
        return callInterfaces;
    }
    
    public void setCallInterfaces(String callInterfaces) {
        this.callInterfaces = callInterfaces;
    }
    
    public String getChangeDescription() {
        return changeDescription;
    }
    
    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }
}
