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

import com.alibaba.nacos.api.plugin.PluginConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies effective configuration to a plugin implementation.
 *
 * @author Nacos
 */
public class PluginConfigApplier {
    
    /**
     * Apply effective configuration when the plugin exposes {@link PluginConfigSpec}.
     *
     * @param pluginId plugin id
     * @param pluginInstance plugin instance
     * @param config effective config
     */
    public void apply(String pluginId, Object pluginInstance, Map<String, String> config) {
        if (!(pluginInstance instanceof PluginConfigSpec)) {
            return;
        }
        try {
            ((PluginConfigSpec) pluginInstance).applyConfig(new LinkedHashMap<>(config));
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to apply config to plugin: " + pluginId, e);
        }
    }
}
