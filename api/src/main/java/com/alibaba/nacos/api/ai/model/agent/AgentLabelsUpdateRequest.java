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

package com.alibaba.nacos.api.ai.model.agent;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

/**
 * Complete replacement request for one Agent's custom Version labels.
 *
 * @author Nacos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentLabelsUpdateRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String agentName;
    
    private Map<String, String> labels;
    
    /**
     * Validate custom labels and their exact Version targets.
     */
    public void validate() {
        AgentAdminRequestUtils.validateIdentity(agentName);
        if (labels == null) {
            throw new IllegalArgumentException("labels must not be null");
        }
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            AgentValidationUtils.validateNonLatestLabel(entry.getKey());
            AgentAdminRequestUtils.validateVersion(entry.getValue());
        }
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
