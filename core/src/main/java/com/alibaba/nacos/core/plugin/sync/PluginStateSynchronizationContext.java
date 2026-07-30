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

package com.alibaba.nacos.core.plugin.sync;

import java.util.Map;

/**
 * Core-owned callback context for an external plugin state synchronizer.
 *
 * <p>Synchronizers own transport and ordering only. They must use this context
 * to persist and apply an accepted operation on the local node.</p>
 *
 * @author Nacos
 */
public interface PluginStateSynchronizationContext {
    
    /**
     * Persist and apply one plugin state change locally.
     *
     * @param pluginId plugin ID
     * @param enabled whether enabled
     */
    void applyStateChange(String pluginId, boolean enabled);
    
    /**
     * Persist and apply one complete runtime configuration map locally.
     *
     * @param pluginId plugin ID
     * @param config complete runtime configuration map
     */
    void applyConfigChange(String pluginId, Map<String, String> config);
}
