/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin.storage;

import java.util.Map;

/**
 * Plugin state persistence service interface.
 * Provides abstraction for persisting plugin states and configurations.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public interface PluginStatePersistenceService {

    /**
     * Load all plugin states.
     *
     * @return map of plugin ID to enabled state
     */
    Map<String, Boolean> loadAllStates();

    /**
     * Save plugin state.
     *
     * @param pluginId plugin ID
     * @param enabled whether the plugin is enabled
     */
    void saveState(String pluginId, boolean enabled);

    /**
     * Delete plugin state.
     *
     * @param pluginId plugin ID
     */
    void deleteState(String pluginId);

    /**
     * Load all plugin configurations.
     *
     * @return map of plugin ID to configuration
     */
    Map<String, Map<String, String>> loadAllConfigs();

    /**
     * Save plugin configuration.
     *
     * @param pluginId plugin ID
     * @param config configuration key-value pairs
     */
    void saveConfig(String pluginId, Map<String, String> config);

    /**
     * Delete plugin configuration.
     *
     * @param pluginId plugin ID
     */
    void deleteConfig(String pluginId);
}
