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

package com.alibaba.nacos.plugin.ai.storage.spi;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageConsistencyMode;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;

/**
 * AI resource storage SPI interface.
 *
 * <p>Similar to Nacos's multi-datasource/multi-storage implementation, each storage provider implements this interface.
 * It only cares about how to read/write by key, and is designed for generic AI resources (Skill, Prompt, etc.).</p>
 *
 * <p>Implementations should be created via {@link AiResourceStorageBuilder}.</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public interface AiResourceStorage extends PluginConfigSpec {
    
    /**
     * Type identifier, corresponding to {@link StorageKey#getProvider()}.
     *
     * @return storage provider type, e.g. "nacos_config", "oss"
     */
    String type();
    
    /**
     * Declare the local read visibility and notification model.
     *
     * <p>The default preserves source and binary compatibility for existing providers. Callers
     * must not assume that an eventually consistent provider emits a visibility callback unless
     * it explicitly returns {@link AiResourceStorageConsistencyMode#EVENTUAL_WITH_NOTIFICATION}.
     * </p>
     *
     * @return provider consistency mode
     */
    default AiResourceStorageConsistencyMode consistencyMode() {
        return AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION;
    }
    
    /**
     * Register a best-effort local visibility listener.
     *
     * @param listener listener to register
     */
    default void addChangeListener(AiResourceStorageChangeListener listener) {
    }
    
    /**
     * Remove a previously registered local visibility listener.
     *
     * @param listener listener to remove
     */
    default void removeChangeListener(AiResourceStorageChangeListener listener) {
    }
    
    /**
     * Save content to storage.
     *
     * @param storageKey the storage key identifying the resource location
     * @param content    the content to save as byte array
     * @throws NacosException if save operation fails
     */
    void save(StorageKey storageKey, byte[] content) throws NacosException;
    
    /**
     * Get content from storage.
     *
     * @param storageKey the storage key identifying the resource location
     * @return the content as byte array, or null if not found
     * @throws NacosException if get operation fails
     */
    byte[] get(StorageKey storageKey) throws NacosException;
    
    /**
     * Delete content from storage.
     *
     * @param storageKey the storage key identifying the resource location
     * @throws NacosException if delete operation fails
     */
    void delete(StorageKey storageKey) throws NacosException;
}
