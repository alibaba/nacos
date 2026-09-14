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

import com.alibaba.nacos.api.ai.model.agent.AgentDefinitionCallInterface;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Shared draft content for Admin creation and Client publication requests.
 *
 * @author Nacos
 * @since 3.3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractAgentDraftRequest extends AbstractAgentMetadata {
    
    private static final long serialVersionUID = 1L;
    
    private Map<String, Object> extensions;
    
    private String version;
    
    private List<AgentDefinitionCallInterface> callInterfaces;
    
    private String author;
    
    private String changeDescription;
    
    private String basedOnVersion;
    
    /**
     * Initialize fields shared by concrete Agent models.
     */
    protected AbstractAgentDraftRequest() {
    }
    
    public Map<String, Object> getExtensions() {
        return extensions;
    }
    
    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public List<AgentDefinitionCallInterface> getCallInterfaces() {
        return callInterfaces;
    }
    
    public void setCallInterfaces(List<AgentDefinitionCallInterface> callInterfaces) {
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
}
