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

package com.alibaba.nacos.ai.model.mcp;

/**
 * Typed schema-versioned content stored in {@code ai_resource.ext} for MCP.
 *
 * @author Nacos
 */
public class McpResourceExt {
    
    public static final int SCHEMA_VERSION = 1;
    
    private Integer schemaVersion;
    
    private String mcpId;
    
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public String getMcpId() {
        return mcpId;
    }
    
    public void setMcpId(String mcpId) {
        this.mcpId = mcpId;
    }
}
