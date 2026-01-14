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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardDetailInfoTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentCardDetailInfo agentCardDetailInfo = new AgentCardDetailInfo();
        agentCardDetailInfo.setProtocolVersion("1.0");
        agentCardDetailInfo.setName("test agent");
        agentCardDetailInfo.setDescription("test description");
        agentCardDetailInfo.setVersion("1.0.0");
        agentCardDetailInfo.setIconUrl("http://test.com/icon.png");
        agentCardDetailInfo.setRegistrationType("URL");
        agentCardDetailInfo.setLatestVersion(true);
        
        String json = mapper.writeValueAsString(agentCardDetailInfo);
        assertNotNull(json);
        assertTrue(json.contains("\"protocolVersion\":\"1.0\""));
        assertTrue(json.contains("\"name\":\"test agent\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"iconUrl\":\"http://test.com/icon.png\""));
        assertTrue(json.contains("\"registrationType\":\"URL\""));
        assertTrue(json.contains("\"latestVersion\":true"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"protocolVersion\":\"1.0\",\"name\":\"test agent\",\"description\":\"test description\","
                + "\"version\":\"1.0.0\",\"iconUrl\":\"http://test.com/icon.png\","
                + "\"registrationType\":\"URL\",\"latestVersion\":true}";
        
        AgentCardDetailInfo agentCardDetailInfo = mapper.readValue(json, AgentCardDetailInfo.class);
        assertNotNull(agentCardDetailInfo);
        assertEquals("1.0", agentCardDetailInfo.getProtocolVersion());
        assertEquals("test agent", agentCardDetailInfo.getName());
        assertEquals("test description", agentCardDetailInfo.getDescription());
        assertEquals("1.0.0", agentCardDetailInfo.getVersion());
        assertEquals("http://test.com/icon.png", agentCardDetailInfo.getIconUrl());
        assertEquals("URL", agentCardDetailInfo.getRegistrationType());
        assertEquals(true, agentCardDetailInfo.isLatestVersion());
    }
}