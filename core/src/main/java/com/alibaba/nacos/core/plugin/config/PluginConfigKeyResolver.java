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
import com.alibaba.nacos.core.plugin.model.PluginInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolver for plugin configuration keys.
 *
 * @author Nacos
 */
public class PluginConfigKeyResolver {
    
    private static final String STANDARD_KEY_PREFIX = "nacos.plugin.";
    
    /**
     * Resolve key candidates for one definition.
     *
     * @param pluginInfo plugin info
     * @param definition config item definition
     * @return key candidate
     */
    public PluginConfigKeyCandidate resolve(PluginInfo pluginInfo,
        ConfigItemDefinition definition) {
        String itemKey = definition.getKey();
        String standardKey = buildStandardKey(pluginInfo, itemKey);
        List<String> aliasKeys = new ArrayList<>();
        if (definition.getAliases() != null) {
            for (String alias : definition.getAliases()) {
                if (StringUtils.isBlank(alias)) {
                    continue;
                }
                aliasKeys.add(toReadableKey(pluginInfo, alias));
            }
        }
        return new PluginConfigKeyCandidate(itemKey, standardKey, aliasKeys);
    }
    
    /**
     * Normalize input config keys to definition item keys.
     *
     * @param pluginInfo plugin info
     * @param config input config
     * @return normalized config
     */
    public Map<String, String> normalizeConfig(PluginInfo pluginInfo, Map<String, String> config) {
        if (config == null || config.isEmpty() || pluginInfo.getConfigDefinitions() == null) {
            return config;
        }
        Map<String, String> keyMapping = buildKeyMapping(pluginInfo);
        Map<String, String> result = new LinkedHashMap<>(config.size());
        config.forEach((key, value) -> result.put(keyMapping.getOrDefault(key, key), value));
        return result;
    }
    
    private Map<String, String> buildKeyMapping(PluginInfo pluginInfo) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ConfigItemDefinition definition : pluginInfo.getConfigDefinitions()) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            PluginConfigKeyCandidate candidate = resolve(pluginInfo, definition);
            result.put(candidate.getItemKey(), candidate.getItemKey());
            result.put(candidate.getStandardKey(), candidate.getItemKey());
            if (definition.getAliases() != null) {
                for (String alias : definition.getAliases()) {
                    if (StringUtils.isBlank(alias)) {
                        continue;
                    }
                    result.put(alias, candidate.getItemKey());
                    result.put(toReadableKey(pluginInfo, alias), candidate.getItemKey());
                }
            }
        }
        return result;
    }
    
    private String toReadableKey(PluginInfo pluginInfo, String key) {
        if (key.startsWith(STANDARD_KEY_PREFIX) || key.startsWith("nacos.")) {
            return key;
        }
        return buildStandardKey(pluginInfo, key);
    }
    
    private String buildStandardKey(PluginInfo pluginInfo, String itemKey) {
        return STANDARD_KEY_PREFIX + pluginInfo.getPluginType().getType() + "."
            + pluginInfo.getPluginName() + "." + itemKey;
    }
}
