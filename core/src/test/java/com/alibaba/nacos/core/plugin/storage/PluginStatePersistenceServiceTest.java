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

package com.alibaba.nacos.core.plugin.storage;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginStatePersistenceServiceTest {
    
    @Test
    void testDefaultReplaceAllStates() {
        InMemoryPersistence persistence = new InMemoryPersistence();
        persistence.saveState("trace:old", true);
        persistence.saveState("trace:updated", false);
        Map<String, Boolean> replacement = new HashMap<>();
        replacement.put("trace:updated", true);
        replacement.put("trace:new", false);
        
        persistence.replaceAllStates(replacement);
        
        assertEquals(replacement, persistence.loadAllStates());
        
        persistence.replaceAllStates(null);
        assertTrue(persistence.loadAllStates().isEmpty());
    }
    
    @Test
    void testDefaultReplaceAllConfigs() {
        InMemoryPersistence persistence = new InMemoryPersistence();
        persistence.saveConfig("trace:old", Collections.singletonMap("key", "old"));
        persistence.saveConfig("trace:updated", Collections.singletonMap("key", "before"));
        Map<String, Map<String, String>> replacement = new HashMap<>();
        replacement.put("trace:updated", Collections.singletonMap("key", "after"));
        replacement.put("trace:new", Collections.singletonMap("key", "new"));
        
        persistence.replaceAllConfigs(replacement);
        
        assertEquals(replacement, persistence.loadAllConfigs());
        
        persistence.replaceAllConfigs(null);
        assertTrue(persistence.loadAllConfigs().isEmpty());
    }
    
    private static class InMemoryPersistence implements PluginStatePersistenceService {
        
        private final Map<String, Boolean> states = new HashMap<>();
        
        private final Map<String, Map<String, String>> configs = new HashMap<>();
        
        @Override
        public Map<String, Boolean> loadAllStates() {
            return new HashMap<>(states);
        }
        
        @Override
        public void saveState(String pluginId, boolean enabled) {
            states.put(pluginId, enabled);
        }
        
        @Override
        public void deleteState(String pluginId) {
            states.remove(pluginId);
        }
        
        @Override
        public Map<String, Map<String, String>> loadAllConfigs() {
            return new HashMap<>(configs);
        }
        
        @Override
        public void saveConfig(String pluginId, Map<String, String> config) {
            configs.put(pluginId, config);
        }
        
        @Override
        public void deleteConfig(String pluginId) {
            configs.remove(pluginId);
        }
    }
}
