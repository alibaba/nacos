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

package com.alibaba.nacos.console.model.ai;

import com.alibaba.nacos.copilot.connection.CopilotConnectionInfo;

import java.util.List;

/**
 * Console view of the Copilot configuration.
 *
 * <p>The stored {@code apiKey} is always masked, and the connection metadata never carries
 * a credential.
 *
 * @author nacos
 */
public class ConsoleCopilotConfig {
    
    private String apiKey;
    
    private String model;
    
    private String studioUrl;
    
    private String studioProject;
    
    private String selectedConnection;
    
    private String activeConnection;
    
    private List<CopilotConnectionInfo> connections;
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getStudioUrl() {
        return studioUrl;
    }
    
    public void setStudioUrl(String studioUrl) {
        this.studioUrl = studioUrl;
    }
    
    public String getStudioProject() {
        return studioProject;
    }
    
    public void setStudioProject(String studioProject) {
        this.studioProject = studioProject;
    }
    
    /**
     * Get the stored connection selection.
     *
     * @return selected connection name, or {@code null} when the default order is used
     */
    public String getSelectedConnection() {
        return selectedConnection;
    }
    
    public void setSelectedConnection(String selectedConnection) {
        this.selectedConnection = selectedConnection;
    }
    
    /**
     * Get the connection serving requests after routing.
     *
     * @return active connection name, or {@code null} when no connection can serve
     */
    public String getActiveConnection() {
        return activeConnection;
    }
    
    public void setActiveConnection(String activeConnection) {
        this.activeConnection = activeConnection;
    }
    
    /**
     * Get the sanitized connection metadata.
     *
     * @return connections in selection order
     */
    public List<CopilotConnectionInfo> getConnections() {
        return connections;
    }
    
    public void setConnections(List<CopilotConnectionInfo> connections) {
        this.connections = connections;
    }
}
