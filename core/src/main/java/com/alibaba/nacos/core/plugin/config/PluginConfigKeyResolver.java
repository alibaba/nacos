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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolver for plugin configuration keys.
 *
 * @author Nacos
 */
public class PluginConfigKeyResolver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginConfigKeyResolver.class);
    
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
        if (config == null || config.isEmpty() || pluginInfo.getConfigDefinitions() == null
            || pluginInfo.getConfigDefinitions().isEmpty()) {
            return config;
        }
        Map<String, ConfigKeyMapping> keyMappings = buildKeyMappings(pluginInfo);
        Map<String, ResolvedConfigValue> resolvedValues = new LinkedHashMap<>();
        Map<String, String> unknownValues = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigKeyMapping> entry : keyMappings.entrySet()) {
            String key = entry.getKey();
            if (config.containsKey(key)) {
                resolveInputValue(pluginInfo, key, config.get(key), entry.getValue(),
                    resolvedValues);
            }
        }
        config.forEach((key, value) -> {
            if (!keyMappings.containsKey(key)) {
                unknownValues.put(key, value);
            }
        });
        Map<String, String> result = new LinkedHashMap<>(config.size());
        for (ConfigItemDefinition definition : pluginInfo.getConfigDefinitions()) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            ResolvedConfigValue resolvedValue = resolvedValues.get(definition.getKey());
            if (resolvedValue != null) {
                result.put(definition.getKey(), resolvedValue.getValue());
            }
        }
        result.putAll(unknownValues);
        return result;
    }
    
    private void resolveInputValue(PluginInfo pluginInfo, String key, String value,
        ConfigKeyMapping keyMapping, Map<String, ResolvedConfigValue> resolvedValues) {
        String itemKey = keyMapping.getItemKey(key);
        ResolvedConfigValue resolvedValue = new ResolvedConfigValue(value,
            keyMapping.getPriority(), key);
        ResolvedConfigValue currentValue = resolvedValues.get(itemKey);
        if (currentValue == null || resolvedValue.hasHigherPriorityThan(currentValue)) {
            resolvedValues.put(itemKey, resolvedValue);
            if (resolvedValue.isAlias()) {
                LOGGER.warn("[PluginConfigKeyResolver] Legacy alias '{}' is used for plugin {} "
                    + "config {}, prefer the canonical item key or normalized key.", key,
                    pluginInfo.getPluginId(), itemKey);
            }
        } else if (resolvedValue.isAlias() && currentValue.isAlias()) {
            LOGGER.warn("[PluginConfigKeyResolver] Multiple aliases are provided for plugin {} "
                + "config {}, use '{}' and ignore '{}'.", pluginInfo.getPluginId(), itemKey,
                currentValue.getSourceKey(), resolvedValue.getSourceKey());
        }
    }
    
    private Map<String, ConfigKeyMapping> buildKeyMappings(PluginInfo pluginInfo) {
        Map<String, ConfigKeyMapping> result = new LinkedHashMap<>();
        for (ConfigItemDefinition definition : pluginInfo.getConfigDefinitions()) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            PluginConfigKeyCandidate candidate = resolve(pluginInfo, definition);
            registerKeyMapping(result, candidate.getItemKey(), candidate.getItemKey(),
                ConfigKeyPriority.ITEM);
            registerKeyMapping(result, candidate.getStandardKey(), candidate.getItemKey(),
                ConfigKeyPriority.STANDARD);
            registerAliasKeyMappings(result, pluginInfo, definition, candidate.getItemKey());
        }
        return result;
    }
    
    private void registerAliasKeyMappings(Map<String, ConfigKeyMapping> mappings,
        PluginInfo pluginInfo, ConfigItemDefinition definition, String itemKey) {
        if (definition.getAliases() == null) {
            return;
        }
        for (String alias : definition.getAliases()) {
            if (StringUtils.isBlank(alias)) {
                continue;
            }
            registerKeyMapping(mappings, alias, itemKey, ConfigKeyPriority.ALIAS);
            registerKeyMapping(mappings, toReadableKey(pluginInfo, alias), itemKey,
                ConfigKeyPriority.ALIAS);
        }
    }
    
    private void registerKeyMapping(Map<String, ConfigKeyMapping> mappings, String inputKey,
        String itemKey, ConfigKeyPriority priority) {
        ConfigKeyMapping mapping = mappings.get(inputKey);
        if (mapping == null) {
            mappings.put(inputKey, new ConfigKeyMapping(itemKey, priority));
        } else {
            mapping.merge(itemKey, priority);
        }
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
    
    private enum ConfigKeyPriority {
        
        ALIAS(1),
        
        STANDARD(2),
        
        ITEM(3);
        
        private final int value;
        
        ConfigKeyPriority(int value) {
            this.value = value;
        }
        
        boolean isHigherThan(ConfigKeyPriority other) {
            return value > other.value;
        }
    }
    
    private static class ConfigKeyMapping {
        
        private final Set<String> itemKeys = new LinkedHashSet<>();
        
        private ConfigKeyPriority priority;
        
        ConfigKeyMapping(String itemKey, ConfigKeyPriority priority) {
            itemKeys.add(itemKey);
            this.priority = priority;
        }
        
        void merge(String itemKey, ConfigKeyPriority newPriority) {
            if (newPriority.isHigherThan(priority)) {
                itemKeys.clear();
                itemKeys.add(itemKey);
                priority = newPriority;
            } else if (newPriority == priority) {
                itemKeys.add(itemKey);
            }
        }
        
        String getItemKey(String inputKey) {
            if (itemKeys.size() > 1) {
                throw new IllegalArgumentException("Ambiguous plugin config key '" + inputKey
                    + "' matches multiple items: " + itemKeys);
            }
            return itemKeys.iterator().next();
        }
        
        ConfigKeyPriority getPriority() {
            return priority;
        }
        
    }
    
    private static class ResolvedConfigValue {
        
        private final String value;
        
        private final ConfigKeyPriority priority;
        
        private final String sourceKey;
        
        ResolvedConfigValue(String value, ConfigKeyPriority priority, String sourceKey) {
            this.value = value;
            this.priority = priority;
            this.sourceKey = sourceKey;
        }
        
        boolean hasHigherPriorityThan(ResolvedConfigValue other) {
            return priority.isHigherThan(other.priority);
        }
        
        boolean isAlias() {
            return ConfigKeyPriority.ALIAS == priority;
        }
        
        String getSourceKey() {
            return sourceKey;
        }
        
        String getValue() {
            return value;
        }
    }
}
