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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.function.BooleanSupplier;

/**
 * Builds and removes local AI resource indexes from Nacos resource lifecycle events.
 *
 * @author nacos
 */
public interface AiResourceIndexService {
    
    /**
     * No-op implementation used before Spring injects the real service.
     */
    AiResourceIndexService NOOP = new AiResourceIndexService() {
        
        @Override
        public void rebuildAiResource(String namespaceId, String resourceType, String name,
            String version) {
        }
        
        @Override
        public boolean rebuildLatestAiResource(String namespaceId, String resourceType,
            String name) {
            return false;
        }
        
        @Override
        public boolean rebuildMcpServer(String namespaceId, McpServerBasicInfo mcpServer) {
            return false;
        }
        
        @Override
        public boolean isEnhancementRequested() {
            return false;
        }
        
        @Override
        public String enhancementFingerprint() {
            return "";
        }
        
        @Override
        public boolean enhanceLatestAiResource(String namespaceId, String resourceType,
            String name) {
            return false;
        }
        
        @Override
        public boolean enhanceMcpServer(String namespaceId, McpServerBasicInfo mcpServer) {
            return false;
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
    boolean rebuildLatestAiResource(String namespaceId, String resourceType, String name)
        throws NacosException;
    
    /**
     * Rebuild an MCP server version index.
     */
    boolean rebuildMcpServer(String namespaceId, McpServerBasicInfo mcpServer)
        throws NacosException;
    
    /**
     * Whether durable LLM enhancement is requested by configuration.
     */
    boolean isEnhancementRequested();
    
    /**
     * Fingerprint of the effective enhancement configuration.
     */
    String enhancementFingerprint();
    
    /**
     * Enhance the latest indexed AI resource and converge its vector index.
     *
     * @return whether a current index entry exists
     */
    boolean enhanceLatestAiResource(String namespaceId, String resourceType, String name)
        throws Exception;
    
    /**
     * Enhance the latest resource only while the caller still owns its durable task.
     *
     * @return exact enhancement fingerprint, or {@code null} if the resource or task is stale
     */
    default String enhanceLatestAiResource(String namespaceId, String resourceType, String name,
        BooleanSupplier ownership) throws Exception {
        if (!ownership.getAsBoolean()) {
            return null;
        }
        return enhanceLatestAiResource(namespaceId, resourceType, name)
            ? enhancementFingerprint() : null;
    }
    
    /**
     * Enhance an indexed MCP server and converge its vector index.
     *
     * @return whether a current index entry exists
     */
    boolean enhanceMcpServer(String namespaceId, McpServerBasicInfo mcpServer)
        throws Exception;
    
    /**
     * Enhance an MCP server only while the caller still owns its durable task.
     *
     * @return exact enhancement fingerprint, or {@code null} if the resource or task is stale
     */
    default String enhanceMcpServer(String namespaceId, McpServerBasicInfo mcpServer,
        BooleanSupplier ownership) throws Exception {
        if (!ownership.getAsBoolean()) {
            return null;
        }
        return enhanceMcpServer(namespaceId, mcpServer) ? enhancementFingerprint() : null;
    }
    
    /**
     * Remove all AI resource index rows for a resource.
     */
    void deleteResource(String namespaceId, String resourceType, String resourceName);
    
    /**
     * Remove all AI resource index rows for a resource version.
     */
    void deleteResourceVersion(String namespaceId, String resourceType, String resourceName,
        String resourceVersion);
}
