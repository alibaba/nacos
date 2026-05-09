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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.event.AgentSpecChangedEvent;
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
 * Nacos AI module agent spec cache holder.
 *
 * <p>Reads agent spec data from Nacos Config via {@link ConfigService}, leveraging its local cache
 * and push notifications for real-time updates. The server writes a manifest config
 * ({@code agentspec_index.json}) at group {@code agentspec__{name}} containing the current online
 * version and file list. Each resource file is stored under group {@code agentspec__{name}__{version}}
 * with the file path as dataId.</p>
 *
 * @author nacos
 */
public class NacosAgentSpecCacheHolder implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(NacosAgentSpecCacheHolder.class);
    
    private static final String MANIFEST_JSON_RESOURCE_NAME = "manifest.json";
    
    private static final long CONFIG_TIMEOUT = 3000L;
    
    private final ConfigService configService;
    
    private final String namespaceId;
    
    private final Map<String, AgentSpec> agentSpecCache;
    
    private final Map<String, AgentSpecSubscriptionInfo> subscriptionMap;
    
    private final ObjectMapper objectMapper;
    
    public NacosAgentSpecCacheHolder(ConfigService configService, String namespaceId) {
        this.configService = configService;
        this.namespaceId = namespaceId;
        this.agentSpecCache = new ConcurrentHashMap<>(4);
        this.subscriptionMap = new ConcurrentHashMap<>(4);
        this.objectMapper =
            JsonMapper.builder().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    /**
     * Load agent spec from Nacos Config (leverages ConfigService local cache).
     *
     * @param agentSpecName name of agent spec
     * @return AgentSpec object, null if agent spec not found or manifest missing
     * @throws NacosException if error occurs
     */
    public AgentSpec queryAgentSpec(String agentSpecName) throws NacosException {
        return loadAgentSpecFromConfig(agentSpecName);
    }
    
    /**
     * Subscribe to agent spec changes via Nacos Config listeners.
     *
     * @param agentSpecName name of agent spec
     * @return current AgentSpec object, nullable if agent spec not found
     * @throws NacosException if error occurs
     */
    public AgentSpec subscribeAgentSpec(String agentSpecName) throws NacosException {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Required parameter `agentSpecName` not present");
        }
        
        if (subscriptionMap.containsKey(agentSpecName)) {
            return agentSpecCache.get(agentSpecName);
        }
        
        // Initial load
        AgentSpec agentSpec = loadAgentSpecFromConfig(agentSpecName);
        if (agentSpec != null) {
            agentSpecCache.put(agentSpecName, agentSpec);
        }
        
        // Set up subscription
        AgentSpecSubscriptionInfo sub = new AgentSpecSubscriptionInfo(agentSpecName);
        subscriptionMap.put(agentSpecName, sub);
        
        AgentSpecIndex index = loadAgentSpecIndex(agentSpecName);
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
                onManifestChanged(agentSpecName, configInfo);
            }
        };
        sub.manifestListener = manifestListener;
        configService.addListener(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID,
            AgentSpecUtils.buildAgentSpecGroup(agentSpecName), manifestListener);
        
        LOGGER.info("Subscribed agent spec via config: {}", agentSpecName);
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
        
        AgentSpecSubscriptionInfo sub = subscriptionMap.remove(agentSpecName);
        if (sub != null) {
            if (sub.manifestListener != null) {
                configService.removeListener(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID,
                    AgentSpecUtils.buildAgentSpecGroup(agentSpecName), sub.manifestListener);
            }
            unsubscribeResources(sub);
        }
        agentSpecCache.remove(agentSpecName);
        
        LOGGER.info("Unsubscribed agent spec: {}", agentSpecName);
    }
    
    @Override
    public void shutdown() throws NacosException {
        for (String agentSpecName : new java.util.HashSet<>(subscriptionMap.keySet())) {
            unsubscribeAgentSpec(agentSpecName);
        }
    }
    
    // ======================== Private methods ========================
    
    private void onManifestChanged(String agentSpecName, String configInfo) {
        try {
            AgentSpecSubscriptionInfo sub = subscriptionMap.get(agentSpecName);
            if (sub == null) {
                return;
            }
            
            AgentSpecIndex newIndex = parseAgentSpecIndex(configInfo);
            String newVersion = newIndex != null ? newIndex.version : null;
            
            if (!StringUtils.equals(sub.currentVersion, newVersion)) {
                LOGGER.info("AgentSpec {} manifest version changed: {} -> {}", agentSpecName,
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
            
            reloadAndPublish(agentSpecName);
        } catch (Exception e) {
            LOGGER.error("Failed to handle manifest change for agent spec: {}", agentSpecName, e);
        }
    }
    
    private void onResourceChanged(String agentSpecName) {
        reloadAndPublish(agentSpecName);
    }
    
    private void reloadAndPublish(String agentSpecName) {
        try {
            AgentSpec oldAgentSpec = agentSpecCache.get(agentSpecName);
            AgentSpec newAgentSpec = loadAgentSpecFromConfig(agentSpecName);
            
            if (isAgentSpecChanged(oldAgentSpec, newAgentSpec)) {
                LOGGER.info("AgentSpec {} changed, publishing event.", agentSpecName);
                if (newAgentSpec != null) {
                    agentSpecCache.put(agentSpecName, newAgentSpec);
                } else {
                    agentSpecCache.remove(agentSpecName);
                }
                NotifyCenter.publishEvent(new AgentSpecChangedEvent(agentSpecName, newAgentSpec));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reload agent spec: {}", agentSpecName, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private AgentSpec loadAgentSpecFromConfig(String agentSpecName) throws NacosException {
        AgentSpecIndex index = loadAgentSpecIndex(agentSpecName);
        if (index == null || StringUtils.isBlank(index.version) || index.files == null
            || index.files.isEmpty()) {
            return null;
        }
        
        String versionGroup =
            AgentSpecUtils.buildAgentSpecVersionGroup(agentSpecName, index.version);
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId(namespaceId);
        Map<String, AgentSpecResource> resourceMap = new HashMap<>(index.files.size());
        
        for (String filePath : index.files) {
            String content = configService.getConfig(filePath, versionGroup, CONFIG_TIMEOUT);
            if (StringUtils.isBlank(content)) {
                continue;
            }
            AgentSpecResource resource = JacksonUtils.toObj(content, AgentSpecResource.class);
            if (resource == null) {
                continue;
            }
            
            if (MANIFEST_JSON_RESOURCE_NAME.equals(resource.getName())) {
                // Extract name and description from manifest.json content
                String manifestContent = resource.getContent();
                if (StringUtils.isNotBlank(manifestContent)) {
                    try {
                        Map<String, Object> manifestMap =
                            JacksonUtils.toObj(manifestContent, Map.class);
                        if (manifestMap != null) {
                            Object nameObj = manifestMap.get("name");
                            if (nameObj != null) {
                                agentSpec.setName(String.valueOf(nameObj));
                            }
                            Object descObj = manifestMap.get("description");
                            if (descObj != null) {
                                agentSpec.setDescription(String.valueOf(descObj));
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse manifest.json content for agent spec: {}",
                            agentSpecName, e);
                    }
                }
                agentSpec.setContent(manifestContent);
            } else {
                String resourceId =
                    AgentSpecUtils.generateResourceId(resource.getType(), resource.getName());
                resourceMap.put(resourceId, resource);
            }
        }
        
        agentSpec.setResource(resourceMap);
        return agentSpec;
    }
    
    private AgentSpecIndex loadAgentSpecIndex(String agentSpecName) throws NacosException {
        String group = AgentSpecUtils.buildAgentSpecGroup(agentSpecName);
        String indexContent = configService.getConfig(AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID, group,
            CONFIG_TIMEOUT);
        return parseAgentSpecIndex(indexContent);
    }
    
    private static AgentSpecIndex parseAgentSpecIndex(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, AgentSpecIndex.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse agent spec index: {}", e.getMessage());
            return null;
        }
    }
    
    private void subscribeResources(AgentSpecSubscriptionInfo sub, AgentSpecIndex index) {
        if (index.files == null || index.files.isEmpty() || StringUtils.isBlank(index.version)) {
            return;
        }
        String versionGroup =
            AgentSpecUtils.buildAgentSpecVersionGroup(sub.agentSpecName, index.version);
        sub.resourceGroup = versionGroup;
        for (String filePath : index.files) {
            Listener listener = new Listener() {
                
                @Override
                public Executor getExecutor() {
                    return null;
                }
                
                @Override
                public void receiveConfigInfo(String configInfo) {
                    onResourceChanged(sub.agentSpecName);
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
    
    private void unsubscribeResources(AgentSpecSubscriptionInfo sub) {
        if (StringUtils.isBlank(sub.resourceGroup)) {
            return;
        }
        for (Map.Entry<String, Listener> entry : sub.resourceListeners.entrySet()) {
            configService.removeListener(entry.getKey(), sub.resourceGroup, entry.getValue());
        }
        sub.resourceListeners.clear();
        sub.resourceGroup = null;
    }
    
    private boolean isAgentSpecChanged(AgentSpec oldAgentSpec, AgentSpec newAgentSpec) {
        try {
            String newJson = objectMapper.writeValueAsString(newAgentSpec);
            if (null == oldAgentSpec) {
                LOGGER.info("Init new agent spec: {} -> {}",
                    newAgentSpec != null ? newAgentSpec.getName() : "null", newJson);
                return true;
            }
            String oldJson = objectMapper.writeValueAsString(oldAgentSpec);
            if (!StringUtils.equals(oldJson, newJson)) {
                LOGGER.info("AgentSpec changed: {} -> {}", oldJson, newJson);
                return true;
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Compare agent spec info failed: ", e);
        }
        return false;
    }
    
    // ======================== Inner classes ========================
    
    private static class AgentSpecSubscriptionInfo {
        
        final String agentSpecName;
        
        String currentVersion;
        
        List<String> currentFiles;
        
        Listener manifestListener;
        
        String resourceGroup;
        
        final Map<String, Listener> resourceListeners = new ConcurrentHashMap<>(4);
        
        AgentSpecSubscriptionInfo(String agentSpecName) {
            this.agentSpecName = agentSpecName;
        }
    }
    
    private static class AgentSpecIndex {
        
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
