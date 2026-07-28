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

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginStartupLifecycle;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.core.plugin.config.PluginConfigApplier;
import com.alibaba.nacos.core.plugin.config.PluginConfigBasicChecker;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolver;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.plugin.environment.CustomEnvironmentPluginManager;
import com.alibaba.nacos.plugin.environment.spi.CustomEnvironmentPluginService;
import com.alibaba.nacos.plugin.environment.spi.EnvironmentPluginTypePolicy;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreContextPluginInitializerTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    private DefaultListableBeanFactory beanFactory;
    
    private MapConfiguration configuration;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        beanFactory = new DefaultListableBeanFactory();
        configuration = new MapConfiguration();
        configuration.setProperty(
            EnvironmentPluginTypePolicy.ENVIRONMENT_ENABLED_PROPERTY, "true");
        CustomEnvironmentPluginManager.getInstance().initialize(Collections.emptyList());
    }
    
    @AfterEach
    void tearDown() {
        CustomEnvironmentPluginManager.getInstance().initialize(Collections.emptyList());
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testConfigurablePluginUsesSameInitializedInstance() {
        TestEnvironmentPlugin plugin = new TestEnvironmentPlugin();
        environment.setProperty(
            "nacos.plugin.environment.test.prefix", "configured-prefix");
        PreContextPluginInitializer initializer =
            newInitializer(Collections.singletonList(provider("test", plugin)));
        
        initializer.initialize();
        initializer.initialize();
        
        assertEquals(PluginInitializationPhase.PRE_CONTEXT,
            initializer.getInitializationPhase());
        PreContextPluginInitializationResult result = getResult();
        PluginInfo info = result.getPluginInfos().get("environment:test");
        assertSame(plugin, result.getPluginInstances().get("environment:test"));
        assertTrue(info.isConfigurable());
        assertTrue(info.isEnabled());
        assertEquals("configured-prefix", plugin.currentConfig.get("prefix"));
        assertEquals("fallback", plugin.currentConfig.get("optional"));
        assertEquals(1, plugin.applyCount);
        assertEquals(1, plugin.initializeCount);
        assertEquals(ConfigItemEffectMode.RUNTIME,
            plugin.definitions.get(0).getEffectMode());
        assertEquals(ConfigItemEffectMode.RESTART,
            info.getConfigDefinitions().get(0).getEffectMode());
        assertEquals("Prefix description",
            info.getConfigDefinitions().get(0).getDescription());
        assertEquals(Collections.singletonList("legacy.prefix"),
            info.getConfigDefinitions().get(0).getAliases());
        assertEquals(Collections.singletonList("unused"),
            info.getConfigDefinitions().get(0).getEnumValues());
        assertTrue(info.getConfigDefinitions().get(0).isRequired());
        assertTrue(info.getConfigDefinitions().get(0).isSensitive());
        assertThrows(UnsupportedOperationException.class,
            () -> info.getConfigDefinitions().add(new ConfigItemDefinition()));
        
        PluginConfigResolution resolution =
            result.getConfigResolutions().get("environment:test");
        assertEquals("co******ix", resolution.getConfig().get("prefix"));
        assertEquals("configured-prefix-value",
            CustomEnvironmentPluginManager.getInstance().getCustomValues(
                Collections.singletonMap("fixture.key", "value")).get("fixture.key"));
    }
    
    @Test
    void testDisabledPluginIsVisibleButDoesNotTransformOrStartLifecycle() {
        configuration.setProperty("nacos.plugin.environment.test.enabled", "false");
        TestEnvironmentPlugin plugin = new TestEnvironmentPlugin();
        
        newInitializer(Collections.singletonList(provider("test", plugin))).initialize();
        
        PluginInfo info = getResult().getPluginInfos().get("environment:test");
        assertFalse(info.isEnabled());
        assertEquals(1, plugin.applyCount);
        assertEquals(0, plugin.initializeCount);
        assertTrue(CustomEnvironmentPluginManager.getInstance().getPropertyKeys().isEmpty());
    }
    
    @Test
    void testDisabledTypeSkipsProvider() {
        configuration.setProperty(
            EnvironmentPluginTypePolicy.ENVIRONMENT_ENABLED_PROPERTY, "false");
        CountingProvider provider = new CountingProvider(Collections.emptyMap());
        
        newInitializer(Collections.singletonList(provider)).initialize();
        
        assertEquals(0, provider.loadCount);
        assertTrue(getResult().getPluginInfos().isEmpty());
    }
    
    @Test
    void testProviderFilteringAndInvalidPlugins() {
        PluginProvider<Object> failedTypeProvider = new PluginProvider<>() {
            
            @Override
            public PluginType getPluginType() {
                throw new IllegalStateException("type");
            }
            
            @Override
            public Map<String, Object> getAllPlugins() {
                return Collections.emptyMap();
            }
        };
        PluginProvider<Object> nullTypeProvider =
            provider((PluginType) null, Collections.emptyMap());
        PluginProvider<Object> standardProvider =
            provider(PluginType.TRACE, Collections.emptyMap());
        PluginProvider<Object> nullPluginsProvider = provider(PluginType.ENVIRONMENT, null);
        Map<String, Object> invalidPlugins = new LinkedHashMap<>();
        invalidPlugins.put("", new TestEnvironmentPlugin());
        invalidPlugins.put("null", null);
        
        newInitializer(Arrays.asList(failedTypeProvider, nullTypeProvider, standardProvider,
            nullPluginsProvider, provider(PluginType.ENVIRONMENT, invalidPlugins))).initialize();
        
        assertTrue(getResult().getPluginInfos().isEmpty());
    }
    
    @Test
    void testProviderDiscoveryFailureStopsInitialization() {
        PluginProvider<Object> provider = new PluginProvider<>() {
            
            @Override
            public PluginType getPluginType() {
                return PluginType.ENVIRONMENT;
            }
            
            @Override
            public Map<String, Object> getAllPlugins() {
                throw new IllegalStateException("load");
            }
        };
        
        assertThrows(IllegalStateException.class,
            () -> newInitializer(Collections.singletonList(provider)).initialize());
    }
    
    @Test
    void testDuplicatePluginKeepsFirstDiscoveredInstance() {
        TestEnvironmentPlugin first = new TestEnvironmentPlugin();
        TestEnvironmentPlugin second = new TestEnvironmentPlugin();
        
        newInitializer(Arrays.asList(provider("test", first),
            provider("test", second))).initialize();
        
        assertSame(first, getResult().getPluginInstances().get("environment:test"));
        assertEquals(1, first.applyCount);
        assertEquals(1, first.initializeCount);
        assertEquals(0, second.applyCount);
        assertEquals(0, second.initializeCount);
    }
    
    @Test
    void testConfigApplyFailureStopsInitialization() {
        TestEnvironmentPlugin plugin = new TestEnvironmentPlugin();
        plugin.failApply = true;
        
        assertThrows(IllegalStateException.class,
            () -> newInitializer(Collections.singletonList(provider("test", plugin)))
                .initialize());
    }
    
    @Test
    void testLifecycleFailureStopsInitialization() {
        TestEnvironmentPlugin plugin = new TestEnvironmentPlugin();
        plugin.failInitialize = true;
        
        assertThrows(IllegalStateException.class,
            () -> newInitializer(Collections.singletonList(provider("test", plugin)))
                .initialize());
    }
    
    @Test
    void testNullDefinitionsAndConfigAreAccepted() {
        NullConfigEnvironmentPlugin plugin = new NullConfigEnvironmentPlugin();
        
        newInitializer(Collections.singletonList(provider("null-config", plugin))).initialize();
        
        PluginInfo info = getResult().getPluginInfos().get("environment:null-config");
        assertTrue(info.isConfigurable());
        assertTrue(info.getConfigDefinitions().isEmpty());
        assertTrue(info.getConfig().isEmpty());
        assertTrue(plugin.applied);
    }
    
    @Test
    void testPublicConstructorRegistersEmptyResultWhenTypeDisabled() {
        environment.setProperty(
            EnvironmentPluginTypePolicy.ENVIRONMENT_ENABLED_PROPERTY, "false");
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            PreContextPluginInitializer initializer = new PreContextPluginInitializer(context);
            
            initializer.initialize();
            
            assertTrue(context.getBeanFactory()
                .getBean(PreContextPluginInitializationResult.class)
                .getPluginInfos().isEmpty());
        }
    }
    
    @Test
    void testInitializationResultIsImmutableAndEmptySingletonIsUsable() {
        PreContextPluginInitializationResult result =
            PreContextPluginInitializationResult.empty();
        
        assertTrue(result.getPluginInfos().isEmpty());
        assertTrue(result.getPluginInstances().isEmpty());
        assertTrue(result.getConfigResolutions().isEmpty());
        assertThrows(UnsupportedOperationException.class,
            () -> result.getPluginInfos().put("id", new PluginInfo()));
    }
    
    private PreContextPluginInitializer newInitializer(
        List<PluginProvider<?>> providers) {
        PluginTypePolicyRegistry policyRegistry = new PluginTypePolicyRegistry(
            Collections.singletonList(new EnvironmentPluginTypePolicy()), configuration);
        return new PreContextPluginInitializer(beanFactory, policyRegistry, providers,
            new PluginConfigResolver(), new PluginConfigBasicChecker(),
            new PluginConfigApplier());
    }
    
    private PreContextPluginInitializationResult getResult() {
        return beanFactory.getBean(PreContextPluginInitializationResult.class);
    }
    
    private PluginProvider<?> provider(String name, Object plugin) {
        return provider(PluginType.ENVIRONMENT, Collections.singletonMap(name, plugin));
    }
    
    private <T> PluginProvider<T> provider(PluginType type, Map<String, T> plugins) {
        return new PluginProvider<>() {
            
            @Override
            public PluginType getPluginType() {
                return type;
            }
            
            @Override
            public Map<String, T> getAllPlugins() {
                return plugins;
            }
        };
    }
    
    private static class CountingProvider implements PluginProvider<Object> {
        
        private final Map<String, Object> plugins;
        
        private int loadCount;
        
        private CountingProvider(Map<String, Object> plugins) {
            this.plugins = plugins;
        }
        
        @Override
        public PluginType getPluginType() {
            return PluginType.ENVIRONMENT;
        }
        
        @Override
        public Map<String, Object> getAllPlugins() {
            loadCount++;
            return plugins;
        }
    }
    
    private static class TestEnvironmentPlugin
        implements CustomEnvironmentPluginService, PluginStartupLifecycle {
        
        private final List<ConfigItemDefinition> definitions = createDefinitions();
        
        private Map<String, String> currentConfig = Collections.emptyMap();
        
        private int applyCount;
        
        private int initializeCount;
        
        private boolean failApply;
        
        private boolean failInitialize;
        
        @Override
        public Map<String, Object> customValue(Map<String, Object> property) {
            Map<String, Object> result = new HashMap<>(property);
            result.computeIfPresent("fixture.key",
                (key, value) -> currentConfig.get("prefix") + "-" + value);
            return result;
        }
        
        @Override
        public Set<String> propertyKey() {
            return Collections.singleton("fixture.key");
        }
        
        @Override
        public Integer order() {
            return 1;
        }
        
        @Override
        public String pluginName() {
            return "test";
        }
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return definitions;
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
        public Map<String, String> getCurrentConfig() {
            return currentConfig;
        }
        
        @Override
        public void initialize() {
            initializeCount++;
            if (failInitialize) {
                throw new IllegalStateException("initialize");
            }
        }
        
        private static List<ConfigItemDefinition> createDefinitions() {
            ConfigItemDefinition prefix =
                new ConfigItemDefinition("prefix", "Prefix", ConfigItemType.STRING);
            prefix.setDescription("Prefix description");
            prefix.setDefaultValue("default-prefix");
            prefix.setRequired(true);
            prefix.setEnumValues(Collections.singletonList("unused"));
            prefix.setAliases(Collections.singletonList("legacy.prefix"));
            prefix.setSensitive(true);
            prefix.setEffectMode(ConfigItemEffectMode.RUNTIME);
            ConfigItemDefinition optional =
                new ConfigItemDefinition("optional", "Optional", ConfigItemType.STRING);
            optional.setDefaultValue("fallback");
            optional.setAliases(null);
            optional.setEnumValues(null);
            return Arrays.asList(prefix, optional);
        }
    }
    
    private static class NullConfigEnvironmentPlugin implements CustomEnvironmentPluginService {
        
        private boolean applied;
        
        @Override
        public boolean isConfigurable() {
            return true;
        }
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return null;
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return null;
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            applied = true;
        }
        
        @Override
        public Map<String, Object> customValue(Map<String, Object> property) {
            return property;
        }
        
        @Override
        public Set<String> propertyKey() {
            return Collections.emptySet();
        }
        
        @Override
        public Integer order() {
            return 0;
        }
        
        @Override
        public String pluginName() {
            return "null-config";
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
