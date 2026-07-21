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

/**
 * Control plugin type policy.
 *
 * @author Nacos
 */
public class ControlPluginTypePolicy implements PluginTypePolicy {
    
    public static final String CONTROL_TYPE_PROPERTY = "nacos.plugin.control.manager.type";
    
    @Override
    public PluginType getPluginType() {
        return PluginType.CONTROL;
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        String selected = configuration.getProperty(CONTROL_TYPE_PROPERTY);
        return StringUtils.isNotBlank(selected) && pluginName.equalsIgnoreCase(selected.trim());
    }
    
    @Override
    public String getSelectionProperty() {
        return CONTROL_TYPE_PROPERTY;
    }
}
