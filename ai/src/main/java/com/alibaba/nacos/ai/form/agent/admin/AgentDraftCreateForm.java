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
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Form for creating one initial or subsequent Agent draft.
 *
 * @author Nacos
 */
public class AgentDraftCreateForm extends AgentVersionForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String displayName;
    
    private String description;
    
    private String iconUrl;
    
    private String provider;
    
    private String tags;
    
    private String extensions;
    
    private String callInterfaces;
    
    private String author;
    
    private String changeDescription;
    
    private String basedOnVersion;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Parse the call-interface JSON field and build the public request model.
     *
     * @return validated Agent draft-create request
     * @throws NacosApiException when the call-interface JSON is invalid
     */
    public AgentDraftCreateRequest toRequest() throws NacosApiException {
        super.validate();
        AgentDraftCreateRequest result = new AgentDraftCreateRequest();
        result.setAgentName(getAgentName());
        result.setDisplayName(displayName);
        result.setDescription(description);
        result.setIconUrl(iconUrl);
        result.setProvider(AgentAdminFormJsonParser.parseOptional("provider", provider,
            AgentProvider.class));
        result.setTags(AgentAdminFormJsonParser.parseOptional("tags", tags,
            new TypeReference<List<String>>() {
            }));
        result.setExtensions(AgentAdminFormJsonParser.parseOptional("extensions", extensions,
            new TypeReference<Map<String, Object>>() {
            }));
        result.setVersion(getVersion());
        result.setCallInterfaces(AgentAdminFormJsonParser.parseOptional("callInterfaces",
            callInterfaces, new TypeReference<List<AgentCallInterface>>() {
            }));
        result.setAuthor(author);
        result.setChangeDescription(changeDescription);
        result.setBasedOnVersion(basedOnVersion);
        result.validate();
        return result;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getExtensions() {
        return extensions;
    }
    
    public void setExtensions(String extensions) {
        this.extensions = extensions;
    }
    
    public String getCallInterfaces() {
        return callInterfaces;
    }
    
    public void setCallInterfaces(String callInterfaces) {
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
