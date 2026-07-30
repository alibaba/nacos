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

import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Default local apply context used by cluster synchronizers.
 *
 * @author Nacos
 */
class DefaultPluginStateSynchronizationContext implements PluginStateSynchronizationContext {
    
    private final PluginStatePersistenceService persistence;
    
    private final Supplier<PluginStateApplier> applierSupplier;
    
    DefaultPluginStateSynchronizationContext(PluginStatePersistenceService persistence,
        Supplier<PluginStateApplier> applierSupplier) {
        this.persistence = persistence;
        this.applierSupplier = applierSupplier;
    }
    
    @Override
    public void applyStateChange(String pluginId, boolean enabled) {
        PluginStateApplier applier = getApplier();
        applier.validateStateChange(pluginId, enabled);
        persistence.saveState(pluginId, enabled);
        applier.applyStateChange(pluginId, enabled);
    }
    
    @Override
    public void applyConfigChange(String pluginId, Map<String, String> config) {
        getApplier().applyConfigChange(pluginId, config);
    }
    
    private PluginStateApplier getApplier() {
        PluginStateApplier result = applierSupplier.get();
        if (result == null) {
            throw new IllegalStateException("Plugin state applier is unavailable");
        }
        return result;
    }
}
