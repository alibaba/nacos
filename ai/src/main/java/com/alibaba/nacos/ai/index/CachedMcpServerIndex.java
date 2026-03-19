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

package com.alibaba.nacos.ai.index;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced MCP cache index implementation combining memory cache and database queries.
 *
 * @author misselvexu
 */
public class CachedMcpServerIndex extends AbstractMcpServerIndex {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedMcpServerIndex.class);
    
    private final McpCacheIndex cacheIndex;

    private final ConfigQueryChainService configQueryChainService;
    
    private final ScheduledExecutorService scheduledExecutor;
    
    private ScheduledFuture<?> syncTask;
    
    private final boolean cacheEnabled;
    
    private final long syncInterval;
    
    /**
     * Constructor.
     */
    public CachedMcpServerIndex(ConfigDetailService configDetailService,
            NamespaceOperationService namespaceOperationService, ConfigQueryChainService configQueryChainService,
            McpCacheIndex cacheIndex, ScheduledExecutorService scheduledExecutor, boolean cacheEnabled,
            long syncInterval) {
        super(namespaceOperationService, configDetailService);
        this.configQueryChainService = configQueryChainService;
        this.cacheIndex = cacheIndex;
        this.scheduledExecutor = scheduledExecutor;
        this.cacheEnabled = cacheEnabled;
        this.syncInterval = syncInterval;
        if (cacheEnabled) {
            startSyncTask();
        }
        LOGGER.info("CachedMcpServerIndex initialized with cacheEnabled={}, syncInterval={}s", cacheEnabled,
                syncInterval);
    }
    
    /**
     * Get MCP server information by ID.
     */
    @Override
    public McpServerIndexData getMcpServerById(String id) {
        if (!cacheEnabled) {
            LOGGER.debug("Cache disabled, querying directly from database for mcpId: {}", id);
            return getMcpServerByIdFromDatabase(id);
        }
        // Priority query cache
        McpServerIndexData cachedData = cacheIndex.getMcpServerById(id);
        if (cachedData != null) {
            LOGGER.debug("Cache hit for mcpId: {}", id);
            return cachedData;
        }
        // Cache miss, query database
        LOGGER.debug("Cache miss for mcpId: {}, querying database", id);
        McpServerIndexData dbData = getMcpServerByIdFromDatabase(id);
        if (dbData != null) {
            cacheIndex.updateIndex(dbData.getNamespaceId(), dbData.getId(), dbData.getId());
            LOGGER.debug("Updated cache for mcpId: {}", id);
        }
        return dbData;
    }
    
    /**
     * Get MCP server information by name.
     */
    @Override
    public McpServerIndexData getMcpServerByName(String namespaceId, String name) {
        if (StringUtils.isEmpty(namespaceId) && StringUtils.isEmpty(name)) {
            LOGGER.warn("Invalid parameters for getMcpServerByName: namespaceId={}, name={}", namespaceId, name);
            return null;
        }

        if (StringUtils.isEmpty(namespaceId)) {
            return getFirstMcpServerByName(name);
        }

        if (!cacheEnabled) {
            LOGGER.debug("Cache disabled, querying directly from database for name: {}:{}", namespaceId, name);
            return getMcpServerByNameFromDatabase(namespaceId, name);
        }
        // Priority query cache
        McpServerIndexData cachedData = cacheIndex.getMcpServerByName(namespaceId, name);
        if (cachedData != null) {
            LOGGER.debug("Cache hit for name: {}:{}", namespaceId, name);
            return cachedData;
        }
        // Cache miss, query database
        LOGGER.debug("Cache miss for name: {}:{}, querying database", namespaceId, name);
        McpServerIndexData dbData = getMcpServerByNameFromDatabase(namespaceId, name);
        if (dbData != null) {
            cacheIndex.updateIndex(namespaceId, name, dbData.getId());
            LOGGER.debug("Updated cache for name: {}:{}", namespaceId, name);
        }
        return dbData;
    }

    @Override
    protected void afterSearch(McpServerIndexData indexData, String name) {
        // Update cache
        if (cacheEnabled) {
            cacheIndex.updateIndex(indexData.getNamespaceId(), name, indexData.getId());
        }
    }
    
    /**
     * Get MCP server from database by ID.
     */
    private McpServerIndexData getMcpServerByIdFromDatabase(String id) {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId(id + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        request.setGroup(Constants.MCP_SERVER_VERSIONS_GROUP);
        List<String> namespaceList = fetchOrderedNamespaceList();
        for (String namespaceId : namespaceList) {
            request.setTenant(namespaceId);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL) {
                McpServerIndexData result = new McpServerIndexData();
                result.setId(id);
                result.setNamespaceId(namespaceId);
                LOGGER.debug("Found MCP server in database: mcpId={}, namespaceId={}", id, namespaceId);
                return result;
            }
        }
        LOGGER.debug("MCP server not found in database: mcpId={}", id);
        return null;
    }
    
    /**
     * Get MCP server from database by name.
     */
    private McpServerIndexData getMcpServerByNameFromDatabase(String namespaceId, String name) {
        // 直接查询数据库，避免调用searchMcpServerByName导致重复更新缓存
        Page<ConfigInfo> serverInfos = searchMcpServers(namespaceId, name, Constants.MCP_LIST_SEARCH_ACCURATE, 1, 1);
        if (CollectionUtils.isNotEmpty(serverInfos.getPageItems())) {
            ConfigInfo configInfo = serverInfos.getPageItems().get(0);
            McpServerIndexData result = new McpServerIndexData();
            result.setId(configInfo.getDataId().replace(Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX, ""));
            result.setNamespaceId(configInfo.getTenant());
            LOGGER.debug("Found MCP server in database: name={}:{}, mcpId={}", namespaceId, name, result.getId());
            return result;
        }
        LOGGER.debug("MCP server not found in database: name={}:{}", namespaceId, name);
        return null;
    }

    /**
     * Start scheduled sync task.
     */
    private void startSyncTask() {
        syncTask = scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                LOGGER.debug("Starting cache sync task");
                syncCacheFromDatabase();
                LOGGER.debug("Cache sync task completed");
            } catch (Exception e) {
                LOGGER.error("Error during cache sync task", e);
            }
        }, syncInterval, syncInterval, TimeUnit.SECONDS);
        LOGGER.info("Cache sync task started with interval: {}s", syncInterval);
    }
    
    /**
     * Shutdown the cache sync task and cleanup resources.
     */
    @PreDestroy
    public void destroy() {
        try {
            if (syncTask != null) {
                syncTask.cancel(true);
            }
            scheduledExecutor.shutdown();
        } catch (Exception e) {
            LOGGER.warn("shutting down sync task schedule executor failed", e);
        }
    }
    
    /**
     * Sync cache from database.
     */
    private void syncCacheFromDatabase() {
        LOGGER.debug("Syncing cache from database");
        List<String> namespaceList = fetchOrderedNamespaceList();
        for (String namespaceId : namespaceList) {
            try {
                searchMcpServerByNameWithPage(namespaceId, null,
                        Constants.MCP_LIST_SEARCH_BLUR, 1, 1000);
            } catch (Exception e) {
                LOGGER.error("Error syncing cache for namespace: {}", namespaceId, e);
            }
        }
    }
    
    /**
     * Get cache statistics.
     */
    public McpCacheIndex.CacheStats getCacheStats() {
        McpCacheIndex.CacheStats stats = cacheIndex.getStats();
        LOGGER.debug("Cache stats: hitCount={}, missCount={}, evictionCount={}, size={}, hitRate=%.2f%%",
                stats.getHitCount(), stats.getMissCount(), stats.getEvictionCount(), stats.getSize(),
                stats.getHitRate() * 100);
        return stats;
    }
    
    /**
     * Clear cache.
     */
    public void clearCache() {
        cacheIndex.clear();
        LOGGER.info("Cache cleared");
    }
    
    /**
     * Manually trigger cache synchronization.
     */
    public void triggerCacheSync() {
        if (cacheEnabled) {
            LOGGER.info("Manual cache sync triggered");
            syncCacheFromDatabase();
        } else {
            LOGGER.warn("Cache is disabled, manual sync ignored");
        }
    }
    
    /**
     * Remove cache entry by namespace ID and MCP server name.
     *
     * @param namespaceId namespace ID
     * @param mcpName     MCP server name
     */
    @Override
    public void removeMcpServerByName(String namespaceId, String mcpName) {
        if (cacheEnabled) {
            LOGGER.debug("Removing cache entry by name: namespaceId={}, mcpName={}", namespaceId, mcpName);
            cacheIndex.removeIndex(namespaceId, mcpName);
        } else {
            LOGGER.debug("Cache is disabled, ignoring cache removal by name: namespaceId={}, mcpName={}", namespaceId,
                    mcpName);
        }
    }
    
    /**
     * Remove cache entry by MCP server ID.
     *
     * @param mcpId MCP server ID
     */
    @Override
    public void removeMcpServerById(String mcpId) {
        if (cacheEnabled) {
            LOGGER.debug("Removing cache entry by ID: mcpId={}", mcpId);
            cacheIndex.removeIndex(mcpId);
        } else {
            LOGGER.debug("Cache is disabled, ignoring cache removal by ID: mcpId={}", mcpId);
        }
    }
} 