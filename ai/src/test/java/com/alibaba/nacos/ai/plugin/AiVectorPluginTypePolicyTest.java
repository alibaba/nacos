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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.ard.vector.ArdVectorIndexRouter;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiVectorPluginTypePolicyTest {
    
    private final AiVectorPluginTypePolicy policy = new AiVectorPluginTypePolicy();
    
    @Test
    void shouldBeActiveOnlyWhenArdIsEnabled() {
        MapConfiguration configuration = new MapConfiguration();
        
        assertEquals(PluginType.AI_VECTOR, policy.getPluginType());
        assertFalse(policy.isActive(configuration));
        configuration.setProperty(Constants.ARD_ENABLED_KEY, "true");
        assertTrue(policy.isActive(configuration));
        assertEquals(ArdVectorIndexRouter.KEY_VECTOR_PROVIDER, policy.getSelectionProperty());
    }
    
    @Test
    void shouldEnableConfiguredProviderByDefault() {
        MapConfiguration configuration = new MapConfiguration();
        
        assertTrue(policy.isPluginEnabledByDefault(
            ArdVectorIndexRouter.DEFAULT_VECTOR_PROVIDER, configuration));
        assertFalse(policy.isPluginEnabledByDefault("custom", configuration));
        
        configuration.setProperty(ArdVectorIndexRouter.KEY_VECTOR_PROVIDER, " custom ");
        assertTrue(policy.isPluginEnabledByDefault("custom", configuration));
        assertFalse(policy.isPluginEnabledByDefault(
            ArdVectorIndexRouter.DEFAULT_VECTOR_PROVIDER, configuration));
    }
    
    @Test
    void shouldPreferImplementationStateProperty() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(ArdVectorIndexRouter.KEY_VECTOR_PROVIDER, "custom");
        configuration.setProperty("nacos.plugin.ai-vector.custom.enabled", "false");
        
        assertFalse(policy.isPluginEnabledByDefault("custom", configuration));
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
