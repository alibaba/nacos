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

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigItemDefinitionTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        assertNull(definition.getKey());
        assertNull(definition.getName());
        assertNull(definition.getDescription());
        assertNull(definition.getDefaultValue());
        assertNull(definition.getType());
        assertFalse(definition.isRequired());
        assertNull(definition.getEnumValues());
        assertNotNull(definition.getAliases());
        assertTrue(definition.getAliases().isEmpty());
        assertFalse(definition.isSensitive());
        assertEquals(ConfigItemEffectMode.RESTART, definition.getEffectMode());
    }
    
    @Test
    @DisplayName("test constructor with key, name and type")
    void testConstructorWithKeyNameAndType() {
        ConfigItemDefinition definition =
            new ConfigItemDefinition("testKey", "Test Name", ConfigItemType.STRING);
        assertEquals("testKey", definition.getKey());
        assertEquals("Test Name", definition.getName());
        assertEquals(ConfigItemType.STRING, definition.getType());
    }
    
    @Test
    @DisplayName("test getter and setter for key")
    void testGetterAndSetterForKey() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setKey("server.port");
        assertEquals("server.port", definition.getKey());
    }
    
    @Test
    @DisplayName("test getter and setter for name")
    void testGetterAndSetterForName() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setName("Server Port");
        assertEquals("Server Port", definition.getName());
    }
    
    @Test
    @DisplayName("test getter and setter for description")
    void testGetterAndSetterForDescription() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setDescription("Port number for server");
        assertEquals("Port number for server", definition.getDescription());
    }
    
    @Test
    @DisplayName("test getter and setter for defaultValue")
    void testGetterAndSetterForDefaultValue() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setDefaultValue("8080");
        assertEquals("8080", definition.getDefaultValue());
    }
    
    @Test
    @DisplayName("test getter and setter for type")
    void testGetterAndSetterForType() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setType(ConfigItemType.NUMBER);
        assertEquals(ConfigItemType.NUMBER, definition.getType());
    }
    
    @Test
    @DisplayName("test getter and setter for required")
    void testGetterAndSetterForRequired() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setRequired(true);
        assertTrue(definition.isRequired());
        definition.setRequired(false);
        assertFalse(definition.isRequired());
    }
    
    @Test
    @DisplayName("test getter and setter for enumValues")
    void testGetterAndSetterForEnumValues() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setType(ConfigItemType.ENUM);
        List<String> enumValues = new ArrayList<>();
        enumValues.add("option1");
        enumValues.add("option2");
        definition.setEnumValues(enumValues);
        assertNotNull(definition.getEnumValues());
        assertEquals(2, definition.getEnumValues().size());
        assertEquals("option1", definition.getEnumValues().get(0));
        assertEquals("option2", definition.getEnumValues().get(1));
    }
    
    @Test
    @DisplayName("test getter and setter for aliases")
    void testGetterAndSetterForAliases() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        List<String> aliases = new ArrayList<>();
        aliases.add("nacos.core.auth.plugin.nacos.token.secret.key");
        definition.setAliases(aliases);
        assertEquals(1, definition.getAliases().size());
        assertEquals("nacos.core.auth.plugin.nacos.token.secret.key",
            definition.getAliases().get(0));
    }
    
    @Test
    @DisplayName("test getter and setter for sensitive")
    void testGetterAndSetterForSensitive() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setSensitive(true);
        assertTrue(definition.isSensitive());
        definition.setSensitive(false);
        assertFalse(definition.isSensitive());
    }
    
    @Test
    @DisplayName("test getter and setter for effectMode")
    void testGetterAndSetterForEffectMode() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setEffectMode(ConfigItemEffectMode.RUNTIME);
        assertEquals(ConfigItemEffectMode.RUNTIME, definition.getEffectMode());
    }
    
    @Test
    @DisplayName("test Builder pattern")
    void testBuilderPattern() {
        ConfigItemDefinition definition =
            new ConfigItemDefinition.Builder("auth.enabled", "Enable Auth", ConfigItemType.BOOLEAN)
                .description("Enable authentication plugin")
                .defaultValue("true")
                .required(true)
                .build();
        
        assertEquals("auth.enabled", definition.getKey());
        assertEquals("Enable Auth", definition.getName());
        assertEquals(ConfigItemType.BOOLEAN, definition.getType());
        assertEquals("Enable authentication plugin", definition.getDescription());
        assertEquals("true", definition.getDefaultValue());
        assertTrue(definition.isRequired());
    }
    
    @Test
    @DisplayName("test Builder pattern with enum values")
    void testBuilderPatternWithEnumValues() {
        List<String> enumValues = new ArrayList<>();
        enumValues.add("mysql");
        enumValues.add("oracle");
        ConfigItemDefinition definition =
            new ConfigItemDefinition.Builder("db.type", "Database Type", ConfigItemType.ENUM)
                .description("Database type selection")
                .defaultValue("mysql")
                .required(true)
                .enumValues(enumValues)
                .build();
        
        assertEquals("db.type", definition.getKey());
        assertEquals(ConfigItemType.ENUM, definition.getType());
        assertEquals(2, definition.getEnumValues().size());
        assertEquals("mysql", definition.getEnumValues().get(0));
    }
    
    @Test
    @DisplayName("test Builder pattern with config metadata")
    void testBuilderPatternWithConfigMetadata() {
        List<String> aliases = new ArrayList<>();
        aliases.add("nacos.core.auth.plugin.nacos.token.secret.key");
        ConfigItemDefinition definition =
            new ConfigItemDefinition.Builder("token.secret.key", "Token Secret",
                ConfigItemType.STRING)
                .aliases(aliases)
                .sensitive(true)
                .effectMode(ConfigItemEffectMode.RUNTIME)
                .build();
        
        assertEquals("token.secret.key", definition.getKey());
        assertEquals(1, definition.getAliases().size());
        assertTrue(definition.isSensitive());
        assertEquals(ConfigItemEffectMode.RUNTIME, definition.getEffectMode());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        ConfigItemDefinition definition =
            new ConfigItemDefinition("testKey", "Test Name", ConfigItemType.STRING);
        definition.setDescription("Test description");
        definition.setDefaultValue("defaultValue");
        definition.setRequired(true);
        List<String> aliases = new ArrayList<>();
        aliases.add("legacy.testKey");
        definition.setAliases(aliases);
        definition.setSensitive(true);
        definition.setEffectMode(ConfigItemEffectMode.RUNTIME);
        
        String json = mapper.writeValueAsString(definition);
        assertNotNull(json);
        assertTrue(json.contains("\"key\":\"testKey\""));
        assertTrue(json.contains("\"name\":\"Test Name\""));
        assertTrue(json.contains("\"type\":\"STRING\""));
        assertTrue(json.contains("\"description\":\"Test description\""));
        assertTrue(json.contains("\"defaultValue\":\"defaultValue\""));
        assertTrue(json.contains("\"required\":true"));
        assertTrue(json.contains("\"aliases\":[\"legacy.testKey\"]"));
        assertTrue(json.contains("\"sensitive\":true"));
        assertTrue(json.contains("\"effectMode\":\"RUNTIME\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"key\":\"testKey\",\"name\":\"Test Name\",\"description\":\"Test\","
            + "\"defaultValue\":\"default\",\"type\":\"STRING\",\"required\":true,"
            + "\"enumValues\":[\"opt1\",\"opt2\"],\"aliases\":[\"legacy.testKey\"],"
            + "\"sensitive\":true,\"effectMode\":\"RUNTIME\"}";
        
        ConfigItemDefinition definition = mapper.readValue(json, ConfigItemDefinition.class);
        assertNotNull(definition);
        assertEquals("testKey", definition.getKey());
        assertEquals("Test Name", definition.getName());
        assertEquals("Test", definition.getDescription());
        assertEquals("default", definition.getDefaultValue());
        assertEquals(ConfigItemType.STRING, definition.getType());
        assertTrue(definition.isRequired());
        assertNotNull(definition.getEnumValues());
        assertEquals(2, definition.getEnumValues().size());
        assertNotNull(definition.getAliases());
        assertEquals(1, definition.getAliases().size());
        assertEquals("legacy.testKey", definition.getAliases().get(0));
        assertTrue(definition.isSensitive());
        assertEquals(ConfigItemEffectMode.RUNTIME, definition.getEffectMode());
    }
}
