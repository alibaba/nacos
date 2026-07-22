/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginStateChecker;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplyException;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.config.PluginConfigService;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.plugin.sync.PluginStateApplier;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Plugin Manager.
 * Central manager for all plugin types, implementing plugin state checking and management.
 *
 * @author WangzJi
 * @since 3.2.0
 */
@Component
public class PluginManager
    implements PluginStateChecker, PluginStateApplier, ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);
    
    /**
     * Plugin registry: pluginId -> PluginInfo.
     */
    private final Map<String, PluginInfo> pluginRegistry = new ConcurrentHashMap<>();
    
    /**
     * Plugin states: pluginId -> enabled.
     */
    private final Map<String, Boolean> pluginStates = new ConcurrentHashMap<>();
    
    /**
     * Plugin instances: pluginId -> instance.
     */
    private final Map<String, Object> pluginInstances = new ConcurrentHashMap<>();
    
    private final PluginConfigService pluginConfigService;
    
    private final PluginStatePersistenceService persistence;
    
    private final PluginTypePolicyRegistry policyRegistry;
    
    /**
     * Plugin state synchronizer for cluster synchronization.
     */
    private final PluginStateSynchronizer synchronizer;
    
    private boolean initialized;
    
    @Autowired
    public PluginManager(PluginStatePersistenceService persistence,
        @Lazy PluginStateSynchronizer synchronizer) {
        this(persistence, synchronizer, new PluginTypePolicyRegistry());
    }
    
    PluginManager(PluginStatePersistenceService persistence,
        PluginStateSynchronizer synchronizer, PluginTypePolicyRegistry policyRegistry) {
        this.persistence = persistence;
        this.synchronizer = synchronizer;
        this.policyRegistry = policyRegistry;
        this.pluginConfigService = new PluginConfigService(persistence);
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initialize();
    }
    
    /**
     * Discover plugins and apply persisted state and effective configuration before Nacos is
     * marked as started. The application-ready listener invokes the same method as a fallback for
     * embedded contexts that do not use the standard Nacos startup lifecycle.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        // Register to static holder
        PluginStateCheckerHolder.setInstance(this);
        policyRegistry.initialize();
        
        // Discover all plugins
        discoverAllPlugins();
        
        // Load persisted states and configs
        loadPersistedData();
        initialized = true;
        
        LOGGER.info("[PluginManager] Initialized, {} plugins discovered", pluginRegistry.size());
    }
    
    @Override
    public boolean isPluginEnabled(String pluginType, String pluginName) {
        String pluginId = buildPluginId(pluginType, pluginName);
        return pluginStates.getOrDefault(pluginId, true);
    }
    
    /**
     * Build plugin ID from type and name.
     *
     * @param pluginType plugin type
     * @param pluginName plugin name
     * @return plugin ID in format "type:name"
     */
    private static String buildPluginId(String pluginType, String pluginName) {
        return pluginType + ":" + pluginName;
    }
    
    /**
     * Set plugin enabled/disabled state.
     *
     * @param pluginId plugin ID
     * @param enabled whether to enable
     * @throws NacosApiException if plugin not found or is critical
     */
    public void setPluginEnabled(String pluginId, boolean enabled) throws NacosApiException {
        setPluginEnabled(pluginId, enabled, false);
    }
    
    /**
     * Set plugin enabled/disabled state.
     *
     * @param pluginId plugin ID
     * @param enabled whether to enable
     * @param localOnly if true, only update local node without Raft sync (for emergency use)
     * @throws NacosApiException if plugin not found or is critical
     */
    public void setPluginEnabled(String pluginId, boolean enabled, boolean localOnly)
        throws NacosApiException {
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Plugin not found: " + pluginId);
        }
        
        try {
            validateStateChange(info, enabled);
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
        }
        if (info.isEnabled() == enabled) {
            LOGGER.debug("[PluginManager] Plugin {} already has enabled={}", pluginId, enabled);
            return;
        }
        
        // LocalOnly mode: only update local memory, skip cluster sync
        if (localOnly) {
            LOGGER.warn(
                "[PluginManager] LocalOnly mode: applying state change to this node only, pluginId={}",
                pluginId);
            try {
                applyStateChange(pluginId, enabled);
            } catch (IllegalArgumentException e) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
            }
            return;
        }
        
        // Synchronize to cluster
        synchronizer.syncStateChange(pluginId, enabled);
        
        LOGGER.info("[PluginManager] Plugin {} status changed to {}", pluginId,
            enabled ? "enabled" : "disabled");
    }
    
    /**
     * Update plugin configuration.
     *
     * @param pluginId plugin ID
     * @param config configuration
     * @throws NacosApiException if plugin not found or not configurable
     */
    public void updatePluginConfig(String pluginId, Map<String, String> config)
        throws NacosApiException {
        updatePluginConfig(pluginId, config, false);
    }
    
    /**
     * Update plugin configuration.
     *
     * @param pluginId plugin ID
     * @param config configuration
     * @param localOnly if true, only update local node without Raft sync (for emergency use)
     * @throws NacosApiException if plugin not found or not configurable
     */
    public void updatePluginConfig(String pluginId, Map<String, String> config, boolean localOnly)
        throws NacosApiException {
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Plugin not found: " + pluginId);
        }
        
        if (!info.isConfigurable()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Plugin does not support configuration: " + pluginId);
        }
        
        PluginConfigSourceType sourceType = localOnly ? PluginConfigSourceType.LOCAL_ONLY
            : PluginConfigSourceType.RUNTIME_PERSISTED;
        Map<String, String> normalizedConfig;
        try {
            normalizedConfig = pluginConfigService.prepareRuntimeUpdate(info, config, sourceType);
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
        }
        
        // LocalOnly mode: only update local memory, skip cluster sync
        if (localOnly) {
            LOGGER.warn(
                "[PluginManager] LocalOnly mode: applying config change to this node only, pluginId={}",
                pluginId);
            try {
                pluginConfigService.updateLocalOnlyConfig(info, pluginInstances.get(pluginId),
                    normalizedConfig);
            } catch (IllegalArgumentException e) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
            } catch (PluginConfigApplyException e) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                    e.getMessage());
            } catch (RuntimeException e) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                    "Failed to apply local-only plugin config: " + pluginId);
            }
            return;
        }
        
        // Synchronize to cluster
        synchronizer.syncConfigChange(pluginId, normalizedConfig);
        
        LOGGER.info("[PluginManager] Plugin {} config updated", pluginId);
    }
    
    /**
     * List all plugins.
     *
     * @return list of plugin info
     */
    public List<PluginInfo> listAllPlugins() {
        return new ArrayList<>(pluginRegistry.values());
    }
    
    /**
     * Get plugin by ID.
     *
     * @param pluginId plugin ID
     * @return optional plugin info
     */
    public Optional<PluginInfo> getPlugin(String pluginId) {
        return Optional.ofNullable(pluginRegistry.get(pluginId));
    }
    
    /**
     * Resolve plugin effective config for detail output.
     *
     * @param pluginInfo plugin info
     * @return plugin config resolution with masked sensitive values
     */
    public PluginConfigResolution resolvePluginConfig(PluginInfo pluginInfo) {
        return pluginConfigService.resolve(pluginInfo, true);
    }
    
    /**
     * Refresh static configuration for all configurable plugins.
     */
    public void refreshStaticPluginConfigs() {
        for (Map.Entry<String, PluginInfo> entry : pluginRegistry.entrySet()) {
            PluginInfo pluginInfo = entry.getValue();
            if (!pluginInfo.isConfigurable()) {
                continue;
            }
            try {
                pluginConfigService.refreshStaticConfig(pluginInfo,
                    pluginInstances.get(entry.getKey()));
            } catch (RuntimeException e) {
                LOGGER.error("[PluginManager] Failed to refresh static plugin config, "
                    + "pluginId={}", entry.getKey(), e);
            }
        }
    }
    
    /**
     * Re-evaluate domain policies after server configuration changes.
     */
    public synchronized void refreshPluginTypePolicies() {
        ensureCriticalTypesAvailable();
        refreshAllCriticalFlags();
    }
    
    /**
     * Get local plugin IDs.
     *
     * @return set of plugin IDs
     */
    public Set<String> getLocalPluginIds() {
        return new HashSet<>(pluginRegistry.keySet());
    }
    
    /**
     * Discover all plugins using SPI-based PluginProvider mechanism.
     * No hard-coded plugin type discovery needed.
     */
    @SuppressWarnings("rawtypes")
    private void discoverAllPlugins() {
        Collection<PluginProvider> providers = NacosServiceLoader.load(PluginProvider.class);
        
        for (PluginProvider provider : providers) {
            try {
                discoverPluginsFromProvider(provider);
            } catch (Exception e) {
                LOGGER.warn("[PluginManager] Failed to discover plugins from provider: {}",
                    provider.getClass().getName(), e);
            }
        }
    }
    
    /**
     * Discover plugins from a single provider.
     *
     * @param provider the plugin provider
     */
    private void discoverPluginsFromProvider(PluginProvider<?> provider) {
        PluginType pluginType = provider.getPluginType();
        Map<String, ?> plugins = provider.getAllPlugins();
        
        if (plugins == null || plugins.isEmpty()) {
            LOGGER.info("[PluginManager] No plugins found for type: {}", pluginType.getType());
            return;
        }
        
        plugins.forEach((name, instance) -> registerPlugin(pluginType, name, instance));
        LOGGER.info("[PluginManager] Discovered {} {} plugins", plugins.size(),
            pluginType.getType());
    }
    
    private void registerPlugin(PluginType type, String name, Object instance) {
        String pluginId = buildPluginId(type.getType(), name);
        
        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(type);
        info.setClassName(instance.getClass().getName());
        info.setCritical(false);
        info.setLoadTimestamp(System.currentTimeMillis());
        boolean defaultEnabled = policyRegistry.isPluginEnabledByDefault(type, name);
        info.setEnabled(defaultEnabled);
        
        // Check if plugin supports configuration
        if (instance instanceof PluginConfigSpec) {
            PluginConfigSpec configSpec = (PluginConfigSpec) instance;
            info.setConfigurable(configSpec.isConfigurable());
            if (info.isConfigurable()) {
                info.setConfigDefinitions(configSpec.getConfigDefinitions());
                info.setConfig(configSpec.getCurrentConfig());
            }
        }
        
        pluginRegistry.put(pluginId, info);
        pluginInstances.put(pluginId, instance);
        pluginStates.put(pluginId, defaultEnabled);
        refreshCriticalFlags(type);
        
        LOGGER.debug("[PluginManager] Registered plugin {} with default enabled={}", pluginId,
            defaultEnabled);
    }
    
    private void loadPersistedData() {
        // Load states
        Map<String, Boolean> states = persistence.loadAllStates();
        states.forEach((pluginId, enabled) -> {
            PluginInfo pluginInfo = pluginRegistry.get(pluginId);
            if (pluginInfo != null) {
                if (enabled == null) {
                    LOGGER.warn("[PluginManager] Ignore null persisted state for plugin {}.",
                        pluginId);
                    return;
                }
                if (pluginInfo.getPluginType().isExclusive()) {
                    if (pluginInfo.isEnabled() != enabled) {
                        LOGGER.warn("[PluginManager] Ignore persisted state for exclusive plugin "
                            + "{}. Selection is controlled by '{}' and requires restart.",
                            pluginId, getSelectionProperty(pluginInfo.getPluginType()));
                    }
                    return;
                }
                pluginStates.put(pluginId, enabled);
                pluginInfo.setEnabled(enabled);
            }
        });
        ensureCriticalTypesAvailable();
        refreshAllCriticalFlags();
        
        // Load configs
        pluginConfigService.initializeRuntimePersistedConfigs();
        pluginRegistry.forEach((pluginId, info) -> {
            if (info.isConfigurable()) {
                pluginConfigService.initializePluginConfig(info, pluginInstances.get(pluginId));
            }
        });
    }
    
    private String getSelectionProperty(PluginType type) {
        return policyRegistry.getSelectionProperty(type);
    }
    
    /**
     * Apply state change.
     * Called by synchronizers after successful synchronization.
     *
     * @param pluginId plugin ID
     * @param enabled whether enabled
     */
    @Override
    public synchronized void applyStateChange(String pluginId, boolean enabled) {
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info != null) {
            validateStateChange(info, enabled);
        }
        pluginStates.put(pluginId, enabled);
        if (info != null) {
            info.setEnabled(enabled);
            refreshCriticalFlags(info.getPluginType());
        }
    }
    
    /**
     * Restore plugin states from a consensus snapshot as one final state map.
     *
     * @param states restored plugin states
     */
    public synchronized void restorePluginStates(Map<String, Boolean> states) {
        Map<String, Boolean> targetStates = new HashMap<>();
        pluginRegistry.forEach((pluginId, info) -> targetStates.put(pluginId, info.isEnabled()));
        states.forEach((pluginId, enabled) -> {
            if (enabled == null) {
                throw new IllegalArgumentException(
                    "Enabled state cannot be null for plugin: " + pluginId);
            }
            PluginInfo info = pluginRegistry.get(pluginId);
            if (info == null) {
                return;
            }
            if (info.getPluginType().isExclusive()) {
                if (info.isEnabled() != enabled) {
                    LOGGER.warn("[PluginManager] Ignore snapshot state for exclusive plugin {}. "
                        + "Selection is controlled by '{}' and requires restart.", pluginId,
                        getSelectionProperty(info.getPluginType()));
                }
                return;
            }
            targetStates.put(pluginId, enabled);
        });
        validateCriticalStates(targetStates);
        pluginRegistry.forEach((pluginId, info) -> {
            if (info.getPluginType().isExclusive()) {
                return;
            }
            boolean targetEnabled = targetStates.get(pluginId);
            if (states.containsKey(pluginId) || info.isEnabled() != targetEnabled) {
                pluginStates.put(pluginId, targetEnabled);
                info.setEnabled(targetEnabled);
                persistence.saveState(pluginId, targetEnabled);
            }
        });
        states.forEach((pluginId, enabled) -> {
            if (!pluginRegistry.containsKey(pluginId)) {
                persistence.saveState(pluginId, enabled);
            }
        });
        refreshAllCriticalFlags();
    }
    
    private void validateStateChange(PluginInfo info, boolean enabled) {
        if (info.isEnabled() == enabled) {
            return;
        }
        PluginType type = info.getPluginType();
        if (type.isCritical() && policyRegistry.isActive(type)) {
            Map<String, Boolean> targetStates = getCurrentStates();
            targetStates.put(info.getPluginId(), enabled);
            String validationError = getCriticalValidationError(type, targetStates);
            if (validationError != null) {
                throw new IllegalArgumentException(validationError);
            }
        }
        if (type.isExclusive()) {
            throw new IllegalArgumentException("Plugin selection for exclusive type '"
                + type.getType() + "' requires restart. Update '" + getSelectionProperty(type)
                + "' instead.");
        }
    }
    
    private long countEnabledPlugins(PluginType type) {
        return pluginRegistry.values().stream()
            .filter(info -> type == info.getPluginType() && info.isEnabled()).count();
    }
    
    private void validateCriticalStates(Map<String, Boolean> targetStates) {
        for (PluginType type : PluginType.values()) {
            String validationError = getCriticalValidationError(type, targetStates);
            if (validationError != null) {
                LOGGER.error("[PluginManager] {}", validationError);
                throw new IllegalStateException(validationError);
            }
        }
    }
    
    private void ensureCriticalTypesAvailable() {
        validateCriticalStates(getCurrentStates());
    }
    
    private void refreshAllCriticalFlags() {
        for (PluginType type : PluginType.values()) {
            refreshCriticalFlags(type);
        }
    }
    
    private void refreshCriticalFlags(PluginType type) {
        if (!type.isCritical() || !policyRegistry.isActive(type)) {
            pluginRegistry.values().stream().filter(info -> type == info.getPluginType())
                .forEach(info -> info.setCritical(false));
            return;
        }
        Set<String> requiredPlugins = policyRegistry.getRequiredPluginNames(type);
        long enabledCount = countEnabledPlugins(type);
        pluginRegistry.values().stream().filter(info -> type == info.getPluginType())
            .forEach(info -> info.setCritical(info.isEnabled()
                && (requiredPlugins.contains(info.getPluginName())
                    || requiredPlugins.isEmpty() && enabledCount <= 1)));
    }
    
    private Map<String, Boolean> getCurrentStates() {
        Map<String, Boolean> result = new HashMap<>();
        pluginRegistry.forEach((pluginId, info) -> result.put(pluginId, info.isEnabled()));
        return result;
    }
    
    private String getCriticalValidationError(PluginType type,
        Map<String, Boolean> targetStates) {
        Map<String, Boolean> implementations = new HashMap<>();
        for (PluginInfo info : pluginRegistry.values()) {
            if (type == info.getPluginType()) {
                implementations.put(info.getPluginName(),
                    Boolean.TRUE.equals(targetStates.get(info.getPluginId())));
            }
        }
        return PluginTypePolicyRegistry.getCriticalValidationError(policyRegistry, type,
            implementations);
    }
    
    /**
     * Apply config change.
     * Called by synchronizers after successful synchronization.
     *
     * @param pluginId plugin ID
     * @param config configuration
     */
    @Override
    public void applyConfigChange(String pluginId, Map<String, String> config) {
        PluginInfo info = pluginRegistry.get(pluginId);
        pluginConfigService.applyRuntimePersistedConfig(pluginId, info,
            pluginInstances.get(pluginId), config);
    }
    
    /**
     * Get all runtime persisted configs for a consensus snapshot.
     *
     * @return complete runtime persisted config snapshot
     */
    public Map<String, Map<String, String>> getRuntimePersistedConfigs() {
        return pluginConfigService.getAllRuntimePersistedConfigs();
    }
    
    /**
     * Restore all runtime persisted configs from a consensus snapshot.
     *
     * @param configs complete runtime persisted config snapshot
     */
    public synchronized void restorePluginConfigs(Map<String, Map<String, String>> configs) {
        pluginConfigService.restoreRuntimePersistedConfigs(configs);
        pluginRegistry.forEach((pluginId, info) -> {
            if (info.isConfigurable()) {
                pluginConfigService.applyRestoredPluginConfig(info,
                    pluginInstances.get(pluginId));
            }
        });
    }
    
    /**
     * Check if plugin is available locally.
     *
     * @param pluginId plugin ID
     * @return true if plugin exists in registry
     */
    public boolean isPluginAvailable(String pluginId) {
        return pluginRegistry.containsKey(pluginId);
    }
    
}
