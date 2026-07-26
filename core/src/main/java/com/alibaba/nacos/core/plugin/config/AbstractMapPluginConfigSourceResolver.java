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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base resolver for map-based plugin configuration source.
 *
 * @author Nacos
 */
abstract class AbstractMapPluginConfigSourceResolver implements PluginConfigSourceResolver {
    
    private volatile Map<String, Map<String, String>> configs = new ConcurrentHashMap<>();
    
    /**
     * Update source config for one plugin.
     *
     * @param pluginId plugin id
     * @param config normalized config
     */
    @Override
    public void updateConfig(String pluginId, Map<String, String> config) {
        Map<String, String> configToStore =
            config == null ? Collections.emptyMap() : new HashMap<>(config);
        configs.put(pluginId, configToStore);
    }
    
    /**
     * Get source config for one plugin.
     *
     * @param pluginInfo plugin info
     * @return source config
     */
    @Override
    public Map<String, String> getConfig(PluginInfo pluginInfo) {
        Map<String, String> config = configs.get(pluginInfo.getPluginId());
        return config == null ? null : new HashMap<>(config);
    }
    
    /**
     * Replace configs for all plugins.
     *
     * @param configs complete source config
     */
    protected void replaceAllConfigs(Map<String, Map<String, String>> configs) {
        Map<String, Map<String, String>> replacement = new ConcurrentHashMap<>();
        if (configs != null) {
            configs.forEach((pluginId, config) -> replacement.put(pluginId,
                config == null ? Collections.emptyMap() : new HashMap<>(config)));
        }
        this.configs = replacement;
    }
    
    /**
     * Get configs for all plugins.
     *
     * @return complete source config snapshot
     */
    protected Map<String, Map<String, String>> getAllConfigsSnapshot() {
        Map<String, Map<String, String>> result = new HashMap<>();
        configs.forEach((pluginId, config) -> result.put(pluginId, new HashMap<>(config)));
        return result;
    }
    
    @Override
    public boolean isUpdatable() {
        return true;
    }
    
    @Override
    public abstract PluginConfigSourceType getSourceType();
}
