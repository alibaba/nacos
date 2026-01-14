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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Static holder for PluginStateChecker.
 * Bridges singleton pattern plugin managers with Spring-managed UnifiedPluginManager.
 * Uses AtomicReference to ensure thread safety.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class PluginStateCheckerHolder {

    private static final AtomicReference<PluginStateChecker> INSTANCE = new AtomicReference<>();

    private PluginStateCheckerHolder() {
    }

    /**
     * Set the PluginStateChecker instance.
     *
     * @param checker the PluginStateChecker instance
     */
    public static void setInstance(PluginStateChecker checker) {
        INSTANCE.set(checker);
    }

    /**
     * Get the PluginStateChecker instance.
     *
     * @return Optional containing the PluginStateChecker instance, or empty if not set
     */
    public static Optional<PluginStateChecker> getInstance() {
        return Optional.ofNullable(INSTANCE.get());
    }

    /**
     * Check if a plugin is enabled.
     * Returns true if no checker is set (backward compatibility).
     *
     * @param pluginType plugin type string
     * @param pluginName plugin name
     * @return true if plugin is enabled or no checker is set
     */
    public static boolean isPluginEnabled(String pluginType, String pluginName) {
        PluginStateChecker checker = INSTANCE.get();
        if (checker == null) {
            return true;
        }
        return checker.isPluginEnabled(pluginType, pluginName);
    }
}
