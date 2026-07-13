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
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.model.vo.PluginConfigValueMeta;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigResolverTest {
    
    private final PluginConfigResolver resolver = new PluginConfigResolver();
    
    private ConfigurableEnvironment cachedEnvironment;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testResolveDefaultValue() {
        PluginInfo pluginInfo = createPluginInfo(createDefinition("timeout", "1000", false));
        
        PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
        
        assertEquals("1000", resolution.getConfig().get("timeout"));
        assertMeta(resolution.getValueMetas().get("timeout"), "timeout",
            PluginConfigSourceType.DEFAULT, false);
    }
    
    @Test
    void testResolveStaticAliasAndMaskSensitiveValue() {
        ConfigItemDefinition definition = createDefinition("token", "default-token", true);
        definition.setAliases(Collections.singletonList("nacos.legacy.token"));
        environment.setProperty("nacos.legacy.token", "static-token");
        PluginInfo pluginInfo = createPluginInfo(definition);
        
        PluginConfigResolution resolution = resolver.resolve(pluginInfo, true);
        
        assertEquals("st******en", resolution.getConfig().get("token"));
        assertMeta(resolution.getValueMetas().get("token"), "token",
            PluginConfigSourceType.STATIC, false);
    }
    
    @Test
    void testResolveLocalOnlyOverridesRuntimeAndStatic() {
        ConfigItemDefinition definition = createDefinition("timeout", "1000", false);
        environment.setProperty("nacos.plugin.trace.demo.timeout", "2000");
        PluginInfo pluginInfo = createPluginInfo(definition);
        Map<String, String> runtimeConfig = new HashMap<>();
        runtimeConfig.put("timeout", "3000");
        Map<String, String> localOnlyConfig = new HashMap<>();
        localOnlyConfig.put("timeout", "4000");
        resolver.updateConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginInfo.getPluginId(),
            runtimeConfig);
        resolver.updateConfig(PluginConfigSourceType.LOCAL_ONLY, pluginInfo.getPluginId(),
            localOnlyConfig);
        
        PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
        
        assertEquals("4000", resolution.getConfig().get("timeout"));
        assertMeta(resolution.getValueMetas().get("timeout"), "timeout",
            PluginConfigSourceType.LOCAL_ONLY, true);
    }
    
    @Test
    void testResolveNormalizesMapBasedSourceKeysBeforeChain() {
        ConfigItemDefinition definition = createDefinition("timeout", "1000", false);
        definition.setAliases(Collections.singletonList("oldTimeout"));
        environment.setProperty("nacos.plugin.trace.demo.timeout", "2000");
        PluginInfo pluginInfo = createPluginInfo(definition);
        Map<String, String> runtimeConfig = new HashMap<>();
        runtimeConfig.put("nacos.plugin.trace.demo.timeout", "3000");
        Map<String, String> localOnlyConfig = new HashMap<>();
        localOnlyConfig.put("oldTimeout", "4000");
        resolver.updateConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginInfo.getPluginId(),
            resolver.normalizeConfig(pluginInfo, runtimeConfig));
        resolver.updateConfig(PluginConfigSourceType.LOCAL_ONLY, pluginInfo.getPluginId(),
            resolver.normalizeConfig(pluginInfo, localOnlyConfig));
        
        PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
        
        assertEquals("4000", resolution.getConfig().get("timeout"));
        assertMeta(resolution.getValueMetas().get("timeout"), "timeout",
            PluginConfigSourceType.LOCAL_ONLY, true);
    }
    
    @Test
    void testUpdateConfigReplacesSourceSnapshot() {
        ConfigItemDefinition definition = createDefinition("timeout", "1000", false);
        PluginInfo pluginInfo = createPluginInfo(definition);
        Map<String, String> runtimeConfig = new HashMap<>();
        runtimeConfig.put("timeout", "3000");
        resolver.updateConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginInfo.getPluginId(),
            runtimeConfig);
        PluginConfigResolution updated = resolver.resolve(pluginInfo, false);
        
        resolver.updateConfig(PluginConfigSourceType.RUNTIME_PERSISTED, pluginInfo.getPluginId(),
            Collections.emptyMap());
        PluginConfigResolution removed = resolver.resolve(pluginInfo, false);
        
        assertEquals("3000", updated.getConfig().get("timeout"));
        assertEquals("1000", removed.getConfig().get("timeout"));
    }
    
    @Test
    void testGetConfigReadsEverySourceIndependentlyFromUpdateCapability() {
        ConfigItemDefinition definition = createDefinition("timeout", "1000", false);
        definition.setAliases(Collections.singletonList("nacos.legacy.timeout"));
        PluginInfo pluginInfo = createPluginInfo(definition);
        environment.setProperty("nacos.legacy.timeout", "2000");
        resolver.updateConfig(PluginConfigSourceType.RUNTIME_PERSISTED,
            pluginInfo.getPluginId(), Collections.singletonMap("timeout", "3000"));
        resolver.updateConfig(PluginConfigSourceType.LOCAL_ONLY, pluginInfo.getPluginId(),
            Collections.singletonMap("timeout", "4000"));
        
        assertEquals("1000",
            resolver.getConfig(PluginConfigSourceType.DEFAULT, pluginInfo).get("timeout"));
        assertEquals("2000",
            resolver.getConfig(PluginConfigSourceType.STATIC, pluginInfo).get("timeout"));
        assertEquals("3000", resolver.getConfig(PluginConfigSourceType.RUNTIME_PERSISTED,
            pluginInfo).get("timeout"));
        assertEquals("4000",
            resolver.getConfig(PluginConfigSourceType.LOCAL_ONLY, pluginInfo).get("timeout"));
    }
    
    @Test
    void testUpdateConfigRejectsReadOnlySource() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> resolver.updateConfig(PluginConfigSourceType.STATIC, "trace:demo",
                Collections.emptyMap()));
        
        assertTrue(exception.getMessage().contains("not updatable"));
    }
    
    @Test
    void testResolveWithoutDefinitionsUsesPluginConfig() {
        PluginInfo pluginInfo = createPluginInfo(null);
        pluginInfo.setConfigDefinitions(null);
        pluginInfo.setConfig(Collections.singletonMap("legacy", "value"));
        
        PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
        
        assertEquals("value", resolution.getConfig().get("legacy"));
        assertTrue(resolution.getValueMetas().isEmpty());
        assertTrue(resolver.getConfig(PluginConfigSourceType.DEFAULT, pluginInfo).isEmpty());
    }
    
    @Test
    void testResolveSkipsBlankDefinition() {
        ConfigItemDefinition blankDefinition = createDefinition(" ", null, false);
        ConfigItemDefinition timeoutDefinition = createDefinition("timeout", "1000", false);
        PluginInfo pluginInfo = createPluginInfo(timeoutDefinition);
        pluginInfo.setConfigDefinitions(Arrays.asList(blankDefinition, timeoutDefinition));
        
        PluginConfigResolution resolution = resolver.resolve(pluginInfo, false);
        
        assertEquals(Collections.singletonMap("timeout", "1000"), resolution.getConfig());
        assertEquals(1, resolution.getValueMetas().size());
    }
    
    @Test
    void testGetConfigRejectsUnknownSource() {
        PluginInfo pluginInfo = createPluginInfo(createDefinition("timeout", "1000", false));
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> resolver.getConfig(null, pluginInfo));
        
        assertTrue(exception.getMessage().contains("source not found"));
    }
    
    private void assertMeta(PluginConfigValueMeta meta, String key,
        PluginConfigSourceType source, boolean overridden) {
        assertEquals(key, meta.getKey());
        assertEquals(source, meta.getSource());
        if (overridden) {
            assertTrue(meta.isOverridden());
        } else {
            assertFalse(meta.isOverridden());
        }
    }
    
    private ConfigItemDefinition createDefinition(String key, String defaultValue,
        boolean sensitive) {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey(key);
        definition.setDefaultValue(defaultValue);
        definition.setSensitive(sensitive);
        return definition;
    }
    
    private PluginInfo createPluginInfo(ConfigItemDefinition definition) {
        PluginInfo pluginInfo = new PluginInfo();
        pluginInfo.setPluginId("trace:demo");
        pluginInfo.setPluginType(PluginType.TRACE);
        pluginInfo.setPluginName("demo");
        pluginInfo.setConfigDefinitions(Collections.singletonList(definition));
        return pluginInfo;
    }
}
