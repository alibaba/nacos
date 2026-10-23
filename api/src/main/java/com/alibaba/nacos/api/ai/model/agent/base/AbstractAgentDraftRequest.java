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

package com.alibaba.nacos.api.ai.model.agent.base;

import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import java.util.List;

/**
 * Shared draft content for Admin creation and Client publication requests.
 *
 * @author Nacos
 * @since 3.3.0
 */
public abstract class AbstractAgentDraftRequest extends AbstractAgentMetadata {
    
    private static final long serialVersionUID = 1L;
    
    private String version;
    
    private List<AgentCallInterface> callInterfaces;
    
    private String author;
    
    private String changeDescription;
    
    private String basedOnVersion;
    
    /**
     * Initialize fields shared by concrete Agent models.
     */
    protected AbstractAgentDraftRequest() {
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public List<AgentCallInterface> getCallInterfaces() {
        return callInterfaces;
    }
    
    public void setCallInterfaces(List<AgentCallInterface> callInterfaces) {
        this.callInterfaces = callInterfaces;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getChangeDescription() {
        return changeDescription;
    }
    
    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }
    
    public String getBasedOnVersion() {
        return basedOnVersion;
    }
    
    public void setBasedOnVersion(String basedOnVersion) {
        this.basedOnVersion = basedOnVersion;
    }
    
    /**
     * Validate the draft identity and content source.
     */
    public void validate() {
        AgentValidationUtils.validateDraft(getAgentName(), getVersion(),
            getCallInterfaces() != null, getBasedOnVersion());
    }
}
