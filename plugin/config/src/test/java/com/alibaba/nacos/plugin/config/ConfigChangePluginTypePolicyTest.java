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

package com.alibaba.nacos.plugin.config;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigChangePluginTypePolicyTest {
    
    private final ConfigChangePluginTypePolicy policy = new ConfigChangePluginTypePolicy();
    
    @Test
    void testTypeAndDefaultState() {
        MapConfiguration configuration = new MapConfiguration();
        
        assertEquals(PluginType.CONFIG_CHANGE, policy.getPluginType());
        assertFalse(policy.isPluginEnabledByDefault("webhook", configuration));
    }
    
    @Test
    void testLegacyState() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.core.config.plugin.webhook.enabled", "true");
        assertTrue(policy.isPluginEnabledByDefault("webhook", configuration));
        
        configuration.setProperty("nacos.core.config.plugin.webhook.enabled", "false");
        assertFalse(policy.isPluginEnabledByDefault("webhook", configuration));
    }
    
    @Test
    void testStandardStateTakesPrecedence() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("nacos.core.config.plugin.webhook.enabled", "false");
        configuration.setProperty("nacos.plugin.config-change.webhook.enabled", "true");
        
        assertTrue(policy.isPluginEnabledByDefault("webhook", configuration));
        configuration.setProperty("nacos.plugin.config-change.webhook.enabled", "false");
        assertFalse(policy.isPluginEnabledByDefault("webhook", configuration));
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
