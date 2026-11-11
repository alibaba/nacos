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

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring binder target for one {@code nacos.copilot.connections.<name>} block.
 *
 * @author nacos
 */
class DeploymentConnectionDefinition {
    
    private String providerId;
    
    private String displayName;
    
    private Boolean enabled;
    
    private String model;
    
    private String apiKey;
    
    private String baseUrl;
    
    private String endpointPath;
    
    private Boolean stream;
    
    private Boolean enableThinking;
    
    private Map<String, Object> options;
    
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
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
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
    
    CopilotModelConnection toConnection(String name) {
        return CopilotModelConnection.builder()
            .name(name)
            .providerId(providerId)
            .displayName(StringUtils.isBlank(displayName) ? name : displayName)
            .enabled(!Boolean.FALSE.equals(enabled))
            .source(ConnectionSource.DEPLOYMENT)
            .priority(DeploymentConfigProviderReader.ORDER)
            .model(model)
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .endpointPath(endpointPath)
            .stream(stream)
            .enableThinking(enableThinking)
            .options(convertOptions())
            .build();
    }
    
    private Map<String, Object> convertOptions() {
        if (options == null) {
            return null;
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String text = ((String) value).trim();
                if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                    value = Boolean.valueOf(text);
                }
            }
            converted.put(entry.getKey(), value);
        }
        return converted;
    }
}
