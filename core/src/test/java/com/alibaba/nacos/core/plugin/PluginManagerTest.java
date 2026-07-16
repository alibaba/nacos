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
    }
    
    @Test
    void isPluginEnabledDefaultValueTest() {
        boolean enabled = manager.isPluginEnabled("auth", "test");
        assertTrue(enabled);
    }
    
    @Test
    void isPluginEnabledExistingPluginTest() throws NacosApiException {
        registerTestPlugin("trace", "test", false, false, false);
        
        manager.setPluginEnabled("trace:test", false);
        
        boolean enabled = manager.isPluginEnabled("trace", "test");
        assertFalse(enabled);
    }
    
    @Test
    void setPluginEnabledNonCriticalPluginTest() throws NacosApiException {
        registerTestPlugin("trace", "test", false, false, false);
        
        manager.setPluginEnabled("trace:test", false);
        
        verify(synchronizer, times(1)).syncStateChange("trace:test", false);
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
        registerTestPlugin("auth", "nacos", true, false, false);
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.setPluginEnabled("auth:nacos", false);
        });
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), exception.getDetailErrCode());
        assertTrue(exception.getErrMsg().contains("Cannot disable critical plugin"));
    }
    
    @Test
    void setPluginEnabledEnableCriticalPluginTest() throws NacosApiException {
        registerTestPlugin("auth", "nacos", true, false, false);
        
        manager.setPluginEnabled("auth:nacos", true);
        
        verify(synchronizer, times(1)).syncStateChange("auth:nacos", true);
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
        registerTestPlugin("trace", "test", false, false, false);
        
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
        registerTestPlugin("trace", "test1", false, false, false);
        registerTestPlugin("auth", "test2", true, false, false);
        
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
        registerTestPlugin("trace", "test", false, false, false);
        
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
        manager.onApplicationEvent(applicationReadyEvent);
        
        verify(persistence, times(1)).loadAllStates();
        verify(persistence, times(1)).loadAllConfigs();
    }
    
    @Test
    void loadPersistedStatesTest() {
        registerTestPlugin("trace", "test", false, false, false);
        
        Map<String, Boolean> states = new HashMap<>();
        states.put("trace:test", false);
        when(persistence.loadAllStates()).thenReturn(states);
        
        manager.onApplicationEvent(applicationReadyEvent);
        
        assertFalse(manager.isPluginEnabled("trace", "test"));
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
    void calculateDefaultEnabledIgnoresRemovedDatasourcePropertyTest() {
        environment.setProperty("spring.datasource.platform", "mysql");
        
        assertTrue(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "derby"));
        assertFalse(calculateDefaultEnabled(PluginType.DATASOURCE_DIALECT, "mysql"));
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
        registerPluginInstance("trace", "test", plainPlugin, false, false);
        
        TestConfigurablePlugin configurablePlugin = new TestConfigurablePlugin();
        registerConfigurablePlugin("trace", "configurable", configurablePlugin);
        
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        manager.updatePluginConfig("trace:configurable", config);
        
        verify(synchronizer, times(1)).syncConfigChange(eq("trace:configurable"), eq(config));
    }
    
    @Test
    void setPluginEnabledLocalOnlyTest() throws NacosApiException {
        registerTestPlugin("trace", "test", false, false, true);
        
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
        registerTestPlugin("trace", "plain", false, false, true);
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
        registerTestPlugin("trace", "test1", false, false, false);
        registerTestPlugin("auth", "test2", false, false, false);
        
        java.util.Set<String> ids = manager.getLocalPluginIds();
        
        assertEquals(2, ids.size());
        assertTrue(ids.contains("trace:test1"));
        assertTrue(ids.contains("auth:test2"));
    }
    
    @Test
    void isPluginAvailableTest() {
        registerTestPlugin("trace", "test", false, false, false);
        
        assertTrue(manager.isPluginAvailable("trace:test"));
        assertFalse(manager.isPluginAvailable("nonexistent:plugin"));
    }
    
    @Test
    void applyStateChangeDirectTest() {
        registerTestPlugin("trace", "test", false, false, true);
        
        manager.applyStateChange("trace:test", false);
        
        assertFalse(manager.isPluginEnabled("trace", "test"));
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
    
    private void registerTestPlugin(String type, String name, boolean critical,
        boolean configurable,
        boolean enabled) {
        Object instance = new Object();
        registerPluginInstance(type, name, instance, critical, enabled);
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
    
    private void registerPluginInstance(String type, String name, Object instance, boolean critical,
        boolean enabled) {
        String pluginId = type + ":" + name;
        
        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(pluginTypeOf(type));
        info.setClassName(instance.getClass().getName());
        info.setCritical(critical);
        info.setLoadTimestamp(System.currentTimeMillis());
        info.setEnabled(enabled);
        info.setConfigurable(false);
        
        Map<String, PluginInfo> registry = getPluginRegistry();
        registry.put(pluginId, info);
        
        Map<String, Object> instances = getPluginInstances();
        instances.put(pluginId, instance);
        
        Map<String, Boolean> states = getPluginStates();
        states.put(pluginId, enabled);
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
