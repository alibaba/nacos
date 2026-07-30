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

import java.util.Map;

/**
 * Internal physical storage for runtime persisted plugin configuration.
 *
 * <p>This is a core extension point beneath the logical
 * {@code RUNTIME_PERSISTED} source. Implementations must not introduce a new
 * effective-value priority.</p>
 *
 * @author Nacos
 */
public interface PluginConfigStorage {
    
    /**
     * Initialize resources before the initial load.
     */
    default void initialize() {
    }
    
    /**
     * Load the complete runtime persisted configuration.
     *
     * @return plugin ID to complete source map
     */
    Map<String, Map<String, String>> loadAllConfigs();
    
    /**
     * Replace one plugin's complete source map.
     *
     * @param pluginId plugin ID
     * @param config complete source map
     */
    void saveConfig(String pluginId, Map<String, String> config);
    
    /**
     * Replace the complete storage content while restoring a snapshot.
     *
     * @param configs complete storage content
     */
    void replaceAllConfigs(Map<String, Map<String, String>> configs);
    
    /**
     * Release storage resources.
     */
    default void shutdown() {
    }
}
