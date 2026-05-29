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
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.AgentSpecChangedEvent;
import com.alibaba.nacos.client.ai.remote.AgentSpecQueryResponse;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos AI module agent spec cache holder.
 *
 * <p>Owns the per-subscription polling loop that periodically calls
 * {@link AiClientProxy#queryAgentSpec(String, String, String, String)} with the locally cached MD5
 * for conditional query. When the server returns 304 ({@link NacosException#NOT_MODIFIED})
 * the local cache is preserved and no callback fires; when the response carries new content
 * (different MD5) an {@link AgentSpecChangedEvent} is published so {@code AiChangeNotifier} can
 * dispatch it to all registered listeners.
 *
 * @author nacos
 */
public class NacosAgentSpecCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(NacosAgentSpecCacheHolder.class);
    
    private final AiClientProxy aiClientProxy;
    
    /**
     * agentSpecName -> last published MD5.
     */
    private final Map<String, String> md5Cache;
    
    /**
     * agentSpecName -> cached AgentSpec object.
     */
    private final Map<String, AgentSpec> agentSpecCache;
    
    private final ScheduledExecutorService updaterExecutor;
    
    private final long updateIntervalMillis;
    
    private final Map<String, AgentSpecUpdater> updateTaskMap;
    
    public NacosAgentSpecCacheHolder(AiClientProxy aiClientProxy,
        NacosClientProperties properties) {
        this.aiClientProxy = aiClientProxy;
        this.md5Cache = new ConcurrentHashMap<>(4);
        this.agentSpecCache = new ConcurrentHashMap<>(4);
        this.updateTaskMap = new ConcurrentHashMap<>(4);
        this.updaterExecutor = new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.agentspec.updater"));
        this.updateIntervalMillis = properties.getLong(
            AiConstants.AI_AGENTSPEC_CACHE_UPDATE_INTERVAL,
            AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL);
    }
    
    /**
     * Query agent spec synchronously (no subscription).
     *
     * @param agentSpecName name of agent spec
     * @return AgentSpec object, null if not found
     * @throws NacosException if error occurs
     */
    public AgentSpec queryAgentSpec(String agentSpecName) throws NacosException {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Required parameter `agentSpecName` not present");
        }
        try {
            AgentSpecQueryResponse response =
                aiClientProxy.queryAgentSpec(agentSpecName, null, null, null);
            return response.getAgentSpec();
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }
    
    /**
     * Subscribe to agent spec changes and start polling.
     *
     * <p>Performs the initial query synchronously and primes the MD5 cache; subsequent polls
     * piggy-back the cached MD5 to short-circuit unchanged content.
     *
     * @param agentSpecName name of agent spec
     * @return current AgentSpec object, null if not found
     * @throws NacosException if error occurs
     */
    public AgentSpec subscribeAgentSpec(String agentSpecName) throws NacosException {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Required parameter `agentSpecName` not present");
        }
        String cacheKey = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        
        AgentSpec agentSpec = null;
        try {
            AgentSpecQueryResponse response =
                aiClientProxy.queryAgentSpec(agentSpecName, null, null, null);
            agentSpec = response.getAgentSpec();
            // Only update cache during initial subscribe; do NOT publish event here.
            // The caller (NacosAiService) handles the first listener notification.
            String newMd5 = response.getMd5();
            if (StringUtils.isNotBlank(newMd5)) {
                md5Cache.put(cacheKey, newMd5);
            }
        } catch (NacosException e) {
            if (e.getErrCode() != NacosException.NOT_FOUND) {
                throw e;
            }
            md5Cache.remove(cacheKey);
        }
        
        if (agentSpec != null) {
            agentSpecCache.put(cacheKey, agentSpec);
        }
        addUpdateTask(agentSpecName);
        LOGGER.info("Subscribed agent spec: {}", agentSpecName);
        return agentSpec;
    }
    
    /**
     * Unsubscribe from agent spec changes.
     *
     * @param agentSpecName name of agent spec
     */
    public void unsubscribeAgentSpec(String agentSpecName) {
        if (StringUtils.isBlank(agentSpecName)) {
            return;
        }
        String cacheKey = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        removeUpdateTask(agentSpecName);
        md5Cache.remove(cacheKey);
        agentSpecCache.remove(cacheKey);
        LOGGER.info("Unsubscribed agent spec: {}", agentSpecName);
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.updaterExecutor.shutdownNow();
    }
    
    private void addUpdateTask(String agentSpecName) {
        String key = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        this.updateTaskMap.computeIfAbsent(key, s -> {
            AgentSpecUpdater task = new AgentSpecUpdater(agentSpecName);
            updaterExecutor.schedule(task, updateIntervalMillis, TimeUnit.MILLISECONDS);
            return task;
        });
    }
    
    private void removeUpdateTask(String agentSpecName) {
        String key = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        AgentSpecUpdater task = this.updateTaskMap.remove(key);
        if (task != null) {
            task.cancel();
        }
    }
    
    private void processAgentSpec(String agentSpecName, String cacheKey,
        AgentSpecQueryResponse response) {
        String oldMd5 = md5Cache.get(cacheKey);
        String newMd5 = response == null ? null : response.getMd5();
        if (response == null) {
            md5Cache.remove(cacheKey);
            agentSpecCache.remove(cacheKey);
        } else if (StringUtils.isNotBlank(newMd5)) {
            md5Cache.put(cacheKey, newMd5);
            agentSpecCache.put(cacheKey, response.getAgentSpec());
        }
        if (response != null && !StringUtils.equals(oldMd5, newMd5)) {
            NotifyCenter.publishEvent(
                new AgentSpecChangedEvent(agentSpecName, response.getAgentSpec()));
        }
    }
    
    private class AgentSpecUpdater implements Runnable {
        
        private final String agentSpecName;
        
        private final String cacheKey;
        
        private final AtomicBoolean cancel = new AtomicBoolean(false);
        
        AgentSpecUpdater(String agentSpecName) {
            this.agentSpecName = agentSpecName;
            this.cacheKey = CacheKeyUtils.buildAgentSpecKey(agentSpecName);
        }
        
        void cancel() {
            cancel.set(true);
        }
        
        @Override
        public void run() {
            if (cancel.get()) {
                return;
            }
            try {
                String currentMd5 = md5Cache.get(cacheKey);
                AgentSpecQueryResponse response =
                    aiClientProxy.queryAgentSpec(agentSpecName, null, null, currentMd5);
                processAgentSpec(agentSpecName, cacheKey, response);
            } catch (NacosException e) {
                if (e.getErrCode() == NacosException.NOT_FOUND) {
                    processAgentSpec(agentSpecName, cacheKey, null);
                } else if (e.getErrCode() == NacosException.NOT_MODIFIED) {
                    // No content change, keep local cache and skip callback.
                } else {
                    LOGGER.warn(
                        "AgentSpec updater query failed: name={}, err={}",
                        agentSpecName, e.getErrMsg());
                }
            } finally {
                if (!cancel.get()) {
                    updaterExecutor.schedule(this, updateIntervalMillis,
                        TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
