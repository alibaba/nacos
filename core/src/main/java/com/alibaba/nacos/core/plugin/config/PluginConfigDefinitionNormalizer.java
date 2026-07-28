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
import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes plugin configuration definitions without mutating plugin-owned metadata.
 *
 * @author Nacos
 */
public final class PluginConfigDefinitionNormalizer {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(PluginConfigDefinitionNormalizer.class);
    
    private static final String STANDARD_KEY_PREFIX = "nacos.plugin.";
    
    private static final String RESERVED_ENABLED_KEY = "enabled";
    
    private PluginConfigDefinitionNormalizer() {
    }
    
    /**
     * Normalize definitions using first-wins conflict handling.
     *
     * @param pluginId plugin identity
     * @param definitions plugin-owned definitions
     * @param initializationPhase plugin initialization phase
     * @return immutable normalized definitions
     */
    public static List<ConfigItemDefinition> normalize(String pluginId,
        List<ConfigItemDefinition> definitions,
        PluginInitializationPhase initializationPhase) {
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConfigItemDefinition> result = new ArrayList<>(definitions.size());
        Map<String, String> inputKeyOwners = new LinkedHashMap<>();
        for (ConfigItemDefinition definition : definitions) {
            normalizeDefinition(pluginId, definition, initializationPhase, inputKeyOwners)
                .ifPresent(result::add);
        }
        return Collections.unmodifiableList(result);
    }
    
    private static Optional<ConfigItemDefinition> normalizeDefinition(String pluginId,
        ConfigItemDefinition definition, PluginInitializationPhase initializationPhase,
        Map<String, String> inputKeyOwners) {
        if (definition == null) {
            LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore null config definition, "
                + "pluginId={}.", pluginId);
            return Optional.empty();
        }
        String itemKey = definition.getKey();
        if (StringUtils.isBlank(itemKey)) {
            LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore config definition with blank "
                + "key, pluginId={}.", pluginId);
            return Optional.empty();
        }
        if (isReservedEnabledKey(pluginId, itemKey)) {
            LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore reserved config definition, "
                + "pluginId={}, key={}.", pluginId, itemKey);
            return Optional.empty();
        }
        Set<String> itemInputKeys = resolveInputKeys(pluginId, itemKey);
        String conflictingInputKey = findConflict(itemInputKeys, inputKeyOwners);
        if (conflictingInputKey != null) {
            LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore conflicting config definition, "
                + "pluginId={}, key={}, inputKey={}, existingKey={}.", pluginId, itemKey,
                conflictingInputKey, inputKeyOwners.get(conflictingInputKey));
            return Optional.empty();
        }
        registerInputKeys(itemInputKeys, itemKey, inputKeyOwners);
        ConfigItemDefinition result = copyDefinition(definition);
        result.setAliases(normalizeAliases(pluginId, itemKey, definition.getAliases(),
            inputKeyOwners));
        if (PluginInitializationPhase.PRE_CONTEXT == initializationPhase
            && ConfigItemEffectMode.RUNTIME == result.getEffectMode()) {
            LOGGER.warn("[PluginConfigDefinitionNormalizer] Treat runtime config as restart-only, "
                + "pluginId={}, key={}.", pluginId, itemKey);
            result.setEffectMode(ConfigItemEffectMode.RESTART);
        }
        return Optional.of(result);
    }
    
    private static List<String> normalizeAliases(String pluginId, String itemKey,
        List<String> aliases, Map<String, String> inputKeyOwners) {
        if (aliases == null) {
            return null;
        }
        List<String> result = new ArrayList<>(aliases.size());
        for (String alias : aliases) {
            if (StringUtils.isBlank(alias)) {
                LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore blank config alias, "
                    + "pluginId={}, key={}.", pluginId, itemKey);
                continue;
            }
            if (isReservedEnabledKey(pluginId, alias)) {
                LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore reserved config alias, "
                    + "pluginId={}, key={}, alias={}.", pluginId, itemKey, alias);
                continue;
            }
            Set<String> aliasInputKeys = resolveInputKeys(pluginId, alias);
            String conflictingInputKey = findConflict(aliasInputKeys, inputKeyOwners);
            if (conflictingInputKey != null) {
                LOGGER.warn("[PluginConfigDefinitionNormalizer] Ignore conflicting config alias, "
                    + "pluginId={}, key={}, alias={}, inputKey={}, existingKey={}.", pluginId,
                    itemKey, alias, conflictingInputKey,
                    inputKeyOwners.get(conflictingInputKey));
                continue;
            }
            registerInputKeys(aliasInputKeys, itemKey, inputKeyOwners);
            result.add(alias);
        }
        return result;
    }
    
    private static Set<String> resolveInputKeys(String pluginId, String key) {
        Set<String> result = new LinkedHashSet<>();
        result.add(key);
        if (!key.startsWith("nacos.")) {
            int separatorIndex = pluginId.indexOf(':');
            if (separatorIndex > 0 && separatorIndex < pluginId.length() - 1) {
                result.add(STANDARD_KEY_PREFIX + pluginId.substring(0, separatorIndex) + "."
                    + pluginId.substring(separatorIndex + 1) + "." + key);
            }
        }
        return result;
    }
    
    private static boolean isReservedEnabledKey(String pluginId, String key) {
        return resolveInputKeys(pluginId, RESERVED_ENABLED_KEY).contains(key);
    }
    
    private static String findConflict(Set<String> inputKeys,
        Map<String, String> inputKeyOwners) {
        for (String inputKey : inputKeys) {
            if (inputKeyOwners.containsKey(inputKey)) {
                return inputKey;
            }
        }
        return null;
    }
    
    private static void registerInputKeys(Set<String> inputKeys, String itemKey,
        Map<String, String> inputKeyOwners) {
        for (String inputKey : inputKeys) {
            inputKeyOwners.put(inputKey, itemKey);
        }
    }
    
    private static ConfigItemDefinition copyDefinition(ConfigItemDefinition source) {
        ConfigItemDefinition result =
            new ConfigItemDefinition(source.getKey(), source.getName(), source.getType());
        result.setDescription(source.getDescription());
        result.setDefaultValue(source.getDefaultValue());
        result.setRequired(source.isRequired());
        result.setEnumValues(copyList(source.getEnumValues()));
        result.setSensitive(source.isSensitive());
        result.setEffectMode(source.getEffectMode());
        return result;
    }
    
    private static List<String> copyList(List<String> source) {
        return source == null ? null : new ArrayList<>(source);
    }
}
