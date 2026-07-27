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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTypePolicyRegistryTest {
    
    @Test
    void testDefaultPolicy() {
        MapConfiguration configuration = new MapConfiguration();
        PluginTypePolicyRegistry registry =
            new PluginTypePolicyRegistry(Collections.emptyList(), configuration);
        
        assertFalse(registry.isActive(PluginType.TRACE));
        assertTrue(registry.shouldLoad(PluginType.TRACE));
        assertFalse(registry.shouldLoad(PluginType.AUTH));
        assertTrue(registry.supportsPreRefreshValidation(PluginType.TRACE));
        assertTrue(registry.isPluginEnabledByDefault(PluginType.TRACE, "test"));
        configuration.setProperty("nacos.plugin.trace.test.enabled", "false");
        assertFalse(registry.isPluginEnabledByDefault(PluginType.TRACE, "test"));
        assertTrue(registry.getRequiredPluginNames(PluginType.TRACE).isEmpty());
        assertEquals("nacos.plugin.trace.type",
            registry.getSelectionProperty(PluginType.TRACE));
        assertEquals(PluginType.TRACE.getDescription(),
            registry.getActivationDescription(PluginType.TRACE));
    }
    
    @Test
    void testRegisteredPolicy() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("active", "true");
        TestPolicy policy = new TestPolicy();
        PluginTypePolicyRegistry registry = new PluginTypePolicyRegistry(
            Collections.singletonList(policy), configuration);
        registry.initialize();
        registry.initialize();
        
        assertTrue(policy.initialized);
        assertTrue(registry.isActive(PluginType.AUTH));
        assertTrue(registry.shouldLoad(PluginType.AUTH));
        assertTrue(registry.isPluginEnabledByDefault(PluginType.AUTH, "selected"));
        assertFalse(registry.isPluginEnabledByDefault(PluginType.AUTH, "other"));
        assertEquals(Collections.singleton("selected"),
            registry.getRequiredPluginNames(PluginType.AUTH));
        assertEquals("selection", registry.getSelectionProperty(PluginType.AUTH));
        assertEquals("activation", registry.getActivationDescription(PluginType.AUTH));
    }
    
    @Test
    void testDuplicatePolicyRejected() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> new PluginTypePolicyRegistry(Arrays.asList(new TestPolicy(), new TestPolicy()),
                new MapConfiguration()));
        
        assertTrue(exception.getMessage().contains("auth"));
    }
    
    @Test
    void testServiceLoadedRegistry() {
        assertNotNull(new PluginTypePolicyRegistry());
    }
    
    @Test
    void testRequiredCriticalImplementationValidation() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("active", "true");
        PluginTypePolicyRegistry registry = new PluginTypePolicyRegistry(
            Collections.singletonList(new TestPolicy()), configuration);
        registry.initialize();
        
        String missingAll = PluginTypePolicyRegistry.getCriticalValidationError(registry,
            PluginType.AUTH, Collections.emptyMap());
        assertTrue(missingAll.contains("no discovered implementation"));
        
        String missingSelected = PluginTypePolicyRegistry.getCriticalValidationError(registry,
            PluginType.AUTH, Collections.singletonMap("other", true));
        assertTrue(missingSelected.contains("requires implementation 'selected'"));
        assertTrue(missingSelected.contains("selection"));
        
        String disabled = PluginTypePolicyRegistry.getCriticalValidationError(registry,
            PluginType.AUTH, Collections.singletonMap("selected", false));
        assertTrue(disabled.contains("disabled"));
        assertNull(PluginTypePolicyRegistry.getCriticalValidationError(registry, PluginType.AUTH,
            Collections.singletonMap("selected", true)));
    }
    
    @Test
    void testCriticalValidationSkipsInactiveAndNonCriticalTypes() {
        MapConfiguration configuration = new MapConfiguration();
        PluginTypePolicyRegistry registry = new PluginTypePolicyRegistry(
            Collections.singletonList(new TestPolicy()), configuration);
        registry.initialize();
        
        assertNull(PluginTypePolicyRegistry.getCriticalValidationError(registry, PluginType.AUTH,
            Collections.emptyMap()));
        assertNull(PluginTypePolicyRegistry.getCriticalValidationError(registry, PluginType.TRACE,
            Collections.emptyMap()));
    }
    
    @Test
    void testExclusiveCriticalTypeRequiresSelection() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("active", "true");
        PluginTypePolicy policy = new FlexiblePolicy(PluginType.AUTH, Collections.emptySet());
        PluginTypePolicyRegistry registry = new PluginTypePolicyRegistry(
            Collections.singletonList(policy), configuration);
        registry.initialize();
        
        String error = PluginTypePolicyRegistry.getCriticalValidationError(registry,
            PluginType.AUTH, Collections.singletonMap("nacos", true));
        
        assertTrue(error.contains("no selected implementation"));
        assertTrue(error.contains("selection"));
    }
    
    @Test
    void testRoutedCriticalTypeRequiresAnEnabledImplementation() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("active", "true");
        PluginTypePolicy policy =
            new FlexiblePolicy(PluginType.AI_STORAGE, Collections.emptySet());
        PluginTypePolicyRegistry registry = new PluginTypePolicyRegistry(
            Collections.singletonList(policy), configuration);
        registry.initialize();
        
        String error = PluginTypePolicyRegistry.getCriticalValidationError(registry,
            PluginType.AI_STORAGE, Collections.singletonMap("storage", false));
        assertTrue(error.contains("no enabled implementation"));
        assertNull(PluginTypePolicyRegistry.getCriticalValidationError(registry,
            PluginType.AI_STORAGE, Collections.singletonMap("storage", true)));
    }
    
    private static class TestPolicy implements PluginTypePolicy {
        
        private boolean initialized;
        
        @Override
        public void initialize(PluginTypeConfiguration configuration) {
            initialized = true;
        }
        
        @Override
        public PluginType getPluginType() {
            return PluginType.AUTH;
        }
        
        @Override
        public boolean isActive(PluginTypeConfiguration configuration) {
            return configuration.getBooleanProperty("active", false);
        }
        
        @Override
        public boolean isLoadingEnabled(PluginTypeConfiguration configuration) {
            return false;
        }
        
        @Override
        public boolean isPluginEnabledByDefault(String pluginName,
            PluginTypeConfiguration configuration) {
            return "selected".equals(pluginName);
        }
        
        @Override
        public Set<String> getRequiredPluginNames(PluginTypeConfiguration configuration) {
            return Collections.singleton("selected");
        }
        
        @Override
        public String getSelectionProperty() {
            return "selection";
        }
        
        @Override
        public String getActivationDescription() {
            return "activation";
        }
    }
    
    private static class FlexiblePolicy implements PluginTypePolicy {
        
        private final PluginType type;
        
        private final Set<String> requiredPlugins;
        
        private FlexiblePolicy(PluginType type, Set<String> requiredPlugins) {
            this.type = type;
            this.requiredPlugins = requiredPlugins;
        }
        
        @Override
        public PluginType getPluginType() {
            return type;
        }
        
        @Override
        public boolean isActive(PluginTypeConfiguration configuration) {
            return configuration.getBooleanProperty("active", false);
        }
        
        @Override
        public Set<String> getRequiredPluginNames(PluginTypeConfiguration configuration) {
            return requiredPlugins;
        }
        
        @Override
        public String getSelectionProperty() {
            return "selection";
        }
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
