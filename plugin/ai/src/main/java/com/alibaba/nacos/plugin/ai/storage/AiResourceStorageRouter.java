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

import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;

import java.util.Collections;
import java.util.Map;
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
    
    private static final AiResourceStorageRouter INSTANCE = new AiResourceStorageRouter();
    
    private static final Map<String, AiResourceStorage> STORAGES_BY_TYPE =
        new ConcurrentHashMap<>(8);
    
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
     * Add/override a storage implementation at runtime.
     *
     * <p>Mainly for tests or embedding scenarios. If the same type already exists, it will be overridden.</p>
     *
     * @param storage storage implementation
     * @return true if storage is joined
     */
    public static synchronized boolean join(AiResourceStorage storage) {
        if (storage == null || StringUtils.isBlank(storage.type())) {
            return false;
        }
        STORAGES_BY_TYPE.put(storage.type(), storage);
        return true;
    }
    
    @JustForTest
    public static synchronized void reset() {
        STORAGES_BY_TYPE.clear();
    }
}
