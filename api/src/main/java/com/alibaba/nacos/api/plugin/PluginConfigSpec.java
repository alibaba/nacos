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

import java.util.List;
import java.util.Map;

/**
 * Plugin configuration specification interface.
 * Allows plugins to declare configurable properties.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public interface PluginConfigSpec {

    /**
     * Get configuration item definitions.
     *
     * @return list of configuration item definitions
     */
    List<ConfigItemDefinition> getConfigDefinitions();

    /**
     * Apply configuration to the plugin.
     *
     * @param config configuration key-value pairs
     */
    void applyConfig(Map<String, String> config);

    /**
     * Get current configuration.
     *
     * @return current configuration as key-value pairs
     */
    Map<String, String> getCurrentConfig();
}
