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

import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Form for replacing the writable fields of one Agent.
 *
 * @author Nacos
 */
public class AgentUpdateForm extends AgentAdminForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String displayName;
    
    private String description;
    
    private String iconUrl;
    
    private String provider;
    
    private String tags;
    
    private String extensions;
    
    private String status;
    
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
     * @return validated Agent update request
     * @throws NacosApiException when a JSON-valued field is invalid
     */
    public AgentUpdateRequest toRequest() throws NacosApiException {
        super.validate();
        AgentUpdateRequest result = new AgentUpdateRequest();
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
        result.setStatus(status);
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
