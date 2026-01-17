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

package com.alibaba.nacos.api.plugin;

import java.util.Map;

/**
 * Plugin provider SPI interface.
 *
 * <p>Each plugin type should have one implementation to provide plugin instances.
 * This interface enables automatic plugin discovery through SPI mechanism,
 * eliminating the need to manually register each plugin type in UnifiedPluginManager.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class AuthPluginProvider implements PluginProvider<AuthPluginService> {
 *     @Override
 *     public PluginType getPluginType() {
 *         return PluginType.AUTH;
 *     }
 *
 *     @Override
 *     public Map<String, AuthPluginService> getAllPlugins() {
 *         return AuthPluginManager.getInstance().getAllPlugins();
 *     }
 * }
 * }</pre>
 *
 * @param <T> the plugin service type
 * @author WangzJi
 * @since 3.2.0
 */
public interface PluginProvider<T> {

    /**
     * Get the plugin type this provider manages.
     *
     * @return plugin type
     */
    PluginType getPluginType();

    /**
     * Get all plugin instances managed by this provider.
     * Key is the plugin name, value is the plugin instance.
     *
     * @return map of plugin name to plugin instance
     */
    Map<String, T> getAllPlugins();

    /**
     * Get the order of this provider. Lower values have higher priority.
     * Used when multiple providers exist for same type.
     *
     * @return order value, default is 0
     */
    default int getOrder() {
        return 0;
    }
}
