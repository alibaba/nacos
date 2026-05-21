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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTypeTest {
    
    @Test
    @DisplayName("test AUTH enum values")
    void testAuthEnumValues() {
        assertEquals("auth", PluginType.AUTH.getType());
        assertEquals("Authentication plugin", PluginType.AUTH.getDescription());
    }
    
    @Test
    @DisplayName("test DATASOURCE_DIALECT enum values")
    void testDatasourceDialectEnumValues() {
        assertEquals("datasource-dialect", PluginType.DATASOURCE_DIALECT.getType());
        assertEquals("Datasource dialect plugin", PluginType.DATASOURCE_DIALECT.getDescription());
    }
    
    @Test
    @DisplayName("test CONFIG_CHANGE enum values")
    void testConfigChangeEnumValues() {
        assertEquals("config-change", PluginType.CONFIG_CHANGE.getType());
        assertEquals("Config change plugin", PluginType.CONFIG_CHANGE.getDescription());
    }
    
    @Test
    @DisplayName("test ENCRYPTION enum values")
    void testEncryptionEnumValues() {
        assertEquals("encryption", PluginType.ENCRYPTION.getType());
        assertEquals("Encryption plugin", PluginType.ENCRYPTION.getDescription());
    }
    
    @Test
    @DisplayName("test TRACE enum values")
    void testTraceEnumValues() {
        assertEquals("trace", PluginType.TRACE.getType());
        assertEquals("Trace plugin", PluginType.TRACE.getDescription());
    }
    
    @Test
    @DisplayName("test ENVIRONMENT enum values")
    void testEnvironmentEnumValues() {
        assertEquals("environment", PluginType.ENVIRONMENT.getType());
        assertEquals("Environment plugin", PluginType.ENVIRONMENT.getDescription());
    }
    
    @Test
    @DisplayName("test CONTROL enum values")
    void testControlEnumValues() {
        assertEquals("control", PluginType.CONTROL.getType());
        assertEquals("Control plugin", PluginType.CONTROL.getDescription());
    }
    
    @Test
    @DisplayName("test VISIBILITY enum values")
    void testVisibilityEnumValues() {
        assertEquals("visibility", PluginType.VISIBILITY.getType());
        assertEquals("Visibility plugin", PluginType.VISIBILITY.getDescription());
    }
    
    @Test
    @DisplayName("test AI_PIPELINE enum values")
    void testAiPipelineEnumValues() {
        assertEquals("ai-pipeline", PluginType.AI_PIPELINE.getType());
        assertEquals("AI publish pipeline plugin", PluginType.AI_PIPELINE.getDescription());
    }
    
    @Test
    @DisplayName("test AI_STORAGE enum values")
    void testAiStorageEnumValues() {
        assertEquals("ai-storage", PluginType.AI_STORAGE.getType());
        assertEquals("AI resource storage plugin", PluginType.AI_STORAGE.getDescription());
    }
    
    @Test
    @DisplayName("test AI_RESOURCE_IMPORT enum values")
    void testAiResourceImportEnumValues() {
        assertEquals("ai-resource-import", PluginType.AI_RESOURCE_IMPORT.getType());
        assertEquals("AI resource import plugin", PluginType.AI_RESOURCE_IMPORT.getDescription());
    }
    
    @Test
    @DisplayName("test fromType with valid type")
    void testFromTypeWithValidType() {
        assertEquals(PluginType.AUTH, PluginType.fromType("auth"));
        assertEquals(PluginType.ENCRYPTION, PluginType.fromType("encryption"));
        assertEquals(PluginType.AI_PIPELINE, PluginType.fromType("ai-pipeline"));
        assertEquals(PluginType.AI_STORAGE, PluginType.fromType("ai-storage"));
        assertEquals(PluginType.AI_RESOURCE_IMPORT, PluginType.fromType("ai-resource-import"));
    }
    
    @Test
    @DisplayName("test fromType with invalid type throws exception")
    void testFromTypeWithInvalidTypeThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            PluginType.fromType("invalid-type");
        });
        assertTrue(exception.getMessage().contains("Unknown plugin type"));
        assertTrue(exception.getMessage().contains("invalid-type"));
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
}
