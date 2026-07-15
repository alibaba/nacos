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
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.model.PluginInfo;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Basic checker for plugin configuration updates and effective values.
 *
 * @author Nacos
 */
public class PluginConfigBasicChecker {
    
    /**
     * Validate a full runtime override map against the current source snapshot.
     *
     * @param pluginInfo plugin info
     * @param currentConfig current source config
     * @param newConfig new full source config
     */
    public void validateRuntimeUpdate(PluginInfo pluginInfo,
        Map<String, String> currentConfig, Map<String, String> newConfig) {
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        Map<String, ConfigItemDefinition> definitionsByKey = indexDefinitions(definitions);
        validateDefinedKeys(newConfig, definitionsByKey);
        validateRuntimeChangedKeys(currentConfig, newConfig, definitionsByKey);
        validateConfigValues(newConfig, definitionsByKey);
    }
    
    /**
     * Validate required fields and basic value types in effective config.
     *
     * @param pluginInfo plugin info
     * @param config effective config
     */
    public void validateEffectiveConfig(PluginInfo pluginInfo, Map<String, String> config) {
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (ConfigItemDefinition definition : definitions) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            String value = config.get(definition.getKey());
            if (definition.isRequired() && StringUtils.isEmpty(value)) {
                throw new IllegalArgumentException(
                    "Required config missing: " + definition.getKey());
            }
            if (value != null) {
                validateValueType(definition, value);
            }
        }
    }
    
    private Map<String, ConfigItemDefinition> indexDefinitions(
        List<ConfigItemDefinition> definitions) {
        Map<String, ConfigItemDefinition> result = new LinkedHashMap<>();
        for (ConfigItemDefinition definition : definitions) {
            if (StringUtils.isNotBlank(definition.getKey())) {
                result.put(definition.getKey(), definition);
            }
        }
        return result;
    }
    
    private void validateDefinedKeys(Map<String, String> config,
        Map<String, ConfigItemDefinition> definitions) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (!definitions.containsKey(entry.getKey())) {
                throw new IllegalArgumentException(
                    "Unknown plugin config key: " + entry.getKey());
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                    "Plugin config value cannot be null: " + entry.getKey());
            }
        }
    }
    
    private void validateRuntimeChangedKeys(Map<String, String> currentConfig,
        Map<String, String> newConfig, Map<String, ConfigItemDefinition> definitions) {
        Map<String, String> current = currentConfig == null ? Collections.emptyMap()
            : currentConfig;
        Set<String> changedKeys = new LinkedHashSet<>(current.keySet());
        changedKeys.addAll(newConfig.keySet());
        for (String key : changedKeys) {
            if (Objects.equals(current.get(key), newConfig.get(key))
                && current.containsKey(key) == newConfig.containsKey(key)) {
                continue;
            }
            ConfigItemDefinition definition = definitions.get(key);
            if (definition != null
                && ConfigItemEffectMode.RUNTIME != definition.getEffectMode()) {
                throw new IllegalArgumentException("Plugin config key requires restart and cannot "
                    + "be updated at runtime: " + key);
            }
        }
    }
    
    private void validateConfigValues(Map<String, String> config,
        Map<String, ConfigItemDefinition> definitions) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            validateValueType(definitions.get(entry.getKey()), entry.getValue());
        }
    }
    
    private void validateValueType(ConfigItemDefinition definition, String value) {
        ConfigItemType type = definition.getType();
        if (type == null || ConfigItemType.STRING == type) {
            return;
        }
        switch (type) {
            case NUMBER:
                validateNumber(definition.getKey(), value);
                break;
            case BOOLEAN:
                validateBoolean(definition.getKey(), value);
                break;
            case ENUM:
                validateEnum(definition, value);
                break;
            default:
                break;
        }
    }
    
    private void validateNumber(String key, String value) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Plugin config value is not a number: " + key, e);
        }
    }
    
    private void validateBoolean(String key, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Plugin config value is not a boolean: " + key);
        }
    }
    
    private void validateEnum(ConfigItemDefinition definition, String value) {
        if (definition.getEnumValues() != null && !definition.getEnumValues().isEmpty()
            && !definition.getEnumValues().contains(value)) {
            throw new IllegalArgumentException("Plugin config value is not in enum values: "
                + definition.getKey());
        }
    }
}
