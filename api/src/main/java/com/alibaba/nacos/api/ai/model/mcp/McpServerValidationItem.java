/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp;

import java.io.Serializable;
import java.util.List;

/**
 * MCP Server Validation Item.
 *
 * @author nacos
 */
public class McpServerValidationItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Server name.
     */
    private String serverName;
    
    /**
     * Server ID (generated or provided).
     */
    private String serverId;
    
    /**
     * Validation status: valid, invalid, duplicate.
     */
    private String status;
    
    /**
     * Validation errors.
     */
    private List<String> errors;
    
    /**
     * Whether server exists.
     */
    private boolean exists;
    
    /**
     * Transformed server detail.
     */
    private McpServerDetailInfo server;
    
    /**
     * Whether selected for import.
     */
    private boolean selected = true;
    
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public boolean isExists() {
        return exists;
    }
    
    public void setExists(boolean exists) {
        this.exists = exists;
    }
    
    public McpServerDetailInfo getServer() {
        return server;
    }
    
    public void setServer(McpServerDetailInfo server) {
        this.server = server;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}