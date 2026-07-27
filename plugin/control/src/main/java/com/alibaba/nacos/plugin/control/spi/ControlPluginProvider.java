/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.ControlPluginAdapter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Control plugin provider implementation.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class ControlPluginProvider implements PluginProvider<ControlPluginAdapter> {
    
    private final Supplier<ControlPluginRegistry> registrySupplier;
    
    public ControlPluginProvider() {
        this(ControlPluginRegistry::getInstance);
    }
    
    ControlPluginProvider(Supplier<ControlPluginRegistry> registrySupplier) {
        this.registrySupplier = registrySupplier;
    }
    
    @Override
    public PluginType getPluginType() {
        return PluginType.CONTROL;
    }
    
    @Override
    public Map<String, ControlPluginAdapter> getAllPlugins() {
        Map<String, ControlPluginAdapter> result = registrySupplier.get().getPlugins();
        String selectedPlugin = ControlConfigs.getInstance().getControlManagerType();
        if (StringUtils.isNotBlank(selectedPlugin)
            && result.keySet().stream().noneMatch(selectedPlugin::equalsIgnoreCase)) {
            Loggers.CONTROL.warn(
                "Selected control plugin '{}' was not found, use no-limit managers.",
                selectedPlugin);
        }
        return result;
    }
}
