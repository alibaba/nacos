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

package com.alibaba.nacos.core.plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Critical plugin configuration.
 * Defines plugins that cannot be disabled.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public final class CriticalPluginConfig {

    private static final Set<String> CRITICAL_PLUGINS;

    static {
        // Only datasource dialects are critical - Nacos requires at least one database
        // backend.
        // Auth plugins are NOT critical - users can disable default auth to use custom
        // plugins.
        Set<String> plugins = new HashSet<>();
        plugins.add("datasource-dialect:mysql");
        plugins.add("datasource-dialect:derby");
        plugins.add("datasource-dialect:postgresql");
        CRITICAL_PLUGINS = Collections.unmodifiableSet(plugins);
    }

    private CriticalPluginConfig() {
    }

    /**
     * Check if a plugin is critical.
     *
     * @param pluginId plugin ID in format "{type}:{name}"
     * @return true if the plugin is critical
     */
    public static boolean isCritical(String pluginId) {
        return CRITICAL_PLUGINS.contains(pluginId);
    }

    /**
     * Get all critical plugins.
     *
     * @return unmodifiable set of critical plugin IDs
     */
    public static Set<String> getCriticalPlugins() {
        return CRITICAL_PLUGINS;
    }
}
