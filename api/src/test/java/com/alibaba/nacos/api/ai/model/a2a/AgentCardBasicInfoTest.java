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

package com.alibaba.nacos.api.ai.model.a2a;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardBasicInfoTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentCardBasicInfo agentCardBasicInfo = new AgentCardBasicInfo();
        agentCardBasicInfo.setProtocolVersion("1.0");
        agentCardBasicInfo.setName("test agent");
        agentCardBasicInfo.setDescription("test description");
        agentCardBasicInfo.setVersion("1.0.0");
        agentCardBasicInfo.setIconUrl("http://test.com/icon.png");
        
        // Create capabilities
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        agentCardBasicInfo.setCapabilities(capabilities);
        
        // Create skills
        AgentSkill skill = new AgentSkill();
        skill.setId("skill-1");
        skill.setName("test skill");
        agentCardBasicInfo.setSkills(Arrays.asList(skill));
        
        String json = mapper.writeValueAsString(agentCardBasicInfo);
        assertNotNull(json);
        assertTrue(json.contains("\"protocolVersion\":\"1.0\""));
        assertTrue(json.contains("\"name\":\"test agent\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"iconUrl\":\"http://test.com/icon.png\""));
        assertTrue(json.contains("\"capabilities\":{\"streaming\":true"));
        assertTrue(json.contains("\"skills\":[{\"id\":\"skill-1\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"protocolVersion\":\"1.0\",\"name\":\"test agent\",\"description\":\"test description\","
                + "\"version\":\"1.0.0\",\"iconUrl\":\"http://test.com/icon.png\","
                + "\"capabilities\":{\"streaming\":true},"
                + "\"skills\":[{\"id\":\"skill-1\",\"name\":\"test skill\"}]}";
        
        AgentCardBasicInfo agentCardBasicInfo = mapper.readValue(json, AgentCardBasicInfo.class);
        assertNotNull(agentCardBasicInfo);
        assertEquals("1.0", agentCardBasicInfo.getProtocolVersion());
        assertEquals("test agent", agentCardBasicInfo.getName());
        assertEquals("test description", agentCardBasicInfo.getDescription());
        assertEquals("1.0.0", agentCardBasicInfo.getVersion());
        assertEquals("http://test.com/icon.png", agentCardBasicInfo.getIconUrl());
        assertNotNull(agentCardBasicInfo.getCapabilities());
        assertEquals(true, agentCardBasicInfo.getCapabilities().getStreaming());
        assertEquals(1, agentCardBasicInfo.getSkills().size());
        assertEquals("skill-1", agentCardBasicInfo.getSkills().get(0).getId());
        assertEquals("test skill", agentCardBasicInfo.getSkills().get(0).getName());
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentCardBasicInfo card1 = new AgentCardBasicInfo();
        card1.setProtocolVersion("1.0");
        card1.setName("test agent");
        card1.setDescription("test description");
        card1.setVersion("1.0.0");
        card1.setIconUrl("http://test.com/icon.png");
        
        AgentCardBasicInfo card2 = new AgentCardBasicInfo();
        card2.setProtocolVersion("1.0");
        card2.setName("test agent");
        card2.setDescription("test description");
        card2.setVersion("1.0.0");
        card2.setIconUrl("http://test.com/icon.png");
        
        AgentCardBasicInfo card3 = new AgentCardBasicInfo();
        card3.setProtocolVersion("2.0");
        
        assertEquals(card1, card1);
        assertEquals(card1, card2);
        assertEquals(card1.hashCode(), card2.hashCode());
        assertNotEquals(card1, card3);
        assertNotEquals(card1.hashCode(), card3.hashCode());
        assertNotEquals(card1, null);
        assertNotEquals(card1, new Object());
    }
}