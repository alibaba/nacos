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
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates plugin configuration normalization, resolution, application and persistence.
 *
 * @author Nacos
 */
public class PluginConfigService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginConfigService.class);
    
    private final PluginConfigResolver resolver;
    
    private final PluginConfigBasicChecker checker;
    
    private final PluginConfigApplier applier;
    
    private final PluginStatePersistenceService persistence;
    
    private final Map<String, Object> pluginLocks = new ConcurrentHashMap<>();
    
    public PluginConfigService(PluginStatePersistenceService persistence) {
        this(new PluginConfigResolver(), new PluginConfigBasicChecker(),
            new PluginConfigApplier(), persistence);
    }
    
    PluginConfigService(PluginConfigResolver resolver, PluginConfigBasicChecker checker,
        PluginConfigApplier applier, PluginStatePersistenceService persistence) {
        this.resolver = resolver;
        this.checker = checker;
        this.applier = applier;
        this.persistence = persistence;
    }
    
    /**
     * Normalize and validate a runtime update before local apply or cluster synchronization.
     *
     * @param pluginInfo plugin info
     * @param config input full override map
     * @param sourceType target runtime source
     * @return normalized full override map
     */
    public Map<String, String> prepareRuntimeUpdate(PluginInfo pluginInfo,
        Map<String, String> config, PluginConfigSourceType sourceType) {
        validateRuntimeSource(sourceType);
        synchronized (getPluginLock(pluginInfo.getPluginId())) {
            Map<String, String> normalizedConfig = normalizeConfig(pluginInfo, config);
            Map<String, String> preparedConfig = preserveMaskedSensitiveValues(pluginInfo,
                normalizedConfig, sourceType);
            validateRuntimeUpdate(pluginInfo, preparedConfig, sourceType);
            return preparedConfig;
        }
    }
    
    /**
     * Load one persisted source snapshot without applying it.
     *
     * @param pluginInfo plugin info, may be {@code null} when the plugin is not loaded locally
     * @param pluginId plugin id
     * @param config persisted config
     */
    public void loadRuntimePersistedConfig(PluginInfo pluginInfo, String pluginId,
        Map<String, String> config) {
        synchronized (getPluginLock(pluginId)) {
            resolver.updateConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginId,
                normalizeConfig(pluginInfo, config));
        }
    }
    
    /**
     * Resolve and apply all effective fields during plugin startup.
     *
     * @param pluginInfo plugin info
     * @param pluginInstance plugin instance
     */
    public void initializePluginConfig(PluginInfo pluginInfo, Object pluginInstance) {
        synchronized (getPluginLock(pluginInfo.getPluginId())) {
            resolver.initializeStaticConfig(pluginInfo);
            PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
            try {
                checker.validateEffectiveConfig(pluginInfo, resolution.getConfig());
                applier.apply(pluginInfo.getPluginId(), pluginInstance, resolution.getConfig());
            } catch (RuntimeException e) {
                LOGGER.error("[PluginConfigService] Failed to initialize plugin config, "
                    + "pluginId={}", pluginInfo.getPluginId(), e);
                throw new PluginConfigApplyException(
                    "Failed to initialize plugin config: " + pluginInfo.getPluginId(), e);
            }
            pluginInfo.setConfig(copyConfig(resolution.getConfig()));
        }
    }
    
    /**
     * Refresh runtime-effective static configuration and apply an effective change.
     *
     * @param pluginInfo plugin info
     * @param pluginInstance plugin instance
     */
    public void refreshStaticConfig(PluginInfo pluginInfo, Object pluginInstance) {
        synchronized (getPluginLock(pluginInfo.getPluginId())) {
            PluginConfigResolution previousResolution = resolver.resolve(pluginInfo, false);
            resolver.refreshStaticConfig(pluginInfo);
            PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
            if (previousResolution.getConfig().equals(resolution.getConfig())) {
                return;
            }
            try {
                checker.validateEffectiveConfig(pluginInfo, resolution.getConfig());
                applier.apply(pluginInfo.getPluginId(), pluginInstance, resolution.getConfig());
            } catch (RuntimeException e) {
                LOGGER.error("[PluginConfigService] Static plugin config was refreshed but failed "
                    + "to apply, pluginId={}", pluginInfo.getPluginId(), e);
                throw new PluginConfigApplyException("Static plugin config was refreshed but "
                    + "failed to apply: " + pluginInfo.getPluginId(), e);
            }
            pluginInfo.setConfig(copyConfig(resolution.getConfig()));
        }
    }
    
    /**
     * Replace and apply the local-only source snapshot.
     *
     * @param pluginInfo plugin info
     * @param pluginInstance plugin instance
     * @param config normalized full override map
     */
    public void updateLocalOnlyConfig(PluginInfo pluginInfo, Object pluginInstance,
        Map<String, String> config) {
        replaceAndApply(pluginInfo.getPluginId(), pluginInfo, pluginInstance,
            PluginConfigSourceType.LOCAL_ONLY, config, true, false);
    }
    
    /**
     * Replace, apply and persist a synchronized runtime source snapshot.
     *
     * @param pluginId plugin id
     * @param pluginInfo plugin info, may be {@code null} when not loaded locally
     * @param pluginInstance plugin instance
     * @param config full override map
     */
    public void applyRuntimePersistedConfig(String pluginId, PluginInfo pluginInfo,
        Object pluginInstance, Map<String, String> config) {
        replaceAndApply(pluginId, pluginInfo, pluginInstance,
            PluginConfigSourceType.RUNTIME_PERSISTED, config, true, true);
    }
    
    /**
     * Restore a runtime source snapshot and effective config while loading a Raft snapshot.
     *
     * @param pluginId plugin id
     * @param pluginInfo plugin info, may be {@code null} when not loaded locally
     * @param pluginInstance plugin instance
     * @param config full override map
     */
    public void restoreRuntimePersistedConfig(String pluginId, PluginInfo pluginInfo,
        Object pluginInstance, Map<String, String> config) {
        replaceAndApply(pluginId, pluginInfo, pluginInstance,
            PluginConfigSourceType.RUNTIME_PERSISTED, config, false, true);
    }
    
    /**
     * Resolve effective config for a query response.
     *
     * @param pluginInfo plugin info
     * @param maskSensitive whether sensitive values should be masked
     * @return effective config resolution
     */
    public PluginConfigResolution resolve(PluginInfo pluginInfo, boolean maskSensitive) {
        synchronized (getPluginLock(pluginInfo.getPluginId())) {
            return resolver.resolve(pluginInfo, maskSensitive);
        }
    }
    
    private void replaceAndApply(String pluginId, PluginInfo pluginInfo, Object pluginInstance,
        PluginConfigSourceType sourceType, Map<String, String> config,
        boolean validateRuntimeUpdate, boolean persist) {
        synchronized (getPluginLock(pluginId)) {
            Map<String, String> normalizedConfig = normalizeConfig(pluginInfo, config);
            if (validateRuntimeUpdate && pluginInfo != null) {
                normalizedConfig = preserveMaskedSensitiveValues(pluginInfo, normalizedConfig,
                    sourceType);
                validateRuntimeUpdate(pluginInfo, normalizedConfig, sourceType);
            }
            if (persist) {
                persistence.saveConfig(pluginId, normalizedConfig);
            }
            resolver.updateConfig(sourceType, pluginId, normalizedConfig);
            if (pluginInfo == null) {
                return;
            }
            PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
            try {
                checker.validateEffectiveConfig(pluginInfo, resolution.getConfig());
                applier.apply(pluginId, pluginInstance, resolution.getConfig());
            } catch (RuntimeException e) {
                LOGGER.error("[PluginConfigService] Plugin config source was updated but failed "
                    + "to apply, pluginId={}, source={}", pluginId, sourceType, e);
                throw new PluginConfigApplyException("Plugin config source was updated but failed "
                    + "to apply, pluginId=" + pluginId + ", source=" + sourceType, e);
            }
            pluginInfo.setConfig(copyConfig(resolution.getConfig()));
        }
    }
    
    private void validateRuntimeUpdate(PluginInfo pluginInfo, Map<String, String> config,
        PluginConfigSourceType sourceType) {
        Map<String, String> currentConfig = resolver.getConfig(sourceType, pluginInfo);
        checker.validateRuntimeUpdate(pluginInfo, currentConfig, config);
    }
    
    private Map<String, String> preserveMaskedSensitiveValues(PluginInfo pluginInfo,
        Map<String, String> config, PluginConfigSourceType sourceType) {
        List<ConfigItemDefinition> definitions = pluginInfo.getConfigDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return config;
        }
        Map<String, String> currentConfig = resolver.getConfig(sourceType, pluginInfo);
        Map<String, String> result = copyConfig(config);
        for (ConfigItemDefinition definition : definitions) {
            String key = definition.getKey();
            if (!definition.isSensitive() || StringUtils.isBlank(key) || !result.containsKey(key)
                || !PluginConfigMasker.isMaskedValue(result.get(key))) {
                continue;
            }
            if (currentConfig != null && currentConfig.containsKey(key)) {
                result.put(key, currentConfig.get(key));
            } else {
                result.remove(key);
            }
            LOGGER.warn("[PluginConfigService] Masked sensitive config input was detected, "
                + "pluginId={}, key={}, source={}", pluginInfo.getPluginId(), key, sourceType);
        }
        return result;
    }
    
    private Map<String, String> normalizeConfig(PluginInfo pluginInfo,
        Map<String, String> config) {
        Map<String, String> configToNormalize = config == null ? Collections.emptyMap() : config;
        if (pluginInfo == null) {
            return copyConfig(configToNormalize);
        }
        return copyConfig(resolver.normalizeConfig(pluginInfo, configToNormalize));
    }
    
    private Map<String, String> copyConfig(Map<String, String> config) {
        return config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }
    
    private Object getPluginLock(String pluginId) {
        return pluginLocks.computeIfAbsent(pluginId, key -> new Object());
    }
    
    private void validateRuntimeSource(PluginConfigSourceType sourceType) {
        if (PluginConfigSourceType.RUNTIME_PERSISTED != sourceType
            && PluginConfigSourceType.LOCAL_ONLY != sourceType) {
            throw new IllegalArgumentException("Plugin config source is not runtime updatable: "
                + sourceType);
        }
    }
}
