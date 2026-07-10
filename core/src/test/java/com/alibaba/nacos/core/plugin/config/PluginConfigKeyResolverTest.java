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
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigKeyResolverTest {
    
    private final PluginConfigKeyResolver resolver = new PluginConfigKeyResolver();
    
    @Test
    void testResolveStandardAndAliasKeys() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("timeout");
        definition.setAliases(Arrays.asList("nacos.legacy.timeout", "oldTimeout"));
        
        PluginConfigKeyCandidate candidate = resolver.resolve(createPluginInfo(), definition);
        
        assertEquals("timeout", candidate.getItemKey());
        assertEquals("nacos.plugin.trace.demo.timeout", candidate.getStandardKey());
        assertEquals("nacos.legacy.timeout", candidate.getAliasKeys().get(0));
        assertEquals("nacos.plugin.trace.demo.oldTimeout", candidate.getAliasKeys().get(1));
    }
    
    @Test
    void testNormalizeConfigWithStandardAndAliasKeys() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("timeout");
        definition.setAliases(Arrays.asList("nacos.legacy.timeout", "oldTimeout"));
        PluginInfo pluginInfo = createPluginInfo();
        pluginInfo.setConfigDefinitions(Arrays.asList(definition));
        
        Map<String, String> input = new LinkedHashMap<>();
        input.put("nacos.plugin.trace.demo.timeout", "1000");
        input.put("nacos.legacy.timeout", "2000");
        input.put("oldTimeout", "3000");
        input.put("unknown", "value");
        
        Map<String, String> result = resolver.normalizeConfig(pluginInfo, input);
        
        assertEquals("1000", result.get("timeout"));
        assertEquals("value", result.get("unknown"));
        assertEquals(2, result.size());
    }
    
    @Test
    void testNormalizeConfigWithItemKeyFirst() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("timeout");
        definition.setAliases(Arrays.asList("nacos.legacy.timeout", "oldTimeout"));
        PluginInfo pluginInfo = createPluginInfo();
        pluginInfo.setConfigDefinitions(Arrays.asList(definition));
        
        Map<String, String> input = new LinkedHashMap<>();
        input.put("nacos.plugin.trace.demo.timeout", "1000");
        input.put("timeout", "900");
        
        Map<String, String> result = resolver.normalizeConfig(pluginInfo, input);
        
        assertEquals("900", result.get("timeout"));
        assertEquals(1, result.size());
    }
    
    @Test
    void testNormalizeConfigWithRawAliasKey() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("timeout");
        definition.setAliases(Arrays.asList("oldTimeout"));
        PluginInfo pluginInfo = createPluginInfo();
        pluginInfo.setConfigDefinitions(Arrays.asList(definition));
        
        Map<String, String> input = new LinkedHashMap<>();
        input.put("oldTimeout", "3000");
        
        Map<String, String> result = resolver.normalizeConfig(pluginInfo, input);
        
        assertEquals("3000", result.get("timeout"));
        assertEquals(1, result.size());
    }
    
    @Test
    void testNormalizeConfigCanonicalKeyWinsAliasCollision() {
        ConfigItemDefinition timeoutDefinition = new ConfigItemDefinition();
        timeoutDefinition.setKey("timeout");
        timeoutDefinition.setAliases(Arrays.asList("retry"));
        ConfigItemDefinition retryDefinition = new ConfigItemDefinition();
        retryDefinition.setKey("retry");
        PluginInfo pluginInfo = createPluginInfo();
        pluginInfo.setConfigDefinitions(Arrays.asList(timeoutDefinition, retryDefinition));
        
        Map<String, String> input = new LinkedHashMap<>();
        input.put("retry", "3");
        
        Map<String, String> result = resolver.normalizeConfig(pluginInfo, input);
        
        assertFalse(result.containsKey("timeout"));
        assertEquals("3", result.get("retry"));
        assertEquals(1, result.size());
    }
    
    @Test
    void testNormalizeConfigRejectsAmbiguousAlias() {
        ConfigItemDefinition timeoutDefinition = new ConfigItemDefinition();
        timeoutDefinition.setKey("timeout");
        timeoutDefinition.setAliases(Arrays.asList("legacyValue"));
        ConfigItemDefinition retryDefinition = new ConfigItemDefinition();
        retryDefinition.setKey("retry");
        retryDefinition.setAliases(Arrays.asList("legacyValue"));
        PluginInfo pluginInfo = createPluginInfo();
        pluginInfo.setConfigDefinitions(Arrays.asList(timeoutDefinition, retryDefinition));
        
        Map<String, String> input = new LinkedHashMap<>();
        input.put("legacyValue", "3");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> resolver.normalizeConfig(pluginInfo, input));
        
        assertTrue(exception.getMessage().contains("Ambiguous plugin config key"));
        assertTrue(exception.getMessage().contains("timeout"));
        assertTrue(exception.getMessage().contains("retry"));
    }
    
    private PluginInfo createPluginInfo() {
        PluginInfo pluginInfo = new PluginInfo();
        pluginInfo.setPluginId("trace:demo");
        pluginInfo.setPluginType(PluginType.TRACE);
        pluginInfo.setPluginName("demo");
        return pluginInfo;
    }
}
