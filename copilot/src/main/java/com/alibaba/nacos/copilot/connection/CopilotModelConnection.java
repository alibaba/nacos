/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.connection;

import io.agentscope.core.model.ModelCreationContext;

import java.util.Map;

/**
 * One model connection managed by Nacos.
 *
 * <p>The first four fields are Nacos management fields. The remaining fields mirror
 * {@link ModelCreationContext} and are passed through to AgentScope without translation.
 *
 * @author nacos
 */
public class CopilotModelConnection {
    
    private String name;
    
    private String providerId;
    
    private String displayName;
    
    private boolean enabled = true;
    
    private String model;
    
    private String apiKey;
    
    private String baseUrl;
    
    private String endpointPath;
    
    private Boolean stream;
    
    private Boolean enableThinking;
    
    private Map<String, Object> options;
    
    /**
     * Build the model id resolved by AgentScope, in {@code providerId:model} form.
     *
     * @return model id
     */
    public String getModelId() {
        return providerId + ":" + model;
    }
    
    /**
     * Convert the passthrough fields to an AgentScope model creation context.
     *
     * @return model creation context
     */
    public ModelCreationContext toCreationContext() {
        ModelCreationContext.Builder builder = ModelCreationContext.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .endpointPath(endpointPath)
            .stream(stream)
            .enableThinking(enableThinking);
        if (options != null) {
            options.forEach(builder::option);
        }
        return builder.build();
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getProviderId() {
        return providerId;
    }
    
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getEndpointPath() {
        return endpointPath;
    }
    
    public void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }
    
    public Boolean getStream() {
        return stream;
    }
    
    public void setStream(Boolean stream) {
        this.stream = stream;
    }
    
    public Boolean getEnableThinking() {
        return enableThinking;
    }
    
    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }
    
    public Map<String, Object> getOptions() {
        return options;
    }
    
    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}
