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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginConfigApplierTest {
    
    private final PluginConfigApplier applier = new PluginConfigApplier();
    
    @Test
    void applyUpdatesPluginConfigSpec() {
        RecordingPlugin plugin = new RecordingPlugin();
        
        applier.apply("trace:test", plugin, Collections.singletonMap("key", "value"));
        
        assertEquals("value", plugin.getCurrentConfig().get("key"));
    }
    
    @Test
    void applyIgnoresPluginWithoutConfigSpec() {
        applier.apply("trace:test", new Object(), Collections.singletonMap("key", "value"));
    }
    
    @Test
    void applyWrapsPluginFailure() {
        RecordingPlugin plugin = new RecordingPlugin();
        plugin.throwOnApply = true;
        
        assertThrows(IllegalStateException.class,
            () -> applier.apply("trace:test", plugin,
                Collections.singletonMap("key", "value")));
    }
    
    private static class RecordingPlugin implements PluginConfigSpec {
        
        private final Map<String, String> currentConfig = new HashMap<>();
        
        private boolean throwOnApply;
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return Collections.emptyList();
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            if (throwOnApply) {
                throw new IllegalArgumentException("invalid config");
            }
            currentConfig.clear();
            currentConfig.putAll(config);
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return currentConfig;
        }
    }
}
