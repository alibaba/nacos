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

import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.config.storage.PluginConfigStorage;
import com.alibaba.nacos.core.plugin.config.storage.PluginConfigStorageProvider;
import com.alibaba.nacos.core.plugin.config.storage.PluginConfigStorageRegistry;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolver for runtime persisted plugin configuration source.
 *
 * @author Nacos
 */
class RuntimePersistedPluginConfigSourceResolver extends AbstractMapPluginConfigSourceResolver
    implements PersistedPluginConfigSourceResolver {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(RuntimePersistedPluginConfigSourceResolver.class);
    
    private final PluginConfigStorageProvider storageProvider;
    
    private final PluginConfigKeyResolver keyResolver = new PluginConfigKeyResolver();
    
    private volatile PluginConfigStorage storage;
    
    private volatile boolean initialized;
    
    private volatile boolean available;
    
    private volatile Throwable unavailableCause;
    
    RuntimePersistedPluginConfigSourceResolver() {
        this(new MemoryPluginConfigStorageProvider());
    }
    
    RuntimePersistedPluginConfigSourceResolver(PluginStatePersistenceService persistence) {
        this(new PluginConfigStorageRegistry(persistence).getSelectedProvider());
    }
    
    RuntimePersistedPluginConfigSourceResolver(PluginConfigStorageProvider storageProvider) {
        this.storageProvider = storageProvider;
        this.available = storageProvider != null;
    }
    
    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (storageProvider == null) {
            markUnavailable(null);
            return;
        }
        try {
            storage = storageProvider.createStorage();
            if (storage == null) {
                throw new IllegalStateException("Storage provider returned null");
            }
            storage.initialize();
            Map<String, Map<String, String>> loadedConfigs = storage.loadAllConfigs();
            replaceAllConfigs(loadedConfigs == null ? Collections.emptyMap() : loadedConfigs);
            available = true;
            LOGGER.info("[RuntimePersistedPluginConfigSourceResolver] Initialized storage: {}",
                getStorageName());
        } catch (RuntimeException | LinkageError e) {
            markUnavailable(e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        initialize();
        return available;
    }
    
    @Override
    public void initializeConfig(PluginInfo pluginInfo) {
        Map<String, String> config = super.getConfig(pluginInfo);
        if (config != null) {
            super.updateConfig(pluginInfo.getPluginId(), keyResolver.normalizeConfig(pluginInfo,
                config));
        }
    }
    
    @Override
    public synchronized void updateConfig(String pluginId, Map<String, String> config) {
        PluginConfigStorage currentStorage = getAvailableStorage();
        Map<String, String> configToStore = config == null ? Collections.emptyMap()
            : new HashMap<>(config);
        currentStorage.saveConfig(pluginId, configToStore);
        super.updateConfig(pluginId, configToStore);
    }
    
    @Override
    public Map<String, Map<String, String>> getAllConfigs() {
        return getAllConfigsSnapshot();
    }
    
    @Override
    public synchronized void restoreConfigs(Map<String, Map<String, String>> configs) {
        PluginConfigStorage currentStorage = getAvailableStorage();
        Map<String, Map<String, String>> configsToRestore = copyConfigs(configs);
        currentStorage.replaceAllConfigs(configsToRestore);
        replaceAllConfigs(configsToRestore);
    }
    
    @Override
    public synchronized void shutdown() {
        available = false;
        shutdownStorage();
    }
    
    @Override
    public PluginConfigSourceType getSourceType() {
        return PluginConfigSourceType.RUNTIME_PERSISTED;
    }
    
    private Map<String, Map<String, String>> copyConfigs(
        Map<String, Map<String, String>> configs) {
        Map<String, Map<String, String>> result = new HashMap<>();
        if (configs != null) {
            configs.forEach((pluginId, config) -> result.put(pluginId,
                config == null ? Collections.emptyMap() : new HashMap<>(config)));
        }
        return result;
    }
    
    private PluginConfigStorage getAvailableStorage() {
        initialize();
        if (!available || storage == null) {
            throw new PluginPersistenceException("Runtime persisted plugin config storage '"
                + getStorageName() + "' is unavailable", unavailableCause);
        }
        return storage;
    }
    
    private void markUnavailable(Throwable cause) {
        available = false;
        unavailableCause = cause;
        replaceAllConfigs(Collections.emptyMap());
        shutdownStorage();
        if (cause == null) {
            LOGGER.error("[RuntimePersistedPluginConfigSourceResolver] No plugin config storage "
                + "is enabled. Continue startup without runtime persisted config.");
        } else {
            LOGGER.error("[RuntimePersistedPluginConfigSourceResolver] Failed to initialize or "
                + "read storage '{}'. Continue startup without runtime persisted config.",
                getStorageName(), cause);
        }
    }
    
    private void shutdownStorage() {
        PluginConfigStorage currentStorage = storage;
        storage = null;
        if (currentStorage == null) {
            return;
        }
        try {
            currentStorage.shutdown();
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("[RuntimePersistedPluginConfigSourceResolver] Failed to shut down storage "
                + "'{}'.", getStorageName(), e);
        }
    }
    
    private String getStorageName() {
        if (storageProvider == null) {
            return "none";
        }
        try {
            String name = storageProvider.getName();
            return name == null ? storageProvider.getClass().getName() : name;
        } catch (RuntimeException | LinkageError e) {
            return storageProvider.getClass().getName();
        }
    }
    
    private static class MemoryPluginConfigStorageProvider
        implements PluginConfigStorageProvider {
        
        @Override
        public String getName() {
            return "memory";
        }
        
        @Override
        public PluginConfigStorage createStorage() {
            return new PluginConfigStorage() {
                
                @Override
                public Map<String, Map<String, String>> loadAllConfigs() {
                    return Collections.emptyMap();
                }
                
                @Override
                public void saveConfig(String pluginId, Map<String, String> config) {
                }
                
                @Override
                public void replaceAllConfigs(Map<String, Map<String, String>> configs) {
                }
            };
        }
    }
}
