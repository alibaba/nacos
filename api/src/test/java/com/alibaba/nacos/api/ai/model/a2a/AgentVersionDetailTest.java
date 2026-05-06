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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVersionDetailTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentVersionDetail agentVersionDetail = new AgentVersionDetail();
        agentVersionDetail.setVersion("1.0.0");
        agentVersionDetail.setCreatedAt("2023-01-01T00:00:00Z");
        agentVersionDetail.setUpdatedAt("2023-01-02T00:00:00Z");
        agentVersionDetail.setLatest(true);
        
        String json = mapper.writeValueAsString(agentVersionDetail);
        assertNotNull(json);
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"createdAt\":\"2023-01-01T00:00:00Z\""));
        assertTrue(json.contains("\"updatedAt\":\"2023-01-02T00:00:00Z\""));
        assertTrue(json.contains("\"latest\":true"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"version\":\"1.0.0\",\"createdAt\":\"2023-01-01T00:00:00Z\",\"updatedAt\":\"2023-01-02T00:00:00Z\",\"latest\":true}";
        
        AgentVersionDetail agentVersionDetail = mapper.readValue(json, AgentVersionDetail.class);
        assertNotNull(agentVersionDetail);
        assertEquals("1.0.0", agentVersionDetail.getVersion());
        assertEquals("2023-01-01T00:00:00Z", agentVersionDetail.getCreatedAt());
        assertEquals("2023-01-02T00:00:00Z", agentVersionDetail.getUpdatedAt());
        assertEquals(true, agentVersionDetail.isLatest());
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentVersionDetail version1 = new AgentVersionDetail();
        version1.setVersion("1.0.0");
        version1.setCreatedAt("2023-01-01T00:00:00Z");
        version1.setUpdatedAt("2023-01-02T00:00:00Z");
        version1.setLatest(true);
        
        AgentVersionDetail version2 = new AgentVersionDetail();
        version2.setVersion("1.0.0");
        version2.setCreatedAt("2023-01-01T00:00:00Z");
        version2.setUpdatedAt("2023-01-02T00:00:00Z");
        version2.setLatest(true);
        
        AgentVersionDetail version3 = new AgentVersionDetail();
        version3.setVersion("2.0.0");
        
        assertEquals(version1, version2);
        assertEquals(version1.hashCode(), version2.hashCode());
        assertNotEquals(version1, version3);
        assertNotEquals(version1.hashCode(), version3.hashCode());
        assertNotEquals(version1, null);
        assertNotEquals(version1, new Object());
    }
}