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

import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.Serial;
import java.util.Map;

/**
 * Form for replacing one Agent's custom Version labels.
 *
 * @author Nacos
 */
public class AgentLabelsUpdateForm extends AgentAdminForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String labels;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Validate this form, parse the labels JSON field, and build the public request model.
     *
     * <p>This method performs both Form and Request validation. Callers should invoke this method
     * directly without calling {@link #validate()} first because {@code validate()} delegates to
     * this method.</p>
     *
     * @return validated Agent labels-update request
     * @throws NacosApiException when the labels JSON is invalid
     */
    public AgentLabelsUpdateRequest toRequest() throws NacosApiException {
        super.validate();
        AgentLabelsUpdateRequest result = new AgentLabelsUpdateRequest();
        result.setAgentName(getAgentName());
        result.setLabels(AgentAdminFormJsonParser.parseOptional("labels", labels,
            new TypeReference<Map<String, String>>() {
            }));
        result.validate();
        return result;
    }
    
    public String getLabels() {
        return labels;
    }
    
    public void setLabels(String labels) {
        this.labels = labels;
    }
}
