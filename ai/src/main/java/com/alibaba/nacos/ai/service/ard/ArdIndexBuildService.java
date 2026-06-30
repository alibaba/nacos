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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Builds and removes local ARD indexes from Nacos resource lifecycle events.
 *
 * @author nacos
 */
public interface ArdIndexBuildService {
    
    /**
     * No-op implementation used before Spring injects the real service.
     */
    ArdIndexBuildService NOOP = new ArdIndexBuildService() {
        
        @Override
        public void rebuildAiResource(String namespaceId, String resourceType, String name,
            String version) {
        }
        
        @Override
        public void rebuildLatestAiResource(String namespaceId, String resourceType, String name) {
        }
        
        @Override
        public void rebuildMcpServer(String namespaceId, McpServerBasicInfo mcpServer) {
        }
        
        @Override
        public void deleteResource(String namespaceId, String resourceType, String resourceName) {
        }
        
        @Override
        public void deleteResourceVersion(String namespaceId, String resourceType,
            String resourceName, String resourceVersion) {
        }
    };
    
    /**
     * Rebuild an AI resource version index.
     */
    void rebuildAiResource(String namespaceId, String resourceType, String name, String version)
        throws NacosException;
    
    /**
     * Rebuild the latest AI resource version index.
     */
    void rebuildLatestAiResource(String namespaceId, String resourceType, String name)
        throws NacosException;
    
    /**
     * Rebuild an MCP server version index.
     */
    void rebuildMcpServer(String namespaceId, McpServerBasicInfo mcpServer) throws NacosException;
    
    /**
     * Remove all ARD index rows for a resource.
     */
    void deleteResource(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Remove all ARD index rows for a resource version.
     */
    void deleteResourceVersion(String namespaceId, String resourceType, String resourceName,
        String resourceVersion);
}
