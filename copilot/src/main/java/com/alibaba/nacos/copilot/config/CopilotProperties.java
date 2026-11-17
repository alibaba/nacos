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

package com.alibaba.nacos.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Copilot configuration properties.
 *
 * @author nacos
 */
@Component
@ConfigurationProperties(prefix = "nacos.copilot")
public class CopilotProperties {
    
    /**
     * Whether Copilot is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Default namespace.
     */
    private String defaultNamespace = "public";
    
    /**
     * API Key (from environment variable or config file).
     */
    private String apiKey;
    
    /**
     * Whether the console cleared the api key. When set, the legacy connection stops
     * falling back to the process environment, so the credential is really gone.
     */
    private Boolean apiKeyCleared;
    
    /**
     * Model name.
     */
    private String model = "qwen-turbo";
    
    /**
     * AgentScope Studio URL.
     */
    private String studioUrl;
    
    /**
     * AgentScope Studio Project.
     */
    private String studioProject = "NacosCopilot";
    
    /**
     * Connection selected from the console, empty to use the default order.
     */
    private String selectedConnection;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getDefaultNamespace() {
        return defaultNamespace;
    }
    
    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }
    
    /**
     * Get API Key.
     *
     * @return API Key
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Set API Key.
     *
     * @param apiKey API Key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    /**
     * Get whether the console cleared the api key.
     *
     * @return {@code true} when the api key was cleared from the console, otherwise {@code null}
     */
    public Boolean getApiKeyCleared() {
        return apiKeyCleared;
    }
    
    /**
     * Set whether the console cleared the api key.
     *
     * @param apiKeyCleared {@code true} when the api key was cleared from the console
     */
    public void setApiKeyCleared(Boolean apiKeyCleared) {
        this.apiKeyCleared = apiKeyCleared;
    }
    
    /**
     * Get model name.
     *
     * @return model name
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Set model name.
     *
     * @param model model name
     */
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * Get Studio URL.
     *
     * @return Studio URL
     */
    public String getStudioUrl() {
        return studioUrl;
    }
    
    /**
     * Set Studio URL.
     *
     * @param studioUrl Studio URL
     */
    public void setStudioUrl(String studioUrl) {
        this.studioUrl = studioUrl;
    }
    
    /**
     * Get Studio Project.
     *
     * @return Studio Project
     */
    public String getStudioProject() {
        return studioProject;
    }
    
    /**
     * Set Studio Project.
     *
     * @param studioProject Studio Project
     */
    public void setStudioProject(String studioProject) {
        this.studioProject = studioProject;
    }
    
    /**
     * Get the connection selected from the console.
     *
     * @return selected connection name, or {@code null} to use the default order
     */
    public String getSelectedConnection() {
        return selectedConnection;
    }
    
    /**
     * Set the connection selected from the console.
     *
     * @param selectedConnection selected connection name
     */
    public void setSelectedConnection(String selectedConnection) {
        this.selectedConnection = selectedConnection;
    }
}
