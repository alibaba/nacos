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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Plugin configuration specification interface.
 * Allows plugins to declare configurable properties.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public interface PluginConfigSpec extends PluginConfigDefinitionSpec {
    
    /**
     * Whether this plugin exposes configurable items.
     *
     * <p>The default keeps legacy and zero-config implementations non-configurable while allowing
     * domain plugin SPIs to inherit this contract without breaking existing implementations.</p>
     *
     * @return {@code true} when at least one configuration item is declared
     */
    default boolean isConfigurable() {
        List<ConfigItemDefinition> definitions = getConfigDefinitions();
        return definitions != null && !definitions.isEmpty();
    }
    
    /**
     * Apply configuration to the plugin.
     *
     * @param config configuration key-value pairs
     */
    default void applyConfig(Map<String, String> config) {
    }
    
    /**
     * Get current configuration.
     *
     * @return current configuration as key-value pairs
     */
    default Map<String, String> getCurrentConfig() {
        return Collections.emptyMap();
    }
}
