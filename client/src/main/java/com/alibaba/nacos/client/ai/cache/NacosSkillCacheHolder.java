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

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.SkillChangedEvent;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Nacos AI module skill cache holder.
 *
 * <p>Reads skill data from Nacos Config via {@link ConfigService}, leveraging its local cache
 * and push notifications for real-time updates. The server writes a manifest config
 * ({@code skill_index.json}) at group {@code skill_{name}} containing the current online
 * version and file list. Each resource file is stored under group {@code skill_{name}__{version}}
 * with the file path as dataId.</p>
 *
 * @author nacos
 */
public class NacosSkillCacheHolder implements Closeable {

    private static final Logger LOGGER = LogUtils.logger(NacosSkillCacheHolder.class);

    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";

    private static final long CONFIG_TIMEOUT = 3000L;

    private final ConfigService configService;

    private final String namespaceId;

    private final Map<String, Skill> skillCache;

    private final Map<String, SkillSubscriptionInfo> subscriptionMap;

    private final ObjectMapper objectMapper;

    public NacosSkillCacheHolder(ConfigService configService, String namespaceId) {
        this.configService = configService;
        this.namespaceId = namespaceId;
        this.skillCache = new ConcurrentHashMap<>(4);
        this.subscriptionMap = new ConcurrentHashMap<>(4);
        this.objectMapper = JsonMapper.builder().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Load skill from Nacos Config (leverages ConfigService local cache).
     *
     * @param skillName name of skill
     * @return Skill object, null if skill not found or manifest missing
     * @throws NacosException if error occurs
     */
    public Skill querySkill(String skillName) throws NacosException {
        return loadSkillFromConfig(skillName);
    }

    /**
     * Subscribe to skill changes via Nacos Config listeners.
     *
     * @param skillName name of skill
     * @return current Skill object, nullable if skill not found
     * @throws NacosException if error occurs
     */
    public Skill subscribeSkill(String skillName) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "Required parameter `skillName` not present");
        }

        if (subscriptionMap.containsKey(skillName)) {
            return skillCache.get(skillName);
        }

        // Initial load
        Skill skill = loadSkillFromConfig(skillName);
        if (skill != null) {
            skillCache.put(skillName, skill);
        }

        // Set up subscription
        SkillSubscriptionInfo sub = new SkillSubscriptionInfo(skillName);
        subscriptionMap.put(skillName, sub);

        SkillIndex index = loadSkillIndex(skillName);
        if (index != null && index.files != null) {
            sub.currentVersion = index.version;
            sub.currentFiles = index.files;
            subscribeResources(sub, index);
        }

        // Listen to manifest for version changes
        Listener manifestListener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                onManifestChanged(skillName, configInfo);
            }
        };
        sub.manifestListener = manifestListener;
        configService.addListener(SkillUtils.SKILL_INDEX_DATA_ID,
                SkillUtils.buildSkillGroup(skillName), manifestListener);

        LOGGER.info("Subscribed skill via config: {}", skillName);
        return skill;
    }

    /**
     * Unsubscribe from skill changes.
     *
     * @param skillName name of skill
     */
    public void unsubscribeSkill(String skillName) {
        if (StringUtils.isBlank(skillName)) {
            return;
        }

        SkillSubscriptionInfo sub = subscriptionMap.remove(skillName);
        if (sub != null) {
            if (sub.manifestListener != null) {
                configService.removeListener(SkillUtils.SKILL_INDEX_DATA_ID,
                        SkillUtils.buildSkillGroup(skillName), sub.manifestListener);
            }
            unsubscribeResources(sub);
        }
        skillCache.remove(skillName);

        LOGGER.info("Unsubscribed skill: {}", skillName);
    }

    @Override
    public void shutdown() throws NacosException {
        for (String skillName : new java.util.HashSet<>(subscriptionMap.keySet())) {
            unsubscribeSkill(skillName);
        }
    }

    // ======================== Private methods ========================

    private void onManifestChanged(String skillName, String configInfo) {
        try {
            SkillSubscriptionInfo sub = subscriptionMap.get(skillName);
            if (sub == null) {
                return;
            }

            SkillIndex newIndex = parseSkillIndex(configInfo);
            String newVersion = newIndex != null ? newIndex.version : null;

            if (!StringUtils.equals(sub.currentVersion, newVersion)) {
                LOGGER.info("Skill {} manifest version changed: {} -> {}", skillName,
                        sub.currentVersion, newVersion);
                unsubscribeResources(sub);
                if (newIndex != null && newIndex.files != null) {
                    sub.currentVersion = newIndex.version;
                    sub.currentFiles = newIndex.files;
                    subscribeResources(sub, newIndex);
                } else {
                    sub.currentVersion = null;
                    sub.currentFiles = null;
                }
            }

            reloadAndPublish(skillName);
        } catch (Exception e) {
            LOGGER.error("Failed to handle manifest change for skill: {}", skillName, e);
        }
    }

    private void onResourceChanged(String skillName) {
        reloadAndPublish(skillName);
    }

    private void reloadAndPublish(String skillName) {
        try {
            Skill oldSkill = skillCache.get(skillName);
            Skill newSkill = loadSkillFromConfig(skillName);

            if (isSkillChanged(oldSkill, newSkill)) {
                LOGGER.info("Skill {} changed, publishing event.", skillName);
                if (newSkill != null) {
                    skillCache.put(skillName, newSkill);
                } else {
                    skillCache.remove(skillName);
                }
                NotifyCenter.publishEvent(new SkillChangedEvent(skillName, newSkill));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reload skill: {}", skillName, e);
        }
    }

    private Skill loadSkillFromConfig(String skillName) throws NacosException {
        SkillIndex index = loadSkillIndex(skillName);
        if (index == null || StringUtils.isBlank(index.version) || index.files == null || index.files.isEmpty()) {
            return null;
        }

        String versionGroup = SkillUtils.buildSkillVersionGroup(skillName, index.version);
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        Map<String, SkillResource> resourceMap = new HashMap<>(index.files.size());

        for (String filePath : index.files) {
            String content = configService.getConfig(filePath, versionGroup, CONFIG_TIMEOUT);
            if (StringUtils.isBlank(content)) {
                continue;
            }
            SkillResource resource = JacksonUtils.toObj(content, SkillResource.class);
            if (resource == null) {
                continue;
            }

            if (SKILL_MD_RESOURCE_NAME.equals(resource.getName())) {
                Map<String, Object> metadata = resource.getMetadata();
                if (metadata != null) {
                    skill.setName((String) metadata.get("name"));
                    skill.setDescription((String) metadata.get("description"));
                    skill.setInstruction((String) metadata.get("instruction"));
                }
            } else {
                String resourceId = SkillUtils.generateResourceId(resource.getType(), resource.getName());
                resourceMap.put(resourceId, resource);
            }
        }

        skill.setResource(resourceMap);
        return skill;
    }

    private SkillIndex loadSkillIndex(String skillName) throws NacosException {
        String group = SkillUtils.buildSkillGroup(skillName);
        String indexContent = configService.getConfig(SkillUtils.SKILL_INDEX_DATA_ID, group, CONFIG_TIMEOUT);
        return parseSkillIndex(indexContent);
    }

    private static SkillIndex parseSkillIndex(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, SkillIndex.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse skill index: {}", e.getMessage());
            return null;
        }
    }

    private void subscribeResources(SkillSubscriptionInfo sub, SkillIndex index) {
        if (index.files == null || index.files.isEmpty() || StringUtils.isBlank(index.version)) {
            return;
        }
        String versionGroup = SkillUtils.buildSkillVersionGroup(sub.skillName, index.version);
        sub.resourceGroup = versionGroup;
        for (String filePath : index.files) {
            Listener listener = new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    onResourceChanged(sub.skillName);
                }
            };
            try {
                configService.addListener(filePath, versionGroup, listener);
                sub.resourceListeners.put(filePath, listener);
            } catch (NacosException e) {
                LOGGER.warn("Failed to add listener for {}:{}", versionGroup, filePath, e);
            }
        }
    }

    private void unsubscribeResources(SkillSubscriptionInfo sub) {
        if (StringUtils.isBlank(sub.resourceGroup)) {
            return;
        }
        for (Map.Entry<String, Listener> entry : sub.resourceListeners.entrySet()) {
            configService.removeListener(entry.getKey(), sub.resourceGroup, entry.getValue());
        }
        sub.resourceListeners.clear();
        sub.resourceGroup = null;
    }

    private boolean isSkillChanged(Skill oldSkill, Skill newSkill) {
        try {
            String newJson = objectMapper.writeValueAsString(newSkill);
            if (null == oldSkill) {
                LOGGER.info("Init new skill: {} -> {}", newSkill != null ? newSkill.getName() : "null", newJson);
                return true;
            }
            String oldJson = objectMapper.writeValueAsString(oldSkill);
            if (!StringUtils.equals(oldJson, newJson)) {
                LOGGER.info("Skill changed: {} -> {}", oldJson, newJson);
                return true;
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Compare skill info failed: ", e);
        }
        return false;
    }

    // ======================== Inner classes ========================

    private static class SkillSubscriptionInfo {

        final String skillName;

        String currentVersion;

        List<String> currentFiles;

        Listener manifestListener;

        String resourceGroup;

        final Map<String, Listener> resourceListeners = new ConcurrentHashMap<>(4);

        SkillSubscriptionInfo(String skillName) {
            this.skillName = skillName;
        }
    }

    private static class SkillIndex {

        private String version;

        private List<String> files;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getFiles() {
            return files;
        }

        public void setFiles(List<String> files) {
            this.files = files;
        }
    }
}
