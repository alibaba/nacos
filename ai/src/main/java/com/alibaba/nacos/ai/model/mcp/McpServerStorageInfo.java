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

package com.alibaba.nacos.ai.model.mcp;

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;

/**
 * Storage model for AI MCP server.
 *
 * @author xiweng.yy
 */
public class McpServerStorageInfo extends McpServerBasicInfo {
    
    private String toolsDescriptionRef;
    
    private String promptDescriptionRef;
    
    private String resourceDescriptionRef;
    
    public String getToolsDescriptionRef() {
        return toolsDescriptionRef;
    }
    
    public void setToolsDescriptionRef(String toolsDescriptionRef) {
        this.toolsDescriptionRef = toolsDescriptionRef;
    }
    
    public String getPromptDescriptionRef() {
        return promptDescriptionRef;
    }
    
    public void setPromptDescriptionRef(String promptDescriptionRef) {
        this.promptDescriptionRef = promptDescriptionRef;
    }
    
    public String getResourceDescriptionRef() {
        return resourceDescriptionRef;
    }
    
    public void setResourceDescriptionRef(String resourceDescriptionRef) {
        this.resourceDescriptionRef = resourceDescriptionRef;
    }
}
