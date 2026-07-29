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
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticPluginConfigSourceResolverTest {
    
    private final StaticPluginConfigSourceResolver resolver =
        new StaticPluginConfigSourceResolver();
    
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
    void testGetConfigWithoutEnvironment() {
        EnvUtil.setEnvironment(null);
        
        assertTrue(resolver.getConfig(createPluginInfo(Collections.emptyList())).isEmpty());
        assertEquals(PluginConfigSourceType.STATIC, resolver.getSourceType());
    }
    
    @Test
    void testGetConfigSkipsBlankDefinitionAndUsesFirstConfiguredAlias() {
        ConfigItemDefinition blankDefinition = new ConfigItemDefinition();
        blankDefinition.setKey(" ");
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("token");
        definition.setAliases(Arrays.asList("nacos.legacy.missing", "nacos.legacy.first",
            "nacos.legacy.second"));
        environment.setProperty("nacos.legacy.first", "first-value");
        environment.setProperty("nacos.legacy.second", "second-value");
        
        Map<String, String> config =
            resolver.getConfig(createPluginInfo(Arrays.asList(blankDefinition, definition)));
        
        assertEquals(Collections.singletonMap("token", "first-value"), config);
    }
    
    @Test
    void testGetConfigUsesEmptyStandardValueInsteadOfAlias() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("token");
        definition.setAliases(Collections.singletonList("nacos.legacy.token"));
        environment.setProperty("nacos.plugin.trace.demo.token", "");
        environment.setProperty("nacos.legacy.token", "legacy-value");
        
        Map<String, String> config =
            resolver.getConfig(createPluginInfo(Collections.singletonList(definition)));
        
        assertEquals(Collections.singletonMap("token", ""), config);
    }
    
    @Test
    void testGetConfigKeepsEmptyStandardValueWithoutAlias() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("token");
        environment.setProperty("nacos.plugin.trace.demo.token", "");
        
        Map<String, String> config =
            resolver.getConfig(createPluginInfo(Collections.singletonList(definition)));
        
        assertEquals(Collections.singletonMap("token", ""), config);
    }
    
    @Test
    void testRefreshAcceptsRuntimeFieldAndKeepsRestartField() {
        ConfigItemDefinition blankDefinition = definition(" ", ConfigItemEffectMode.RUNTIME);
        ConfigItemDefinition runtimeDefinition =
            definition("runtime", ConfigItemEffectMode.RUNTIME);
        ConfigItemDefinition restartDefinition =
            definition("restart", ConfigItemEffectMode.RESTART);
        PluginInfo pluginInfo = createPluginInfo(
            Arrays.asList(blankDefinition, runtimeDefinition, restartDefinition));
        environment.setProperty("nacos.plugin.trace.demo.runtime", "runtime-old");
        environment.setProperty("nacos.plugin.trace.demo.restart", "restart-old");
        resolver.initializeConfig(pluginInfo);
        
        MockEnvironment refreshedEnvironment = new MockEnvironment();
        refreshedEnvironment.setProperty("nacos.plugin.trace.demo.runtime", "runtime-new");
        refreshedEnvironment.setProperty("nacos.plugin.trace.demo.restart", "restart-new");
        EnvUtil.setEnvironment(refreshedEnvironment);
        resolver.refreshConfig(pluginInfo);
        
        Map<String, String> config = resolver.getConfig(pluginInfo);
        assertEquals("runtime-new", config.get("runtime"));
        assertEquals("restart-old", config.get("restart"));
    }
    
    @Test
    void testRefreshRemovesRuntimeFieldAndKeepsRemovedRestartField() {
        ConfigItemDefinition runtimeDefinition =
            definition("runtime", ConfigItemEffectMode.RUNTIME);
        ConfigItemDefinition restartDefinition =
            definition("restart", ConfigItemEffectMode.RESTART);
        PluginInfo pluginInfo =
            createPluginInfo(Arrays.asList(runtimeDefinition, restartDefinition));
        environment.setProperty("nacos.plugin.trace.demo.runtime", "runtime-old");
        environment.setProperty("nacos.plugin.trace.demo.restart", "restart-old");
        resolver.initializeConfig(pluginInfo);
        
        EnvUtil.setEnvironment(new MockEnvironment());
        resolver.refreshConfig(pluginInfo);
        
        assertEquals(Collections.singletonMap("restart", "restart-old"),
            resolver.getConfig(pluginInfo));
    }
    
    @Test
    void testRefreshBeforeInitializationAcceptsCurrentEnvironment() {
        ConfigItemDefinition restartDefinition =
            definition("restart", ConfigItemEffectMode.RESTART);
        PluginInfo pluginInfo = createPluginInfo(Collections.singletonList(restartDefinition));
        environment.setProperty("nacos.plugin.trace.demo.restart", "restart-current");
        
        resolver.refreshConfig(pluginInfo);
        
        assertEquals(Collections.singletonMap("restart", "restart-current"),
            resolver.getConfig(pluginInfo));
    }
    
    @Test
    void testRefreshWithNullDefinitionsKeepsSnapshot() {
        PluginInfo pluginInfo = createPluginInfo(null);
        resolver.initializeConfig(pluginInfo);
        
        resolver.refreshConfig(pluginInfo);
        
        assertTrue(resolver.getConfig(pluginInfo).isEmpty());
    }
    
    private ConfigItemDefinition definition(String key, ConfigItemEffectMode effectMode) {
        ConfigItemDefinition result = new ConfigItemDefinition();
        result.setKey(key);
        result.setEffectMode(effectMode);
        return result;
    }
    
    private PluginInfo createPluginInfo(List<ConfigItemDefinition> definitions) {
        PluginInfo pluginInfo = new PluginInfo();
        pluginInfo.setPluginId("trace:demo");
        pluginInfo.setPluginType(PluginType.TRACE);
        pluginInfo.setPluginName("demo");
        pluginInfo.setConfigDefinitions(definitions);
        return pluginInfo;
    }
}
