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

package com.alibaba.nacos.plugin.control.spi;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPluginTypePolicyTest {
    
    private final ControlPluginTypePolicy policy = new ControlPluginTypePolicy();
    
    @Test
    void testTypeAndSelectionProperty() {
        assertEquals(PluginType.CONTROL, policy.getPluginType());
        assertEquals(ControlPluginTypePolicy.CONTROL_TYPE_PROPERTY,
            policy.getSelectionProperty());
    }
    
    @Test
    void testInitialSelection() {
        MapConfiguration configuration = new MapConfiguration();
        policy.initialize(configuration);
        assertFalse(policy.isPluginEnabledByDefault("local", configuration));
        assertFalse(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(ControlPluginTypePolicy.CONTROL_TYPE_PROPERTY, " local ");
        policy.initialize(configuration);
        assertTrue(policy.isPluginEnabledByDefault("LOCAL", configuration));
        assertFalse(policy.isPluginEnabledByDefault("remote", configuration));
        assertTrue(policy.isLoadingEnabled(configuration));
        
        configuration.setProperty(ControlPluginTypePolicy.CONTROL_TYPE_PROPERTY, "remote");
        assertTrue(policy.isPluginEnabledByDefault("local", configuration));
    }
    
    @Test
    void testLegacySelection() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(ControlPluginTypePolicy.LEGACY_CONTROL_TYPE_PROPERTY, " local ");
        
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("LOCAL", configuration));
        assertTrue(policy.isLoadingEnabled(configuration));
    }
    
    @Test
    void testStandardSelectionTakesPrecedence() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(ControlPluginTypePolicy.CONTROL_TYPE_PROPERTY, " ");
        configuration.setProperty(ControlPluginTypePolicy.LEGACY_CONTROL_TYPE_PROPERTY, "legacy");
        
        policy.initialize(configuration);
        
        assertFalse(policy.isPluginEnabledByDefault("legacy", configuration));
        assertFalse(policy.isLoadingEnabled(configuration));
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
