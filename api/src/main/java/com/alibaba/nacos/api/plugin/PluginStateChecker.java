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

/**
 * Plugin state checker interface.
 * Used to decouple plugin managers from core module.
 *
 * @author WangzJi
 * @since 3.0.0
 */
public interface PluginStateChecker {

    /**
     * Check if plugin is enabled.
     *
     * @param pluginType plugin type string
     * @param pluginName plugin name
     * @return true if plugin is enabled, false otherwise
     */
    boolean isPluginEnabled(String pluginType, String pluginName);
}
