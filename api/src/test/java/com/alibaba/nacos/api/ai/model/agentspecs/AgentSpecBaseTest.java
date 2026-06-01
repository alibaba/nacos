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

package com.alibaba.nacos.api.ai.model.agentspecs;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpecBaseTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        AgentSpecBase base = new AgentSpecBase();
        assertNull(base.getNamespaceId());
        assertNull(base.getName());
        assertNull(base.getDescription());
    }
    
    @Test
    @DisplayName("test getter and setter for namespaceId")
    void testGetterAndSetterForNamespaceId() {
        AgentSpecBase base = new AgentSpecBase();
        base.setNamespaceId("public");
        assertEquals("public", base.getNamespaceId());
    }
    
    @Test
    @DisplayName("test getter and setter for name")
    void testGetterAndSetterForName() {
        AgentSpecBase base = new AgentSpecBase();
        base.setName("testAgentSpec");
        assertEquals("testAgentSpec", base.getName());
    }
    
    @Test
    @DisplayName("test getter and setter for description")
    void testGetterAndSetterForDescription() {
        AgentSpecBase base = new AgentSpecBase();
        base.setDescription("Test description");
        assertEquals("Test description", base.getDescription());
    }
    
    @Test
    @DisplayName("test all fields set together")
    void testAllFieldsSetTogether() {
        AgentSpecBase base = new AgentSpecBase();
        base.setNamespaceId("public");
        base.setName("testAgentSpec");
        base.setDescription("Test agent spec");
        
        assertEquals("public", base.getNamespaceId());
        assertEquals("testAgentSpec", base.getName());
        assertEquals("Test agent spec", base.getDescription());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        AgentSpecBase base = new AgentSpecBase();
        base.setNamespaceId("public");
        base.setName("testAgentSpec");
        base.setDescription("Test description");
        
        String json = mapper.writeValueAsString(base);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testAgentSpec\""));
        assertTrue(json.contains("\"description\":\"Test description\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json =
            "{\"namespaceId\":\"public\",\"name\":\"testAgentSpec\",\"description\":\"Test\"}";
        
        AgentSpecBase base = mapper.readValue(json, AgentSpecBase.class);
        assertNotNull(base);
        assertEquals("public", base.getNamespaceId());
        assertEquals("testAgentSpec", base.getName());
        assertEquals("Test", base.getDescription());
    }
}
