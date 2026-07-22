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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolver for runtime persisted plugin configuration source.
 *
 * @author Nacos
 */
class RuntimePersistedPluginConfigSourceResolver extends AbstractMapPluginConfigSourceResolver
    implements PersistedPluginConfigSourceResolver {
    
    private final PluginStatePersistenceService persistence;
    
    private final PluginConfigKeyResolver keyResolver = new PluginConfigKeyResolver();
    
    RuntimePersistedPluginConfigSourceResolver() {
        this(null);
    }
    
    RuntimePersistedPluginConfigSourceResolver(PluginStatePersistenceService persistence) {
        this.persistence = persistence;
    }
    
    @Override
    public void initialize() {
        if (persistence != null) {
            replaceAllConfigs(persistence.loadAllConfigs());
        }
    }
    
    @Override
    public void initializeConfig(PluginInfo pluginInfo) {
        Map<String, String> config = super.getConfig(pluginInfo);
        if (config != null) {
            super.updateConfig(pluginInfo.getPluginId(), keyResolver.normalizeConfig(pluginInfo,
                config));
        }
    }
    
    @Override
    public void updateConfig(String pluginId, Map<String, String> config) {
        Map<String, String> configToStore = config == null ? Collections.emptyMap()
            : new HashMap<>(config);
        if (persistence != null) {
            persistence.saveConfig(pluginId, configToStore);
        }
        super.updateConfig(pluginId, configToStore);
    }
    
    @Override
    public Map<String, Map<String, String>> getAllConfigs() {
        return getAllConfigsSnapshot();
    }
    
    @Override
    public void restoreConfigs(Map<String, Map<String, String>> configs) {
        Map<String, Map<String, String>> configsToRestore = copyConfigs(configs);
        if (persistence != null) {
            persistence.replaceAllConfigs(configsToRestore);
        }
        replaceAllConfigs(configsToRestore);
    }
    
    @Override
    public PluginConfigSourceType getSourceType() {
        return PluginConfigSourceType.RUNTIME_PERSISTED;
    }
    
    private Map<String, Map<String, String>> copyConfigs(
        Map<String, Map<String, String>> configs) {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (configs != null) {
            configs.forEach((pluginId, config) -> result.put(pluginId,
                config == null ? Collections.emptyMap() : new HashMap<>(config)));
        }
        return result;
    }
}
