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

package com.alibaba.nacos.persistence.utils;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceDialectPluginTypePolicyTest {
    
    private final DatasourceDialectPluginTypePolicy policy =
        new DatasourceDialectPluginTypePolicy();
    
    @AfterEach
    void tearDown() {
        EnvUtil.setIsStandalone(null);
        System.clearProperty(PersistenceConstant.EMBEDDED_STORAGE);
    }
    
    @Test
    void testTypeAndDiagnostics() {
        MapConfiguration configuration = new MapConfiguration();
        assertEquals(PluginType.DATASOURCE_DIALECT, policy.getPluginType());
        assertTrue(policy.isActive(configuration));
        assertEquals(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
            policy.getSelectionProperty());
        assertTrue(policy.getActivationDescription().contains("persistence"));
    }
    
    @Test
    void testStandaloneDefaultSelection() {
        EnvUtil.setIsStandalone(true);
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty("spring.datasource.platform", "mysql");
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("DERBY", configuration));
        assertFalse(policy.isPluginEnabledByDefault("mysql", configuration));
        assertEquals("derby", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testClusterDefaultSelection() {
        EnvUtil.setIsStandalone(false);
        MapConfiguration configuration = new MapConfiguration();
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("MYSQL", configuration));
        assertFalse(policy.isPluginEnabledByDefault("derby", configuration));
        assertEquals("mysql", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testEmbeddedClusterDefaultSelection() {
        EnvUtil.setIsStandalone(false);
        System.setProperty(PersistenceConstant.EMBEDDED_STORAGE, Boolean.TRUE.toString());
        MapConfiguration configuration = new MapConfiguration();
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("DERBY", configuration));
        assertFalse(policy.isPluginEnabledByDefault("mysql", configuration));
        assertEquals("derby", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testLegacySelection() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, " mysql ");
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("MYSQL", configuration));
        assertEquals("mysql", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testStandardSelectionTakesPrecedence() {
        MapConfiguration configuration = new MapConfiguration();
        configuration.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY, "mysql");
        configuration.setProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
            " postgresql ");
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("POSTGRESQL", configuration));
        assertFalse(policy.isPluginEnabledByDefault("mysql", configuration));
        assertEquals("postgresql",
            policy.getRequiredPluginNames(configuration).iterator().next());
        configuration.setProperty(PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY, "mysql");
        assertTrue(policy.isPluginEnabledByDefault("POSTGRESQL", configuration));
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
