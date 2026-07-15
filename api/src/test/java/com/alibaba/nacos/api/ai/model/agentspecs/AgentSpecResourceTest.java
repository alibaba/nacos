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

class AgentSpecResourceTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        AgentSpecResource resource = new AgentSpecResource();
        assertNull(resource.getName());
        assertNull(resource.getType());
        assertNull(resource.getContent());
        assertNull(resource.getMetadata());
    }
    
    @Test
    @DisplayName("test getter and setter for name")
    void testGetterAndSetterForName() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("config/SOUL.md");
        assertEquals("config/SOUL.md", resource.getName());
    }
    
    @Test
    @DisplayName("test getter and setter for type")
    void testGetterAndSetterForType() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setType("config");
        assertEquals("config", resource.getType());
    }
    
    @Test
    @DisplayName("test getter and setter for content")
    void testGetterAndSetterForContent() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setContent("file content here");
        assertEquals("file content here", resource.getContent());
    }
    
    @Test
    @DisplayName("test getter and setter for metadata")
    void testGetterAndSetterForMetadata() {
        AgentSpecResource resource = new AgentSpecResource();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", 123);
        resource.setMetadata(metadata);
        assertNotNull(resource.getMetadata());
        assertEquals(2, resource.getMetadata().size());
        assertEquals("value1", resource.getMetadata().get("key1"));
        assertEquals(123, resource.getMetadata().get("key2"));
    }
    
    @Test
    @DisplayName("test getResourceIdentifier with type")
    void testGetResourceIdentifierWithType() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("config.yaml");
        resource.setType("config");
        assertEquals("config::config.yaml", resource.getResourceIdentifier());
    }
    
    @Test
    @DisplayName("test getResourceIdentifier without type")
    void testGetResourceIdentifierWithoutType() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("README.md");
        resource.setType(null);
        assertEquals("README.md", resource.getResourceIdentifier());
    }
    
    @Test
    @DisplayName("test getResourceIdentifier with empty type")
    void testGetResourceIdentifierWithEmptyType() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("test.txt");
        resource.setType("");
        assertEquals("test.txt", resource.getResourceIdentifier());
    }
    
    @Test
    @DisplayName("test getResourceIdentifier with whitespace type")
    void testGetResourceIdentifierWithWhitespaceType() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("test.txt");
        resource.setType("   ");
        assertEquals("test.txt", resource.getResourceIdentifier());
    }
    
    @Test
    @DisplayName("test getResourceIdentifier with skill type")
    void testGetResourceIdentifierWithSkillType() {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("skills/search.json");
        resource.setType("skill");
        assertEquals("skill::skills/search.json", resource.getResourceIdentifier());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName("config.yaml");
        resource.setType("config");
        resource.setContent("content here");
        
        String json = mapper.writeValueAsString(resource);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"config.yaml\""));
        assertTrue(json.contains("\"type\":\"config\""));
        assertTrue(json.contains("\"content\":\"content here\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"name\":\"config.yaml\",\"type\":\"config\",\"content\":\"test content\","
            + "\"metadata\":{\"key\":\"value\"}}";
        
        AgentSpecResource resource = mapper.readValue(json, AgentSpecResource.class);
        assertNotNull(resource);
        assertEquals("config.yaml", resource.getName());
        assertEquals("config", resource.getType());
        assertEquals("test content", resource.getContent());
        assertNotNull(resource.getMetadata());
        assertEquals("value", resource.getMetadata().get("key"));
    }
}
