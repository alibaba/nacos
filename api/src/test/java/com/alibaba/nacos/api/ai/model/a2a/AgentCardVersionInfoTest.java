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

class AgentCardVersionInfoTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentCardVersionInfo agentCardVersionInfo = new AgentCardVersionInfo();
        agentCardVersionInfo.setProtocolVersion("1.0");
        agentCardVersionInfo.setName("test agent");
        agentCardVersionInfo.setDescription("test description");
        agentCardVersionInfo.setVersion("1.0.0");
        agentCardVersionInfo.setIconUrl("http://test.com/icon.png");
        agentCardVersionInfo.setLatestPublishedVersion("2.0.0");
        agentCardVersionInfo.setRegistrationType("URL");
        
        // Create version detail
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        versionDetail.setVersion("1.0.0");
        versionDetail.setLatest(true);
        agentCardVersionInfo.setVersionDetails(Arrays.asList(versionDetail));
        
        String json = mapper.writeValueAsString(agentCardVersionInfo);
        assertNotNull(json);
        assertTrue(json.contains("\"protocolVersion\":\"1.0\""));
        assertTrue(json.contains("\"name\":\"test agent\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"iconUrl\":\"http://test.com/icon.png\""));
        assertTrue(json.contains("\"latestPublishedVersion\":\"2.0.0\""));
        assertTrue(json.contains("\"registrationType\":\"URL\""));
        assertTrue(json.contains("\"versionDetails\":[{\"version\":\"1.0.0\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"protocolVersion\":\"1.0\",\"name\":\"test agent\",\"description\":\"test description\","
                + "\"version\":\"1.0.0\",\"iconUrl\":\"http://test.com/icon.png\","
                + "\"latestPublishedVersion\":\"2.0.0\",\"registrationType\":\"URL\","
                + "\"versionDetails\":[{\"version\":\"1.0.0\",\"latest\":true}]}";
        
        AgentCardVersionInfo agentCardVersionInfo = mapper.readValue(json, AgentCardVersionInfo.class);
        assertNotNull(agentCardVersionInfo);
        assertEquals("1.0", agentCardVersionInfo.getProtocolVersion());
        assertEquals("test agent", agentCardVersionInfo.getName());
        assertEquals("test description", agentCardVersionInfo.getDescription());
        assertEquals("1.0.0", agentCardVersionInfo.getVersion());
        assertEquals("http://test.com/icon.png", agentCardVersionInfo.getIconUrl());
        assertEquals("2.0.0", agentCardVersionInfo.getLatestPublishedVersion());
        assertEquals("URL", agentCardVersionInfo.getRegistrationType());
        assertEquals(1, agentCardVersionInfo.getVersionDetails().size());
        assertEquals("1.0.0", agentCardVersionInfo.getVersionDetails().get(0).getVersion());
        assertEquals(true, agentCardVersionInfo.getVersionDetails().get(0).isLatest());
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentCardVersionInfo card1 = new AgentCardVersionInfo();
        card1.setProtocolVersion("1.0");
        card1.setName("test agent");
        card1.setDescription("test description");
        card1.setVersion("1.0.0");
        card1.setIconUrl("http://test.com/icon.png");
        card1.setLatestPublishedVersion("2.0.0");
        card1.setRegistrationType("URL");
        
        AgentCardVersionInfo card2 = new AgentCardVersionInfo();
        card2.setProtocolVersion("1.0");
        card2.setName("test agent");
        card2.setDescription("test description");
        card2.setVersion("1.0.0");
        card2.setIconUrl("http://test.com/icon.png");
        card2.setLatestPublishedVersion("2.0.0");
        card2.setRegistrationType("URL");
        
        AgentCardVersionInfo card3 = new AgentCardVersionInfo();
        card3.setProtocolVersion("2.0");
        
        assertEquals(card1, card2);
        assertEquals(card1.hashCode(), card2.hashCode());
        assertNotEquals(card1, card3);
        assertNotEquals(card1.hashCode(), card3.hashCode());
        assertNotEquals(card1, null);
        assertNotEquals(card1, new Object());
    }
}