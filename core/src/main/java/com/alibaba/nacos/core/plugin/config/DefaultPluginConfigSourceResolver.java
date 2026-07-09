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

/**
 * Resolver for default plugin configuration source.
 *
 * @author Nacos
 */
class DefaultPluginConfigSourceResolver implements PluginConfigSourceResolver {
    
    @Override
    public PluginConfigSourceValue resolve(PluginInfo pluginInfo, ConfigItemDefinition definition,
        PluginConfigKeyCandidate candidate) {
        if (definition.getDefaultValue() != null) {
            return PluginConfigSourceValue.present(definition.getDefaultValue(),
                PluginConfigSourceType.DEFAULT);
        }
        return PluginConfigSourceValue.absent(PluginConfigSourceType.DEFAULT);
    }
    
    @Override
    public PluginConfigSourceType getSourceType() {
        return PluginConfigSourceType.DEFAULT;
    }
}
