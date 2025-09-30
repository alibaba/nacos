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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced MCP cache index implementation combining memory cache and database queries.
 *
 * @author misselvexu
 */
public class CachedMcpServerIndex implements McpServerIndex {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedMcpServerIndex.class);
    
    private final McpCacheIndex cacheIndex;
    
    private final ConfigDetailService configDetailService;
    
    private final NamespaceOperationService namespaceOperationService;
    
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
        this.configDetailService = configDetailService;
        this.namespaceOperationService = namespaceOperationService;
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
     * Search MCP servers by name with pagination.
     */
    @Override
    public Page<McpServerIndexData> searchMcpServerByName(String namespaceId, String name, String search, int offset,
            int limit) {
        return searchFromDatabase(namespaceId, name, search, offset, limit);
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
        if (namespaceId == null || name == null || namespaceId.isEmpty() || name.isEmpty()) {
            LOGGER.warn("Invalid parameters for getMcpServerByName: namespaceId={}, name={}", namespaceId, name);
            return null;
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
    
    /**
     * Search MCP servers from database with pagination.
     */
    private Page<McpServerIndexData> searchFromDatabase(String namespaceId, String name, String search, int offset,
            int limit) {
        int pageNo = offset / limit + 1;
        LOGGER.debug("Searching from database: namespaceId={}, name={}, search={}, pageNo={}, limit={}", namespaceId,
                name, search, pageNo, limit);
        Page<ConfigInfo> serverInfos = searchMcpServers(namespaceId, name, search, pageNo, limit);
        List<McpServerIndexData> indexDataList = serverInfos.getPageItems().stream()
                .map(this::mapMcpServerVersionConfigToIndexData).toList();
        Page<McpServerIndexData> result = new Page<>();
        result.setPageItems(indexDataList);
        result.setTotalCount(serverInfos.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) serverInfos.getTotalCount() / (double) limit));
        result.setPageNumber(pageNo);
        // Update cache
        if (cacheEnabled) {
            for (McpServerIndexData indexData : indexDataList) {
                cacheIndex.updateIndex(indexData.getNamespaceId(), name, indexData.getId());
            }
            LOGGER.debug("Updated cache with {} entries from search results", indexDataList.size());
        }
        return result;
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
     * Search MCP servers.
     */
    private Page<ConfigInfo> searchMcpServers(String namespace, String serverName, String search, int pageNo,
            int limit) {
        HashMap<String, Object> advanceInfo = new HashMap<>(1);
        if (Objects.isNull(serverName)) {
            serverName = "";
        }
        String dataId = Constants.ALL_PATTERN;
        if (Constants.MCP_LIST_SEARCH_BLUR.equals(search) || serverName.isEmpty()) {
            String nameTag = McpConfigUtils.formatServerNameTagBlurSearchValue(serverName);
            advanceInfo.put(Constants.CONFIG_TAGS_NAME, nameTag);
            search = Constants.MCP_LIST_SEARCH_BLUR;
        } else {
            advanceInfo.put(Constants.CONFIG_TAGS_NAME,
                    McpConfigUtils.formatServerNameTagAccurateSearchValue(serverName));
            dataId = null;
        }
        return configDetailService.findConfigInfoPage(search, pageNo, limit, dataId,
                Constants.MCP_SERVER_VERSIONS_GROUP, namespace, advanceInfo);
    }
    
    /**
     * Map configuration information to index data.
     */
    private McpServerIndexData mapMcpServerVersionConfigToIndexData(ConfigInfo configInfo) {
        McpServerIndexData data = new McpServerIndexData();
        data.setId(configInfo.getDataId().replace(Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX, ""));
        data.setNamespaceId(configInfo.getTenant());
        return data;
    }
    
    /**
     * Get ordered namespace list.
     */
    private List<String> fetchOrderedNamespaceList() {
        return namespaceOperationService.getNamespaceList().stream()
                .sorted(Comparator.comparing(com.alibaba.nacos.api.model.response.Namespace::getNamespace))
                .map(com.alibaba.nacos.api.model.response.Namespace::getNamespace).toList();
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
                Page<McpServerIndexData> result = searchMcpServerByName(namespaceId, null,
                        Constants.MCP_LIST_SEARCH_BLUR, 0, 1000);
                if (result != null && CollectionUtils.isNotEmpty(result.getPageItems())) {
                    for (McpServerIndexData indexData : result.getPageItems()) {
                        cacheIndex.updateIndex(namespaceId, indexData.getId(), indexData.getId());
                    }
                    LOGGER.debug("Synced {} MCP servers for namespace: {}", result.getPageItems().size(), namespaceId);
                }
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