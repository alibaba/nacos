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

package com.alibaba.nacos.core.plugin.config.storage;

/**
 * Provider for an internal plugin configuration storage implementation.
 *
 * <p>Providers are selected once during startup. A lower order has higher
 * selection priority. SPI implementations must expose a public no-argument
 * constructor and defer storage resource access to {@link #createStorage()} and
 * {@link PluginConfigStorage#initialize()}.</p>
 *
 * @author Nacos
 */
public interface PluginConfigStorageProvider {
    
    /**
     * Get the stable storage identity used by its static enable property.
     *
     * @return storage name
     */
    String getName();
    
    /**
     * Get startup selection order.
     *
     * @return order, lower values have higher priority
     */
    default int getOrder() {
        return 0;
    }
    
    /**
     * Whether this provider is enabled when no explicit static property exists.
     *
     * @return default enabled state
     */
    default boolean isEnabledByDefault() {
        return false;
    }
    
    /**
     * Create the selected storage.
     *
     * @return storage implementation
     */
    PluginConfigStorage createStorage();
}
