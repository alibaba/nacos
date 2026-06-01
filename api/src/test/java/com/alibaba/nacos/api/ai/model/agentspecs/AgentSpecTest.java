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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpecTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        AgentSpec agentSpec = new AgentSpec();
        assertNull(agentSpec.getNamespaceId());
        assertNull(agentSpec.getName());
        assertNull(agentSpec.getDescription());
        assertNull(agentSpec.getBizTags());
        assertNull(agentSpec.getContent());
        assertNull(agentSpec.getResource());
    }
    
    @Test
    @DisplayName("test getter and setter for namespaceId")
    void testGetterAndSetterForNamespaceId() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId("public");
        assertEquals("public", agentSpec.getNamespaceId());
    }
    
    @Test
    @DisplayName("test getter and setter for name")
    void testGetterAndSetterForName() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName("testAgent");
        assertEquals("testAgent", agentSpec.getName());
    }
    
    @Test
    @DisplayName("test getter and setter for description")
    void testGetterAndSetterForDescription() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setDescription("Test agent description");
        assertEquals("Test agent description", agentSpec.getDescription());
    }
    
    @Test
    @DisplayName("test getter and setter for bizTags")
    void testGetterAndSetterForBizTags() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setBizTags("[\"tag1\",\"tag2\"]");
        assertEquals("[\"tag1\",\"tag2\"]", agentSpec.getBizTags());
    }
    
    @Test
    @DisplayName("test getter and setter for content")
    void testGetterAndSetterForContent() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setContent("{\"manifest\": \"content\"}");
        assertEquals("{\"manifest\": \"content\"}", agentSpec.getContent());
    }
    
    @Test
    @DisplayName("test getter and setter for resource")
    void testGetterAndSetterForResource() {
        AgentSpec agentSpec = new AgentSpec();
        Map<String, AgentSpecResource> resourceMap = new HashMap<>();
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("config.yaml");
        resource.setType("config");
        resourceMap.put("config.yaml", resource);
        agentSpec.setResource(resourceMap);
        assertNotNull(agentSpec.getResource());
        assertEquals(1, agentSpec.getResource().size());
        assertTrue(agentSpec.getResource().containsKey("config.yaml"));
        assertEquals("config.yaml", agentSpec.getResource().get("config.yaml").getName());
    }
    
    @Test
    @DisplayName("test all fields set together")
    void testAllFieldsSetTogether() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId("public");
        agentSpec.setName("testAgent");
        agentSpec.setDescription("Test");
        agentSpec.setBizTags("[\"ai\"]");
        agentSpec.setContent("{\"version\":\"1.0\"}");
        Map<String, AgentSpecResource> resourceMap = new HashMap<>();
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("skill.json");
        resourceMap.put("skill.json", resource);
        agentSpec.setResource(resourceMap);
        
        assertEquals("public", agentSpec.getNamespaceId());
        assertEquals("testAgent", agentSpec.getName());
        assertEquals("Test", agentSpec.getDescription());
        assertEquals("[\"ai\"]", agentSpec.getBizTags());
        assertEquals("{\"version\":\"1.0\"}", agentSpec.getContent());
        assertNotNull(agentSpec.getResource());
        assertEquals(1, agentSpec.getResource().size());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId("public");
        agentSpec.setName("testAgent");
        agentSpec.setDescription("Test");
        agentSpec.setBizTags("[\"ai\"]");
        agentSpec.setContent("{\"version\":\"1.0\"}");
        
        String json = mapper.writeValueAsString(agentSpec);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testAgent\""));
        assertTrue(json.contains("\"description\":\"Test\""));
        assertTrue(json.contains("\"bizTags\":\"[\\\"ai\\\"]\""));
        assertTrue(json.contains("\"content\":\"{\\\"version\\\":\\\"1.0\\\"}\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"namespaceId\":\"public\",\"name\":\"testAgent\",\"description\":\"Test\","
            + "\"bizTags\":\"[\\\"ai\\\"]\",\"content\":\"{\\\"version\\\":\\\"1.0\\\"}\","
            + "\"resource\":{\"config.yaml\":{\"name\":\"config.yaml\",\"type\":\"config\"}}}";
        
        AgentSpec agentSpec = mapper.readValue(json, AgentSpec.class);
        assertNotNull(agentSpec);
        assertEquals("public", agentSpec.getNamespaceId());
        assertEquals("testAgent", agentSpec.getName());
        assertEquals("Test", agentSpec.getDescription());
        assertEquals("[\"ai\"]", agentSpec.getBizTags());
        assertEquals("{\"version\":\"1.0\"}", agentSpec.getContent());
        assertNotNull(agentSpec.getResource());
        assertTrue(agentSpec.getResource().containsKey("config.yaml"));
    }
}
