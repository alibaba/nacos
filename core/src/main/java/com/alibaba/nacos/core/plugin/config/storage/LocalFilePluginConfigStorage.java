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

package com.alibaba.nacos.core.plugin.config.storage;

import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;

import java.util.Map;
import java.util.Objects;

/**
 * Built-in plugin configuration storage backed by the local persistence service.
 *
 * @author Nacos
 */
class LocalFilePluginConfigStorage implements PluginConfigStorage {
    
    private final PluginStatePersistenceService persistence;
    
    LocalFilePluginConfigStorage(PluginStatePersistenceService persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }
    
    @Override
    public Map<String, Map<String, String>> loadAllConfigs() {
        return persistence.loadAllConfigs();
    }
    
    @Override
    public void saveConfig(String pluginId, Map<String, String> config) {
        persistence.saveConfig(pluginId, config);
    }
    
    @Override
    public void replaceAllConfigs(Map<String, Map<String, String>> configs) {
        persistence.replaceAllConfigs(configs);
    }
}
