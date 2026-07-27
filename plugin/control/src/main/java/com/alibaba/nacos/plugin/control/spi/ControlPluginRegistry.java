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

package com.alibaba.nacos.plugin.control.spi;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.ControlPluginAdapter;
import com.alibaba.nacos.plugin.control.Loggers;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Single registry for control manager builders and stable plugin adapters.
 *
 * @author Nacos
 */
public final class ControlPluginRegistry {
    
    private final Map<String, ControlPluginAdapter> plugins;
    
    ControlPluginRegistry(Collection<ControlManagerBuilder> builders) {
        Map<String, ControlPluginAdapter> result = new LinkedHashMap<>();
        Map<String, String> normalizedNames = new LinkedHashMap<>();
        if (builders != null) {
            for (ControlManagerBuilder builder : builders) {
                registerBuilder(result, normalizedNames, builder);
            }
        }
        plugins = Collections.unmodifiableMap(result);
    }
    
    public static ControlPluginRegistry getInstance() {
        return RegistryHolder.INSTANCE;
    }
    
    /**
     * Get stable control plugin adapters.
     *
     * @return plugin name to adapter map
     */
    public Map<String, ControlPluginAdapter> getPlugins() {
        return plugins;
    }
    
    private void registerBuilder(Map<String, ControlPluginAdapter> result,
        Map<String, String> normalizedNames, ControlManagerBuilder builder) {
        if (builder == null) {
            throw new IllegalStateException("Control manager builder cannot be null");
        }
        String pluginName = builder.getName();
        if (StringUtils.isBlank(pluginName)) {
            throw new IllegalStateException(
                "Control manager builder name cannot be blank: " + builder.getClass().getName());
        }
        String normalizedName = pluginName.toLowerCase(Locale.ROOT);
        String existingName = normalizedNames.putIfAbsent(normalizedName, pluginName);
        if (existingName != null) {
            throw new IllegalStateException(
                "Duplicate control plugin name: " + existingName + ", " + pluginName);
        }
        result.put(pluginName, new ControlPluginAdapter(builder));
        Loggers.CONTROL.info("Found control manager plugin, name={}, class={}", pluginName,
            builder.getClass().getName());
    }
    
    private static final class RegistryHolder {
        
        private static final ControlPluginRegistry INSTANCE =
            new ControlPluginRegistry(NacosServiceLoader.load(ControlManagerBuilder.class));
    }
}
