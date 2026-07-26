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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginConfigServiceTest {
    
    private static final String PLUGIN_ID = "trace:test";
    
    @Mock
    private PluginStatePersistenceService persistence;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    private PluginConfigService service;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        service = new PluginConfigService(persistence);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void initializePluginConfigAppliesStaticValueWithoutPersistence() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        environment.setProperty("nacos.plugin.trace.test.endpoint", "static");
        
        service.initializePluginConfig(pluginInfo, plugin);
        
        assertEquals("static", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("static", pluginInfo.getConfig().get("endpoint"));
        verify(persistence, never()).saveConfig(eq(PLUGIN_ID), anyMap());
    }
    
    @Test
    void initializePluginConfigAppliesNormalizedPersistedRestartValue() {
        ConfigItemDefinition definition = new ConfigItemDefinition("endpoint", "endpoint",
            ConfigItemType.STRING);
        definition.setDefaultValue("default");
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        when(persistence.loadAllConfigs()).thenReturn(Collections.singletonMap(PLUGIN_ID,
            Collections.singletonMap("nacos.plugin.trace.test.endpoint", "persisted")));
        service.initializeRuntimePersistedConfigs();
        
        service.initializePluginConfig(pluginInfo, plugin);
        
        assertEquals("persisted", plugin.getCurrentConfig().get("endpoint"));
        verify(persistence, never()).saveConfig(eq(PLUGIN_ID), anyMap());
    }
    
    @Test
    void initializePluginConfigWrapsValidationFailure() {
        ConfigItemDefinition definition = runtimeDefinition("required", null);
        definition.setRequired(true);
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        
        PluginConfigApplyException exception = assertThrows(PluginConfigApplyException.class,
            () -> service.initializePluginConfig(pluginInfo, plugin));
        
        assertTrue(exception.getMessage().contains("Failed to initialize plugin config"));
    }
    
    @Test
    void refreshStaticConfigAppliesRuntimeFieldAndKeepsRestartField() {
        ConfigItemDefinition runtimeDefinition = runtimeDefinition("runtime", "runtime-default");
        ConfigItemDefinition restartDefinition = new ConfigItemDefinition("restart", "restart",
            ConfigItemType.STRING);
        restartDefinition.setDefaultValue("restart-default");
        PluginInfo pluginInfo = pluginInfo(runtimeDefinition, restartDefinition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        environment.setProperty("nacos.plugin.trace.test.runtime", "runtime-old");
        environment.setProperty("nacos.plugin.trace.test.restart", "restart-old");
        service.initializePluginConfig(pluginInfo, plugin);
        
        MockEnvironment refreshedEnvironment = new MockEnvironment();
        refreshedEnvironment.setProperty("nacos.plugin.trace.test.runtime", "runtime-new");
        refreshedEnvironment.setProperty("nacos.plugin.trace.test.restart", "restart-new");
        EnvUtil.setEnvironment(refreshedEnvironment);
        service.refreshStaticConfig(pluginInfo, plugin);
        
        assertEquals("runtime-new", plugin.getCurrentConfig().get("runtime"));
        assertEquals("restart-old", plugin.getCurrentConfig().get("restart"));
        assertEquals(plugin.getCurrentConfig(), pluginInfo.getConfig());
        assertEquals(2, plugin.getApplyCount());
        assertEquals("restart-old",
            service.resolve(pluginInfo, false).getConfig().get("restart"));
    }
    
    @Test
    void refreshStaticConfigSkipsApplyWhenEffectiveConfigIsUnchanged() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        
        service.refreshStaticConfig(pluginInfo, plugin);
        
        assertEquals(1, plugin.getApplyCount());
    }
    
    @Test
    void refreshStaticConfigKeepsAcceptedSourceWhenApplyFails() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        FailOncePlugin plugin = new FailOncePlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        environment.setProperty("nacos.plugin.trace.test.endpoint", "bad");
        
        PluginConfigApplyException exception = assertThrows(PluginConfigApplyException.class,
            () -> service.refreshStaticConfig(pluginInfo, plugin));
        
        assertTrue(exception.getMessage().contains("Static plugin config was refreshed"));
        assertEquals("default", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("default", pluginInfo.getConfig().get("endpoint"));
        assertEquals("bad", service.resolve(pluginInfo, false).getConfig().get("endpoint"));
        
        service.refreshStaticConfig(pluginInfo, plugin);
        
        assertEquals("bad", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("bad", pluginInfo.getConfig().get("endpoint"));
        assertEquals(2, plugin.getApplyCount());
    }
    
    @Test
    void runtimeUpdatePersistsCanonicalMapAndEmptyMapFallsBack() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        
        service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin,
            Collections.singletonMap("nacos.plugin.trace.test.endpoint", "runtime"));
        
        assertEquals("runtime", plugin.getCurrentConfig().get("endpoint"));
        verify(persistence).saveConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "runtime"));
        
        service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin,
            Collections.emptyMap());
        
        assertEquals("default", plugin.getCurrentConfig().get("endpoint"));
        verify(persistence).saveConfig(PLUGIN_ID, Collections.emptyMap());
    }
    
    @Test
    void localOnlyUpdateOverridesRuntimeWithoutPersistence() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin,
            Collections.singletonMap("endpoint", "runtime"));
        
        service.updateLocalOnlyConfig(pluginInfo, plugin,
            Collections.singletonMap("endpoint", "local"));
        
        assertEquals("local", plugin.getCurrentConfig().get("endpoint"));
        
        service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin,
            Collections.singletonMap("endpoint", "runtime-new"));
        
        assertEquals("local", plugin.getCurrentConfig().get("endpoint"));
        assertEquals(4, plugin.getApplyCount());
        
        service.updateLocalOnlyConfig(pluginInfo, plugin, Collections.emptyMap());
        
        assertEquals("runtime-new", plugin.getCurrentConfig().get("endpoint"));
        verify(persistence).saveConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "runtime"));
        verify(persistence).saveConfig(PLUGIN_ID,
            Collections.singletonMap("endpoint", "runtime-new"));
    }
    
    @Test
    void applyFailureKeepsAcceptedSourceAndSameMapRetriesApply() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        FailOncePlugin plugin = new FailOncePlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        Map<String, String> failedConfig = Collections.singletonMap("endpoint", "bad");
        
        assertThrows(PluginConfigApplyException.class,
            () -> service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin,
                failedConfig));
        
        assertEquals("default", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("default", pluginInfo.getConfig().get("endpoint"));
        assertEquals("bad", service.resolve(pluginInfo, false).getConfig().get("endpoint"));
        verify(persistence).saveConfig(PLUGIN_ID, failedConfig);
        
        service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin, failedConfig);
        
        assertEquals("bad", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("bad", pluginInfo.getConfig().get("endpoint"));
        verify(persistence, times(2)).saveConfig(PLUGIN_ID, failedConfig);
    }
    
    @Test
    void persistenceFailureLeavesSourceAndPluginConfigUnchanged() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        doThrow(new PluginPersistenceException("save failed")).when(persistence)
            .saveConfig(eq(PLUGIN_ID), anyMap());
        
        assertThrows(PluginPersistenceException.class,
            () -> service.applyRuntimePersistedConfig(PLUGIN_ID, pluginInfo, plugin,
                Collections.singletonMap("endpoint", "runtime")));
        
        assertEquals("default", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("default", service.resolve(pluginInfo, false).getConfig().get("endpoint"));
    }
    
    @Test
    void prepareRuntimeUpdatePreservesMaskedValueFromTargetSource() {
        ConfigItemDefinition definition = runtimeDefinition("secret", "secret-value");
        definition.setSensitive(true);
        PluginInfo pluginInfo = pluginInfo(definition);
        when(persistence.loadAllConfigs()).thenReturn(Collections.singletonMap(PLUGIN_ID,
            Collections.singletonMap("secret", "runtime-secret")));
        service.initializeRuntimePersistedConfigs();
        
        for (String maskedValue : new String[] {"******", "a******z", "ab******yz"}) {
            Map<String, String> prepared = service.prepareRuntimeUpdate(pluginInfo,
                Collections.singletonMap("secret", maskedValue),
                PluginConfigSourceType.RUNTIME_PERSISTED);
            assertEquals("runtime-secret", prepared.get("secret"));
        }
    }
    
    @Test
    void prepareRuntimeUpdateDoesNotCopyStaticSensitiveValueToRuntimeSource() {
        ConfigItemDefinition definition = runtimeDefinition("secret", "default-secret");
        definition.setSensitive(true);
        PluginInfo pluginInfo = pluginInfo(definition);
        environment.setProperty("nacos.plugin.trace.test.secret", "static-secret");
        
        Map<String, String> prepared = service.prepareRuntimeUpdate(pluginInfo,
            Collections.singletonMap("secret", "st******et"),
            PluginConfigSourceType.RUNTIME_PERSISTED);
        
        assertTrue(prepared.isEmpty());
        assertEquals("static-secret", service.resolve(pluginInfo, false).getConfig().get("secret"));
    }
    
    @Test
    void restoreRuntimePersistedConfigsAllowsRestartField() {
        ConfigItemDefinition definition = new ConfigItemDefinition("endpoint", "endpoint",
            ConfigItemType.STRING);
        definition.setDefaultValue("default");
        definition.setEffectMode(ConfigItemEffectMode.RESTART);
        PluginInfo pluginInfo = pluginInfo(definition);
        RecordingPlugin plugin = new RecordingPlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        
        Map<String, Map<String, String>> restored = Collections.singletonMap(PLUGIN_ID,
            Collections.singletonMap("endpoint", "restored"));
        service.restoreRuntimePersistedConfigs(restored);
        service.applyRestoredPluginConfig(pluginInfo, plugin);
        
        assertEquals("restored", plugin.getCurrentConfig().get("endpoint"));
        assertEquals(restored, service.getAllRuntimePersistedConfigs());
        verify(persistence).replaceAllConfigs(restored);
    }
    
    @Test
    void applyRestoredPluginConfigWrapsApplyFailure() {
        ConfigItemDefinition definition = runtimeDefinition("endpoint", "default");
        PluginInfo pluginInfo = pluginInfo(definition);
        FailOncePlugin plugin = new FailOncePlugin(pluginInfo.getConfigDefinitions());
        service.initializePluginConfig(pluginInfo, plugin);
        service.restoreRuntimePersistedConfigs(Collections.singletonMap(PLUGIN_ID,
            Collections.singletonMap("endpoint", "bad")));
        
        PluginConfigApplyException exception = assertThrows(PluginConfigApplyException.class,
            () -> service.applyRestoredPluginConfig(pluginInfo, plugin));
        
        assertTrue(exception.getMessage().contains("Failed to apply restored plugin config"));
        assertEquals("default", plugin.getCurrentConfig().get("endpoint"));
        assertEquals("bad", service.resolve(pluginInfo, false).getConfig().get("endpoint"));
    }
    
    @Test
    void applyRuntimePersistedConfigWithoutLocalPluginPersistsInput() {
        Map<String, String> config = Collections.singletonMap("legacy", "value");
        
        service.applyRuntimePersistedConfig(PLUGIN_ID, null, null, config);
        service.applyRuntimePersistedConfig(PLUGIN_ID, null, null, null);
        
        verify(persistence).saveConfig(PLUGIN_ID, config);
        verify(persistence).saveConfig(PLUGIN_ID, Collections.emptyMap());
    }
    
    @Test
    void prepareRuntimeUpdateRejectsReadOnlySource() {
        PluginInfo pluginInfo = pluginInfo(runtimeDefinition("endpoint", "default"));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> service.prepareRuntimeUpdate(pluginInfo, Collections.emptyMap(),
                PluginConfigSourceType.STATIC));
        
        assertTrue(exception.getMessage().contains("not runtime updatable"));
    }
    
    private PluginInfo pluginInfo(ConfigItemDefinition definition) {
        return pluginInfo(new ConfigItemDefinition[] {definition});
    }
    
    private PluginInfo pluginInfo(ConfigItemDefinition... definitions) {
        PluginInfo result = new PluginInfo();
        result.setPluginId(PLUGIN_ID);
        result.setPluginType(PluginType.TRACE);
        result.setPluginName("test");
        result.setConfigurable(true);
        result.setConfigDefinitions(java.util.Arrays.asList(definitions));
        result.setConfig(new HashMap<>());
        return result;
    }
    
    private ConfigItemDefinition runtimeDefinition(String key, String defaultValue) {
        ConfigItemDefinition result = new ConfigItemDefinition(key, key, ConfigItemType.STRING);
        result.setDefaultValue(defaultValue);
        result.setEffectMode(ConfigItemEffectMode.RUNTIME);
        return result;
    }
    
    private static class RecordingPlugin implements PluginConfigSpec {
        
        private final List<ConfigItemDefinition> definitions;
        
        private final Map<String, String> currentConfig = new HashMap<>();
        
        private int applyCount;
        
        RecordingPlugin(List<ConfigItemDefinition> definitions) {
            this.definitions = new ArrayList<>(definitions);
        }
        
        @Override
        public List<ConfigItemDefinition> getConfigDefinitions() {
            return definitions;
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            applyCount++;
            currentConfig.clear();
            currentConfig.putAll(config);
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return currentConfig;
        }
        
        int getApplyCount() {
            return applyCount;
        }
    }
    
    private static class FailOncePlugin extends RecordingPlugin {
        
        private boolean failed;
        
        FailOncePlugin(List<ConfigItemDefinition> definitions) {
            super(definitions);
        }
        
        @Override
        public void applyConfig(Map<String, String> config) {
            if ("bad".equals(config.get("endpoint")) && !failed) {
                failed = true;
                throw new IllegalArgumentException("bad endpoint");
            }
            super.applyConfig(config);
        }
    }
}
