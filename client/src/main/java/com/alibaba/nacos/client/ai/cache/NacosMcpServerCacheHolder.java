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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.McpServerChangedEvent;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos AI module mcp server cache holder.
 *
 * @author xiweng.yy
 */
public class NacosMcpServerCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosMcpServerCacheHolder.class);
    
    private final AiGrpcClient aiGrpcClient;
    
    private final Map<String, McpServerDetailInfo> mcpServerCache;
    
    private final ObjectMapper objectMapper;
    
    private final ScheduledExecutorService updaterExecutor;
    
    private final long updateIntervalMillis;
    
    private final Map<String, McpServerUpdater> updateTaskMap;
    
    public NacosMcpServerCacheHolder(AiGrpcClient aiGrpcClient, NacosClientProperties properties) {
        this.aiGrpcClient = aiGrpcClient;
        this.mcpServerCache = new ConcurrentHashMap<>(4);
        this.updateTaskMap = new ConcurrentHashMap<>(4);
        this.objectMapper = JsonMapper.builder().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.updaterExecutor = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.mcp.server.updater"));
        this.updateIntervalMillis = properties.getLong(AiConstants.AI_MCP_SERVER_CACHE_UPDATE_INTERVAL,
                AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL);
    }
    
    public McpServerDetailInfo getMcpServer(String mcpName, String version) {
        String key = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        return mcpServerCache.get(key);
    }
    
    /**
     * Process new mcp server detail info.
     *
     * @param detailInfo new mcp server detail info
     */
    public void processMcpServerDetailInfo(McpServerDetailInfo detailInfo) {
        String mcpName = detailInfo.getName();
        String version = detailInfo.getVersionDetail().getVersion();
        Boolean isLatest = detailInfo.getVersionDetail().getIs_latest();
        String key = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        McpServerDetailInfo oldMcpServer = mcpServerCache.get(key);
        mcpServerCache.put(key, detailInfo);
        if (null != isLatest && isLatest) {
            String latestVersionKey = CacheKeyUtils.buildMcpServerKey(mcpName, null);
            mcpServerCache.put(latestVersionKey, detailInfo);
        }
        if (isMcpServerChanged(oldMcpServer, detailInfo)) {
            LOGGER.info("mcp server {} changed.", detailInfo.getName());
            NotifyCenter.publishEvent(new McpServerChangedEvent(detailInfo));
        }
    }
    
    /**
     * Add new update task for mcp server.
     *
     * @param mcpName name of mcp server
     * @param version version of mcp server
     */
    public void addMcpServerUpdateTask(String mcpName, String version) {
        String mcpServerKey = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        this.updateTaskMap.computeIfAbsent(mcpServerKey, s -> {
            McpServerUpdater updateTask = new McpServerUpdater(mcpName, version);
            updaterExecutor.schedule(updateTask, updateIntervalMillis, TimeUnit.MILLISECONDS);
            return updateTask;
        });
    }
    
    /**
     * Remove new update task for mcp server.
     *
     * @param mcpName name of mcp server
     * @param version version of mcp server
     */
    public void removeMcpServerUpdateTask(String mcpName, String version) {
        String mcpServerKey = CacheKeyUtils.buildMcpServerKey(mcpName, version);
        McpServerUpdater updateTask = this.updateTaskMap.remove(mcpServerKey);
        if (null != updateTask) {
            updateTask.cancel();
        }
    }
    
    private boolean isMcpServerChanged(McpServerDetailInfo oldMcpServer, McpServerDetailInfo detailInfo) {
        try {
            String newJson = objectMapper.writeValueAsString(detailInfo);
            if (null == oldMcpServer) {
                LOGGER.info("init new mcp service: {} -> {}", detailInfo.getName(), newJson);
                return true;
            }
            String oldJson = objectMapper.writeValueAsString(oldMcpServer);
            if (!StringUtils.equals(oldJson, newJson)) {
                LOGGER.info("mcp service changed: {} -> {}", oldJson, newJson);
                return true;
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Compare mcp server info failed: ", e);
        }
        return false;
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.updaterExecutor.shutdownNow();
    }
    
    private class McpServerUpdater implements Runnable {
        
        private final String mcpName;
        
        private final String version;
        
        private final AtomicBoolean cancel;
        
        public McpServerUpdater(String mcpName, String version) {
            this.mcpName = mcpName;
            this.version = version;
            this.cancel = new AtomicBoolean(false);
        }
        
        @Override
        public void run() {
            if (cancel.get()) {
                return;
            }
            try {
                McpServerDetailInfo detailInfo = aiGrpcClient.queryMcpServer(mcpName, version);
                processMcpServerDetailInfo(detailInfo);
            } catch (Exception e) {
                if (e instanceof NacosException) {
                    NacosException nacosException = (NacosException) e;
                    if (nacosException.getErrCode() == NacosException.NOT_FOUND) {
                        return;
                    }
                }
                LOGGER.warn("Mcp server updater execute query failed", e);
            } finally {
                if (!cancel.get()) {
                    updaterExecutor.schedule(this, updateIntervalMillis, TimeUnit.MILLISECONDS);
                }
            }
        }
        
        public void cancel() {
            cancel.set(true);
        }
    }
}
