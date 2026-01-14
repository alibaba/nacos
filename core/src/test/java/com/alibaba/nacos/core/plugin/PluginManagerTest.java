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
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.core.plugin.sync.PluginStateSynchronizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
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
import static org.mockito.Mockito.lenient;
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

    @BeforeEach
    void setUp() {
        lenient().when(persistence.loadAllStates()).thenReturn(new HashMap<>());
        lenient().when(persistence.loadAllConfigs()).thenReturn(new HashMap<>());
        lenient().doNothing().when(persistence).saveState(any(), anyBoolean());
        lenient().doNothing().when(persistence).saveConfig(any(), anyMap());

        manager = new PluginManager(persistence, synchronizer);
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
    void updatePluginConfigMissingRequiredConfigTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition requiredDef = new ConfigItemDefinition();
        requiredDef.setKey("requiredKey");
        requiredDef.setRequired(true);

        List<ConfigItemDefinition> definitions = new ArrayList<>();
        definitions.add(requiredDef);
        plugin.setConfigDefinitions(definitions);

        registerConfigurablePlugin("trace", "test", plugin);

        Map<String, String> config = new HashMap<>();
        config.put("otherKey", "value");

        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.updatePluginConfig("trace:test", config);
        });

        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
        assertEquals(ErrorCode.PARAMETER_MISSING.getCode(), exception.getDetailErrCode());
        assertTrue(exception.getErrMsg().contains("Required config missing"));
    }

    @Test
    void updatePluginConfigSuccessTest() throws NacosApiException {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition requiredDef = new ConfigItemDefinition();
        requiredDef.setKey("requiredKey");
        requiredDef.setRequired(true);

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
    void validateConfigEmptyValueTest() {
        TestConfigurablePlugin plugin = new TestConfigurablePlugin();
        ConfigItemDefinition requiredDef = new ConfigItemDefinition();
        requiredDef.setKey("requiredKey");
        requiredDef.setRequired(true);

        List<ConfigItemDefinition> definitions = new ArrayList<>();
        definitions.add(requiredDef);
        plugin.setConfigDefinitions(definitions);

        registerConfigurablePlugin("trace", "test", plugin);

        Map<String, String> config = new HashMap<>();
        config.put("requiredKey", "");

        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            manager.updatePluginConfig("trace:test", config);
        });

        assertEquals(ErrorCode.PARAMETER_MISSING.getCode(), exception.getDetailErrCode());
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

    private void registerTestPlugin(String type, String name, boolean critical, boolean configurable,
            boolean enabled) {
        Object instance = new Object();
        registerPluginInstance(type, name, instance, critical, enabled);
    }

    private void registerConfigurablePlugin(String type, String name, TestConfigurablePlugin plugin) {
        String pluginId = type + ":" + name;

        PluginInfo info = new PluginInfo();
        info.setPluginId(pluginId);
        info.setPluginName(name);
        info.setPluginType(PluginType.fromType(type));
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
        info.setPluginType(PluginType.fromType(type));
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
}
