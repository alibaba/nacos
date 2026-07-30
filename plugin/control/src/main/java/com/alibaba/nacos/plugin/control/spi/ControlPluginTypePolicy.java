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

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.Loggers;

/**
 * Control plugin type policy.
 *
 * @author Nacos
 */
public class ControlPluginTypePolicy implements PluginTypePolicy {
    
    public static final String CONTROL_TYPE_PROPERTY = "nacos.plugin.control.type";
    
    /**
     * Legacy control plugin selection property.
     *
     * @deprecated use {@link #CONTROL_TYPE_PROPERTY} instead. Planned for removal in Nacos 4.0.0.
     */
    @Deprecated
    public static final String LEGACY_CONTROL_TYPE_PROPERTY =
        "nacos.plugin.control.manager.type";
    
    private String selectedPlugin = "";
    
    @Override
    public void initialize(PluginTypeConfiguration configuration) {
        if (configuration.containsProperty(CONTROL_TYPE_PROPERTY)) {
            selectedPlugin = normalize(configuration.getProperty(CONTROL_TYPE_PROPERTY));
            return;
        }
        selectedPlugin = normalize(configuration.getProperty(LEGACY_CONTROL_TYPE_PROPERTY));
        if (StringUtils.isNotBlank(selectedPlugin)) {
            Loggers.CONTROL.warn(
                "Control plugin selection uses deprecated key '{}'; migrate to '{}'.",
                LEGACY_CONTROL_TYPE_PROPERTY, CONTROL_TYPE_PROPERTY);
        }
    }
    
    @Override
    public PluginType getPluginType() {
        return PluginType.CONTROL;
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        return StringUtils.isNotBlank(selectedPlugin)
            && pluginName.equalsIgnoreCase(selectedPlugin);
    }
    
    @Override
    public boolean isLoadingEnabled(PluginTypeConfiguration configuration) {
        return StringUtils.isNotBlank(selectedPlugin);
    }
    
    @Override
    public String getSelectionProperty() {
        return CONTROL_TYPE_PROPERTY;
    }
    
    private String normalize(String value) {
        return StringUtils.isBlank(value) ? "" : value.trim();
    }
}
