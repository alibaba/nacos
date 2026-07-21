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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTypePolicyTest {
    
    @Test
    void testConfigurationDefaults() {
        MapConfiguration configuration = new MapConfiguration();
        
        assertEquals("fallback", configuration.getProperty("missing", "fallback"));
        assertFalse(configuration.getBooleanProperty("missing", false));
        assertTrue(configuration.getBooleanProperty("missing", true));
        
        configuration.setProperty("value", "configured");
        configuration.setProperty("enabled", " true ");
        configuration.setProperty("disabled", "false");
        assertEquals("configured", configuration.getProperty("value", "fallback"));
        assertTrue(configuration.getBooleanProperty("enabled", false));
        assertFalse(configuration.getBooleanProperty("disabled", true));
    }
    
    @Test
    void testPolicyDefaults() {
        MapConfiguration configuration = new MapConfiguration();
        PluginTypePolicy policy = new PluginTypePolicy() {
            
            @Override
            public PluginType getPluginType() {
                return PluginType.TRACE;
            }
        };
        policy.initialize(configuration);
        
        assertFalse(policy.isActive(configuration));
        assertTrue(policy.supportsPreRefreshValidation());
        assertTrue(policy.isPluginEnabledByDefault("test", configuration));
        configuration.setProperty("nacos.plugin.trace.test.enabled", "false");
        assertFalse(policy.isPluginEnabledByDefault("test", configuration));
        assertTrue(policy.getRequiredPluginNames(configuration).isEmpty());
        assertEquals("nacos.plugin.trace.type", policy.getSelectionProperty());
        assertEquals(PluginType.TRACE.getDescription(), policy.getActivationDescription());
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
