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
import com.alibaba.nacos.core.plugin.model.vo.PluginConfigValueMeta;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolver for plugin effective configuration.
 *
 * @author Nacos
 */
public class PluginConfigResolver {
    
    private final PluginConfigKeyResolver keyResolver = new PluginConfigKeyResolver();
    
    private final PluginConfigSourceRegistry sourceRegistry;
    
    public PluginConfigResolver() {
        this(new PluginConfigSourceRegistry());
    }
    
    PluginConfigResolver(PluginStatePersistenceService persistence) {
        this(new PluginConfigSourceRegistry(persistence));
    }
    
    PluginConfigResolver(PluginConfigSourceRegistry sourceRegistry) {
        this.sourceRegistry = sourceRegistry;
    }
    
    /**
     * Normalize config keys with plugin config definitions.
     *
     * @param pluginInfo plugin info
     * @param config input config
     * @return normalized config
     */
    public Map<String, String> normalizeConfig(PluginInfo pluginInfo, Map<String, String> config) {
        return keyResolver.normalizeConfig(pluginInfo, config);
    }
    
    /**
     * Initialize the static source snapshot for one plugin.
     *
     * @param pluginInfo plugin info
     */
    public void initializeStaticConfig(PluginInfo pluginInfo) {
        sourceRegistry.initializeConfig(PluginConfigSourceType.STATIC, pluginInfo);
    }
    
    /**
     * Load all runtime persisted source configs during startup.
     */
    public void initializeRuntimePersistedConfigs() {
        sourceRegistry.initializePersistedConfigs();
    }
    
    /**
     * Whether the runtime persisted source storage is available.
     *
     * @return true when the selected storage initialized successfully
     */
    public boolean isRuntimePersistedSourceAvailable() {
        return sourceRegistry.isPersistedSourceAvailable();
    }
    
    /**
     * Normalize one loaded runtime persisted source config with plugin definitions.
     *
     * @param pluginInfo plugin info
     */
    public void initializeRuntimePersistedConfig(PluginInfo pluginInfo) {
        sourceRegistry.initializeConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginInfo);
    }
    
    /**
     * Refresh runtime-effective fields in the static source snapshot.
     *
     * @param pluginInfo plugin info
     */
    public void refreshStaticConfig(PluginInfo pluginInfo) {
        sourceRegistry.refreshConfig(PluginConfigSourceType.STATIC, pluginInfo);
    }
    
    /**
     * Replace config in an updatable source.
     *
     * @param sourceType source type
     * @param pluginId plugin id
     * @param config normalized full source config
     */
    public void updateConfig(PluginConfigSourceType sourceType, String pluginId,
        Map<String, String> config) {
        PluginConfigSourceResolver sourceResolver = sourceRegistry.getSourceResolver(sourceType);
        if (!sourceResolver.isUpdatable()) {
            throw new IllegalArgumentException("Plugin config source is not updatable: "
                + sourceType);
        }
        sourceResolver.updateConfig(pluginId, config);
    }
    
    /**
     * Get the current config snapshot of a source.
     *
     * @param sourceType source type
     * @param pluginInfo plugin info
     * @return source config, or {@code null} if no map-based snapshot exists
     */
    public Map<String, String> getConfig(PluginConfigSourceType sourceType,
        PluginInfo pluginInfo) {
        return sourceRegistry.getSourceResolver(sourceType).getConfig(pluginInfo);
    }
    
    /**
     * Get all runtime persisted source configs for a snapshot.
     *
     * @return complete runtime persisted source snapshot
     */
    public Map<String, Map<String, String>> getAllRuntimePersistedConfigs() {
        return sourceRegistry.getAllPersistedConfigs();
    }
    
    /**
     * Restore all runtime persisted source configs from a snapshot.
     *
     * @param configs complete runtime persisted source snapshot
     */
    public void restoreRuntimePersistedConfigs(Map<String, Map<String, String>> configs) {
        sourceRegistry.restorePersistedConfigs(configs);
    }
    
    /**
     * Release the runtime persisted source storage.
     */
    public void shutdown() {
        sourceRegistry.shutdownPersistedConfigs();
    }
    
    /**
     * Resolve effective plugin config from source resolvers.
     *
     * @param pluginInfo plugin info
     * @param maskSensitive whether sensitive values should be masked
     * @return resolution
     */
    public PluginConfigResolution resolve(PluginInfo pluginInfo, boolean maskSensitive) {
        return resolveInternal(pluginInfo, maskSensitive);
    }
    
    private PluginConfigResolution resolveInternal(PluginInfo pluginInfo, boolean maskSensitive) {
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return resolveWithoutDefinitions(pluginInfo);
        }
        Map<PluginConfigSourceType, Map<String, String>> sourceConfigs =
            getSourceConfigs(pluginInfo);
        Map<String, String> config = new LinkedHashMap<>();
        Map<String, PluginConfigValueMeta> metas = new LinkedHashMap<>(definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            resolveItem(definition, sourceConfigs, maskSensitive, config, metas);
        }
        return new PluginConfigResolution(config, metas);
    }
    
    private PluginConfigResolution resolveWithoutDefinitions(PluginInfo pluginInfo) {
        Map<String, String> config = new LinkedHashMap<>();
        Map<String, String> runtimeConfig =
            getConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginInfo);
        Map<String, String> localOnlyConfig =
            getConfig(PluginConfigSourceType.LOCAL_ONLY, pluginInfo);
        if (runtimeConfig == null && localOnlyConfig == null && pluginInfo.getConfig() != null) {
            config.putAll(pluginInfo.getConfig());
        }
        if (runtimeConfig != null) {
            config.putAll(runtimeConfig);
        }
        if (localOnlyConfig != null) {
            config.putAll(localOnlyConfig);
        }
        return new PluginConfigResolution(config, Collections.emptyMap());
    }
    
    private void resolveItem(ConfigItemDefinition definition,
        Map<PluginConfigSourceType, Map<String, String>> sourceConfigs, boolean maskSensitive,
        Map<String, String> config,
        Map<String, PluginConfigValueMeta> metas) {
        List<PluginConfigSourceValue> sourceValues = resolveSourceValues(definition, sourceConfigs);
        PluginConfigSourceValue effectiveValue = firstPresent(sourceValues);
        if (effectiveValue.getValue() != null) {
            String value = effectiveValue.getValue();
            if (maskSensitive) {
                value = PluginConfigMasker.mask(definition, value);
            }
            config.put(definition.getKey(), value);
        }
        metas.put(definition.getKey(), buildValueMeta(definition.getKey(),
            effectiveValue.getSource(), countOverrideSources(sourceValues) > 1));
    }
    
    private Map<PluginConfigSourceType, Map<String, String>> getSourceConfigs(
        PluginInfo pluginInfo) {
        Map<PluginConfigSourceType, Map<String, String>> result = new LinkedHashMap<>();
        for (PluginConfigSourceResolver sourceResolver : sourceRegistry.getSourceResolvers()) {
            result.put(sourceResolver.getSourceType(), sourceResolver.getConfig(pluginInfo));
        }
        return result;
    }
    
    private List<PluginConfigSourceValue> resolveSourceValues(ConfigItemDefinition definition,
        Map<PluginConfigSourceType, Map<String, String>> sourceConfigs) {
        List<PluginConfigSourceResolver> sourceResolvers = sourceRegistry.getSourceResolvers();
        List<PluginConfigSourceValue> result = new ArrayList<>(sourceResolvers.size());
        for (PluginConfigSourceResolver sourceResolver : sourceResolvers) {
            PluginConfigSourceType sourceType = sourceResolver.getSourceType();
            Map<String, String> sourceConfig = sourceConfigs.get(sourceType);
            String itemKey = definition.getKey();
            if (sourceConfig != null && sourceConfig.containsKey(itemKey)
                && sourceConfig.get(itemKey) != null) {
                result.add(PluginConfigSourceValue.present(sourceConfig.get(itemKey), sourceType));
            } else {
                result.add(PluginConfigSourceValue.absent(sourceType));
            }
        }
        return result;
    }
    
    private PluginConfigSourceValue firstPresent(List<PluginConfigSourceValue> values) {
        for (PluginConfigSourceValue value : values) {
            if (value.isPresent()) {
                return value;
            }
        }
        return PluginConfigSourceValue.absent(PluginConfigSourceType.DEFAULT);
    }
    
    private int countOverrideSources(List<PluginConfigSourceValue> values) {
        Set<PluginConfigSourceType> sources = new LinkedHashSet<>();
        for (PluginConfigSourceValue value : values) {
            if (value.isPresent() && PluginConfigSourceType.DEFAULT != value.getSource()) {
                sources.add(value.getSource());
            }
        }
        return sources.size();
    }
    
    private PluginConfigValueMeta buildValueMeta(String key, PluginConfigSourceType source,
        boolean overridden) {
        PluginConfigValueMeta meta = new PluginConfigValueMeta();
        meta.setKey(key);
        meta.setSource(source);
        meta.setOverridden(overridden);
        return meta;
    }
}
