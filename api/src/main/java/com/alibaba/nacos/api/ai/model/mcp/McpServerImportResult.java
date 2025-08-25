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

/**
 * MCP Server Import Result.
 *
 * @author nacos
 */
public class McpServerImportResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Server name.
     */
    private String serverName;
    
    /**
     * Server ID after import.
     */
    private String serverId;
    
    /**
     * Import status: success, failed, skipped.
     */
    private String status;
    
    /**
     * Error message if failed.
     */
    private String errorMessage;
    
    /**
     * Conflict type if skipped.
     */
    private String conflictType;
    
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
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getConflictType() {
        return conflictType;
    }
    
    public void setConflictType(String conflictType) {
        this.conflictType = conflictType;
    }
}