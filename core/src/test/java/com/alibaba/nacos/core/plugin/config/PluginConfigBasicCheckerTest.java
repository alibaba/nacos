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
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginConfigBasicCheckerTest {
    
    private final PluginConfigBasicChecker checker = new PluginConfigBasicChecker();
    
    @Test
    void validateRuntimeUpdateAcceptsRuntimeAdditionAndRemoval() {
        PluginInfo pluginInfo = pluginInfo(definition("timeout", ConfigItemType.NUMBER,
            ConfigItemEffectMode.RUNTIME));
        Map<String, String> current = Collections.singletonMap("timeout", "1000");
        
        assertDoesNotThrow(() -> checker.validateRuntimeUpdate(pluginInfo, current,
            Collections.emptyMap()));
    }
    
    @Test
    void validateRuntimeUpdateRejectsRestartAdditionAndRemoval() {
        PluginInfo pluginInfo = pluginInfo(definition("endpoint", ConfigItemType.STRING,
            ConfigItemEffectMode.RESTART));
        
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateRuntimeUpdate(pluginInfo, Collections.emptyMap(),
                Collections.singletonMap("endpoint", "new")));
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateRuntimeUpdate(pluginInfo,
                Collections.singletonMap("endpoint", "old"), Collections.emptyMap()));
    }
    
    @Test
    void validateRuntimeUpdateRejectsUnknownAndNullValues() {
        ConfigItemDefinition secret = definition("secret", ConfigItemType.STRING,
            ConfigItemEffectMode.RUNTIME);
        secret.setSensitive(true);
        PluginInfo pluginInfo = pluginInfo(secret);
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("secret", null);
        
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateRuntimeUpdate(pluginInfo, Collections.emptyMap(),
                Collections.singletonMap("unknown", "value")));
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateRuntimeUpdate(pluginInfo, Collections.emptyMap(), nullValue));
    }
    
    @Test
    void validateRuntimeUpdateRejectsInvalidSourceValue() {
        PluginInfo pluginInfo = pluginInfo(definition("timeout", ConfigItemType.NUMBER,
            ConfigItemEffectMode.RUNTIME));
        
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateRuntimeUpdate(pluginInfo, Collections.emptyMap(),
                Collections.singletonMap("timeout", "invalid")));
    }
    
    @Test
    void validateEffectiveConfigChecksRequiredAndTypes() {
        ConfigItemDefinition required = definition("required", ConfigItemType.STRING,
            ConfigItemEffectMode.RUNTIME);
        required.setRequired(true);
        ConfigItemDefinition number = definition("number", ConfigItemType.NUMBER,
            ConfigItemEffectMode.RUNTIME);
        ConfigItemDefinition bool = definition("bool", ConfigItemType.BOOLEAN,
            ConfigItemEffectMode.RUNTIME);
        ConfigItemDefinition mode = definition("mode", ConfigItemType.ENUM,
            ConfigItemEffectMode.RUNTIME);
        mode.setEnumValues(Arrays.asList("A", "B"));
        ConfigItemDefinition blank = definition(" ", ConfigItemType.STRING,
            ConfigItemEffectMode.RUNTIME);
        PluginInfo pluginInfo = pluginInfo(blank, required, number, bool, mode);
        
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateEffectiveConfig(pluginInfo, Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateEffectiveConfig(pluginInfo,
                effectiveConfig("required", "value", "number", "invalid")));
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateEffectiveConfig(pluginInfo,
                effectiveConfig("required", "value", "number", "1", "bool", "invalid")));
        assertThrows(IllegalArgumentException.class,
            () -> checker.validateEffectiveConfig(pluginInfo, effectiveConfig("required", "value",
                "number", "1", "bool", "true", "mode", "C")));
        assertDoesNotThrow(() -> checker.validateEffectiveConfig(pluginInfo,
            effectiveConfig("required", "value", "number", "1.5", "bool", "false", "mode",
                "A")));
    }
    
    private PluginInfo pluginInfo(ConfigItemDefinition... definitions) {
        PluginInfo result = new PluginInfo();
        result.setConfigDefinitions(Arrays.asList(definitions));
        return result;
    }
    
    private ConfigItemDefinition definition(String key, ConfigItemType type,
        ConfigItemEffectMode effectMode) {
        ConfigItemDefinition result = new ConfigItemDefinition(key, key, type);
        result.setEffectMode(effectMode);
        return result;
    }
    
    private Map<String, String> effectiveConfig(String... values) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }
}
