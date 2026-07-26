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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpecBasicInfoTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        assertNull(info.getNamespaceId());
        assertNull(info.getName());
        assertNull(info.getDescription());
        assertNull(info.getUpdateTime());
    }
    
    @Test
    @DisplayName("test inherited fields from AgentSpecBase")
    void testInheritedFieldsFromAgentSpecBase() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        info.setNamespaceId("public");
        info.setName("testAgentSpec");
        info.setDescription("Test description");
        
        assertEquals("public", info.getNamespaceId());
        assertEquals("testAgentSpec", info.getName());
        assertEquals("Test description", info.getDescription());
    }
    
    @Test
    @DisplayName("test getter and setter for updateTime")
    void testGetterAndSetterForUpdateTime() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        info.setUpdateTime(1234567890L);
        assertEquals(1234567890L, info.getUpdateTime());
    }
    
    @Test
    @DisplayName("test equals with same object")
    void testEqualsWithSameObject() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        info.setNamespaceId("public");
        info.setName("test");
        assertTrue(info.equals(info));
    }
    
    @Test
    @DisplayName("test equals with null")
    void testEqualsWithNull() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        assertFalse(info.equals(null));
    }
    
    @Test
    @DisplayName("test equals with different class")
    void testEqualsWithDifferentClass() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        assertFalse(info.equals("string"));
    }
    
    @Test
    @DisplayName("test equals with identical objects")
    void testEqualsWithIdenticalObjects() {
        AgentSpecBasicInfo info1 = new AgentSpecBasicInfo();
        info1.setNamespaceId("public");
        info1.setName("test");
        info1.setDescription("desc");
        info1.setUpdateTime(1234567890L);
        
        AgentSpecBasicInfo info2 = new AgentSpecBasicInfo();
        info2.setNamespaceId("public");
        info2.setName("test");
        info2.setDescription("desc");
        info2.setUpdateTime(1234567890L);
        
        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }
    
    @Test
    @DisplayName("test equals with different namespaceId")
    void testEqualsWithDifferentNamespaceId() {
        AgentSpecBasicInfo info1 = new AgentSpecBasicInfo();
        info1.setNamespaceId("public");
        info1.setName("test");
        
        AgentSpecBasicInfo info2 = new AgentSpecBasicInfo();
        info2.setNamespaceId("private");
        info2.setName("test");
        
        assertNotEquals(info1, info2);
    }
    
    @Test
    @DisplayName("test equals with different name")
    void testEqualsWithDifferentName() {
        AgentSpecBasicInfo info1 = new AgentSpecBasicInfo();
        info1.setNamespaceId("public");
        info1.setName("test1");
        
        AgentSpecBasicInfo info2 = new AgentSpecBasicInfo();
        info2.setNamespaceId("public");
        info2.setName("test2");
        
        assertNotEquals(info1, info2);
    }
    
    @Test
    @DisplayName("test equals with different updateTime")
    void testEqualsWithDifferentUpdateTime() {
        AgentSpecBasicInfo info1 = new AgentSpecBasicInfo();
        info1.setNamespaceId("public");
        info1.setName("test");
        info1.setUpdateTime(1234567890L);
        
        AgentSpecBasicInfo info2 = new AgentSpecBasicInfo();
        info2.setNamespaceId("public");
        info2.setName("test");
        info2.setUpdateTime(1234567900L);
        
        assertNotEquals(info1, info2);
    }
    
    @Test
    @DisplayName("test hashCode consistency")
    void testHashCodeConsistency() {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        info.setNamespaceId("public");
        info.setName("test");
        info.setDescription("desc");
        int hashCode1 = info.hashCode();
        int hashCode2 = info.hashCode();
        assertEquals(hashCode1, hashCode2);
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        AgentSpecBasicInfo info = new AgentSpecBasicInfo();
        info.setNamespaceId("public");
        info.setName("testAgentSpec");
        info.setDescription("Test description");
        info.setUpdateTime(1234567890L);
        
        String json = mapper.writeValueAsString(info);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testAgentSpec\""));
        assertTrue(json.contains("\"description\":\"Test description\""));
        assertTrue(json.contains("\"updateTime\":1234567890"));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json =
            "{\"namespaceId\":\"public\",\"name\":\"testAgentSpec\",\"description\":\"Test\","
                + "\"updateTime\":1234567890}";
        
        AgentSpecBasicInfo info = mapper.readValue(json, AgentSpecBasicInfo.class);
        assertNotNull(info);
        assertEquals("public", info.getNamespaceId());
        assertEquals("testAgentSpec", info.getName());
        assertEquals("Test", info.getDescription());
        assertEquals(1234567890L, info.getUpdateTime());
    }
}
