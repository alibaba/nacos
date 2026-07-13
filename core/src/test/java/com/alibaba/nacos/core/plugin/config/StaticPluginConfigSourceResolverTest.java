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
    
    private PluginInfo createPluginInfo(List<ConfigItemDefinition> definitions) {
        PluginInfo pluginInfo = new PluginInfo();
        pluginInfo.setPluginId("trace:demo");
        pluginInfo.setPluginType(PluginType.TRACE);
        pluginInfo.setPluginName("demo");
        pluginInfo.setConfigDefinitions(definitions);
        return pluginInfo;
    }
}
