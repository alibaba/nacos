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

package com.alibaba.nacos.common.spi;

import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Utilities for deterministic first-wins plugin registration.
 *
 * @author Nacos
 */
public final class PluginRegistryUtils {
    
    private PluginRegistryUtils() {
    }
    
    /**
     * Register one plugin if its identity has not been claimed.
     *
     * @param plugins target plugin map
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @param plugin plugin instance
     * @param logger domain logger
     * @param <T> plugin type
     * @return true when the plugin is registered
     */
    public static <T> boolean registerFirst(Map<String, T> plugins, String pluginType,
        String pluginName, T plugin, Logger logger) {
        if (StringUtils.isBlank(pluginName) || plugin == null) {
            logger.warn("[PluginRegistry] Ignore invalid plugin, type={}, name={}, "
                + "instancePresent={}.", pluginType, pluginName, plugin != null);
            return false;
        }
        T existing = plugins.putIfAbsent(pluginName, plugin);
        if (existing != null) {
            logger.warn("[PluginRegistry] Ignore duplicate plugin, type={}, name={}, "
                + "existingClass={}, ignoredClass={}.", pluginType, pluginName,
                existing.getClass().getName(), plugin.getClass().getName());
            return false;
        }
        return true;
    }
}
