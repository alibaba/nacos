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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolver for static plugin configuration source.
 *
 * @author Nacos
 */
class StaticPluginConfigSourceResolver implements PluginConfigSourceResolver {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(StaticPluginConfigSourceResolver.class);
    
    private final PluginConfigKeyResolver keyResolver = new PluginConfigKeyResolver();
    
    private final Map<String, Map<String, String>> snapshots = new ConcurrentHashMap<>();
    
    @Override
    public void initializeConfig(PluginInfo pluginInfo) {
        snapshots.put(pluginInfo.getPluginId(), readEnvironmentConfig(pluginInfo));
    }
    
    @Override
    public void refreshConfig(PluginInfo pluginInfo) {
        Map<String, String> latestConfig = readEnvironmentConfig(pluginInfo);
        List<String> ignoredRestartKeys = new ArrayList<>();
        snapshots.compute(pluginInfo.getPluginId(), (pluginId, currentConfig) -> {
            if (currentConfig == null) {
                return latestConfig;
            }
            Map<String, String> acceptedConfig = new LinkedHashMap<>(currentConfig);
            List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
            if (definitions == null) {
                return acceptedConfig;
            }
            for (ConfigItemDefinition definition : definitions) {
                String itemKey = definition.getKey();
                if (StringUtils.isBlank(itemKey)) {
                    continue;
                }
                if (ConfigItemEffectMode.RUNTIME == definition.getEffectMode()) {
                    replaceItem(acceptedConfig, latestConfig, itemKey);
                } else if (isItemChanged(currentConfig, latestConfig, itemKey)) {
                    ignoredRestartKeys.add(itemKey);
                }
            }
            return acceptedConfig;
        });
        if (!ignoredRestartKeys.isEmpty()) {
            LOGGER.warn("[StaticPluginConfigSourceResolver] Ignore runtime refresh of restart-"
                + "required plugin config, pluginId={}, keys={}", pluginInfo.getPluginId(),
                ignoredRestartKeys);
        }
    }
    
    @Override
    public Map<String, String> getConfig(PluginInfo pluginInfo) {
        Map<String, String> config = snapshots.computeIfAbsent(pluginInfo.getPluginId(),
            pluginId -> readEnvironmentConfig(pluginInfo));
        return new LinkedHashMap<>(config);
    }
    
    private Map<String, String> readEnvironmentConfig(PluginInfo pluginInfo) {
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
    
    private void replaceItem(Map<String, String> target, Map<String, String> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        } else {
            target.remove(key);
        }
    }
    
    private boolean isItemChanged(Map<String, String> current, Map<String, String> latest,
        String key) {
        return current.containsKey(key) != latest.containsKey(key)
            || !Objects.equals(current.get(key), latest.get(key));
    }
    
    private String getEnvironmentValue(PluginInfo pluginInfo,
        PluginConfigKeyCandidate candidate) {
        String standardValue = null;
        if (EnvUtil.containsProperty(candidate.getStandardKey())) {
            standardValue = EnvUtil.getProperty(candidate.getStandardKey());
            if (StringUtils.isNotEmpty(standardValue)) {
                return standardValue;
            }
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
                LOGGER.warn("[StaticPluginConfigSourceResolver] Legacy alias '{}' is configured "
                    + "for plugin {} config {}, prefer '{}'.", aliasKey,
                    pluginInfo.getPluginId(), candidate.getItemKey(), candidate.getStandardKey());
            } else {
                LOGGER.warn("[StaticPluginConfigSourceResolver] Multiple aliases are configured "
                    + "for plugin {} config {}, use '{}' and ignore '{}'.",
                    pluginInfo.getPluginId(), candidate.getItemKey(), selectedAlias, aliasKey);
            }
        }
        return selectedAlias == null ? standardValue : selectedValue;
    }
    
    @Override
    public PluginConfigSourceType getSourceType() {
        return PluginConfigSourceType.STATIC;
    }
    
}
