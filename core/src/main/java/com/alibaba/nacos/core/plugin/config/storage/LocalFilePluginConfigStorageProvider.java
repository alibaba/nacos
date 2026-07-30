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

import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;

/**
 * Provider for the built-in local file plugin configuration storage.
 *
 * @author Nacos
 */
class LocalFilePluginConfigStorageProvider implements PluginConfigStorageProvider {
    
    static final String NAME = "local-file";
    
    private final PluginStatePersistenceService persistence;
    
    LocalFilePluginConfigStorageProvider(PluginStatePersistenceService persistence) {
        this.persistence = persistence;
    }
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
    
    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
    
    @Override
    public PluginConfigStorage createStorage() {
        return new LocalFilePluginConfigStorage(persistence);
    }
}
