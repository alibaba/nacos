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

/**
 * Sanitized view of one model connection, safe to expose to the console.
 *
 * <p>The credential itself is never part of this view: {@code apiKeyConfigured} only tells
 * whether the connection carries one.
 *
 * @author nacos
 */
public class CopilotConnectionInfo {
    
    private String name;
    
    private String providerId;
    
    private String displayName;
    
    private String model;
    
    private ConnectionSource source;
    
    private boolean enabled;
    
    private boolean available;
    
    private String unavailableReason;
    
    private boolean apiKeyConfigured;
    
    /**
     * Create the view from a connection.
     *
     * @param connection connection to describe
     * @return sanitized connection view
     */
    public static CopilotConnectionInfo from(CopilotModelConnection connection) {
        CopilotConnectionInfo info = new CopilotConnectionInfo();
        info.setName(connection.getName());
        info.setProviderId(connection.getProviderId());
        info.setDisplayName(connection.getDisplayName());
        info.setModel(connection.getModel());
        info.setSource(connection.getSource());
        info.setEnabled(connection.isEnabled());
        info.setAvailable(connection.isAvailable());
        info.setUnavailableReason(connection.getUnavailableReason());
        info.setApiKeyConfigured(StringUtils.isNotBlank(connection.getApiKey()));
        return info;
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
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public ConnectionSource getSource() {
        return source;
    }
    
    public void setSource(ConnectionSource source) {
        this.source = source;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public void setAvailable(boolean available) {
        this.available = available;
    }
    
    public String getUnavailableReason() {
        return unavailableReason;
    }
    
    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }
    
    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }
    
    public void setApiKeyConfigured(boolean apiKeyConfigured) {
        this.apiKeyConfigured = apiKeyConfigured;
    }
}
