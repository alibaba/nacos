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

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PluginConfigStorageContractTest {
    
    @Test
    void defaultLifecycleAndProviderMetadataAreNoOp() {
        PluginConfigStorage storage = new NoOpStorage();
        PluginConfigStorageProvider provider = new PluginConfigStorageProvider() {
            
            @Override
            public String getName() {
                return "test";
            }
            
            @Override
            public PluginConfigStorage createStorage() {
                return storage;
            }
        };
        
        storage.initialize();
        storage.shutdown();
        
        assertEquals(0, provider.getOrder());
        assertFalse(provider.isEnabledByDefault());
        assertEquals(storage, provider.createStorage());
    }
    
    private static class NoOpStorage implements PluginConfigStorage {
        
        @Override
        public Map<String, Map<String, String>> loadAllConfigs() {
            return Collections.emptyMap();
        }
        
        @Override
        public void saveConfig(String pluginId, Map<String, String> config) {
        }
        
        @Override
        public void replaceAllConfigs(Map<String, Map<String, String>> configs) {
        }
    }
}
