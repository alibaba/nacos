/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTypeTest {
    
    @Test
    @DisplayName("test AUTH enum values")
    void testAuthEnumValues() {
        assertEquals("auth", PluginType.AUTH.getType());
        assertEquals("Authentication plugin", PluginType.AUTH.getDescription());
        assertEquals(PluginExecutionMode.EXCLUSIVE, PluginType.AUTH.getExecutionMode());
        assertTrue(PluginType.AUTH.isCritical());
    }
    
    @Test
    @DisplayName("test DATASOURCE_DIALECT enum values")
    void testDatasourceDialectEnumValues() {
        assertEquals("datasource-dialect", PluginType.DATASOURCE_DIALECT.getType());
        assertEquals("Datasource dialect plugin", PluginType.DATASOURCE_DIALECT.getDescription());
        assertEquals(PluginExecutionMode.EXCLUSIVE,
            PluginType.DATASOURCE_DIALECT.getExecutionMode());
        assertTrue(PluginType.DATASOURCE_DIALECT.isCritical());
    }
    
    @Test
    @DisplayName("test CONFIG_CHANGE enum values")
    void testConfigChangeEnumValues() {
        assertEquals("config-change", PluginType.CONFIG_CHANGE.getType());
        assertEquals("Config change plugin", PluginType.CONFIG_CHANGE.getDescription());
        assertEquals(PluginExecutionMode.CHAIN, PluginType.CONFIG_CHANGE.getExecutionMode());
        assertFalse(PluginType.CONFIG_CHANGE.isCritical());
    }
    
    @Test
    @DisplayName("test ENCRYPTION enum values")
    void testEncryptionEnumValues() {
        assertEquals("encryption", PluginType.ENCRYPTION.getType());
        assertEquals("Encryption plugin", PluginType.ENCRYPTION.getDescription());
        assertEquals(PluginExecutionMode.ROUTED, PluginType.ENCRYPTION.getExecutionMode());
    }
    
    @Test
    @DisplayName("test TRACE enum values")
    void testTraceEnumValues() {
        assertEquals("trace", PluginType.TRACE.getType());
        assertEquals("Trace plugin", PluginType.TRACE.getDescription());
        assertEquals(PluginExecutionMode.BROADCAST, PluginType.TRACE.getExecutionMode());
    }
    
    @Test
    @DisplayName("test ENVIRONMENT enum values")
    void testEnvironmentEnumValues() {
        assertEquals("environment", PluginType.ENVIRONMENT.getType());
        assertEquals("Environment plugin", PluginType.ENVIRONMENT.getDescription());
        assertEquals(PluginExecutionMode.CHAIN, PluginType.ENVIRONMENT.getExecutionMode());
        assertEquals(PluginInitializationPhase.PRE_CONTEXT,
            PluginType.ENVIRONMENT.getInitializationPhase());
    }
    
    @Test
    @DisplayName("test CONTROL enum values")
    void testControlEnumValues() {
        assertEquals("control", PluginType.CONTROL.getType());
        assertEquals("Control plugin", PluginType.CONTROL.getDescription());
        assertEquals(PluginExecutionMode.EXCLUSIVE, PluginType.CONTROL.getExecutionMode());
    }
    
    @Test
    @DisplayName("test VISIBILITY enum values")
    void testVisibilityEnumValues() {
        assertEquals("visibility", PluginType.VISIBILITY.getType());
        assertEquals("Visibility plugin", PluginType.VISIBILITY.getDescription());
        assertEquals(PluginExecutionMode.ROUTED, PluginType.VISIBILITY.getExecutionMode());
    }
    
    @Test
    @DisplayName("test AI_PIPELINE enum values")
    void testAiPipelineEnumValues() {
        assertEquals("ai-pipeline", PluginType.AI_PIPELINE.getType());
        assertEquals("AI publish pipeline plugin", PluginType.AI_PIPELINE.getDescription());
        assertEquals(PluginExecutionMode.CHAIN, PluginType.AI_PIPELINE.getExecutionMode());
    }
    
    @Test
    @DisplayName("test AI_STORAGE enum values")
    void testAiStorageEnumValues() {
        assertEquals("ai-storage", PluginType.AI_STORAGE.getType());
        assertEquals("AI resource storage plugin", PluginType.AI_STORAGE.getDescription());
        assertEquals(PluginExecutionMode.ROUTED, PluginType.AI_STORAGE.getExecutionMode());
        assertTrue(PluginType.AI_STORAGE.isCritical());
    }
    
    @Test
    @DisplayName("test AI_RESOURCE_IMPORT enum values")
    void testAiResourceImportEnumValues() {
        assertEquals("ai-resource-import", PluginType.AI_RESOURCE_IMPORT.getType());
        assertEquals("AI resource import plugin", PluginType.AI_RESOURCE_IMPORT.getDescription());
        assertEquals(PluginExecutionMode.ROUTED,
            PluginType.AI_RESOURCE_IMPORT.getExecutionMode());
    }
    
    @Test
    @DisplayName("test all enum values count")
    void testAllEnumValuesCount() {
        PluginType[] values = PluginType.values();
        assertEquals(11, values.length);
    }
    
    @Test
    @DisplayName("test enum valueOf")
    void testEnumValueOf() {
        assertEquals(PluginType.AUTH, PluginType.valueOf("AUTH"));
        assertEquals(PluginType.ENCRYPTION, PluginType.valueOf("ENCRYPTION"));
        assertEquals(PluginType.AI_PIPELINE, PluginType.valueOf("AI_PIPELINE"));
        assertEquals(PluginType.AI_RESOURCE_IMPORT, PluginType.valueOf("AI_RESOURCE_IMPORT"));
    }
    
    @Test
    @DisplayName("test exclusive type capability")
    void testExclusiveTypeCapability() {
        assertTrue(PluginType.AUTH.isExclusive());
        assertTrue(PluginType.DATASOURCE_DIALECT.isExclusive());
        assertTrue(PluginType.CONTROL.isExclusive());
        assertFalse(PluginType.TRACE.isExclusive());
    }
    
    @Test
    void testExecutionModeValues() {
        assertEquals(4, PluginExecutionMode.values().length);
        assertEquals(PluginExecutionMode.EXCLUSIVE,
            PluginExecutionMode.valueOf("EXCLUSIVE"));
        assertEquals(PluginExecutionMode.CHAIN, PluginExecutionMode.valueOf("CHAIN"));
        assertEquals(PluginExecutionMode.ROUTED, PluginExecutionMode.valueOf("ROUTED"));
        assertEquals(PluginExecutionMode.BROADCAST, PluginExecutionMode.valueOf("BROADCAST"));
    }
    
    @Test
    void testInitializationPhaseValues() {
        assertEquals(2, PluginInitializationPhase.values().length);
        assertEquals(PluginInitializationPhase.PRE_CONTEXT,
            PluginInitializationPhase.valueOf("PRE_CONTEXT"));
        for (PluginType type : PluginType.values()) {
            if (PluginType.ENVIRONMENT != type) {
                assertEquals(PluginInitializationPhase.STANDARD,
                    type.getInitializationPhase());
            }
        }
    }
}
