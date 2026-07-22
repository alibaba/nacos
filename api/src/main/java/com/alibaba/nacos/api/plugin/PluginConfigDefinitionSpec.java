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

package com.alibaba.nacos.api.plugin;

import java.util.Collections;
import java.util.List;

/**
 * Declarative plugin configuration definition contract.
 *
 * <p>This contract is suitable for plugin factories that must expose configuration metadata
 * before creating the runtime plugin instance.</p>
 *
 * @author Nacos
 */
public interface PluginConfigDefinitionSpec {
    
    /**
     * Get configuration item definitions.
     *
     * @return list of configuration item definitions
     */
    default List<ConfigItemDefinition> getConfigDefinitions() {
        return Collections.emptyList();
    }
}
