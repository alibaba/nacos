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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.plugin.PluginStartupLifecycle;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplier;
import com.alibaba.nacos.core.plugin.config.PluginConfigBasicChecker;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolver;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleAuthPluginInitializerTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    private ConsoleAuthPluginInitializer initializer;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (initializer != null) {
            initializer.destroy();
        }
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void initializeAllConfigurablePluginsAndStartSelectedLifecycle() {
        TestAuthPlugin nacosPlugin = new TestAuthPlugin("nacos");
        TestAuthPlugin customPlugin = new TestAuthPlugin("custom");
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "custom");
        environment.setProperty("nacos.plugin.auth.nacos.runtime-value", "nacos-runtime");
        environment.setProperty("nacos.plugin.auth.custom.runtime-value", "custom-runtime");
        initializer = newInitializer(plugins(nacosPlugin, customPlugin));
        
        initializer.afterSingletonsInstantiated();
        initializer.afterSingletonsInstantiated();
        
        assertEquals(1, nacosPlugin.applyCount);
        assertEquals("nacos-runtime", nacosPlugin.currentConfig.get("runtime-value"));
        assertEquals(0, nacosPlugin.initializeCount);
        assertEquals(1, customPlugin.applyCount);
        assertEquals("custom-runtime", customPlugin.currentConfig.get("runtime-value"));
        assertEquals(1, customPlugin.initializeCount);
        assertSame(ServerConfigChangeEvent.class, initializer.subscribeType());
        assertNotNull(initializer.executor());
    }
    
    @Test
    void useDefaultAuthPluginWhenSelectionIsBlank() {
        TestAuthPlugin nacosPlugin = new TestAuthPlugin("nacos");
        initializer = newInitializer(plugins(nacosPlugin));
        
        initializer.initialize();
        
        assertEquals(1, nacosPlugin.applyCount);
        assertEquals(1, nacosPlugin.initializeCount);
    }
    
    @Test
    void resolveLegacySelectionAndConfigAlias() {
        TestAuthPlugin legacyPlugin = new TestAuthPlugin("legacy");
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "legacy");
        environment.setProperty("nacos.core.auth.legacy.runtime-value", "from-alias");
        initializer = newInitializer(plugins(legacyPlugin));
        
        initializer.initialize();
        
        assertEquals("from-alias", legacyPlugin.currentConfig.get("runtime-value"));
        assertEquals(1, legacyPlugin.initializeCount);
    }
    
    @Test
    void failWhenSelectedPluginIsMissing() {
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "missing");
        TestAuthPlugin plugin = new TestAuthPlugin("nacos");
        initializer = newInitializer(plugins(plugin));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            initializer::initialize);
        
        assertTrue(exception.getMessage().contains("missing"));
        assertEquals(0, plugin.applyCount);
    }
    
    @Test
    void failWhenPluginConfigCannotBeApplied() {
        TestAuthPlugin plugin = new TestAuthPlugin("nacos");
        plugin.failApply = true;
        initializer = newInitializer(plugins(plugin));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            initializer::initialize);
        
        assertTrue(exception.getMessage().contains("auth:nacos"));
    }
    
    @Test
    void failWhenSelectedPluginLifecycleCannotStart() {
        TestAuthPlugin plugin = new TestAuthPlugin("nacos");
        plugin.failInitialize = true;
        initializer = newInitializer(plugins(plugin));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            initializer::initialize);
        
        assertTrue(exception.getMessage().contains("nacos"));
    }
    
    @Test
    void refreshRuntimeConfigAndKeepRestartConfig() {
        TestAuthPlugin plugin = new TestAuthPlugin("nacos");
        environment.setProperty("nacos.plugin.auth.nacos.runtime-value", "before");
        environment.setProperty("nacos.plugin.auth.nacos.restart-value", "restart-before");
        initializer = newInitializer(plugins(plugin));
        initializer.initialize();
        
        environment.setProperty("nacos.plugin.auth.nacos.runtime-value", "after");
        environment.setProperty("nacos.plugin.auth.nacos.restart-value", "restart-after");
        initializer.onEvent(ServerConfigChangeEvent.newEvent());
        initializer.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertEquals(2, plugin.applyCount);
        assertEquals("after", plugin.currentConfig.get("runtime-value"));
        assertEquals("restart-before", plugin.currentConfig.get("restart-value"));
    }
    
    @Test
    void retryRuntimeConfigAfterApplyFailure() {
        TestAuthPlugin plugin = new TestAuthPlugin("nacos");
        environment.setProperty("nacos.plugin.auth.nacos.runtime-value", "before");
        initializer = newInitializer(plugins(plugin));
        initializer.initialize();
        environment.setProperty("nacos.plugin.auth.nacos.runtime-value", "after");
        plugin.failApply = true;
        
        initializer.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals("before", plugin.currentConfig.get("runtime-value"));
        
        plugin.failApply = false;
        initializer.onEvent(ServerConfigChangeEvent.newEvent());
        assertEquals("after", plugin.currentConfig.get("runtime-value"));
    }
    
    @Test
    void ignoreInvalidAndNonConfigurablePlugins() {
        Map<String, AuthPluginService> plugins = new LinkedHashMap<>();
        TestAuthPlugin nacosPlugin = new TestAuthPlugin("nacos");
        plugins.put("nacos", nacosPlugin);
        plugins.put("", new TestAuthPlugin(""));
        plugins.put("invalid", null);
        plugins.put("zero-config", new ZeroConfigAuthPlugin("zero-config"));
        initializer = newInitializer(plugins);
        
        initializer.initialize();
        initializer.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertEquals(1, nacosPlugin.applyCount);
    }
    
    @Test
    void warnAndKeepStartupSelectionWhenEnvironmentChanges() {
        TestAuthPlugin nacosPlugin = new TestAuthPlugin("nacos");
        TestAuthPlugin customPlugin = new TestAuthPlugin("custom");
        initializer = newInitializer(plugins(nacosPlugin, customPlugin));
        initializer.initialize();
        
        environment.setProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE, "custom");
        initializer.onEvent(ServerConfigChangeEvent.newEvent());
        
        assertEquals(1, nacosPlugin.initializeCount);
        assertEquals(0, customPlugin.initializeCount);
    }
    
    private ConsoleAuthPluginInitializer newInitializer(
        Map<String, AuthPluginService> plugins) {
        return new ConsoleAuthPluginInitializer(plugins, new PluginConfigResolver(),
            new PluginConfigBasicChecker(), new PluginConfigApplier());
    }
    
    private Map<String, AuthPluginService> plugins(TestAuthPlugin... plugins) {
        Map<String, AuthPluginService> result = new LinkedHashMap<>();
        Arrays.stream(plugins).forEach(plugin -> result.put(plugin.name, plugin));
        return result;
    }
    
    private static class TestAuthPlugin implements AuthPluginService, PluginStartupLifecycle {
        
        private final String name;
        
        private final List<ConfigItemDefinition> definitions;
        
        private Map<String, String> currentConfig = Collections.emptyMap();
        
        private int applyCount;
        
        private int initializeCount;
        
        private boolean failApply;
        
        private boolean failInitialize;
        
        private TestAuthPlugin(String name) {
            this.name = name;
            ConfigItemDefinition runtime = new ConfigItemDefinition("runtime-value",
                "Runtime value", ConfigItemType.STRING);
            runtime.setDefaultValue("runtime-default");
            runtime.setAliases(Collections.singletonList(
                "nacos.core.auth." + name + ".runtime-value"));
            runtime.setEffectMode(ConfigItemEffectMode.RUNTIME);
            ConfigItemDefinition restart = new ConfigItemDefinition("restart-value",
                "Restart value", ConfigItemType.STRING);
            restart.setDefaultValue("restart-default");
            restart.setEffectMode(ConfigItemEffectMode.RESTART);
            definitions = Arrays.asList(runtime, restart);
        }
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return definitions;
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return new LinkedHashMap<>(currentConfig);
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            applyCount++;
            if (failApply) {
                throw new IllegalStateException("apply");
            }
            currentConfig = new LinkedHashMap<>(config);
        }
        
        @Override
        public void initialize() {
            initializeCount++;
            if (failInitialize) {
                throw new IllegalStateException("initialize");
            }
        }
        
        @Override
        public Collection<String> identityNames() {
            return Collections.emptyList();
        }
        
        @Override
        public boolean enableAuth(ActionTypes action, String type) {
            return true;
        }
        
        @Override
        public AuthResult validateIdentity(IdentityContext identityContext, Resource resource) {
            return AuthResult.successResult();
        }
        
        @Override
        public AuthResult validateAuthority(IdentityContext identityContext,
            Permission permission) {
            return AuthResult.successResult();
        }
        
        @Override
        public String getAuthServiceName() {
            return name;
        }
    }
    
    private static class ZeroConfigAuthPlugin implements AuthPluginService {
        
        private final String name;
        
        private ZeroConfigAuthPlugin(String name) {
            this.name = name;
        }
        
        @Override
        public Collection<String> identityNames() {
            return Collections.emptyList();
        }
        
        @Override
        public boolean enableAuth(ActionTypes action, String type) {
            return true;
        }
        
        @Override
        public AuthResult validateIdentity(IdentityContext identityContext, Resource resource) {
            return AuthResult.successResult();
        }
        
        @Override
        public AuthResult validateAuthority(IdentityContext identityContext,
            Permission permission) {
            return AuthResult.successResult();
        }
        
        @Override
        public String getAuthServiceName() {
            return name;
        }
    }
}
