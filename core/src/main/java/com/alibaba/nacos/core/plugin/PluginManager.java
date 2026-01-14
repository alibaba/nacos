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
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginStateChecker;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.plugin.sync.PluginStateApplier;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class PluginManager implements PluginStateChecker, PluginStateApplier, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    /**
     * Configuration property for auth plugin type.
     */
    private static final String AUTH_TYPE_PROPERTY = "nacos.core.auth.system.type";

    /**
     * Default auth plugin type.
     */
    private static final String AUTH_TYPE_DEFAULT = "nacos";

    /**
     * Configuration property for datasource platform (new).
     */
    private static final String DATASOURCE_PLATFORM_PROPERTY = "spring.sql.init.platform";

    /**
     * Configuration property for datasource platform (legacy).
     */
    private static final String DATASOURCE_PLATFORM_PROPERTY_OLD = "spring.datasource.platform";

    /**
     * Default datasource platform.
     */
    private static final String DATASOURCE_PLATFORM_DEFAULT = "mysql";

    /**
     * Plugin registry: pluginId -> PluginInfo.
     */
    private final Map<String, PluginInfo> pluginRegistry = new ConcurrentHashMap<>();

    /**
     * Plugin states: pluginId -> enabled.
     */
    private final Map<String, Boolean> pluginStates = new ConcurrentHashMap<>();

    /**
     * Plugin configurations: pluginId -> config.
     */
    private final Map<String, Map<String, String>> pluginConfigs = new ConcurrentHashMap<>();

    /**
     * Plugin instances: pluginId -> instance.
     */
    private final Map<String, Object> pluginInstances = new ConcurrentHashMap<>();

    private final PluginStatePersistenceService persistence;

    /**
     * Plugin state synchronizer for cluster synchronization.
     */
    private final PluginStateSynchronizer synchronizer;

    public PluginManager(PluginStatePersistenceService persistence,
            @Lazy PluginStateSynchronizer synchronizer) {
        this.persistence = persistence;
        this.synchronizer = synchronizer;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Register to static holder
        PluginStateCheckerHolder.setInstance(this);

        // Discover all plugins
        discoverAllPlugins();

        // Load persisted states and configs
        loadPersistedData();

        LOGGER.info("[PluginManager] Initialized, {} plugins discovered", pluginRegistry.size());
    }

    @Override
    public boolean isPluginEnabled(String pluginType, String pluginName) {
        String pluginId = pluginType + ":" + pluginName;
        return pluginStates.getOrDefault(pluginId, true);
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
    public void setPluginEnabled(String pluginId, boolean enabled, boolean localOnly) throws NacosApiException {
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Plugin not found: " + pluginId);
        }

        // Critical plugins cannot be disabled
        if (info.isCritical() && !enabled) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Cannot disable critical plugin: " + pluginId);
        }

        // LocalOnly mode: only update local memory, skip cluster sync
        if (localOnly) {
            LOGGER.warn("[PluginManager] LocalOnly mode: applying state change to this node only, pluginId={}", pluginId);
            applyStateChange(pluginId, enabled);
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
    public void updatePluginConfig(String pluginId, Map<String, String> config) throws NacosApiException {
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
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Plugin does not support configuration: " + pluginId);
        }

        // Validate config
        validateConfig(info, config);

        // LocalOnly mode: only update local memory, skip cluster sync
        if (localOnly) {
            LOGGER.warn("[PluginManager] LocalOnly mode: applying config change to this node only, pluginId={}",
                    pluginId);
            applyConfigChange(pluginId, config);
            return;
        }

        // Synchronize to cluster
        synchronizer.syncConfigChange(pluginId, config);

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
        LOGGER.info("[PluginManager] Discovered {} {} plugins", plugins.size(), pluginType.getType());
    }

    private void registerPlugin(PluginType type, String name, Object instance) {
        String pluginId = type.getType() + ":" + name;

        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(type);
        info.setClassName(instance.getClass().getName());
        info.setCritical(CriticalPluginConfig.isCritical(pluginId));
        info.setLoadTimestamp(System.currentTimeMillis());
        boolean defaultEnabled = calculateDefaultEnabled(type, name);
        info.setEnabled(defaultEnabled);

        // Check if plugin supports configuration
        if (instance instanceof PluginConfigSpec) {
            PluginConfigSpec configSpec = (PluginConfigSpec) instance;
            info.setConfigurable(true);
            info.setConfigDefinitions(configSpec.getConfigDefinitions());
            info.setConfig(configSpec.getCurrentConfig());
        }

        pluginRegistry.put(pluginId, info);
        pluginInstances.put(pluginId, instance);
        pluginStates.put(pluginId, defaultEnabled);

        LOGGER.debug("[PluginManager] Registered plugin {} with default enabled={}", pluginId, defaultEnabled);
    }

    private void loadPersistedData() {
        // Load states
        Map<String, Boolean> states = persistence.loadAllStates();
        states.forEach((pluginId, enabled) -> {
            if (pluginRegistry.containsKey(pluginId)) {
                pluginStates.put(pluginId, enabled);
                pluginRegistry.get(pluginId).setEnabled(enabled);
            }
        });

        // Load configs
        Map<String, Map<String, String>> configs = persistence.loadAllConfigs();
        configs.forEach((pluginId, config) -> {
            if (pluginRegistry.containsKey(pluginId)) {
                pluginConfigs.put(pluginId, config);
                pluginRegistry.get(pluginId).setConfig(config);
                applyConfigToPlugin(pluginId, config);
            }
        });
    }

    private void validateConfig(PluginInfo info, Map<String, String> config) throws NacosApiException {
        List<ConfigItemDefinition> definitions = info.getConfigDefinitions();
        if (definitions == null) {
            return;
        }

        for (ConfigItemDefinition def : definitions) {
            String value = config.get(def.getKey());
            boolean valueIsEmpty = value == null || value.isEmpty();
            if (def.isRequired() && valueIsEmpty) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "Required config missing: " + def.getKey());
            }
        }
    }

    private void applyConfigToPlugin(String pluginId, Map<String, String> config) {
        Object instance = pluginInstances.get(pluginId);
        if (instance instanceof PluginConfigSpec) {
            try {
                ((PluginConfigSpec) instance).applyConfig(config);
            } catch (Exception e) {
                LOGGER.error("[PluginManager] Failed to apply config to plugin {}", pluginId, e);
                throw new RuntimeException("Failed to apply config to plugin: " + pluginId, e);
            }
        }
    }

    /**
     * Calculate the default enabled status for a plugin based on its type and configuration.
     * For exclusive plugins (AUTH, DATASOURCE), only the configured one is enabled by default.
     * For non-exclusive plugins, all are enabled by default.
     *
     * @param type plugin type
     * @param pluginName plugin name
     * @return default enabled status
     */
    private boolean calculateDefaultEnabled(PluginType type, String pluginName) {
        switch (type) {
            case AUTH:
                String authType = EnvUtil.getProperty(AUTH_TYPE_PROPERTY, AUTH_TYPE_DEFAULT);
                return pluginName.equalsIgnoreCase(authType);
            case DATASOURCE_DIALECT:
                String platform = getDatasourcePlatform();
                return pluginName.equalsIgnoreCase(platform);
            default:
                // Non-exclusive plugins are enabled by default
                return true;
        }
    }

    /**
     * Get the configured datasource platform.
     *
     * @return datasource platform name
     */
    private String getDatasourcePlatform() {
        String platform = EnvUtil.getProperty(DATASOURCE_PLATFORM_PROPERTY);
        if (StringUtils.isBlank(platform)) {
            platform = EnvUtil.getProperty(DATASOURCE_PLATFORM_PROPERTY_OLD);
        }
        return StringUtils.isBlank(platform) ? DATASOURCE_PLATFORM_DEFAULT : platform;
    }

    /**
     * Apply state change.
     * Called by synchronizers after successful synchronization.
     *
     * @param pluginId plugin ID
     * @param enabled whether enabled
     */
    @Override
    public void applyStateChange(String pluginId, boolean enabled) {
        pluginStates.put(pluginId, enabled);
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info != null) {
            info.setEnabled(enabled);
        }
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
        pluginConfigs.put(pluginId, new HashMap<>(config));
        PluginInfo info = pluginRegistry.get(pluginId);
        if (info != null) {
            info.setConfig(config);
        }
        applyConfigToPlugin(pluginId, config);
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
