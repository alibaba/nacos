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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads canonical AgentSpec version packages from their persisted storage provider.
 *
 * @author Nacos
 */
@Service
public class AgentSpecStorageReader {
    
    private static final String DEFAULT_PROVIDER = NacosConfigAiResourceStorage.TYPE;
    
    private final AiResourceStorageRouter storageRouter;
    
    public AgentSpecStorageReader() {
        this(AiResourceStorageRouter.getInstance());
    }
    
    AgentSpecStorageReader(AiResourceStorageRouter storageRouter) {
        this.storageRouter = storageRouter;
    }
    
    /**
     * Load one complete AgentSpec package without applying request visibility.
     *
     * @param namespaceId namespace identifier
     * @param agentSpecName AgentSpec name
     * @param version exact version
     * @param storageJson persisted storage descriptor
     * @return complete AgentSpec package
     * @throws NacosException when the canonical package is unavailable
     */
    public AgentSpec read(String namespaceId, String agentSpecName, String version,
        String storageJson) throws NacosException {
        AgentSpecMainConfig mainConfig = readMainConfig(namespaceId, agentSpecName, version,
            storageJson);
        AgentSpec result = toAgentSpec(namespaceId, mainConfig);
        Map<String, AgentSpecResource> resources = new HashMap<>(
            mainConfig.getResources() == null ? 16 : mainConfig.getResources().size());
        if (mainConfig.getResources() != null) {
            String provider = parseProvider(storageJson);
            for (AgentSpecResourceRef resourceRef : mainConfig.getResources()) {
                if (resourceRef == null) {
                    continue;
                }
                AgentSpecResource resource = readResource(provider, namespaceId, agentSpecName,
                    version, resourceRef);
                if (resource != null) {
                    resources.put(AgentSpecUtils.generateResourceId(resourceRef.getType(),
                        resourceRef.getName()), resource);
                }
            }
        }
        result.setResource(resources);
        return result;
    }
    
    /**
     * Load AgentSpec manifest metadata without reading referenced resource files.
     */
    public AgentSpec readMeta(String namespaceId, String agentSpecName, String version,
        String storageJson) throws NacosException {
        AgentSpecMainConfig mainConfig = readMainConfig(namespaceId, agentSpecName, version,
            storageJson);
        AgentSpec result = toAgentSpec(namespaceId, mainConfig);
        Map<String, AgentSpecResource> resources = new HashMap<>(
            mainConfig.getResources() == null ? 16 : mainConfig.getResources().size());
        if (mainConfig.getResources() != null) {
            for (AgentSpecResourceRef resourceRef : mainConfig.getResources()) {
                if (resourceRef == null) {
                    continue;
                }
                AgentSpecResource resource = new AgentSpecResource();
                resource.setName(resourceRef.getName());
                resource.setType(resourceRef.getType());
                resources.put(AgentSpecUtils.generateResourceId(resourceRef.getType(),
                    resourceRef.getName()), resource);
            }
        }
        result.setResource(resources);
        return result;
    }
    
    private AgentSpecMainConfig readMainConfig(String namespaceId, String agentSpecName,
        String version, String storageJson) throws NacosException {
        String provider = parseProvider(storageJson);
        StorageKey mainKey = buildKey(provider, namespaceId, agentSpecName, version,
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + agentSpecName);
        }
        return JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8),
            AgentSpecMainConfig.class);
    }
    
    private AgentSpecResource readResource(String provider, String namespaceId,
        String agentSpecName, String version, AgentSpecResourceRef resourceRef)
        throws NacosException {
        String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(
            resourceRef.getType(), resourceRef.getName());
        StorageKey resourceKey = buildKey(provider, namespaceId, agentSpecName, version, path);
        byte[] bytes = storageRouter.route(resourceKey).get(resourceKey);
        return bytes == null ? null : JacksonUtils.toObj(
            new String(bytes, StandardCharsets.UTF_8), AgentSpecResource.class);
    }
    
    private StorageKey buildKey(String provider, String namespaceId, String agentSpecName,
        String version, String path) {
        return NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version, path);
    }
    
    private AgentSpec toAgentSpec(String namespaceId, AgentSpecMainConfig mainConfig) {
        AgentSpec result = new AgentSpec();
        result.setNamespaceId(namespaceId);
        result.setName(mainConfig.getName());
        result.setDescription(mainConfig.getDescription());
        result.setContent(mainConfig.getContent());
        return result;
    }
    
    private String parseProvider(String storageJson) {
        if (StringUtils.isNotBlank(storageJson)) {
            try {
                Map<?, ?> storage = JacksonUtils.toObj(storageJson, Map.class);
                Object provider = storage == null ? null : storage.get("provider");
                if (provider instanceof String && StringUtils.isNotBlank((String) provider)) {
                    return ((String) provider).trim();
                }
            } catch (Exception ignored) {
                // Legacy descriptors use the Nacos Config provider.
            }
        }
        return DEFAULT_PROVIDER;
    }
    
    private static class AgentSpecMainConfig {
        
        private String name;
        
        private String description;
        
        private String content;
        
        private List<AgentSpecResourceRef> resources;
        
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
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public List<AgentSpecResourceRef> getResources() {
            return resources;
        }
        
        public void setResources(List<AgentSpecResourceRef> resources) {
            this.resources = resources;
        }
    }
    
    private static class AgentSpecResourceRef {
        
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
