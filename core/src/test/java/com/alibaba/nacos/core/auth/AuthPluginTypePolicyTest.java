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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPluginTypePolicyTest {
    
    private final AuthPluginTypePolicy policy = new AuthPluginTypePolicy();
    
    private MapConfiguration configuration;
    
    @BeforeEach
    void setUp() {
        configuration = new MapConfiguration();
        disableAllAuthScopes();
    }
    
    @Test
    void testTypeAndDiagnostics() {
        assertEquals(PluginType.AUTH, policy.getPluginType());
        assertEquals(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, policy.getSelectionProperty());
        assertTrue(policy.getActivationDescription().contains("authentication"));
    }
    
    @Test
    void testActiveConditions() {
        policy.initialize(configuration);
        assertFalse(policy.isActive(configuration));
        
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "true");
        assertTrue(policy.isActive(configuration));
        disableAllAuthScopes();
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "true");
        assertTrue(policy.isActive(configuration));
        disableAllAuthScopes();
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_CONSOLE_ENABLED, "true");
        assertTrue(policy.isActive(configuration));
        disableAllAuthScopes();
        configuration.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "nacos");
        assertFalse(policy.isActive(configuration));
        
        MapConfiguration defaults = new MapConfiguration();
        AuthPluginTypePolicy defaultPolicy = new AuthPluginTypePolicy();
        defaultPolicy.initialize(defaults);
        assertTrue(defaultPolicy.isActive(defaults));
    }
    
    @Test
    void testDefaultSelectionWithoutConfiguredType() {
        policy.initialize(configuration);
        assertTrue(policy.isPluginEnabledByDefault("nacos", configuration));
        assertFalse(policy.isPluginEnabledByDefault("ldap", configuration));
        assertEquals("nacos", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testStandardSelectionTakesPrecedence() {
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "ldap");
        configuration.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, " oidc ");
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("OIDC", configuration));
        assertFalse(policy.isPluginEnabledByDefault("ldap", configuration));
        assertEquals("oidc", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testLegacySelection() {
        configuration.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, " ");
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, " ldap ");
        policy.initialize(configuration);
        
        assertTrue(policy.isPluginEnabledByDefault("LDAP", configuration));
        assertEquals("ldap", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    @Test
    void testConfiguredSelectionIsCapturedAtInitialization() {
        configuration.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "oidc");
        policy.initialize(configuration);
        configuration.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "ldap");
        
        assertTrue(policy.isActive(configuration));
        assertTrue(policy.isPluginEnabledByDefault("oidc", configuration));
        assertFalse(policy.isPluginEnabledByDefault("ldap", configuration));
        assertEquals("oidc", policy.getRequiredPluginNames(configuration).iterator().next());
    }
    
    private void disableAllAuthScopes() {
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, "false");
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        configuration.setProperty(Constants.Auth.NACOS_CORE_AUTH_CONSOLE_ENABLED, "false");
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
