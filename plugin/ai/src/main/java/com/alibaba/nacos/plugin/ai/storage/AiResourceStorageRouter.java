/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.ai.storage;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.spi.PluginRegistryUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Router (facade) for {@link AiResourceStorage}.
 *
 * <p>Upper layers (Skill/Prompt etc.) should only depend on this router and construct a {@link StorageKey}
 * with provider + opaque key, then delegate read/write to the router.</p>
 *
 * <p>Storage implementations are registered via {@link #join(AiResourceStorage)} by external initializer
 * (e.g. {@code AiResourceStorageInitializer} in ai module) when the root application context has finished
 * refreshing.</p>
 *
 * @author nacos
 * @since 3.2.0
 */
public class AiResourceStorageRouter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceStorageRouter.class);
    
    private static final AiResourceStorageRouter INSTANCE = new AiResourceStorageRouter();
    
    private static final Map<String, AiResourceStorage> STORAGES_BY_TYPE =
        new ConcurrentHashMap<>(8);
    
    private final Set<AiResourceStorageChangeListener> changeListeners =
        new LinkedHashSet<AiResourceStorageChangeListener>();
    
    private AiResourceStorageRouter() {
        // Storage implementations are registered via join() by external initializer
    }
    
    /**
     * Get global singleton instance.
     *
     * @return router instance
     */
    public static AiResourceStorageRouter getInstance() {
        return INSTANCE;
    }
    
    /**
     * Route to storage implementation by {@link StorageKey#getProvider()}.
     *
     * @param storageKey storage key
     * @return storage implementation
     */
    public AiResourceStorage route(StorageKey storageKey) {
        if (storageKey == null || StringUtils.isBlank(storageKey.getProvider())) {
            throw new IllegalArgumentException("StorageKey.provider is blank");
        }
        if (!PluginStateCheckerHolder.isPluginEnabled(PluginType.AI_STORAGE.getType(),
            storageKey.getProvider())) {
            throw new IllegalStateException(
                "AiResourceStorage plugin is disabled: " + storageKey.getProvider());
        }
        AiResourceStorage storage = STORAGES_BY_TYPE.get(storageKey.getProvider());
        if (storage == null) {
            throw new IllegalStateException(
                "No AiResourceStorage for provider: " + storageKey.getProvider());
        }
        return storage;
    }
    
    public Map<String, AiResourceStorage> allStorages() {
        return Collections.unmodifiableMap(STORAGES_BY_TYPE);
    }
    
    /**
     * Attach one listener to every current and subsequently joined storage provider.
     *
     * @param listener listener to attach
     */
    public synchronized void addChangeListener(AiResourceStorageChangeListener listener) {
        if (listener == null || !changeListeners.add(listener)) {
            return;
        }
        for (AiResourceStorage storage : STORAGES_BY_TYPE.values()) {
            attachListener(storage, listener);
        }
    }
    
    /**
     * Detach one listener from every current storage provider.
     *
     * @param listener listener to detach
     */
    public synchronized void removeChangeListener(AiResourceStorageChangeListener listener) {
        if (listener == null || !changeListeners.remove(listener)) {
            return;
        }
        for (AiResourceStorage storage : STORAGES_BY_TYPE.values()) {
            detachListener(storage, listener);
        }
    }
    
    /**
     * Register a storage implementation at runtime using first-wins semantics.
     *
     * <p>Mainly for tests or embedding scenarios. A duplicate type is ignored with a warning.</p>
     *
     * @param storage storage implementation
     * @return true if storage is joined
     */
    public static boolean join(AiResourceStorage storage) {
        synchronized (INSTANCE) {
            String type = storage == null ? null : storage.type();
            boolean joined = PluginRegistryUtils.registerFirst(STORAGES_BY_TYPE,
                PluginType.AI_STORAGE.getType(), type, storage, LOGGER);
            if (joined) {
                for (AiResourceStorageChangeListener listener : INSTANCE.changeListeners) {
                    INSTANCE.attachListener(storage, listener);
                }
            }
            return joined;
        }
    }
    
    /**
     * Detach listeners and clear registered storages for isolated tests.
     */
    @JustForTest
    public static void reset() {
        synchronized (INSTANCE) {
            for (AiResourceStorage storage : STORAGES_BY_TYPE.values()) {
                for (AiResourceStorageChangeListener listener : INSTANCE.changeListeners) {
                    INSTANCE.detachListener(storage, listener);
                }
            }
            STORAGES_BY_TYPE.clear();
            INSTANCE.changeListeners.clear();
        }
    }
    
    private void attachListener(AiResourceStorage storage,
        AiResourceStorageChangeListener listener) {
        try {
            storage.addChangeListener(listener);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to attach AI storage change listener to provider {}",
                storage.type(), e);
        }
    }
    
    private void detachListener(AiResourceStorage storage,
        AiResourceStorageChangeListener listener) {
        try {
            storage.removeChangeListener(listener);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to detach AI storage change listener from provider {}",
                storage.type(), e);
        }
    }
}
