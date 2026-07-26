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

package com.alibaba.nacos.plugin.config;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config change plugin type policy.
 *
 * @author Nacos
 */
public class ConfigChangePluginTypePolicy implements PluginTypePolicy {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ConfigChangePluginTypePolicy.class);
    
    @Override
    public PluginType getPluginType() {
        return PluginType.CONFIG_CHANGE;
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String standardProperty = "nacos.plugin.config-change." + pluginName + ".enabled";
        if (configuration.containsProperty(standardProperty)) {
            return configuration.getBooleanProperty(standardProperty, false);
        }
        String legacyProperty = ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX + pluginName
            + ".enabled";
        boolean enabled = configuration.getBooleanProperty(legacyProperty, false);
        if (configuration.containsProperty(legacyProperty)) {
            LOGGER.warn("[ConfigChangePluginTypePolicy] Plugin config-change:{} initial enabled "
                + "state '{}' is read from legacy property '{}'. Persisted plugin state takes "
                + "precedence; use the plugin management API for future state changes.",
                pluginName, enabled, legacyProperty);
        }
        return enabled;
    }
}
