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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MCP Server cache invalidation service.
 * 
 * <p>This service listens to ConfigDataChangeEvent and invalidates MCP server cache
 * when MCP-related configurations are deleted or modified. The implementation follows
 * the same pattern as AsyncNotifyService for configuration synchronization.</p>
 *
 * @author xinluo
 */
@Service
public class McpServerCacheInvalidateService extends Subscriber<LocalDataChangeEvent> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServerCacheInvalidateService.class);
    
    private final McpServerIndex mcpServerIndex;
    
    @Autowired
    public McpServerCacheInvalidateService(McpServerIndex mcpServerIndex) {
        this.mcpServerIndex = mcpServerIndex;
        NotifyCenter.registerSubscriber(this);
    }
    
    /**
     * Handle ConfigDataChangeEvent to invalidate MCP server cache.
     *
     * @param event configuration change event
     */
    void handleConfigDataChangeEvent(LocalDataChangeEvent event) {
        // Check if the configuration is MCP server related
        String groupKey = event.groupKey;

        String[] strings = GroupKey.parseKey(groupKey);
        String dataId = strings[0];
        String group = strings[1];
        String tenant = strings.length > 2 ? strings[2] : "";
        if (!isMcpServerConfig(group)) {
            return;
        }
        
        // Extract server ID from dataId
        String serverId = extractServerIdFromDataId(group, dataId);
        if (StringUtils.isEmpty(serverId)) {
            LOGGER.warn("Failed to extract server ID from dataId: {}, group: {}", dataId, group);
            return;
        }
        
        // Invalidate cache
        invalidateCache(tenant, serverId);
        
        LOGGER.info("Handled MCP server config change event: namespaceId={}, group={}, dataId={}, serverId={}",
                tenant, group, dataId, serverId);
    }
    
    /**
     * Check if the configuration group is MCP server related.
     *
     * @param group configuration group
     * @return true if the group is MCP server related
     */
    private boolean isMcpServerConfig(String group) {
        return Constants.MCP_SERVER_VERSIONS_GROUP.equals(group);
    }
    
    /**
     * Extract server ID from dataId based on the configuration group.
     *
     * <p>MCP server configurations follow these naming patterns:</p>
     * <ul>
     *   <li>Version info: {serverId}-mcp-versions.json (group: mcp-server-versions)</li>
     *   <li>Server spec: {serverId}-{version}-mcp-server.json (group: mcp-server)</li>
     *   <li>Tool spec: {serverId}-{version}-mcp-tools.json (group: mcp-tools)</li>
     * </ul>
     *
     * @param group configuration group
     * @param dataId configuration dataId
     * @return extracted server ID, or null if extraction fails
     */
    private String extractServerIdFromDataId(String group, String dataId) {
        if (Constants.MCP_SERVER_VERSIONS_GROUP.equals(group)) {
            // Version info: remove "-mcp-versions.json" suffix
            if (StringUtils.isNotEmpty(dataId) && dataId.endsWith(Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX)) {
                return dataId.substring(0, dataId.length() - Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX.length());
            }
        }
        return null;
    }
    
    /**
     * Invalidate MCP server cache by server ID.
     *
     * <p>This method is idempotent - calling it multiple times with the same
     * serverId will not cause any side effects.</p>
     *
     * @param namespaceId namespace ID
     * @param serverId MCP server ID
     */
    private void invalidateCache(String namespaceId, String serverId) {
        try {
            // Clear cache by server ID
            mcpServerIndex.removeMcpServerById(serverId);
            
            LOGGER.info("MCP Server cache invalidated successfully: namespaceId={}, serverId={}", namespaceId,
                    serverId);
        } catch (Exception e) {
            // Cache invalidation failure should not affect configuration deletion
            LOGGER.error("Failed to invalidate MCP Server cache: namespaceId={}, serverId={}, error={}", namespaceId,
                    serverId, e.getMessage(), e);
        }
    }

    @Override
    public void onEvent(LocalDataChangeEvent event) {
        handleConfigDataChangeEvent(event);
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return LocalDataChangeEvent.class;
    }
}
