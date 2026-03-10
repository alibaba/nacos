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
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.concurrent.Executor;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos AI module skill cache holder.
 *
 * @author nacos
 */
public class NacosSkillCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(NacosSkillCacheHolder.class);
    
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
     * Subscribe skill and start listening for configuration changes.
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
        
        // Check if already subscribed
        if (subscriptionMap.containsKey(skillName)) {
            return skillCache.get(skillName);
        }
        
        // Load skill initially
        Skill skill = loadSkill(skillName);
        
        // Create subscription info
        SkillSubscriptionInfo subscriptionInfo = new SkillSubscriptionInfo(skillName);
        subscriptionInfo.setCurrentSkill(skill);
        
        // Build main config info
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        
        // Add listener for main config
        Listener mainConfigListener = new SkillConfigListener(skillName, true, null);
        try {
            configService.addListener(mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), mainConfigListener);
            subscriptionInfo.setMainConfigListener(mainConfigListener);
        } catch (NacosException e) {
            LOGGER.warn("Failed to add listener for main config: skillName={}, error={}", skillName, e.getMessage());
        }
        
        // Add listeners for all resource configs
        if (skill != null && skill.getResource() != null && !skill.getResource().isEmpty()) {
            // Load main config to get resourceId mapping
            SkillMainConfig mainConfig = loadMainConfig(skillName, mainConfigInfo.getGroup());
            if (mainConfig != null && mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
                for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                    // Generate resourceId from type and name
                    String resourceId = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                    
                    // Build resource config info using resourceRef
                    SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                            skillName, resourceRef.getType(), resourceRef.getName());
                    Listener resourceListener = new SkillConfigListener(skillName, false, resourceId);
                    try {
                        configService.addListener(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), resourceListener);
                        subscriptionInfo.getResourceListeners().put(resourceId, resourceListener);
                    } catch (NacosException e) {
                        LOGGER.warn("Failed to add listener for resource config: skillName={}, resourceId={}, error={}",
                                skillName, resourceId, e.getMessage());
                    }
                }
            }
        }
        
        // Cache skill and subscription info
        if (skill != null) {
            skillCache.put(skillName, skill);
        }
        subscriptionMap.put(skillName, subscriptionInfo);
        
        LOGGER.info("Subscribed skill: {}", skillName);
        return skill;
    }
    
    /**
     * Unsubscribe skill and remove all listeners.
     *
     * @param skillName name of skill
     */
    public void unsubscribeSkill(String skillName) {
        if (StringUtils.isBlank(skillName)) {
            return;
        }
        
        SkillSubscriptionInfo subscriptionInfo = subscriptionMap.remove(skillName);
        if (subscriptionInfo == null) {
            return;
        }
        
        // Build main config info
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        
        // Remove main config listener
        if (subscriptionInfo.getMainConfigListener() != null) {
            try {
                configService.removeListener(mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), 
                        subscriptionInfo.getMainConfigListener());
            } catch (Exception e) {
                LOGGER.warn("Failed to remove listener for main config: skillName={}, error={}", skillName, e.getMessage());
            }
        }
        
        // Remove all resource config listeners
        // Note: We need to load mainConfig to get resourceRef info for building ConfigInfo
        SkillMainConfig mainConfig = loadMainConfig(skillName, mainConfigInfo.getGroup());
        if (mainConfig != null && mainConfig.getResources() != null) {
            Map<String, SkillResourceRef> resourceRefMap = new HashMap<>(
                    mainConfig.getResources().size() > 0 ? (int) (mainConfig.getResources().size() / 0.75f + 1) : 16);
            for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                String resourceId = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                resourceRefMap.put(resourceId, resourceRef);
            }
            for (Map.Entry<String, Listener> entry : subscriptionInfo.getResourceListeners().entrySet()) {
                String resourceId = entry.getKey();
                Listener listener = entry.getValue();
                SkillResourceRef resourceRef = resourceRefMap.get(resourceId);
                if (resourceRef != null) {
                    SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                            skillName, resourceRef.getType(), resourceRef.getName());
                    try {
                        configService.removeListener(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), listener);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to remove listener for resource config: skillName={}, resourceId={}, error={}",
                                skillName, resourceId, e.getMessage());
                    }
                }
            }
        }
        
        // Clear cache
        skillCache.remove(skillName);
        
        LOGGER.info("Unsubscribed skill: {}", skillName);
    }
    
    /**
     * Load skill from configuration.
     *
     * @param skillName name of skill
     * @return Skill object, nullable if skill not found
     */
    private Skill loadSkill(String skillName) {
        // Build main config info
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        
        // Load main config
        SkillMainConfig mainConfig = loadMainConfig(skillName, mainConfigInfo.getGroup());
        if (mainConfig == null) {
            return null;
        }
        
        // Build Skill object
        Skill skill = new Skill();
        skill.setNamespaceId(this.namespaceId);
        skill.setName(mainConfig.getName());
        skill.setDescription(mainConfig.getDescription());
        skill.setInstruction(mainConfig.getInstruction());
        
        // Query all Resource configs
        Map<String, SkillResource> resourceMap = new HashMap<>(
                mainConfig.getResources() != null ? mainConfig.getResources().size() : 16);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                // Generate resourceId from type and name
                String resourceId = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                
                // Query resource config using resourceRef info
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                String resourceContent;
                try {
                    resourceContent = configService.getConfig(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), 3000);
                } catch (NacosException e) {
                    LOGGER.warn("Resource configuration not found: dataId={}, group={}, error={}",
                            resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), e.getMessage());
                    continue;
                }
                
                if (StringUtils.isNotBlank(resourceContent)) {
                    try {
                        SkillResource resource = JacksonUtils.toObj(resourceContent, SkillResource.class);
                        // Use resource name as key (from resource object, not resourceId)
                        resourceMap.put(resource.getName() != null ? resource.getName() : resourceId, resource);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse resource configuration: dataId={}, group={}, error={}",
                                resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), e.getMessage());
                    }
                }
            }
        }
        skill.setResource(resourceMap);
        
        return skill;
    }
    
    /**
     * Load main config from configuration.
     *
     * @param skillName name of skill
     * @param skillGroup group of skill
     * @return SkillMainConfig object, nullable if not found
     */
    private SkillMainConfig loadMainConfig(String skillName, String skillGroup) {
        // Query main config (skill.json)
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        String mainConfigContent;
        try {
            mainConfigContent = configService.getConfig(mainConfigInfo.getDataId(), skillGroup, 3000);
        } catch (NacosException e) {
            LOGGER.warn("Skill main configuration not found: skillName={}, error={}", skillName, e.getMessage());
            return null;
        }
        
        if (StringUtils.isBlank(mainConfigContent)) {
            LOGGER.warn("Skill main configuration is blank: skillName={}", skillName);
            return null;
        }
        
        // Parse main config
        try {
            return JacksonUtils.toObj(mainConfigContent, SkillMainConfig.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse skill main configuration: skillName={}, error={}", skillName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Reload skill and check for changes.
     *
     * @param skillName name of skill
     */
    private void reloadSkill(String skillName) {
        try {
            SkillSubscriptionInfo subscriptionInfo = subscriptionMap.get(skillName);
            if (subscriptionInfo == null) {
                return;
            }
            
            Skill oldSkill = subscriptionInfo.getCurrentSkill();
            Skill newSkill = loadSkill(skillName);
            
            if (isSkillChanged(oldSkill, newSkill)) {
                LOGGER.info("Skill {} changed.", skillName);
                subscriptionInfo.setCurrentSkill(newSkill);
                if (newSkill != null) {
                    skillCache.put(skillName, newSkill);
                } else {
                    skillCache.remove(skillName);
                }
                
                // Update resource listeners if resource list changed
                updateResourceListeners(skillName, oldSkill, newSkill);
                
                // Publish change event
                NotifyCenter.publishEvent(new SkillChangedEvent(skillName, newSkill));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reload skill: skillName={}, error={}", skillName, e.getMessage(), e);
        }
    }
    
    /**
     * Update resource listeners based on resource list changes.
     *
     * @param skillName name of skill
     * @param oldSkill  old skill object
     * @param newSkill  new skill object
     */
    private void updateResourceListeners(String skillName, Skill oldSkill, Skill newSkill) {
        SkillSubscriptionInfo subscriptionInfo = subscriptionMap.get(skillName);
        if (subscriptionInfo == null) {
            return;
        }
        
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        
        // Load main configs to get resourceId mappings
        SkillMainConfig oldMainConfig = oldSkill != null ? loadMainConfig(skillName, mainConfigInfo.getGroup()) : null;
        SkillMainConfig newMainConfig = newSkill != null ? loadMainConfig(skillName, mainConfigInfo.getGroup()) : null;
        
        int oldResourceSize = oldMainConfig != null && oldMainConfig.getResources() != null 
                ? oldMainConfig.getResources().size() : 0;
        int newResourceSize = newMainConfig != null && newMainConfig.getResources() != null 
                ? newMainConfig.getResources().size() : 0;
        
        Set<String> oldResourceIds = new HashSet<>(oldResourceSize > 0 ? (int) (oldResourceSize / 0.75f + 1) : 16);
        Map<String, SkillResourceRef> oldResourceRefMap = new HashMap<>(
                oldResourceSize > 0 ? (int) (oldResourceSize / 0.75f + 1) : 16);
        if (oldMainConfig != null && oldMainConfig.getResources() != null) {
            for (SkillResourceRef ref : oldMainConfig.getResources()) {
                String resourceId = SkillUtils.generateResourceId(ref.getType(), ref.getName());
                oldResourceIds.add(resourceId);
                oldResourceRefMap.put(resourceId, ref);
            }
        }
        Set<String> newResourceIds = new HashSet<>(newResourceSize > 0 ? (int) (newResourceSize / 0.75f + 1) : 16);
        Map<String, SkillResourceRef> newResourceRefMap = new HashMap<>(
                newResourceSize > 0 ? (int) (newResourceSize / 0.75f + 1) : 16);
        if (newMainConfig != null && newMainConfig.getResources() != null) {
            for (SkillResourceRef ref : newMainConfig.getResources()) {
                String resourceId = SkillUtils.generateResourceId(ref.getType(), ref.getName());
                newResourceIds.add(resourceId);
                newResourceRefMap.put(resourceId, ref);
            }
        }
        
        // Remove listeners for deleted resources
        Set<String> toRemove = new HashSet<>(oldResourceIds);
        toRemove.removeAll(newResourceIds);
        for (String resourceId : toRemove) {
            Listener listener = subscriptionInfo.getResourceListeners().remove(resourceId);
            if (listener != null) {
                SkillResourceRef resourceRef = oldResourceRefMap.get(resourceId);
                if (resourceRef != null) {
                    SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                            skillName, resourceRef.getType(), resourceRef.getName());
                    try {
                        configService.removeListener(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), listener);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to remove listener for deleted resource: skillName={}, resourceId={}, error={}",
                                skillName, resourceId, e.getMessage());
                    }
                }
            }
        }
        
        // Add listeners for new resources
        Set<String> toAdd = new HashSet<>(newResourceIds);
        toAdd.removeAll(oldResourceIds);
        for (String resourceId : toAdd) {
            SkillResourceRef resourceRef = newResourceRefMap.get(resourceId);
            if (resourceRef != null) {
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                Listener resourceListener = new SkillConfigListener(skillName, false, resourceId);
                try {
                    configService.addListener(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), resourceListener);
                    subscriptionInfo.getResourceListeners().put(resourceId, resourceListener);
                } catch (NacosException e) {
                    LOGGER.warn("Failed to add listener for new resource: skillName={}, resourceId={}, error={}",
                            skillName, resourceId, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Check if skill has changed by comparing JSON serialization.
     *
     * @param oldSkill old skill object
     * @param newSkill new skill object
     * @return true if changed, false otherwise
     */
    private boolean isSkillChanged(Skill oldSkill, Skill newSkill) {
        try {
            String newJson = objectMapper.writeValueAsString(newSkill);
            if (null == oldSkill) {
                LOGGER.info("init new skill: {} -> {}", newSkill != null ? newSkill.getName() : "null", newJson);
                return true;
            }
            String oldJson = objectMapper.writeValueAsString(oldSkill);
            if (!StringUtils.equals(oldJson, newJson)) {
                LOGGER.info("skill changed: {} -> {}", oldJson, newJson);
                return true;
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Compare skill info failed: ", e);
        }
        return false;
    }
    
    @Override
    public void shutdown() throws NacosException {
        // Unsubscribe all skills
        Set<String> skillNames = new HashSet<>(subscriptionMap.keySet());
        for (String skillName : skillNames) {
            unsubscribeSkill(skillName);
        }
    }
    
    /**
     * Skill configuration listener.
     */
    private class SkillConfigListener implements Listener {
        
        private final String skillName;
        
        private final boolean isMainConfig;
        
        private final String resourceName;
        
        public SkillConfigListener(String skillName, boolean isMainConfig, String resourceName) {
            this.skillName = skillName;
            this.isMainConfig = isMainConfig;
            this.resourceName = resourceName;
        }
        
        @Override
        public Executor getExecutor() {
            return null;
        }
        
        @Override
        public void receiveConfigInfo(String configInfo) {
            LOGGER.info("Skill configuration changed: skillName={}, isMainConfig={}, resourceName={}",
                    skillName, isMainConfig, resourceName);
            // Reload skill when any configuration changes
            reloadSkill(skillName);
        }
    }
    
    /**
     * Skill subscription information.
     */
    private static class SkillSubscriptionInfo {
        
        private final String skillName;
        
        private Listener mainConfigListener;
        
        private final Map<String, Listener> resourceListeners;
        
        private Skill currentSkill;
        
        public SkillSubscriptionInfo(String skillName) {
            this.skillName = skillName;
            this.resourceListeners = new ConcurrentHashMap<>();
        }
        
        public String getSkillName() {
            return skillName;
        }
        
        public Listener getMainConfigListener() {
            return mainConfigListener;
        }
        
        public void setMainConfigListener(Listener mainConfigListener) {
            this.mainConfigListener = mainConfigListener;
        }
        
        public Map<String, Listener> getResourceListeners() {
            return resourceListeners;
        }
        
        public Skill getCurrentSkill() {
            return currentSkill;
        }
        
        public void setCurrentSkill(Skill currentSkill) {
            this.currentSkill = currentSkill;
        }
    }
    
    /**
     * Skill main config (from skill.json).
     */
    private static class SkillMainConfig {
        private String name;
        private String description;
        private String instruction;
        private List<SkillResourceRef> resources;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getInstruction() {
            return instruction;
        }
        
        public void setInstruction(String instruction) {
            this.instruction = instruction;
        }
        
        public List<SkillResourceRef> getResources() {
            return resources;
        }
        
        public void setResources(List<SkillResourceRef> resources) {
            this.resources = resources;
        }
    }
    
    /**
     * Skill resource reference (in skill.json).
     */
    private static class SkillResourceRef {
        private String name;
        private String type;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
}
