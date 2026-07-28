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
import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigDefinitionNormalizerTest {
    
    @Test
    void testEmptyDefinitions() {
        assertTrue(PluginConfigDefinitionNormalizer.normalize("trace:test", null,
            PluginInitializationPhase.STANDARD).isEmpty());
        assertTrue(PluginConfigDefinitionNormalizer.normalize("trace:test",
            Collections.emptyList(), PluginInitializationPhase.STANDARD).isEmpty());
    }
    
    @Test
    void testIgnoreInvalidAndConflictingDefinitionsAndAliases() {
        ConfigItemDefinition first = definition("first");
        first.setAliases(Arrays.asList(null, " ", "enabled",
            "nacos.plugin.trace.test.enabled", "legacy.first", "nacos.legacy.first",
            "legacy.first", "second"));
        ConfigItemDefinition duplicateKey = definition("first");
        ConfigItemDefinition aliasClaimedKey = definition("second");
        ConfigItemDefinition reserved = definition("enabled");
        ConfigItemDefinition normalizedReserved =
            definition("nacos.plugin.trace.test.enabled");
        ConfigItemDefinition blank = definition(" ");
        ConfigItemDefinition third = definition("third");
        third.setAliases(Arrays.asList("nacos.plugin.trace.test.first",
            "nacos.plugin.trace.test.third", "legacy.third"));
        ConfigItemDefinition fullKey = definition("nacos.legacy.key");
        
        List<ConfigItemDefinition> result = PluginConfigDefinitionNormalizer.normalize(
            "trace:test", Arrays.asList(null, first, duplicateKey, aliasClaimedKey, reserved,
                normalizedReserved, blank, third, fullKey),
            PluginInitializationPhase.STANDARD);
        
        assertEquals(3, result.size());
        assertEquals("first", result.get(0).getKey());
        assertEquals(Arrays.asList("legacy.first", "nacos.legacy.first", "second"),
            result.get(0).getAliases());
        assertEquals("third", result.get(1).getKey());
        assertEquals(Collections.singletonList("legacy.third"), result.get(1).getAliases());
        assertEquals("nacos.legacy.key", result.get(2).getKey());
    }
    
    @Test
    void testCopyMetadataAndNormalizePreContextEffectMode() {
        ConfigItemDefinition source =
            new ConfigItemDefinition("endpoint", "Endpoint", ConfigItemType.STRING);
        source.setDescription("Endpoint description");
        source.setDefaultValue("https://example.com");
        source.setRequired(true);
        source.setEnumValues(Collections.singletonList("value"));
        source.setAliases(null);
        source.setSensitive(true);
        source.setEffectMode(ConfigItemEffectMode.RUNTIME);
        
        List<ConfigItemDefinition> standard = PluginConfigDefinitionNormalizer.normalize(
            "trace:test", Collections.singletonList(source),
            PluginInitializationPhase.STANDARD);
        
        ConfigItemDefinition standardCopy = standard.get(0);
        assertNotSame(source, standardCopy);
        assertEquals(source.getKey(), standardCopy.getKey());
        assertEquals(source.getName(), standardCopy.getName());
        assertEquals(source.getType(), standardCopy.getType());
        assertEquals(source.getDescription(), standardCopy.getDescription());
        assertEquals(source.getDefaultValue(), standardCopy.getDefaultValue());
        assertTrue(standardCopy.isRequired());
        assertEquals(source.getEnumValues(), standardCopy.getEnumValues());
        assertNotSame(source.getEnumValues(), standardCopy.getEnumValues());
        assertNull(standardCopy.getAliases());
        assertTrue(standardCopy.isSensitive());
        assertEquals(ConfigItemEffectMode.RUNTIME, standardCopy.getEffectMode());
        assertEquals(ConfigItemEffectMode.RESTART,
            PluginConfigDefinitionNormalizer.normalize("environment:test",
                Collections.singletonList(source), PluginInitializationPhase.PRE_CONTEXT)
                .get(0).getEffectMode());
        assertEquals(ConfigItemEffectMode.RUNTIME, source.getEffectMode());
        assertThrows(UnsupportedOperationException.class,
            () -> standard.add(definition("other")));
    }
    
    @Test
    void testPluginIdWithoutSeparatorKeepsRawInputKeys() {
        ConfigItemDefinition first = definition("first");
        first.setAliases(Collections.singletonList("legacy"));
        ConfigItemDefinition second = definition("second");
        second.setAliases(Collections.singletonList("nacos.legacy"));
        
        List<ConfigItemDefinition> result = PluginConfigDefinitionNormalizer.normalize(
            "invalid", Arrays.asList(first, second), PluginInitializationPhase.STANDARD);
        
        assertEquals(2, result.size());
        assertFalse(result.get(0).getAliases().isEmpty());
        assertFalse(result.get(1).getAliases().isEmpty());
    }
    
    private ConfigItemDefinition definition(String key) {
        return new ConfigItemDefinition(key, key, ConfigItemType.STRING);
    }
}
