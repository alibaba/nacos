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

import java.util.Map;

/**
 * Resolver for one plugin configuration source.
 *
 * @author Nacos
 */
interface PluginConfigSourceResolver {
    
    /**
     * Initialize this source for one plugin during startup.
     *
     * @param pluginInfo plugin info
     */
    default void initializeConfig(PluginInfo pluginInfo) {
    }
    
    /**
     * Refresh this source for one plugin at runtime.
     *
     * @param pluginInfo plugin info
     */
    default void refreshConfig(PluginInfo pluginInfo) {
    }
    
    /**
     * Get source config for one plugin.
     *
     * @param pluginInfo plugin info
     * @return source config using canonical item keys
     */
    Map<String, String> getConfig(PluginInfo pluginInfo);
    
    /**
     * Whether this source supports runtime updates.
     *
     * @return true if the source is updatable
     */
    default boolean isUpdatable() {
        return false;
    }
    
    /**
     * Replace source config for one plugin.
     *
     * @param pluginId plugin id
     * @param config normalized full source config
     */
    default void updateConfig(String pluginId, Map<String, String> config) {
        throw new UnsupportedOperationException("Plugin config source is not updatable: "
            + getSourceType());
    }
    
    /**
     * Get source type.
     *
     * @return source type
     */
    PluginConfigSourceType getSourceType();
}
