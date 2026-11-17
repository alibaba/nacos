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

/**
 * Copilot configuration fields accepted by the console write API.
 *
 * <p>Every field defaults to {@code null}, so a field absent from the request keeps its
 * stored value instead of being reset.
 *
 * @author nacos
 */
public class ConsoleCopilotConfigForm {
    
    private String apiKey;
    
    private String model;
    
    private String studioUrl;
    
    private String studioProject;
    
    private String selectedConnection;
    
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
    
    public String getSelectedConnection() {
        return selectedConnection;
    }
    
    public void setSelectedConnection(String selectedConnection) {
        this.selectedConnection = selectedConnection;
    }
}
