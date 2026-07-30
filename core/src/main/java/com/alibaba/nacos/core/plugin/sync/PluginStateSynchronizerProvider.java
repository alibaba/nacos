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

/**
 * Provider for a cluster plugin state synchronizer.
 *
 * <p>External providers are selected only when their name is explicitly
 * configured. SPI implementations must expose a public no-argument constructor
 * and defer resource access until synchronizer creation and initialization.</p>
 *
 * @author Nacos
 */
public interface PluginStateSynchronizerProvider {
    
    /**
     * Get the stable synchronizer identity used by static selection.
     *
     * @return synchronizer name
     */
    String getName();
    
    /**
     * Create a synchronizer with the Core-owned local apply context.
     *
     * @param context synchronization context
     * @return synchronizer
     */
    PluginStateSynchronizer createSynchronizer(PluginStateSynchronizationContext context);
}
