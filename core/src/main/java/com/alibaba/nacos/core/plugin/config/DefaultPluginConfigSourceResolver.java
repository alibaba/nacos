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

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolver for default plugin configuration source.
 *
 * @author Nacos
 */
class DefaultPluginConfigSourceResolver implements PluginConfigSourceResolver {
    
    @Override
    public Map<String, String> getConfig(PluginInfo pluginInfo) {
        Map<String, String> result = new LinkedHashMap<>();
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null) {
            return result;
        }
        for (ConfigItemDefinition definition : definitions) {
            if (definition.getKey() != null && definition.getDefaultValue() != null) {
                result.put(definition.getKey(), definition.getDefaultValue());
            }
        }
        return result;
    }
    
    @Override
    public PluginConfigSourceType getSourceType() {
        return PluginConfigSourceType.DEFAULT;
    }
}
