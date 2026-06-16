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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test constructor with all args")
    void testConstructorWithAllArgs() {
        Prompt prompt = new Prompt("testKey", "1.0.0", "Hello {{name}}!");
        assertEquals("testKey", prompt.getPromptKey());
        assertEquals("1.0.0", prompt.getVersion());
        assertEquals("Hello {{name}}!", prompt.getTemplate());
    }
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        Prompt prompt = new Prompt();
        assertNull(prompt.getPromptKey());
        assertNull(prompt.getVersion());
        assertNull(prompt.getTemplate());
    }
    
    @Test
    @DisplayName("test getter and setter for promptKey")
    void testGetterAndSetterForPromptKey() {
        Prompt prompt = new Prompt();
        prompt.setPromptKey("myPrompt");
        assertEquals("myPrompt", prompt.getPromptKey());
    }
    
    @Test
    @DisplayName("test getter and setter for version")
    void testGetterAndSetterForVersion() {
        Prompt prompt = new Prompt();
        prompt.setVersion("2.0.0");
        assertEquals("2.0.0", prompt.getVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for template")
    void testGetterAndSetterForTemplate() {
        Prompt prompt = new Prompt();
        prompt.setTemplate("Template content");
        assertEquals("Template content", prompt.getTemplate());
    }
    
    @Test
    @DisplayName("test getter and setter for md5")
    void testGetterAndSetterForMd5() {
        Prompt prompt = new Prompt();
        prompt.setMd5("abc123def456");
        assertEquals("abc123def456", prompt.getMd5());
    }
    
    @Test
    @DisplayName("test getter and setter for variables")
    void testGetterAndSetterForVariables() {
        Prompt prompt = new Prompt();
        List<PromptVariable> variables = new ArrayList<>();
        variables.add(new PromptVariable("name", "default", "description"));
        prompt.setVariables(variables);
        assertNotNull(prompt.getVariables());
        assertEquals(1, prompt.getVariables().size());
        assertEquals("name", prompt.getVariables().get(0).getName());
    }
    
    @Test
    @DisplayName("test toString method")
    void testToStringMethod() {
        Prompt prompt = new Prompt("testKey", "1.0.0", "template");
        String str = prompt.toString();
        assertTrue(str.contains("testKey"));
        assertTrue(str.contains("1.0.0"));
        assertTrue(str.contains("Prompt"));
    }
    
    @Test
    @DisplayName("test serialize prompt to json")
    void testSerializePromptToJson() throws JsonProcessingException {
        Prompt prompt = new Prompt("testKey", "1.0.0", "Hello {{name}}!");
        prompt.setMd5("abc123");
        List<PromptVariable> variables = new ArrayList<>();
        variables.add(new PromptVariable("name", "Guest", "desc"));
        prompt.setVariables(variables);
        
        String json = mapper.writeValueAsString(prompt);
        assertNotNull(json);
        assertTrue(json.contains("\"promptKey\":\"testKey\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"template\":\"Hello {{name}}!\""));
        assertTrue(json.contains("\"md5\":\"abc123\""));
        assertTrue(json.contains("\"variables\""));
    }
    
    @Test
    @DisplayName("test deserialize prompt from json")
    void testDeserializePromptFromJson() throws JsonProcessingException {
        String json =
            "{\"promptKey\":\"testKey\",\"version\":\"1.0.0\",\"template\":\"Hello {{name}}!\",\"md5\":\"abc123\"}";
        
        Prompt prompt = mapper.readValue(json, Prompt.class);
        assertNotNull(prompt);
        assertEquals("testKey", prompt.getPromptKey());
        assertEquals("1.0.0", prompt.getVersion());
        assertEquals("Hello {{name}}!", prompt.getTemplate());
        assertEquals("abc123", prompt.getMd5());
    }
}
