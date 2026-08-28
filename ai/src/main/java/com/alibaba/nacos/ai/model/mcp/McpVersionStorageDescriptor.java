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
 * Storage descriptor persisted in one MCP Version row.
 *
 * @author Nacos
 */
public class McpVersionStorageDescriptor {
    
    public static final String PROVIDER = "nacos_config";
    
    public static final String KEY_FORMAT = "mcp-config-v1";
    
    public static final int SCHEMA_VERSION = 1;
    
    private String provider;
    
    private String keyFormat;
    
    private String serverKey;
    
    private String toolKey;
    
    private String resourceKey;
    
    private Integer schemaVersion;
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getKeyFormat() {
        return keyFormat;
    }
    
    public void setKeyFormat(String keyFormat) {
        this.keyFormat = keyFormat;
    }
    
    public String getServerKey() {
        return serverKey;
    }
    
    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
    }
    
    public String getToolKey() {
        return toolKey;
    }
    
    public void setToolKey(String toolKey) {
        this.toolKey = toolKey;
    }
    
    public String getResourceKey() {
        return resourceKey;
    }
    
    public void setResourceKey(String resourceKey) {
        this.resourceKey = resourceKey;
    }
    
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
