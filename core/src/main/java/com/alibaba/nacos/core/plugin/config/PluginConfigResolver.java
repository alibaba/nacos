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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/**
 * Resolver for plugin effective configuration.
 *
 * @author Nacos
 */
public class PluginConfigResolver {
    
    private final PluginConfigKeyResolver keyResolver = new PluginConfigKeyResolver();
    
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
     * Resolve effective plugin config.
     *
     * @param pluginInfo plugin info
     * @param runtimeConfig runtime persisted config
     * @param localOnlyConfig local-only config
     * @param maskSensitive whether sensitive values should be masked
     * @return resolution
     */
    public PluginConfigResolution resolve(PluginInfo pluginInfo,
        Map<String, String> runtimeConfig, Map<String, String> localOnlyConfig,
        boolean maskSensitive) {
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return resolveWithoutDefinitions(pluginInfo, runtimeConfig, localOnlyConfig);
        }
        Map<String, String> normalizedRuntime = normalizeConfig(pluginInfo, runtimeConfig);
        Map<String, String> normalizedLocalOnly = normalizeConfig(pluginInfo, localOnlyConfig);
        Map<String, String> config = new LinkedHashMap<>();
        List<PluginConfigValueMeta> metas = new ArrayList<>(definitions.size());
        for (ConfigItemDefinition definition : definitions) {
            if (StringUtils.isBlank(definition.getKey())) {
                continue;
            }
            resolveItem(pluginInfo, definition, normalizedRuntime, normalizedLocalOnly,
                maskSensitive, config, metas);
        }
        return new PluginConfigResolution(config, metas);
    }
    
    private PluginConfigResolution resolveWithoutDefinitions(PluginInfo pluginInfo,
        Map<String, String> runtimeConfig, Map<String, String> localOnlyConfig) {
        Map<String, String> config = new LinkedHashMap<>();
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
    
    private void resolveItem(PluginInfo pluginInfo, ConfigItemDefinition definition,
        Map<String, String> runtimeConfig, Map<String, String> localOnlyConfig,
        boolean maskSensitive, Map<String, String> config,
        List<PluginConfigValueMeta> metas) {
        PluginConfigKeyCandidate candidate = keyResolver.resolve(pluginInfo, definition);
        ValueWithSource staticValue = getStaticValue(candidate);
        ValueWithSource runtimeValue = getMapValue(runtimeConfig, definition.getKey(),
            PluginConfigSourceType.RUNTIME_PERSISTED);
        ValueWithSource localOnlyValue = getMapValue(localOnlyConfig, definition.getKey(),
            PluginConfigSourceType.LOCAL_ONLY);
        ValueWithSource defaultValue = getDefaultValue(definition);
        ValueWithSource effectiveValue =
            firstPresent(localOnlyValue, runtimeValue, staticValue, defaultValue);
        if (effectiveValue.getValue() != null) {
            String value = effectiveValue.getValue();
            if (maskSensitive) {
                value = PluginConfigMasker.mask(definition, value);
            }
            config.put(definition.getKey(), value);
        }
        metas.add(buildValueMeta(definition.getKey(), effectiveValue.getSource(),
            countOverrideSources(staticValue, runtimeValue, localOnlyValue) > 1));
    }
    
    private ValueWithSource getStaticValue(PluginConfigKeyCandidate candidate) {
        ConfigurableEnvironment environment = EnvUtil.getEnvironment();
        if (environment == null) {
            return ValueWithSource.absent(PluginConfigSourceType.STATIC);
        }
        ValueWithSource standardValue =
            getEnvironmentValue(environment, candidate.getStandardKey());
        if (standardValue.isPresent()) {
            return standardValue;
        }
        for (String aliasKey : candidate.getAliasKeys()) {
            ValueWithSource aliasValue = getEnvironmentValue(environment, aliasKey);
            if (aliasValue.isPresent()) {
                return aliasValue;
            }
        }
        return ValueWithSource.absent(PluginConfigSourceType.STATIC);
    }
    
    private ValueWithSource getEnvironmentValue(ConfigurableEnvironment environment, String key) {
        if (environment.containsProperty(key)) {
            return ValueWithSource.present(environment.getProperty(key),
                PluginConfigSourceType.STATIC);
        }
        return ValueWithSource.absent(PluginConfigSourceType.STATIC);
    }
    
    private ValueWithSource getMapValue(Map<String, String> config, String key,
        PluginConfigSourceType source) {
        if (config != null && config.containsKey(key) && config.get(key) != null) {
            return ValueWithSource.present(config.get(key), source);
        }
        return ValueWithSource.absent(source);
    }
    
    private ValueWithSource getDefaultValue(ConfigItemDefinition definition) {
        if (definition.getDefaultValue() != null) {
            return ValueWithSource.present(definition.getDefaultValue(),
                PluginConfigSourceType.DEFAULT);
        }
        return ValueWithSource.absent(PluginConfigSourceType.DEFAULT);
    }
    
    private ValueWithSource firstPresent(ValueWithSource... values) {
        for (ValueWithSource value : values) {
            if (value.isPresent()) {
                return value;
            }
        }
        return ValueWithSource.absent(PluginConfigSourceType.DEFAULT);
    }
    
    private int countOverrideSources(ValueWithSource... values) {
        Set<PluginConfigSourceType> sources = new LinkedHashSet<>();
        for (ValueWithSource value : values) {
            if (value.isPresent()) {
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
    
    private static class ValueWithSource {
        
        private final String value;
        
        private final PluginConfigSourceType source;
        
        private final boolean present;
        
        private ValueWithSource(String value, PluginConfigSourceType source, boolean present) {
            this.value = value;
            this.source = source;
            this.present = present;
        }
        
        static ValueWithSource present(String value, PluginConfigSourceType source) {
            return new ValueWithSource(value, source, true);
        }
        
        static ValueWithSource absent(PluginConfigSourceType source) {
            return new ValueWithSource(null, source, false);
        }
        
        String getValue() {
            return value;
        }
        
        PluginConfigSourceType getSource() {
            return source;
        }
        
        boolean isPresent() {
            return present;
        }
    }
}
