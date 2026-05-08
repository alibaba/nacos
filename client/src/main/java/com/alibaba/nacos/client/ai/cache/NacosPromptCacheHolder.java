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
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.event.PromptChangedEvent;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos AI module prompt cache holder.
 *
 * @author nacos
 */
public class NacosPromptCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(NacosPromptCacheHolder.class);
    
    private final AiClientProxy aiClientProxy;
    
    private final Map<String, Prompt> promptCache;
    
    private final ScheduledExecutorService updaterExecutor;
    
    private final long updateIntervalMillis;
    
    private final Map<String, PromptUpdater> updateTaskMap;
    
    public NacosPromptCacheHolder(AiClientProxy aiClientProxy, NacosClientProperties properties) {
        this.aiClientProxy = aiClientProxy;
        this.promptCache = new ConcurrentHashMap<>(4);
        this.updateTaskMap = new ConcurrentHashMap<>(4);
        this.updaterExecutor = new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.prompt.updater"));
        this.updateIntervalMillis = properties.getLong(AiConstants.AI_PROMPT_CACHE_UPDATE_INTERVAL,
            AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL);
    }
    
    private Prompt queryPrompt(String promptKey, String version, String label)
        throws NacosException {
        return queryPrompt(promptKey, version, label, null);
    }
    
    private Prompt queryPrompt(String promptKey, String version, String label, String md5)
        throws NacosException {
        return aiClientProxy.queryPrompt(promptKey, version, label, md5);
    }
    
    /**
     * Subscribe prompt and start polling for prompt changes.
     *
     * @param promptKey prompt key
     * @return current Prompt object, null if not found
     * @throws NacosException if error occurs
     */
    public Prompt subscribePrompt(String promptKey, String version, String label)
        throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Required parameter `promptKey` not present");
        }
        String cacheKey = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        
        Prompt prompt = null;
        try {
            prompt = queryPrompt(promptKey, version, label);
            processPrompt(promptKey, cacheKey, prompt);
        } catch (NacosException e) {
            if (e.getErrCode() != NacosException.NOT_FOUND) {
                throw e;
            }
            processPrompt(promptKey, cacheKey, null);
        }
        addPromptUpdateTask(promptKey, version, label);
        LOGGER.info("Subscribed prompt: {}, version: {}, label: {}", promptKey, version, label);
        return prompt;
    }
    
    /**
     * Unsubscribe prompt and remove update task.
     *
     * @param promptKey prompt key
     */
    public void unsubscribePrompt(String promptKey, String version, String label) {
        if (StringUtils.isBlank(promptKey)) {
            return;
        }
        String cacheKey = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        
        removePromptUpdateTask(promptKey, version, label);
        promptCache.remove(cacheKey);
        LOGGER.info("Unsubscribed prompt: {}, version: {}, label: {}", promptKey, version, label);
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.updaterExecutor.shutdownNow();
    }
    
    private void addPromptUpdateTask(String promptKey, String version, String label) {
        String key = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        this.updateTaskMap.computeIfAbsent(key, s -> {
            PromptUpdater task = new PromptUpdater(promptKey, version, label);
            updaterExecutor.schedule(task, updateIntervalMillis, TimeUnit.MILLISECONDS);
            return task;
        });
    }
    
    private void removePromptUpdateTask(String promptKey, String version, String label) {
        String key = CacheKeyUtils.buildPromptKey(promptKey, version, label);
        PromptUpdater task = this.updateTaskMap.remove(key);
        if (task != null) {
            task.cancel();
        }
    }
    
    private void processPrompt(String promptKey, String cacheKey, Prompt newPrompt) {
        Prompt oldPrompt = promptCache.get(cacheKey);
        if (newPrompt == null) {
            promptCache.remove(cacheKey);
        } else {
            promptCache.put(cacheKey, newPrompt);
        }
        if (isPromptChanged(oldPrompt, newPrompt)) {
            NotifyCenter.publishEvent(new PromptChangedEvent(promptKey, cacheKey, newPrompt));
        }
    }
    
    private boolean isPromptChanged(Prompt oldPrompt, Prompt newPrompt) {
        String oldJson = oldPrompt == null ? StringUtils.EMPTY : JacksonUtils.toJson(oldPrompt);
        String newJson = newPrompt == null ? StringUtils.EMPTY : JacksonUtils.toJson(newPrompt);
        return !StringUtils.equals(oldJson, newJson);
    }
    
    private class PromptUpdater implements Runnable {
        
        private final String promptKey;
        
        private final String version;
        
        private final String label;
        
        private final String cacheKey;
        
        private final AtomicBoolean cancel = new AtomicBoolean(false);
        
        PromptUpdater(String promptKey, String version, String label) {
            this.promptKey = promptKey;
            this.version = version;
            this.label = label;
            this.cacheKey = CacheKeyUtils.buildPromptKey(promptKey, version, label);
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
                Prompt currentPrompt = promptCache.get(cacheKey);
                String currentMd5 = currentPrompt == null ? null : currentPrompt.getMd5();
                Prompt latestPrompt = queryPrompt(promptKey, version, label, currentMd5);
                processPrompt(promptKey, cacheKey, latestPrompt);
            } catch (NacosException e) {
                if (e.getErrCode() == NacosException.NOT_FOUND) {
                    processPrompt(promptKey, cacheKey, null);
                } else if (e.getErrCode() == NacosException.NOT_MODIFIED) {
                    // No content change, keep local cache and skip callback.
                } else {
                    LOGGER.warn("Prompt updater execute query failed: promptKey={}, err={}",
                        promptKey, e.getErrMsg());
                }
            } finally {
                if (!cancel.get()) {
                    updaterExecutor.schedule(this, updateIntervalMillis, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
