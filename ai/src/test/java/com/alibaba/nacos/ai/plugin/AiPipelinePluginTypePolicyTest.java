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

package com.alibaba.nacos.ai.plugin;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPipelinePluginTypePolicyTest {
    
    private final AiPipelinePluginTypePolicy policy = new AiPipelinePluginTypePolicy();
    
    @Test
    void testTypeAndEmptySelection() {
        MapConfiguration configuration = new MapConfiguration();
        assertEquals(PluginType.AI_PIPELINE, policy.getPluginType());
        assertTrue(policy.isLoadingEnabled(configuration));
        assertFalse(policy.isPluginEnabledByDefault("skill-scanner", configuration));
    }
    
    @Test
    void testLoadingEnabledByModuleSwitches() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai-pipeline.enabled", "false");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty("nacos.plugin.ai-pipeline.enabled", "true");
        configuration.setProperty("nacos.extension.ai.enabled", "false");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty("nacos.extension.ai.enabled", "true");
        configuration.setProperty("nacos.functionMode", "config");
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty("nacos.functionMode", "ai");
        assertTrue(policy.isLoadingEnabled(configuration));
    }
    
    @Test
    void testCompatibilitySelectionList() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai-pipeline.type",
            "skill-scanner, skill-spector");
        
        assertTrue(policy.isPluginEnabledByDefault("skill-scanner", configuration));
        assertTrue(policy.isPluginEnabledByDefault("skill-spector", configuration));
        assertFalse(policy.isPluginEnabledByDefault("other", configuration));
    }
    
    @Test
    void testImplementationStateTakesPrecedence() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.plugin.ai-pipeline.type", "other");
        configuration.setProperty("nacos.plugin.ai-pipeline.skill-scanner.enabled", "true");
        assertTrue(policy.isPluginEnabledByDefault("skill-scanner", configuration));
        
        configuration.setProperty("nacos.plugin.ai-pipeline.skill-scanner.enabled", "false");
        assertFalse(policy.isPluginEnabledByDefault("skill-scanner", configuration));
    }
    
    private static class MapConfiguration implements PluginTypeConfiguration {
        
        private final Map<String, String> properties = new HashMap<>();
        
        void setProperty(String key, String value) {
            properties.put(key, value);
        }
        
        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }
        
        @Override
        public boolean containsProperty(String key) {
            return properties.containsKey(key);
        }
    }
}
