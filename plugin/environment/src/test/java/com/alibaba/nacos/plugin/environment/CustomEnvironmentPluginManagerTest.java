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

package com.alibaba.nacos.plugin.environment;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import com.alibaba.nacos.plugin.environment.spi.EnvironmentPluginProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CustomEnvironment Plugin Test.
 *
 * @author : huangtianhui
 */
class CustomEnvironmentPluginManagerTest {
    
    private final CustomEnvironmentPluginManager manager =
        CustomEnvironmentPluginManager.getInstance();
    
    @BeforeEach
    @AfterEach
    void resetManager() {
        manager.initialize(Collections.emptyList());
    }
    
    @Test
    void testInstance() {
        assertNotNull(manager);
    }
    
    @Test
    void testInitializeFiltersInvalidServicesAndUsesOrder() {
        TestEnvironmentPlugin low = new TestEnvironmentPlugin("low", 1, "-low");
        TestEnvironmentPlugin high = new TestEnvironmentPlugin("high", 10, "-high");
        TestEnvironmentPlugin blank = new TestEnvironmentPlugin("", 20, "-blank");
        
        manager.initialize(Arrays.asList(high, null, blank, low));
        
        assertEquals(Collections.singleton("key"), manager.getPropertyKeys());
        Map<String, Object> source = Collections.singletonMap("key", "value");
        assertEquals("value-high", manager.getCustomValues(source).get("key"));
    }
    
    @Test
    @SuppressWarnings("deprecation")
    void testJoinFiltersReturnedValues() {
        CustomEnvironmentPluginManager.join(new CustomEnvironmentPluginService() {
            
            @Override
            public Map<String, Object> customValue(Map<String, Object> property) {
                property.put("key", "changed");
                property.put("null-key", null);
                property.put("unknown", "ignored");
                return property;
            }
            
            @Override
            public Set<String> propertyKey() {
                return Set.of("key", "null-key");
            }
            
            @Override
            public Integer order() {
                return 0;
            }
            
            @Override
            public String pluginName() {
                return "joined";
            }
        });
        CustomEnvironmentPluginManager.join(null);
        
        Map<String, Object> result = manager.getCustomValues(
            Collections.singletonMap("key", "value"));
        
        assertEquals("changed", result.get("key"));
        assertFalse(result.containsKey("null-key"));
        assertFalse(result.containsKey("unknown"));
    }
    
    @Test
    void testPluginConfigCompatibilityDefaults() {
        CustomEnvironmentPluginService service =
            new TestEnvironmentPlugin("test", 1, "-value");
        
        assertFalse(service.isConfigurable());
        assertTrue(service.getConfigDefinitions().isEmpty());
        assertTrue(service.getCurrentConfig().isEmpty());
        service.applyConfig(Collections.singletonMap("key", "value"));
    }
    
    @Test
    void testEnvironmentPluginProvider() {
        EnvironmentPluginProvider provider = new EnvironmentPluginProvider();
        
        assertEquals(PluginType.ENVIRONMENT, provider.getPluginType());
        assertTrue(provider.getAllPlugins().containsKey("spi-environment"));
        assertFalse(provider.getAllPlugins().containsKey(""));
    }
    
    private static class TestEnvironmentPlugin implements CustomEnvironmentPluginService {
        
        private final String name;
        
        private final int order;
        
        private final String suffix;
        
        private TestEnvironmentPlugin(String name, int order, String suffix) {
            this.name = name;
            this.order = order;
            this.suffix = suffix;
        }
        
        @Override
        public Map<String, Object> customValue(Map<String, Object> property) {
            Map<String, Object> result = new HashMap<>(property);
            result.computeIfPresent("key", (key, value) -> value + suffix);
            return result;
        }
        
        @Override
        public Set<String> propertyKey() {
            return Collections.singleton("key");
        }
        
        @Override
        public Integer order() {
            return order;
        }
        
        @Override
        public String pluginName() {
            return name;
        }
    }
}
