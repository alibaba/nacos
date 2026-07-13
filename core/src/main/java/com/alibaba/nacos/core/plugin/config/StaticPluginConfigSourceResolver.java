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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolver for static plugin configuration source.
 *
 * @author Nacos
 */
class StaticPluginConfigSourceResolver implements PluginConfigSourceResolver {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(StaticPluginConfigSourceResolver.class);
    
    private final PluginConfigKeyResolver keyResolver = new PluginConfigKeyResolver();
    
    @Override
    public Map<String, String> getConfig(PluginInfo pluginInfo) {
        Map<String, String> result = new LinkedHashMap<>();
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (EnvUtil.getEnvironment() == null || definitions == null) {
            return result;
        }
        for (ConfigItemDefinition definition : definitions) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            PluginConfigKeyCandidate candidate = keyResolver.resolve(pluginInfo, definition);
            String value = getEnvironmentValue(pluginInfo, candidate);
            if (value != null) {
                result.put(definition.getKey(), value);
            }
        }
        return result;
    }
    
    private String getEnvironmentValue(PluginInfo pluginInfo,
        PluginConfigKeyCandidate candidate) {
        if (EnvUtil.containsProperty(candidate.getStandardKey())) {
            return EnvUtil.getProperty(candidate.getStandardKey());
        }
        String selectedAlias = null;
        String selectedValue = null;
        for (String aliasKey : candidate.getAliasKeys()) {
            if (!EnvUtil.containsProperty(aliasKey)) {
                continue;
            }
            if (selectedAlias == null) {
                selectedAlias = aliasKey;
                selectedValue = EnvUtil.getProperty(aliasKey);
            } else {
                LOGGER.warn("[StaticPluginConfigSourceResolver] Multiple aliases are configured "
                    + "for plugin {} config {}, use '{}' and ignore '{}'.",
                    pluginInfo.getPluginId(), candidate.getItemKey(), selectedAlias, aliasKey);
            }
        }
        return selectedValue;
    }
    
    @Override
    public PluginConfigSourceType getSourceType() {
        return PluginConfigSourceType.STATIC;
    }
    
}
