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

package com.alibaba.nacos.api.ai.model.prompt;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptVariableTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        PromptVariable variable = new PromptVariable();
        assertNull(variable.getName());
        assertNull(variable.getDefaultValue());
        assertNull(variable.getDescription());
    }
    
    @Test
    @DisplayName("test constructor with all args")
    void testConstructorWithAllArgs() {
        PromptVariable variable = new PromptVariable("question", "defaultAnswer", "User question");
        assertEquals("question", variable.getName());
        assertEquals("defaultAnswer", variable.getDefaultValue());
        assertEquals("User question", variable.getDescription());
    }
    
    @Test
    @DisplayName("test getter and setter for name")
    void testGetterAndSetterForName() {
        PromptVariable variable = new PromptVariable();
        variable.setName("testName");
        assertEquals("testName", variable.getName());
    }
    
    @Test
    @DisplayName("test getter and setter for defaultValue")
    void testGetterAndSetterForDefaultValue() {
        PromptVariable variable = new PromptVariable();
        variable.setDefaultValue("defaultValue");
        assertEquals("defaultValue", variable.getDefaultValue());
    }
    
    @Test
    @DisplayName("test getter and setter for description")
    void testGetterAndSetterForDescription() {
        PromptVariable variable = new PromptVariable();
        variable.setDescription("test description");
        assertEquals("test description", variable.getDescription());
    }
    
    @Test
    @DisplayName("test toString method")
    void testToStringMethod() {
        PromptVariable variable = new PromptVariable("name", "default", "desc");
        String str = variable.toString();
        assertTrue(str.contains("name"));
        assertTrue(str.contains("default"));
        assertTrue(str.contains("desc"));
        assertTrue(str.contains("PromptVariable"));
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        PromptVariable variable = new PromptVariable("question", "defaultAnswer", "User question");
        String json = mapper.writeValueAsString(variable);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"question\""));
        assertTrue(json.contains("\"defaultValue\":\"defaultAnswer\""));
        assertTrue(json.contains("\"description\":\"User question\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json =
            "{\"name\":\"question\",\"defaultValue\":\"defaultAnswer\",\"description\":\"User question\"}";
        PromptVariable variable = mapper.readValue(json, PromptVariable.class);
        assertNotNull(variable);
        assertEquals("question", variable.getName());
        assertEquals("defaultAnswer", variable.getDefaultValue());
        assertEquals("User question", variable.getDescription());
    }
    
    @Test
    @DisplayName("test deserialize with null defaultValue")
    void testDeserializeWithNullDefaultValue() throws JsonProcessingException {
        String json = "{\"name\":\"requiredVar\",\"description\":\"Required variable\"}";
        PromptVariable variable = mapper.readValue(json, PromptVariable.class);
        assertNotNull(variable);
        assertEquals("requiredVar", variable.getName());
        assertNull(variable.getDefaultValue());
        assertEquals("Required variable", variable.getDescription());
    }
}
