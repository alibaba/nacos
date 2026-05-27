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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.SkillChangedEvent;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.remote.SkillQueryResponse;
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
 * Nacos AI module skill cache holder.
 *
 * <p>Owns the per-subscription polling loop that periodically calls
 * {@link AiClientProxy#querySkill(String, String, String, String)} with the locally cached MD5
 * for conditional download. When the server returns 304 ({@link NacosException#NOT_MODIFIED})
 * the local cache is preserved and no callback fires; when the response carries new content
 * (different MD5) a {@link SkillChangedEvent} is published so {@code AiChangeNotifier} can
 * dispatch it to all registered listeners.
 *
 * @author nacos
 */
public class NacosSkillCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(NacosSkillCacheHolder.class);
    
    private final AiClientProxy aiClientProxy;
    
    /**
     * cacheKey -> last published MD5 of the locally cached skill ZIP.
     */
    private final Map<String, String> skillMd5Cache;
    
    private final ScheduledExecutorService updaterExecutor;
    
    private final long updateIntervalMillis;
    
    private final Map<String, SkillUpdater> updateTaskMap;
    
    public NacosSkillCacheHolder(AiClientProxy aiClientProxy, NacosClientProperties properties) {
        this.aiClientProxy = aiClientProxy;
        this.skillMd5Cache = new ConcurrentHashMap<>(4);
        this.updateTaskMap = new ConcurrentHashMap<>(4);
        this.updaterExecutor = new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.skill.updater"));
        this.updateIntervalMillis = properties.getLong(AiConstants.AI_SKILL_CACHE_UPDATE_INTERVAL,
            AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL);
    }
    
    /**
     * Subscribe skill and start polling for skill changes.
     *
     * <p>Performs the initial download synchronously and primes the MD5 cache; subsequent polls
     * piggy-back the cached MD5 to short-circuit unchanged content.
     *
     * @param skillName skill name
     * @param version   skill version, optional
     * @param label     skill label, optional
     * @return current skill ZIP bytes, never null when the server has the skill
     * @throws NacosException if error occurs
     */
    public byte[] subscribeSkill(String skillName, String version, String label)
        throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Required parameter `skillName` not present");
        }
        String cacheKey = CacheKeyUtils.buildSkillKey(skillName, version, label);
        
        byte[] zipBytes = null;
        try {
            SkillQueryResponse response = aiClientProxy.querySkill(skillName, version, label, null);
            zipBytes = response.getZipBytes();
            // Only update cache during initial subscribe; do NOT publish event here.
            // The caller (NacosAiService) handles the first listener notification to avoid
            // duplicate callbacks racing with the async NotifyCenter dispatch.
            String newMd5 = response.getMd5();
            if (StringUtils.isNotBlank(newMd5)) {
                skillMd5Cache.put(cacheKey, newMd5);
            }
        } catch (NacosException e) {
            if (e.getErrCode() != NacosException.NOT_FOUND) {
                throw e;
            }
            skillMd5Cache.remove(cacheKey);
        }
        addSkillUpdateTask(skillName, version, label);
        LOGGER.info("Subscribed skill: {}, version: {}, label: {}", skillName, version, label);
        return zipBytes;
    }
    
    /**
     * Unsubscribe skill and remove update task.
     *
     * @param skillName skill name
     * @param version   skill version, optional
     * @param label     skill label, optional
     */
    public void unsubscribeSkill(String skillName, String version, String label) {
        if (StringUtils.isBlank(skillName)) {
            return;
        }
        String cacheKey = CacheKeyUtils.buildSkillKey(skillName, version, label);
        
        removeSkillUpdateTask(skillName, version, label);
        skillMd5Cache.remove(cacheKey);
        LOGGER.info("Unsubscribed skill: {}, version: {}, label: {}", skillName, version, label);
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.updaterExecutor.shutdownNow();
    }
    
    private void addSkillUpdateTask(String skillName, String version, String label) {
        String key = CacheKeyUtils.buildSkillKey(skillName, version, label);
        this.updateTaskMap.computeIfAbsent(key, s -> {
            SkillUpdater task = new SkillUpdater(skillName, version, label);
            updaterExecutor.schedule(task, updateIntervalMillis, TimeUnit.MILLISECONDS);
            return task;
        });
    }
    
    private void removeSkillUpdateTask(String skillName, String version, String label) {
        String key = CacheKeyUtils.buildSkillKey(skillName, version, label);
        SkillUpdater task = this.updateTaskMap.remove(key);
        if (task != null) {
            task.cancel();
        }
    }
    
    private void processSkill(String skillName, String cacheKey, SkillQueryResponse response) {
        String oldMd5 = skillMd5Cache.get(cacheKey);
        String newMd5 = response == null ? null : response.getMd5();
        if (response == null) {
            skillMd5Cache.remove(cacheKey);
        } else if (StringUtils.isNotBlank(newMd5)) {
            skillMd5Cache.put(cacheKey, newMd5);
        }
        if (response != null && !StringUtils.equals(oldMd5, newMd5)) {
            NotifyCenter.publishEvent(new SkillChangedEvent(skillName, cacheKey,
                response.getZipBytes(), newMd5, response.getResolvedVersion()));
        }
    }
    
    private class SkillUpdater implements Runnable {
        
        private final String skillName;
        
        private final String version;
        
        private final String label;
        
        private final String cacheKey;
        
        private final AtomicBoolean cancel = new AtomicBoolean(false);
        
        SkillUpdater(String skillName, String version, String label) {
            this.skillName = skillName;
            this.version = version;
            this.label = label;
            this.cacheKey = CacheKeyUtils.buildSkillKey(skillName, version, label);
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
                String currentMd5 = skillMd5Cache.get(cacheKey);
                SkillQueryResponse response = aiClientProxy.querySkill(skillName, version, label,
                    currentMd5);
                processSkill(skillName, cacheKey, response);
            } catch (NacosException e) {
                if (e.getErrCode() == NacosException.NOT_FOUND) {
                    processSkill(skillName, cacheKey, null);
                } else if (e.getErrCode() == NacosException.NOT_MODIFIED) {
                    // No content change, keep local cache and skip callback.
                } else {
                    LOGGER.warn("Skill updater execute query failed: skillName={}, err={}",
                        skillName, e.getErrMsg());
                }
            } finally {
                if (!cancel.get()) {
                    updaterExecutor.schedule(this, updateIntervalMillis, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
