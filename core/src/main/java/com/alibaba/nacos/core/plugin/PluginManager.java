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
import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginStartupLifecycle;
import com.alibaba.nacos.api.plugin.PluginStateChecker;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplyException;
import com.alibaba.nacos.core.plugin.config.PluginConfigDefinitionNormalizer;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.config.PluginConfigService;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.plugin.sync.PluginStateApplier;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
public class PluginManager implements PluginStateChecker, PluginStateApplier {
    
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
     * Plugin states derived from startup policy before persisted overrides are applied.
     */
    private final Map<String, Boolean> pluginDefaultStates = new ConcurrentHashMap<>();
    
    /**
     * Plugin instances: pluginId -> instance.
     */
    private final Map<String, Object> pluginInstances = new ConcurrentHashMap<>();
    
    /**
     * Accepted masked configuration snapshots for pre-context plugins.
     */
    private final Map<String, PluginConfigResolution> preContextConfigResolutions =
        new ConcurrentHashMap<>();
    
    /**
     * Discovered lightweight providers grouped by plugin type.
     */
    private final Map<PluginType, List<PluginProvider<?>>> pluginProviders =
        new EnumMap<>(PluginType.class);
    
    /**
     * Providers whose implementation instances have already been loaded.
     */
    private final Set<PluginProvider<?>> loadedProviders = new HashSet<>();
    
    /**
     * Configurable plugins that have been discovered but not initialized successfully.
     */
    private final Set<String> pendingConfigInitializationPluginIds = new HashSet<>();
    
    /**
     * Startup lifecycle plugins that have been discovered but not initialized successfully.
     */
    private final Set<String> pendingStartupInitializationPluginIds = new HashSet<>();
    
    private final PluginConfigService pluginConfigService;
    
    private final PluginStatePersistenceService persistence;
    
    private final PluginTypePolicyRegistry policyRegistry;
    
    private final PreContextPluginInitializationResult preContextInitializationResult;
    
    /**
     * Plugin state synchronizer for cluster synchronization.
     */
    private final PluginStateSynchronizer synchronizer;
    
    private boolean initialized;
    
    @Autowired
    public PluginManager(PluginStatePersistenceService persistence,
        ObjectProvider<PluginStateSynchronizer> synchronizerProvider,
        ObjectProvider<PreContextPluginInitializationResult> preContextResultProvider) {
        this(persistence, synchronizerProvider.getIfAvailable(), new PluginTypePolicyRegistry(),
            preContextResultProvider.getIfAvailable(
                PreContextPluginInitializationResult::empty));
    }
    
    public PluginManager(PluginStatePersistenceService persistence,
        PluginStateSynchronizer synchronizer,
        ObjectProvider<PreContextPluginInitializationResult> preContextResultProvider) {
        this(persistence, synchronizer, new PluginTypePolicyRegistry(),
            preContextResultProvider.getIfAvailable(
                PreContextPluginInitializationResult::empty));
    }
    
    public PluginManager(PluginStatePersistenceService persistence,
        PluginStateSynchronizer synchronizer) {
        this(persistence, synchronizer, new PluginTypePolicyRegistry(),
            PreContextPluginInitializationResult.empty());
    }
    
    PluginManager(PluginStatePersistenceService persistence,
        PluginStateSynchronizer synchronizer, PluginTypePolicyRegistry policyRegistry) {
        this(persistence, synchronizer, policyRegistry,
            PreContextPluginInitializationResult.empty());
    }
    
    PluginManager(PluginStatePersistenceService persistence,
        PluginStateSynchronizer synchronizer, PluginTypePolicyRegistry policyRegistry,
        PreContextPluginInitializationResult preContextInitializationResult) {
        this.persistence = persistence;
        this.synchronizer = synchronizer;
        this.policyRegistry = policyRegistry;
        this.preContextInitializationResult = preContextInitializationResult;
        this.pluginConfigService = new PluginConfigService(persistence);
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
        
        importPreContextPlugins();
        
        // Discover lightweight providers, then load only currently required plugin types.
        discoverPluginProviders();
        loadEnabledPluginTypes();
        
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
            validateRuntimeChangeSupported(info, "state");
            validateStateChangeInternal(info, enabled);
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
        
        if (EnvUtil.getStandaloneMode()) {
            applyStandaloneStateChange(pluginId, enabled);
        } else {
            getClusterSynchronizer().syncStateChange(pluginId, enabled);
        }
        
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
        
        try {
            validateRuntimeChangeSupported(info, "configuration");
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
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
        } catch (PluginPersistenceException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                e.getMessage());
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
        
        if (EnvUtil.getStandaloneMode()) {
            applyStandaloneConfigChange(pluginId, normalizedConfig);
        } else {
            getClusterSynchronizer().syncConfigChange(pluginId, normalizedConfig);
        }
        
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
        if (isPreContextPlugin(pluginInfo)) {
            return preContextConfigResolutions.get(pluginInfo.getPluginId());
        }
        return pluginConfigService.resolve(pluginInfo, true);
    }
    
    /**
     * Refresh static configuration for all configurable plugins.
     */
    public void refreshStaticPluginConfigs() {
        for (Map.Entry<String, PluginInfo> entry : pluginRegistry.entrySet()) {
            PluginInfo pluginInfo = entry.getValue();
            if (!pluginInfo.isConfigurable() || isPreContextPlugin(pluginInfo)) {
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
        Set<String> loadedPluginIds = loadEnabledPluginTypes();
        loadPersistedStates(loadedPluginIds);
        ensureCriticalTypesAvailable();
        refreshAllCriticalFlags();
        initializePluginConfigs(pendingConfigInitializationPluginIds);
        initializePluginLifecycles(pendingStartupInitializationPluginIds);
    }
    
    /**
     * Get local plugin IDs.
     *
     * @return set of plugin IDs
     */
    public Set<String> getLocalPluginIds() {
        return new HashSet<>(pluginRegistry.keySet());
    }
    
    private void importPreContextPlugins() {
        preContextInitializationResult.getPluginInfos().forEach((pluginId, pluginInfo) -> {
            Object instance =
                preContextInitializationResult.getPluginInstances().get(pluginId);
            if (pluginInfo == null || instance == null) {
                LOGGER.warn("[PluginManager] Ignore invalid pre-context plugin, pluginId={}, "
                    + "infoPresent={}, instancePresent={}.", pluginId, pluginInfo != null,
                    instance != null);
                return;
            }
            PluginInfo existing = pluginRegistry.putIfAbsent(pluginId, pluginInfo);
            if (existing != null) {
                LOGGER.warn("[PluginManager] Ignore duplicate pre-context plugin, pluginId={}, "
                    + "existingClass={}, ignoredClass={}.", pluginId, existing.getClassName(),
                    instance.getClass().getName());
                return;
            }
            pluginInstances.put(pluginId, instance);
            pluginDefaultStates.put(pluginId, pluginInfo.isEnabled());
            pluginStates.put(pluginId, pluginInfo.isEnabled());
            PluginConfigResolution resolution =
                preContextInitializationResult.getConfigResolutions().get(pluginId);
            if (resolution != null) {
                preContextConfigResolutions.put(pluginId, resolution);
            }
        });
    }
    
    /**
     * Discover lightweight plugin providers without loading their implementation instances.
     */
    @SuppressWarnings("rawtypes")
    private void discoverPluginProviders() {
        Collection<PluginProvider> providers = NacosServiceLoader.load(PluginProvider.class);
        List<PluginProvider> orderedProviders = new ArrayList<>(providers);
        orderedProviders.sort(Comparator.comparingInt(PluginProvider::getOrder));
        
        for (PluginProvider provider : orderedProviders) {
            try {
                PluginType pluginType = provider.getPluginType();
                if (pluginType == null) {
                    LOGGER.warn("[PluginManager] Ignore plugin provider without type: {}",
                        provider.getClass().getName());
                    continue;
                }
                if (PluginInitializationPhase.STANDARD != pluginType.getInitializationPhase()) {
                    continue;
                }
                pluginProviders.computeIfAbsent(pluginType, key -> new ArrayList<>()).add(provider);
            } catch (Exception e) {
                LOGGER.warn("[PluginManager] Failed to identify plugin provider: {}",
                    provider.getClass().getName(), e);
            }
        }
    }
    
    private Set<String> loadEnabledPluginTypes() {
        Set<String> result = new HashSet<>();
        for (Map.Entry<PluginType, List<PluginProvider<?>>> entry : pluginProviders.entrySet()) {
            PluginType pluginType = entry.getKey();
            if (!policyRegistry.shouldLoad(pluginType)) {
                continue;
            }
            for (PluginProvider<?> provider : entry.getValue()) {
                if (loadedProviders.contains(provider)) {
                    continue;
                }
                try {
                    result.addAll(discoverPluginsFromProvider(pluginType, provider));
                    loadedProviders.add(provider);
                } catch (Exception e) {
                    LOGGER.warn("[PluginManager] Failed to discover plugins from provider: {}",
                        provider.getClass().getName(), e);
                }
            }
        }
        return result;
    }
    
    /**
     * Discover plugins from a single provider.
     *
     * @param provider the plugin provider
     */
    private Set<String> discoverPluginsFromProvider(PluginType pluginType,
        PluginProvider<?> provider) {
        Map<String, ?> plugins = provider.getAllPlugins();
        
        if (plugins == null || plugins.isEmpty()) {
            LOGGER.info("[PluginManager] No plugins found for type: {}", pluginType.getType());
            return new HashSet<>();
        }
        
        Set<String> result = new HashSet<>();
        plugins.forEach((name, instance) -> {
            String pluginId = registerPlugin(pluginType, name, instance);
            if (pluginId != null) {
                result.add(pluginId);
            }
        });
        LOGGER.info("[PluginManager] Discovered {} {} plugins", result.size(),
            pluginType.getType());
        return result;
    }
    
    private String registerPlugin(PluginType type, String name, Object instance) {
        if (StringUtils.isBlank(name) || instance == null) {
            LOGGER.warn("[PluginManager] Ignore invalid {} plugin, name={}, instancePresent={}.",
                type.getType(), name, instance != null);
            return null;
        }
        String pluginId = buildPluginId(type.getType(), name);
        PluginInfo existing = pluginRegistry.get(pluginId);
        if (existing != null) {
            LOGGER.warn("[PluginManager] Ignore duplicate plugin, pluginId={}, existingClass={}, "
                + "ignoredClass={}.", pluginId, existing.getClassName(),
                instance.getClass().getName());
            return null;
        }
        
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
                info.setConfigDefinitions(PluginConfigDefinitionNormalizer.normalize(pluginId,
                    configSpec.getConfigDefinitions(), type.getInitializationPhase()));
                info.setConfig(configSpec.getCurrentConfig());
            }
        }
        
        pluginRegistry.put(pluginId, info);
        pluginInstances.put(pluginId, instance);
        pluginDefaultStates.put(pluginId, defaultEnabled);
        pluginStates.put(pluginId, defaultEnabled);
        if (info.isConfigurable()) {
            pendingConfigInitializationPluginIds.add(pluginId);
        }
        if (instance instanceof PluginStartupLifecycle) {
            pendingStartupInitializationPluginIds.add(pluginId);
        }
        refreshCriticalFlags(type);
        
        LOGGER.debug("[PluginManager] Registered plugin {} with default enabled={}", pluginId,
            defaultEnabled);
        return pluginId;
    }
    
    private void loadPersistedData() {
        Set<String> standardPluginIds = getStandardPluginIds();
        loadPersistedStates(standardPluginIds);
        ensureCriticalTypesAvailable();
        refreshAllCriticalFlags();
        
        // Load configs
        pluginConfigService.initializeRuntimePersistedConfigs();
        initializePluginConfigs(standardPluginIds);
        initializePluginLifecycles(pendingStartupInitializationPluginIds);
    }
    
    private void loadPersistedStates(Collection<String> pluginIds) {
        if (pluginIds.isEmpty()) {
            return;
        }
        Set<String> targetPluginIds = new HashSet<>(pluginIds);
        Map<String, Boolean> states = persistence.loadAllStates();
        states.forEach((pluginId, enabled) -> {
            if (!targetPluginIds.contains(pluginId)) {
                return;
            }
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
    }
    
    private void initializePluginConfigs(Collection<String> pluginIds) {
        for (String pluginId : new HashSet<>(pluginIds)) {
            PluginInfo info = pluginRegistry.get(pluginId);
            if (info.isConfigurable() && !isPreContextPlugin(info)) {
                pluginConfigService.initializePluginConfig(info, pluginInstances.get(pluginId));
                pendingConfigInitializationPluginIds.remove(pluginId);
            }
        }
    }
    
    private void initializePluginLifecycles(Collection<String> pluginIds) {
        for (String pluginId : new HashSet<>(pluginIds)) {
            PluginInfo info = pluginRegistry.get(pluginId);
            if (!info.isEnabled() || isPreContextPlugin(info)) {
                continue;
            }
            PluginStartupLifecycle lifecycle =
                (PluginStartupLifecycle) pluginInstances.get(pluginId);
            try {
                lifecycle.initialize();
                pendingStartupInitializationPluginIds.remove(pluginId);
            } catch (RuntimeException e) {
                LOGGER.error("[PluginManager] Failed to initialize plugin startup lifecycle, "
                    + "pluginId={}", pluginId, e);
                throw new IllegalStateException(
                    "Failed to initialize plugin startup lifecycle: " + pluginId, e);
            }
        }
    }
    
    private String getSelectionProperty(PluginType type) {
        return policyRegistry.getSelectionProperty(type);
    }
    
    /**
     * Validate a state change without mutating the current state.
     *
     * @param pluginId plugin ID
     * @param enabled whether enabled
     */
    @Override
    public synchronized void validateStateChange(String pluginId, boolean enabled) {
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info != null) {
            validateRuntimeChangeSupported(info, "state");
            validateStateChangeInternal(info, enabled);
        } else if (isPreContextPluginId(pluginId)) {
            throw new IllegalArgumentException(
                "Pre-context plugin state requires restart: " + pluginId);
        }
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
        validateStateChange(pluginId, enabled);
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
        Map<String, Boolean> applicableStates = filterStandardEntries(states,
            "persisted plugin state");
        for (Map.Entry<String, Boolean> entry : applicableStates.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(
                    "Enabled state cannot be null for plugin: " + entry.getKey());
            }
        }
        if (!initialized) {
            persistence.replaceAllStates(applicableStates);
            LOGGER.info("[PluginManager] Staged {} plugin states restored before plugin "
                + "discovery; critical validation will run during initialization.",
                applicableStates.size());
            return;
        }
        Map<String, Boolean> targetStates = new HashMap<>(pluginDefaultStates);
        applicableStates.forEach((pluginId, enabled) -> {
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
        persistence.replaceAllStates(applicableStates);
        pluginRegistry.forEach((pluginId, info) -> {
            if (info.getPluginType().isExclusive() || isPreContextPlugin(info)) {
                return;
            }
            boolean targetEnabled = targetStates.get(pluginId);
            pluginStates.put(pluginId, targetEnabled);
            info.setEnabled(targetEnabled);
        });
        refreshAllCriticalFlags();
    }
    
    private void validateStateChangeInternal(PluginInfo info, boolean enabled) {
        validateRuntimeChangeSupported(info, "state");
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
    
    private Set<String> getStandardPluginIds() {
        Set<String> result = new HashSet<>();
        pluginRegistry.forEach((pluginId, pluginInfo) -> {
            if (!isPreContextPlugin(pluginInfo)) {
                result.add(pluginId);
            }
        });
        return result;
    }
    
    private boolean isPreContextPlugin(PluginInfo pluginInfo) {
        return pluginInfo != null && PluginInitializationPhase.PRE_CONTEXT == pluginInfo
            .getPluginType().getInitializationPhase();
    }
    
    private boolean isPreContextPluginId(String pluginId) {
        if (pluginId == null) {
            return false;
        }
        PluginInfo pluginInfo = pluginRegistry.get(pluginId);
        if (pluginInfo != null) {
            return isPreContextPlugin(pluginInfo);
        }
        int separator = pluginId.indexOf(':');
        String typeName = separator < 0 ? pluginId : pluginId.substring(0, separator);
        for (PluginType type : PluginType.values()) {
            if (type.getType().equals(typeName)) {
                return PluginInitializationPhase.PRE_CONTEXT == type.getInitializationPhase();
            }
        }
        return false;
    }
    
    private void validateRuntimeChangeSupported(PluginInfo pluginInfo, String operation) {
        if (isPreContextPlugin(pluginInfo)) {
            throw new IllegalArgumentException("Pre-context plugin " + operation
                + " requires restart: " + pluginInfo.getPluginId());
        }
    }
    
    private PluginStateSynchronizer getClusterSynchronizer() throws NacosApiException {
        if (synchronizer == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Plugin state synchronizer is unavailable in cluster mode");
        }
        return synchronizer;
    }
    
    private void applyStandaloneStateChange(String pluginId, boolean enabled)
        throws NacosApiException {
        try {
            persistence.saveState(pluginId, enabled);
            applyStateChange(pluginId, enabled);
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
        } catch (PluginPersistenceException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                "Failed to persist plugin state: " + pluginId);
        }
    }
    
    private void applyStandaloneConfigChange(String pluginId, Map<String, String> config)
        throws NacosApiException {
        try {
            applyConfigChange(pluginId, config);
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e.getMessage());
        } catch (PluginConfigApplyException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                e.getMessage());
        } catch (RuntimeException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                "Failed to apply or persist plugin config: " + pluginId);
        }
    }
    
    private <T> Map<String, T> filterStandardEntries(Map<String, T> source,
        String sourceDescription) {
        Map<String, T> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((pluginId, value) -> {
            if (isPreContextPluginId(pluginId)) {
                LOGGER.warn("[PluginManager] Ignore {} for pre-context plugin {}.",
                    sourceDescription, pluginId);
            } else {
                result.put(pluginId, value);
            }
        });
        return result;
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
        if (isPreContextPluginId(pluginId)) {
            throw new IllegalArgumentException(
                "Pre-context plugin configuration requires restart: " + pluginId);
        }
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
        return filterStandardEntries(pluginConfigService.getAllRuntimePersistedConfigs(),
            "runtime persisted plugin config");
    }
    
    /**
     * Restore all runtime persisted configs from a consensus snapshot.
     *
     * @param configs complete runtime persisted config snapshot
     */
    public synchronized void restorePluginConfigs(Map<String, Map<String, String>> configs) {
        Map<String, Map<String, String>> applicableConfigs =
            filterStandardEntries(configs, "runtime persisted plugin config");
        pluginConfigService.restoreRuntimePersistedConfigs(applicableConfigs);
        pluginRegistry.forEach((pluginId, info) -> {
            if (info.isConfigurable() && !isPreContextPlugin(info)) {
                pluginConfigService.applyRestoredPluginConfig(info,
                    pluginInstances.get(pluginId));
            }
        });
    }
    
    Map<String, Boolean> getPersistedPluginStates() {
        return filterStandardEntries(persistence.loadAllStates(), "persisted plugin state");
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
    
    /**
     * Release internal plugin configuration storage resources.
     */
    @PreDestroy
    public void shutdown() {
        pluginConfigService.shutdown();
    }
    
}
