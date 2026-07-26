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

class PromptVersionInfoTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        PromptVersionInfo info = new PromptVersionInfo();
        assertNull(info.getTemplate());
        assertNull(info.getMd5());
        assertNull(info.getVariables());
    }
    
    @Test
    @DisplayName("test inherited fields from PromptVersionSummary")
    void testInheritedFieldsFromPromptVersionSummary() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey("testPrompt");
        info.setVersion("1.0.0");
        info.setStatus("online");
        info.setCommitMsg("Initial commit");
        info.setSrcUser("admin");
        info.setGmtModified(1234567890L);
        
        assertEquals("testPrompt", info.getPromptKey());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("online", info.getStatus());
        assertEquals("Initial commit", info.getCommitMsg());
        assertEquals("admin", info.getSrcUser());
        assertEquals(1234567890L, info.getGmtModified());
    }
    
    @Test
    @DisplayName("test getter and setter for template")
    void testGetterAndSetterForTemplate() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setTemplate("Hello {{name}}!");
        assertEquals("Hello {{name}}!", info.getTemplate());
    }
    
    @Test
    @DisplayName("test getter and setter for md5")
    void testGetterAndSetterForMd5() {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setMd5("abc123def456");
        assertEquals("abc123def456", info.getMd5());
    }
    
    @Test
    @DisplayName("test getter and setter for variables")
    void testGetterAndSetterForVariables() {
        PromptVersionInfo info = new PromptVersionInfo();
        List<PromptVariable> variables = new ArrayList<>();
        variables.add(new PromptVariable("name", "Guest", "User name"));
        info.setVariables(variables);
        assertNotNull(info.getVariables());
        assertEquals(1, info.getVariables().size());
        assertEquals("name", info.getVariables().get(0).getName());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey("testPrompt");
        info.setVersion("1.0.0");
        info.setTemplate("Hello {{name}}!");
        info.setMd5("abc123");
        
        String json = mapper.writeValueAsString(info);
        assertNotNull(json);
        assertTrue(json.contains("\"promptKey\":\"testPrompt\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"template\":\"Hello {{name}}!\""));
        assertTrue(json.contains("\"md5\":\"abc123\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"promptKey\":\"testPrompt\",\"version\":\"1.0.0\",\"status\":\"online\","
            + "\"template\":\"Hello {{name}}!\",\"md5\":\"abc123\",\"variables\":[{\"name\":\"name\",\"defaultValue\":\"Guest\"}]}";
        
        PromptVersionInfo info = mapper.readValue(json, PromptVersionInfo.class);
        assertNotNull(info);
        assertEquals("testPrompt", info.getPromptKey());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("online", info.getStatus());
        assertEquals("Hello {{name}}!", info.getTemplate());
        assertEquals("abc123", info.getMd5());
        assertNotNull(info.getVariables());
        assertEquals(1, info.getVariables().size());
        assertEquals("name", info.getVariables().get(0).getName());
        assertEquals("Guest", info.getVariables().get(0).getDefaultValue());
    }
}
