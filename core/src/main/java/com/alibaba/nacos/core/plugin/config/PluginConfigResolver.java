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

import java.util.ArrayList;
import java.util.Arrays;
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
    
    private final LocalOnlyPluginConfigSourceResolver localOnlySourceResolver =
        new LocalOnlyPluginConfigSourceResolver();
    
    private final RuntimePersistedPluginConfigSourceResolver runtimePersistedSourceResolver =
        new RuntimePersistedPluginConfigSourceResolver();
    
    private final StaticPluginConfigSourceResolver staticSourceResolver =
        new StaticPluginConfigSourceResolver();
    
    private final DefaultPluginConfigSourceResolver defaultSourceResolver =
        new DefaultPluginConfigSourceResolver();
    
    private final List<PluginConfigSourceResolver> sourceResolvers = Arrays.asList(
        localOnlySourceResolver, runtimePersistedSourceResolver, staticSourceResolver,
        defaultSourceResolver);
    
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
     * Update runtime persisted config for one plugin.
     *
     * @param pluginId plugin id
     * @param config normalized config
     */
    public void updateRuntimeConfig(String pluginId, Map<String, String> config) {
        runtimePersistedSourceResolver.updateConfig(pluginId, config);
    }
    
    /**
     * Update local-only config for one plugin.
     *
     * @param pluginId plugin id
     * @param config normalized config
     */
    public void updateLocalOnlyConfig(String pluginId, Map<String, String> config) {
        localOnlySourceResolver.updateConfig(pluginId, config);
    }
    
    /**
     * Resolve effective plugin config from source resolvers.
     *
     * @param pluginInfo plugin info
     * @param maskSensitive whether sensitive values should be masked
     * @return resolution
     */
    public PluginConfigResolution resolve(PluginInfo pluginInfo, boolean maskSensitive) {
        return resolveInternal(pluginInfo, null, null, maskSensitive);
    }
    
    /**
     * Resolve effective plugin config with a pending source update.
     *
     * @param pluginInfo plugin info
     * @param updatingSource source type to update
     * @param updatingConfig config to preview
     * @param maskSensitive whether sensitive values should be masked
     * @return resolution
     */
    public PluginConfigResolution resolveWithUpdate(PluginInfo pluginInfo,
        PluginConfigSourceType updatingSource, Map<String, String> updatingConfig,
        boolean maskSensitive) {
        Map<String, String> normalizedConfig = normalizeConfig(pluginInfo, updatingConfig);
        return resolveInternal(pluginInfo, updatingSource, normalizedConfig, maskSensitive);
    }
    
    private PluginConfigResolution resolveInternal(PluginInfo pluginInfo,
        PluginConfigSourceType updatingSource, Map<String, String> updatingConfig,
        boolean maskSensitive) {
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return resolveWithoutDefinitions(pluginInfo, updatingSource, updatingConfig);
        }
        Map<String, String> config = new LinkedHashMap<>();
        List<PluginConfigValueMeta> metas = new ArrayList<>(definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            resolveItem(pluginInfo, definition, updatingSource, updatingConfig, maskSensitive,
                config, metas);
        }
        return new PluginConfigResolution(config, metas);
    }
    
    private PluginConfigResolution resolveWithoutDefinitions(PluginInfo pluginInfo,
        PluginConfigSourceType updatingSource, Map<String, String> updatingConfig) {
        Map<String, String> config = new LinkedHashMap<>();
        Map<String, String> runtimeConfig = getSourceConfig(runtimePersistedSourceResolver,
            pluginInfo.getPluginId(), updatingSource, updatingConfig);
        Map<String, String> localOnlyConfig = getSourceConfig(localOnlySourceResolver,
            pluginInfo.getPluginId(), updatingSource, updatingConfig);
        if (runtimeConfig == null && localOnlyConfig == null && pluginInfo.getConfig() != null) {
            config.putAll(pluginInfo.getConfig());
        }
        if (runtimeConfig != null) {
            config.putAll(runtimeConfig);
        }
        if (localOnlyConfig != null) {
            config.putAll(localOnlyConfig);
        }
        return new PluginConfigResolution(config, Collections.emptyList());
    }
    
    private Map<String, String> getSourceConfig(
        AbstractMapPluginConfigSourceResolver sourceResolver,
        String pluginId, PluginConfigSourceType updatingSource,
        Map<String, String> updatingConfig) {
        if (sourceResolver.getSourceType() == updatingSource) {
            return updatingConfig;
        }
        return sourceResolver.getConfig(pluginId);
    }
    
    private void resolveItem(PluginInfo pluginInfo, ConfigItemDefinition definition,
        PluginConfigSourceType updatingSource, Map<String, String> updatingConfig,
        boolean maskSensitive, Map<String, String> config, List<PluginConfigValueMeta> metas) {
        PluginConfigKeyCandidate candidate = keyResolver.resolve(pluginInfo, definition);
        List<PluginConfigSourceValue> sourceValues = resolveSourceValues(pluginInfo, definition,
            candidate, updatingSource, updatingConfig);
        PluginConfigSourceValue effectiveValue = firstPresent(sourceValues);
        if (effectiveValue.getValue() != null) {
            String value = effectiveValue.getValue();
            if (maskSensitive) {
                value = PluginConfigMasker.mask(definition, value);
            }
            config.put(definition.getKey(), value);
        }
        metas.add(buildValueMeta(definition.getKey(), effectiveValue.getSource(),
            countOverrideSources(sourceValues) > 1));
    }
    
    private List<PluginConfigSourceValue> resolveSourceValues(PluginInfo pluginInfo,
        ConfigItemDefinition definition, PluginConfigKeyCandidate candidate,
        PluginConfigSourceType updatingSource, Map<String, String> updatingConfig) {
        List<PluginConfigSourceValue> result = new ArrayList<>(sourceResolvers.size());
        for (PluginConfigSourceResolver sourceResolver : sourceResolvers) {
            if (sourceResolver.getSourceType() == updatingSource
                && sourceResolver instanceof AbstractMapPluginConfigSourceResolver) {
                result.add(((AbstractMapPluginConfigSourceResolver) sourceResolver)
                    .resolveFromConfig(updatingConfig, definition));
                continue;
            }
            result.add(sourceResolver.resolve(pluginInfo, definition, candidate));
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
