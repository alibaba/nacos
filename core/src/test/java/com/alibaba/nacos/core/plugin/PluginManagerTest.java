/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginProvider;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.core.plugin.config.PluginConfigResolution;
import com.alibaba.nacos.core.plugin.config.PluginConfigService;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginManager} unit test.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginManagerTest {
    
    @Mock
    private PluginStatePersistenceService persistence;
    
    @Mock
    private PluginStateSynchronizer synchronizer;
    
    @Mock
    private ApplicationReadyEvent applicationReadyEvent;
    
    private PluginManager manager;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        
        lenient().when(persistence.loadAllStates()).thenReturn(new HashMap<>());
        lenient().when(persistence.loadAllConfigs()).thenReturn(new HashMap<>());
        lenient().doNothing().when(persistence).saveState(any(), anyBoolean());
        lenient().doNothing().when(persistence).saveConfig(any(), anyMap());
        
        manager = new PluginManager(persistence, synchronizer);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void isPluginEnabledDefaultValueTest() {
        boolean enabled = manager.isPluginEnabled("auth", "test");
        assertTrue(enabled);
    }
    
    @Test
    void isPluginEnabledExistingPluginTest() throws NacosApiException {
        registerTestPlugin("trace", "test", false, false);
        
        manager.setPluginEnabled("trace:test", false);
        
        boolean enabled = manager.isPluginEnabled("trace", "test");
        assertFalse(enabled);
    }
    
    @Test
    void setPluginEnabledNonCriticalPluginTest() throws NacosApiException {
        registerTestPlugin("trace", "test", true);
        
        manager.setPluginEnabled("trace:test", false);
        
        verify(synchronizer, times(1)).syncStateChange("trace:test", false);
    }
    
    @Test
    void setPluginEnabledEnablesNonCriticalPluginTest() throws NacosApiException {
        registerTestPlugin("trace", "test", false);
        
        manager.setPluginEnabled("trace:test", true);
        
        verify(synchronizer).syncStateChange("trace:test", true);
    }
    
    @Test
    void setPluginEnabledConvertsLocalValidationFailureTest() {
        registerTestPlugin("trace", "test", true);
        PluginManager spyManager = spy(manager);
        doThrow(new IllegalArgumentException("state changed concurrently")).when(spyManager)
            .applyStateChange("trace:test", false);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> spyManager.setPluginEnabled("trace:test", false, true));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertTrue(exception.getErrMsg().contains("state changed concurrently"));
    }
    
    @Test
    void setPluginEnabledPluginNotFoundTest() {
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.setPluginEnabled("nonexistent:plugin", false);
        });
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void setPluginEnabledDisableCriticalPluginTest() {
        registerTestPlugin("auth", "nacos", true);
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.setPluginEnabled("auth:nacos", false);
        });
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), exception.getDetailErrCode());
        assertTrue(exception.getErrMsg().contains("last enabled implementation"));
    }
    
    @Test
    void setPluginEnabledRejectsExclusiveSelectionChangeTest() throws NacosApiException {
        registerTestPlugin("auth", "nacos", true);
        registerTestPlugin("auth", "ldap", false);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.setPluginEnabled("auth:ldap", true));
        
        assertTrue(exception.getErrMsg().contains("requires restart"));
        assertTrue(exception.getErrMsg().contains("nacos.plugin.auth.type"));
        verify(synchronizer, never()).syncStateChange(any(), anyBoolean());
    }
    
    @Test
    void setPluginEnabledNoOpTest() throws NacosApiException {
        registerTestPlugin("auth", "nacos", true);
        
        manager.setPluginEnabled("auth:nacos", true);
        
        verify(synchronizer, never()).syncStateChange(any(), anyBoolean());
    }
    
    @Test
    void setPluginEnabledRejectsLastCriticalRoutedPluginTest() throws NacosApiException {
        registerTestPlugin("ai-storage", "nacos_config", true);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.setPluginEnabled("ai-storage:nacos_config", false));
        
        assertTrue(exception.getErrMsg().contains("critical plugin type"));
        verify(synchronizer, never()).syncStateChange(any(), anyBoolean());
    }
    
    @Test
    void setPluginEnabledAllowsOneOfMultipleCriticalPluginsTest() throws NacosApiException {
        registerTestPlugin("ai-storage", "nacos_config", true);
        registerTestPlugin("ai-storage", "custom", true);
        
        manager.setPluginEnabled("ai-storage:custom", false);
        
        verify(synchronizer).syncStateChange("ai-storage:custom", false);
    }
    
    @Test
    void updatePluginConfigPluginNotFoundTest() {
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.updatePluginConfig("nonexistent:plugin", config);
        });
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void updatePluginConfigNotConfigurablePluginTest() {
        registerTestPlugin("trace", "test", false, false);
        
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.updatePluginConfig("trace:test", config);
        });
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), exception.getDetailErrCode());
        assertTrue(exception.getErrMsg().contains("does not support configuration"));
    }
    
    @Test
    void updatePluginConfigUnknownKeyTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("knownKey");
        plugin.setConfigDefinitions(Collections.singletonList(definition));
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("unknownKey", "value");
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.updatePluginConfig("trace:test", config));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), exception.getDetailErrCode());
        assertTrue(exception.getErrMsg().contains("Unknown plugin config key: unknownKey"));
    }
    
    @Test
    void updateLocalPluginConfigMissingRequiredEffectiveValueTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("requiredKey");
        definition.setRequired(true);
        definition.setEffectMode(ConfigItemEffectMode.RUNTIME);
        plugin.setConfigDefinitions(Collections.singletonList(definition));
        registerConfigurablePlugin("trace", "test", plugin);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.updatePluginConfig("trace:test", new HashMap<>(), true));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertTrue(exception.getErrMsg().contains(
            "Plugin config source was updated but failed to apply"));
    }
    
    @Test
    void updatePluginConfigSuccessTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition requiredDef = new ConfigItemDefinition();
        requiredDef.setKey("requiredKey");
        requiredDef.setRequired(true);
        requiredDef.setEffectMode(ConfigItemEffectMode.RUNTIME);
        
        List<ConfigItemDefinition> definitions = new ArrayList<>();
        definitions.add(requiredDef);
        plugin.setConfigDefinitions(definitions);
        
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("requiredKey", "value");
        
        manager.updatePluginConfig("trace:test", config);
        
        verify(synchronizer, times(1)).syncConfigChange(eq("trace:test"), eq(config));
    }
    
    @Test
    void listAllPluginsTest() {
        registerTestPlugin("trace", "test1", false, false);
        registerTestPlugin("auth", "test2", false, false);
        
        List<PluginInfo> plugins = manager.listAllPlugins();
        
        assertNotNull(plugins);
        assertEquals(2, plugins.size());
    }
    
    @Test
    void listAllPluginsEmptyTest() {
        List<PluginInfo> plugins = manager.listAllPlugins();
        
        assertNotNull(plugins);
        assertEquals(0, plugins.size());
    }
    
    @Test
    void getPluginExistingPluginTest() {
        registerTestPlugin("trace", "test", false, false);
        
        Optional<PluginInfo> plugin = manager.getPlugin("trace:test");
        
        assertTrue(plugin.isPresent());
        assertEquals("trace:test", plugin.get().getPluginId());
        assertEquals("test", plugin.get().getPluginName());
    }
    
    @Test
    void getPluginNonExistingPluginTest() {
        Optional<PluginInfo> plugin = manager.getPlugin("nonexistent:plugin");
        
        assertFalse(plugin.isPresent());
    }
    
    @Test
    void onApplicationEventTest() {
        registerSelectedAuthPlugin();
        manager.onApplicationEvent(applicationReadyEvent);
        
        verify(persistence, times(1)).loadAllStates();
        verify(persistence, times(1)).loadAllConfigs();
    }
    
    @Test
    void initializeShouldBeIdempotentWithApplicationReadyFallback() {
        registerSelectedAuthPlugin();
        
        manager.initialize();
        manager.onApplicationEvent(applicationReadyEvent);
        
        verify(persistence).loadAllStates();
        verify(persistence).loadAllConfigs();
    }
    
    @Test
    void loadPersistedStatesTest() {
        registerTestPlugin("trace", "test", false, false);
        registerSelectedAuthPlugin();
        
        Map<String, Boolean> states = new HashMap<>();
        states.put("trace:test", false);
        when(persistence.loadAllStates()).thenReturn(states);
        
        manager.onApplicationEvent(applicationReadyEvent);
        
        assertFalse(manager.isPluginEnabled("trace", "test"));
    }
    
    @Test
    void loadPersistedDataIgnoresExclusivePluginStatesTest() {
        registerTestPlugin("auth", "nacos", true);
        registerTestPlugin("auth", "ldap", false);
        Map<String, Boolean> states = new HashMap<>();
        states.put("auth:nacos", false);
        states.put("auth:ldap", true);
        when(persistence.loadAllStates()).thenReturn(states);
        
        ReflectionTestUtils.invokeMethod(manager, "loadPersistedData");
        
        assertTrue(manager.isPluginEnabled("auth", "nacos"));
        assertFalse(manager.isPluginEnabled("auth", "ldap"));
        verify(persistence, never()).saveState(any(), anyBoolean());
    }
    
    @Test
    void loadPersistedDataRejectsUnselectedCriticalExclusiveTypeTest() {
        registerTestPlugin("auth", "nacos", false);
        registerTestPlugin("auth", "ldap", false);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> ReflectionTestUtils.invokeMethod(manager, "loadPersistedData"));
        
        assertTrue(exception.getMessage().contains("nacos.plugin.auth.type"));
    }
    
    @Test
    void loadPersistedDataRestoresCriticalRoutedTypeTest() {
        registerTestPlugin("ai-storage", "a", true);
        registerTestPlugin("ai-storage", "b", true);
        Map<String, Boolean> states = new HashMap<>();
        states.put("ai-storage:a", false);
        states.put("ai-storage:b", false);
        when(persistence.loadAllStates()).thenReturn(states);
        
        ReflectionTestUtils.invokeMethod(manager, "loadPersistedData");
        
        assertTrue(manager.isPluginEnabled("ai-storage", "a"));
        assertFalse(manager.isPluginEnabled("ai-storage", "b"));
        verify(persistence).saveState("ai-storage:a", true);
    }
    
    @Test
    void loadPersistedDataAllowsInactiveCriticalTypesTest() {
        registerTestPlugin("trace", "test", true);
        
        ReflectionTestUtils.invokeMethod(manager, "loadPersistedData");
        
        assertTrue(manager.isPluginEnabled("trace", "test"));
        verify(persistence, never()).saveState(any(), anyBoolean());
    }
    
    @Test
    void loadPersistedDataIgnoresNullStateTest() {
        registerTestPlugin("trace", "test", true);
        when(persistence.loadAllStates()).thenReturn(
            Collections.singletonMap("trace:test", null));
        
        ReflectionTestUtils.invokeMethod(manager, "loadPersistedData");
        
        assertTrue(manager.isPluginEnabled("trace", "test"));
    }
    
    @Test
    void persistedStateOverridesInitialVisibilitySelectionTest() {
        environment.setProperty("nacos.plugin.visibility.type", "custom");
        boolean initialEnabled = calculateDefaultEnabled(PluginType.VISIBILITY, "nacos");
        registerPluginInstance("visibility", "nacos", new Object(), initialEnabled);
        registerSelectedAuthPlugin();
        when(persistence.loadAllStates()).thenReturn(
            Collections.singletonMap("visibility:nacos", true));
        
        manager.onApplicationEvent(applicationReadyEvent);
        
        assertTrue(manager.isPluginEnabled("visibility", "nacos"));
    }
    
    @Test
    void calculateDefaultEnabledUsesCurrentSelectionPropertiesTest() {
        environment.setProperty("nacos.core.auth.system.type", "custom");
        environment.setProperty("spring.sql.init.platform", "mysql");
        
        assertTrue(calculateDefaultEnabled(PluginType.AUTH, "custom"));
        assertFalse(calculateDefaultEnabled(PluginType.AUTH, "nacos"));
        assertTrue(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "mysql"));
        assertFalse(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "derby"));
        assertTrue(calculateDefaultEnabled(PluginType.TRACE, "test"));
    }
    
    @Test
    void calculateDefaultEnabledPrefersStandardSelectionPropertiesTest() {
        environment.setProperty("nacos.core.auth.system.type", "ldap");
        environment.setProperty("nacos.plugin.auth.type", "oidc");
        environment.setProperty("spring.sql.init.platform", "mysql");
        environment.setProperty("nacos.plugin.datasource-dialect.type", "postgresql");
        
        assertTrue(calculateDefaultEnabled(PluginType.AUTH, "oidc"));
        assertFalse(calculateDefaultEnabled(PluginType.AUTH, "ldap"));
        assertTrue(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "postgresql"));
        assertFalse(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "mysql"));
    }
    
    @Test
    void calculateDefaultEnabledIgnoresRemovedDatasourcePropertyTest() {
        environment.setProperty("spring.datasource.platform", "mysql");
        
        assertTrue(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "derby"));
        assertFalse(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "mysql"));
    }
    
    @Test
    void calculateDefaultEnabledUsesControlBootstrapSelectionTest() {
        assertFalse(calculateDefaultEnabled(PluginType.CONTROL, "local"));
        
        environment.setProperty("nacos.plugin.control.manager.type", "local");
        
        assertTrue(calculateDefaultEnabled(PluginType.CONTROL, "local"));
        assertFalse(calculateDefaultEnabled(PluginType.CONTROL, "remote"));
    }
    
    @Test
    void setPluginEnabledRejectsControlBootstrapSelectionChangeTest() {
        registerTestPlugin("control", "local", true);
        registerTestPlugin("control", "remote", false);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.setPluginEnabled("control:remote", true));
        
        assertTrue(exception.getErrMsg().contains("nacos.plugin.control.manager.type"));
    }
    
    @Test
    void calculateDefaultEnabledMigratesConfigChangePropertyTest() {
        assertFalse(calculateDefaultEnabled(PluginType.CONFIG_CHANGE, "webhook"));
        
        environment.setProperty("nacos.core.config.plugin.webhook.enabled", "true");
        assertTrue(calculateDefaultEnabled(PluginType.CONFIG_CHANGE, "webhook"));
        
        environment.setProperty("nacos.core.config.plugin.webhook.enabled", "false");
        assertFalse(calculateDefaultEnabled(PluginType.CONFIG_CHANGE, "webhook"));
    }
    
    @Test
    void calculateDefaultEnabledPrefersImplementationKeyTest() {
        environment.setProperty("nacos.core.config.plugin.webhook.enabled", "false");
        environment.setProperty("nacos.plugin.config-change.webhook.enabled", "true");
        
        assertTrue(calculateDefaultEnabled(PluginType.CONFIG_CHANGE, "webhook"));
    }
    
    @Test
    void calculateDefaultEnabledUsesVisibilityPropertiesTest() {
        assertTrue(calculateDefaultEnabled(PluginType.VISIBILITY, "nacos"));
        assertFalse(calculateDefaultEnabled(PluginType.VISIBILITY, "custom"));
        
        environment.setProperty("nacos.plugin.visibility.type", "custom");
        assertFalse(calculateDefaultEnabled(PluginType.VISIBILITY, "nacos"));
        assertTrue(calculateDefaultEnabled(PluginType.VISIBILITY, "custom"));
    }
    
    @Test
    void calculateDefaultEnabledPrefersVisibilityImplementationKeyTest() {
        environment.setProperty("nacos.plugin.visibility.type", "custom");
        environment.setProperty("nacos.plugin.visibility.enabled", "false");
        environment.setProperty("nacos.plugin.visibility.custom.enabled", "true");
        
        assertTrue(calculateDefaultEnabled(PluginType.VISIBILITY, "custom"));
    }
    
    @Test
    void calculateDefaultEnabledMigratesAiPipelinePropertiesTest() {
        assertFalse(calculateDefaultEnabled(PluginType.AI_PIPELINE, "skill-scanner"));
        
        environment.setProperty("nacos.plugin.ai-pipeline.type",
            "skill-scanner, skill-spector");
        
        assertTrue(calculateDefaultEnabled(PluginType.AI_PIPELINE, "skill-scanner"));
        assertFalse(calculateDefaultEnabled(PluginType.AI_PIPELINE, "other"));
        
        environment.setProperty("nacos.plugin.ai-pipeline.enabled", "false");
        assertTrue(calculateDefaultEnabled(PluginType.AI_PIPELINE, "skill-scanner"));
        
        environment.setProperty("nacos.plugin.ai-pipeline.skill-scanner.enabled", "true");
        assertTrue(calculateDefaultEnabled(PluginType.AI_PIPELINE, "skill-scanner"));
    }
    
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void discoverAllPluginsContinuesWhenProviderFailsTest() {
        PluginProvider provider = mock(PluginProvider.class);
        when(provider.getPluginType()).thenThrow(new IllegalStateException("discovery failed"));
        try (MockedStatic<NacosServiceLoader> loader = mockStatic(NacosServiceLoader.class)) {
            loader.when(() -> NacosServiceLoader.load(PluginProvider.class))
                .thenReturn(Collections.singletonList(provider));
            
            ReflectionTestUtils.invokeMethod(manager, "discoverAllPlugins");
        }
        
        assertTrue(manager.listAllPlugins().isEmpty());
    }
    
    @Test
    void registerPluginReadsConfigSpecMetadataTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("endpoint");
        plugin.setConfigDefinitions(Collections.singletonList(definition));
        plugin.applyConfig(Collections.singletonMap("endpoint", "https://example.com"));
        
        ReflectionTestUtils.invokeMethod(manager, "registerPlugin", PluginType.TRACE,
            "configurable", plugin);
        
        PluginInfo info = manager.getPlugin("trace:configurable").get();
        assertTrue(info.isConfigurable());
        assertEquals(plugin.getConfigDefinitions(), info.getConfigDefinitions());
        assertEquals(plugin.getCurrentConfig(), info.getConfig());
    }
    
    @Test
    void loadPersistedConfigsTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        registerSelectedAuthPlugin();
        
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        Map<String, Map<String, String>> configs = new HashMap<>();
        configs.put("trace:test", config);
        when(persistence.loadAllConfigs()).thenReturn(configs);
        
        manager.onApplicationEvent(applicationReadyEvent);
        
        assertEquals("value", plugin.getCurrentConfig().get("key"));
        verify(persistence, never()).saveConfig(any(), anyMap());
    }
    
    @Test
    void initializeConfigWithoutPersistedOverrideTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("endpoint");
        definition.setDefaultValue("default");
        plugin.setConfigDefinitions(Collections.singletonList(definition));
        registerConfigurablePlugin("trace", "test", plugin);
        registerSelectedAuthPlugin();
        
        manager.onApplicationEvent(applicationReadyEvent);
        
        assertEquals("default", plugin.getCurrentConfig().get("endpoint"));
    }
    
    @Test
    void validateConfigOptionalFieldTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition optionalDef = new ConfigItemDefinition();
        optionalDef.setKey("optionalKey");
        optionalDef.setRequired(false);
        
        List<ConfigItemDefinition> definitions = new ArrayList<>();
        definitions.add(optionalDef);
        plugin.setConfigDefinitions(definitions);
        
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        
        manager.updatePluginConfig("trace:test", config);
        
        verify(synchronizer, times(1)).syncConfigChange(eq("trace:test"), eq(config));
    }
    
    @Test
    void applyConfigToNonConfigurablePluginTest() throws NacosApiException {
        Object plainPlugin = new Object();
        registerPluginInstance("trace", "test", plainPlugin, false);
        
        TestConfigurablePlugin configurablePlugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "configurable", configurablePlugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        manager.updatePluginConfig("trace:configurable", config);
        
        verify(synchronizer, times(1)).syncConfigChange(eq("trace:configurable"), eq(config));
    }
    
    @Test
    void setPluginEnabledLocalOnlyTest() throws NacosApiException {
        registerTestPlugin("trace", "test", false, true);
        
        manager.setPluginEnabled("trace:test", false, true);
        
        assertFalse(manager.isPluginEnabled("trace", "test"));
        verify(synchronizer, times(0)).syncStateChange(any(), anyBoolean());
    }
    
    @Test
    void updatePluginConfigLocalOnlyTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        manager.updatePluginConfig("trace:test", config, true);
        
        assertEquals("value", plugin.getCurrentConfig().get("key"));
        verify(synchronizer, times(0)).syncConfigChange(any(), anyMap());
    }
    
    @Test
    void updatePluginConfigLocalOnlyMapsValidationFailure() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        PluginInfo pluginInfo = manager.getPlugin("trace:test").get();
        Map<String, String> config = Collections.singletonMap("key", "value");
        PluginConfigService configService = mock(PluginConfigService.class);
        ReflectionTestUtils.setField(manager, "pluginConfigService", configService);
        when(configService.prepareRuntimeUpdate(pluginInfo, config,
            PluginConfigSourceType.LOCAL_ONLY)).thenReturn(config);
        doThrow(new IllegalArgumentException("invalid config")).when(configService)
            .updateLocalOnlyConfig(pluginInfo, plugin, config);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.updatePluginConfig("trace:test", config, true));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertTrue(exception.getErrMsg().contains("invalid config"));
    }
    
    @Test
    void updatePluginConfigLocalOnlyMapsUnexpectedFailure() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        PluginInfo pluginInfo = manager.getPlugin("trace:test").get();
        Map<String, String> config = Collections.singletonMap("key", "value");
        PluginConfigService configService = mock(PluginConfigService.class);
        ReflectionTestUtils.setField(manager, "pluginConfigService", configService);
        when(configService.prepareRuntimeUpdate(pluginInfo, config,
            PluginConfigSourceType.LOCAL_ONLY)).thenReturn(config);
        doThrow(new IllegalStateException("unexpected")).when(configService)
            .updateLocalOnlyConfig(pluginInfo, plugin, config);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.updatePluginConfig("trace:test", config, true));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertTrue(exception.getErrMsg().contains("Failed to apply local-only plugin config"));
    }
    
    @Test
    void updatePluginConfigNormalizesStandardKeyTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition requiredDef = new ConfigItemDefinition();
        requiredDef.setKey("requiredKey");
        requiredDef.setRequired(true);
        requiredDef.setEffectMode(ConfigItemEffectMode.RUNTIME);
        
        List<ConfigItemDefinition> definitions = new ArrayList<>();
        definitions.add(requiredDef);
        plugin.setConfigDefinitions(definitions);
        
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("nacos.plugin.trace.test.requiredKey", "value");
        Map<String, String> expectedConfig = new HashMap<>();
        expectedConfig.put("requiredKey", "value");
        
        manager.updatePluginConfig("trace:test", config);
        
        verify(synchronizer, times(1)).syncConfigChange(eq("trace:test"), eq(expectedConfig));
    }
    
    @Test
    void updatePluginConfigRejectsRestartFieldTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("endpoint");
        plugin.setConfigDefinitions(Collections.singletonList(definition));
        registerConfigurablePlugin("trace", "test", plugin);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.updatePluginConfig("trace:test",
                Collections.singletonMap("endpoint", "new")));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        verify(synchronizer, never()).syncConfigChange(any(), anyMap());
    }
    
    @Test
    void updatePluginConfigIgnoresMaskedSensitiveValueWithoutTargetOverrideTest()
        throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("secret");
        definition.setDefaultValue("secret-value");
        definition.setSensitive(true);
        definition.setEffectMode(ConfigItemEffectMode.RUNTIME);
        plugin.setConfigDefinitions(Collections.singletonList(definition));
        registerConfigurablePlugin("trace", "test", plugin);
        
        manager.updatePluginConfig("trace:test",
            Collections.singletonMap("secret", "se******ue"));
        
        verify(synchronizer).syncConfigChange("trace:test", Collections.emptyMap());
    }
    
    @Test
    void resolvePluginConfigWithLayeredSourcesTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("secret");
        definition.setDefaultValue("default-secret");
        definition.setSensitive(true);
        definition.setEffectMode(ConfigItemEffectMode.RUNTIME);
        
        List<ConfigItemDefinition> definitions = new ArrayList<>();
        definitions.add(definition);
        plugin.setConfigDefinitions(definitions);
        environment.setProperty("nacos.plugin.trace.test.secret", "static-secret");
        
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> runtimeConfig = new HashMap<>();
        runtimeConfig.put("secret", "runtime-secret");
        manager.applyConfigChange("trace:test", runtimeConfig);
        
        Map<String, String> localOnlyConfig = new HashMap<>();
        localOnlyConfig.put("secret", "local-secret");
        manager.updatePluginConfig("trace:test", localOnlyConfig, true);
        
        PluginInfo pluginInfo = manager.getPlugin("trace:test").get();
        PluginConfigResolution resolution = manager.resolvePluginConfig(pluginInfo);
        
        assertEquals("local-secret", plugin.getCurrentConfig().get("secret"));
        assertEquals("lo******et", resolution.getConfig().get("secret"));
        assertEquals(PluginConfigSourceType.LOCAL_ONLY,
            resolution.getValueMetas().get("secret").getSource());
        assertTrue(resolution.getValueMetas().get("secret").isOverridden());
    }
    
    @Test
    void refreshStaticPluginConfigsContinuesAfterPluginFailureTest() {
        TestConfigurablePlugin failedPlugin = new TestConfigurablePlugin();
        TestConfigurablePlugin successfulPlugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "failed", failedPlugin);
        registerConfigurablePlugin("trace", "successful", successfulPlugin);
        registerTestPlugin("trace", "plain", false, true);
        PluginInfo failedInfo = manager.getPlugin("trace:failed").get();
        PluginInfo successfulInfo = manager.getPlugin("trace:successful").get();
        PluginConfigService configService = mock(PluginConfigService.class);
        ReflectionTestUtils.setField(manager, "pluginConfigService", configService);
        doThrow(new IllegalStateException("refresh failed")).doNothing().when(configService)
            .refreshStaticConfig(failedInfo, failedPlugin);
        doThrow(new IllegalStateException("refresh failed")).doNothing().when(configService)
            .refreshStaticConfig(successfulInfo, successfulPlugin);
        
        manager.refreshStaticPluginConfigs();
        manager.refreshStaticPluginConfigs();
        
        verify(configService, times(2)).refreshStaticConfig(failedInfo, failedPlugin);
        verify(configService, times(2)).refreshStaticConfig(successfulInfo, successfulPlugin);
        verify(configService, times(4)).refreshStaticConfig(any(), any());
    }
    
    @Test
    void getLocalPluginIdsTest() {
        registerTestPlugin("trace", "test1", false, false);
        registerTestPlugin("auth", "test2", false, false);
        
        java.util.Set<String> ids = manager.getLocalPluginIds();
        
        assertEquals(2, ids.size());
        assertTrue(ids.contains("trace:test1"));
        assertTrue(ids.contains("auth:test2"));
    }
    
    @Test
    void isPluginAvailableTest() {
        registerTestPlugin("trace", "test", false, false);
        
        assertTrue(manager.isPluginAvailable("trace:test"));
        assertFalse(manager.isPluginAvailable("nonexistent:plugin"));
    }
    
    @Test
    void applyStateChangeDirectTest() {
        registerTestPlugin("trace", "test", false, true);
        
        manager.applyStateChange("trace:test", false);
        
        assertFalse(manager.isPluginEnabled("trace", "test"));
    }
    
    @Test
    void applyStateChangeRefreshesCriticalFlagTest() {
        registerTestPlugin("ai-storage", "nacos_config", true);
        registerTestPlugin("ai-storage", "custom", true);
        assertFalse(manager.getPlugin("ai-storage:nacos_config").get().isCritical());
        assertFalse(manager.getPlugin("ai-storage:custom").get().isCritical());
        
        manager.applyStateChange("ai-storage:custom", false);
        
        assertTrue(manager.getPlugin("ai-storage:nacos_config").get().isCritical());
        assertFalse(manager.getPlugin("ai-storage:custom").get().isCritical());
    }
    
    @Test
    void applyStateChangeRejectsExclusiveChangeTest() {
        registerTestPlugin("auth", "nacos", true);
        registerTestPlugin("auth", "ldap", false);
        
        assertThrows(IllegalArgumentException.class,
            () -> manager.applyStateChange("auth:ldap", true));
    }
    
    @Test
    void restorePluginStatesTest() {
        registerTestPlugin("trace", "test", true);
        
        manager.restorePluginStates(Collections.singletonMap("trace:test", false));
        
        assertFalse(manager.isPluginEnabled("trace", "test"));
        verify(persistence).saveState("trace:test", false);
    }
    
    @Test
    void restorePluginStatesNormalizesCriticalFinalStateTest() {
        registerTestPlugin("ai-storage", "a", false);
        registerTestPlugin("ai-storage", "b", true);
        
        manager.restorePluginStates(Collections.singletonMap("ai-storage:b", false));
        
        assertTrue(manager.isPluginEnabled("ai-storage", "a"));
        assertFalse(manager.isPluginEnabled("ai-storage", "b"));
        verify(persistence).saveState("ai-storage:a", true);
        verify(persistence).saveState("ai-storage:b", false);
    }
    
    @Test
    void restorePluginStatesIgnoresExclusiveAndKeepsUnknownStateTest() {
        registerTestPlugin("auth", "nacos", true);
        registerTestPlugin("auth", "ldap", false);
        Map<String, Boolean> states = new HashMap<>();
        states.put("auth:nacos", false);
        states.put("auth:ldap", true);
        states.put("unknown:plugin", false);
        
        manager.restorePluginStates(states);
        
        assertTrue(manager.isPluginEnabled("auth", "nacos"));
        assertFalse(manager.isPluginEnabled("auth", "ldap"));
        verify(persistence, never()).saveState("auth:nacos", false);
        verify(persistence, never()).saveState("auth:ldap", true);
        verify(persistence).saveState("unknown:plugin", false);
    }
    
    @Test
    void restorePluginStatesRejectsInvalidExclusiveFinalStateTest() {
        registerTestPlugin("auth", "nacos", false);
        registerTestPlugin("auth", "ldap", false);
        
        assertThrows(IllegalStateException.class,
            () -> manager.restorePluginStates(Collections.emptyMap()));
    }
    
    @Test
    void restorePluginStatesRejectsNullStateTest() {
        registerTestPlugin("trace", "test", true);
        
        assertThrows(IllegalArgumentException.class,
            () -> manager.restorePluginStates(Collections.singletonMap("trace:test", null)));
    }
    
    @Test
    void applyConfigChangeDirectTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("k", "v");
        
        manager.applyConfigChange("trace:test", config);
        
        assertEquals("v", plugin.getCurrentConfig().get("k"));
        verify(persistence).saveConfig("trace:test", config);
    }
    
    @Test
    void restoreConfigChangeDirectTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        Map<String, String> config = Collections.singletonMap("k", "restored");
        
        manager.restoreConfigChange("trace:test", config);
        
        assertEquals("restored", plugin.getCurrentConfig().get("k"));
        verify(persistence).saveConfig("trace:test", config);
    }
    
    @Test
    void updatePluginConfigWithNullDefinitionsTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        plugin.setConfigDefinitions(null);
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        manager.updatePluginConfig("trace:test", config, true);
        
        assertEquals("value", plugin.getCurrentConfig().get("key"));
    }
    
    @Test
    void applyConfigChangeWhenApplyConfigThrowsTest() {
        ThrowingConfigurablePlugin plugin = new ThrowingConfigurablePlugin();
        registerConfigurablePlugin("trace", "test", plugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("k", "v");
        
        assertThrows(RuntimeException.class, () -> manager.applyConfigChange("trace:test", config));
        verify(persistence).saveConfig("trace:test", config);
    }
    
    @Test
    void applyStateChangeWithUnknownPluginIdTest() {
        manager.applyStateChange("unknown:plugin", true);
        assertTrue(manager.isPluginEnabled("unknown", "plugin"));
    }
    
    private void registerTestPlugin(String type, String name, boolean configurable,
        boolean enabled) {
        Object instance = new Object();
        registerPluginInstance(type, name, instance, enabled);
    }
    
    private void registerTestPlugin(String type, String name, boolean enabled) {
        registerPluginInstance(type, name, new Object(), enabled);
    }
    
    private void registerSelectedAuthPlugin() {
        registerTestPlugin("auth", "nacos", true);
    }
    
    private void registerConfigurablePlugin(String type, String name,
        TestConfigurablePlugin plugin) {
        registerConfigurablePlugin(type, name, (PluginConfigSpec) plugin);
    }
    
    private void registerConfigurablePlugin(String type, String name, PluginConfigSpec plugin) {
        String pluginId = type + ":" + name;
        
        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(pluginTypeOf(type));
        info.setClassName(plugin.getClass().getName());
        info.setCritical(false);
        info.setLoadTimestamp(System.currentTimeMillis());
        info.setEnabled(true);
        info.setConfigurable(true);
        info.setConfigDefinitions(plugin.getConfigDefinitions());
        info.setConfig(plugin.getCurrentConfig());
        
        Map<String, PluginInfo> registry = getPluginRegistry();
        registry.put(pluginId, info);
        
        Map<String, Object> instances = getPluginInstances();
        instances.put(pluginId, plugin);
        
        Map<String, Boolean> states = getPluginStates();
        states.put(pluginId, true);
    }
    
    private void registerPluginInstance(String type, String name, Object instance,
        boolean enabled) {
        String pluginId = type + ":" + name;
        
        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(pluginTypeOf(type));
        info.setClassName(instance.getClass().getName());
        info.setCritical(false);
        info.setLoadTimestamp(System.currentTimeMillis());
        info.setEnabled(enabled);
        info.setConfigurable(false);
        
        Map<String, PluginInfo> registry = getPluginRegistry();
        registry.put(pluginId, info);
        
        Map<String, Object> instances = getPluginInstances();
        instances.put(pluginId, instance);
        
        Map<String, Boolean> states = getPluginStates();
        states.put(pluginId, enabled);
        ReflectionTestUtils.invokeMethod(manager, "refreshCriticalFlags", info.getPluginType());
    }
    
    private PluginType pluginTypeOf(String type) {
        for (PluginType pluginType : PluginType.values()) {
            if (pluginType.getType().equals(type)) {
                return pluginType;
            }
        }
        return null;
    }
    
    private boolean calculateDefaultEnabled(PluginType type, String pluginName) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(manager,
            "calculateDefaultEnabled", type, pluginName));
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, PluginInfo> getPluginRegistry() {
        return (Map<String, PluginInfo>) ReflectionTestUtils.getField(manager, "pluginRegistry");
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getPluginInstances() {
        return (Map<String, Object>) ReflectionTestUtils.getField(manager, "pluginInstances");
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Boolean> getPluginStates() {
        return (Map<String, Boolean>) ReflectionTestUtils.getField(manager, "pluginStates");
    }
    
    static class TestConfigurablePlugin implements PluginConfigSpec {
        
        private List<ConfigItemDefinition> configDefinitions = new ArrayList<>();
        
        private Map<String, String> currentConfig = new HashMap<>();
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return configDefinitions;
        }
        
        public void setConfigDefinitions(List<ConfigItemDefinition> definitions) {
            this.configDefinitions = definitions;
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            this.currentConfig.clear();
            this.currentConfig.putAll(config);
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return currentConfig;
        }
    }
    
    static class ThrowingConfigurablePlugin implements PluginConfigSpec {
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return new ArrayList<>();
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            throw new RuntimeException("applyConfig failed");
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return new HashMap<>();
        }
    }
}
