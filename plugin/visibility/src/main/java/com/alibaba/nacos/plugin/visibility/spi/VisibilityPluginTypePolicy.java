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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visibility plugin type policy.
 *
 * @author Nacos
 */
public class VisibilityPluginTypePolicy implements PluginTypePolicy {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(VisibilityPluginTypePolicy.class);
    
    private static final String VISIBILITY_TYPE_PROPERTY = "nacos.plugin.visibility.type";
    
    private static final String DEFAULT_VISIBILITY_PLUGIN = "nacos";
    
    @Override
    public PluginType getPluginType() {
        return PluginType.VISIBILITY;
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String implementationProperty =
            "nacos.plugin.visibility." + pluginName + ".enabled";
        if (configuration.containsProperty(implementationProperty)) {
            return configuration.getBooleanProperty(implementationProperty, true);
        }
        String selected = configuration.getProperty(VISIBILITY_TYPE_PROPERTY,
            DEFAULT_VISIBILITY_PLUGIN).trim();
        boolean enabled = pluginName.equalsIgnoreCase(selected);
        if (enabled && configuration.containsProperty(VISIBILITY_TYPE_PROPERTY)) {
            LOGGER.warn("[VisibilityPluginTypePolicy] Plugin visibility initial selection '{}' "
                + "is read from compatibility property '{}'. Persisted implementation state "
                + "takes precedence; use the plugin management API for future state changes.",
                selected, VISIBILITY_TYPE_PROPERTY);
        }
        return enabled;
    }
}
