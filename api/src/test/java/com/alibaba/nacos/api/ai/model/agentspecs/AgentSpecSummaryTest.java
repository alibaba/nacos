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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpecSummaryTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        AgentSpecSummary summary = new AgentSpecSummary();
        assertFalse(summary.isEnable());
        assertNull(summary.getBizTags());
        assertNull(summary.getFrom());
        assertNull(summary.getScope());
        assertNull(summary.getLabels());
        assertNull(summary.getEditingVersion());
        assertNull(summary.getReviewingVersion());
        assertNull(summary.getOnlineCnt());
        assertNull(summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test inherited fields from AgentSpecBasicInfo")
    void testInheritedFieldsFromAgentSpecBasicInfo() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setNamespaceId("public");
        summary.setName("testAgentSpec");
        summary.setDescription("Test agent spec description");
        summary.setUpdateTime(1234567890L);
        
        assertEquals("public", summary.getNamespaceId());
        assertEquals("testAgentSpec", summary.getName());
        assertEquals("Test agent spec description", summary.getDescription());
        assertEquals(1234567890L, summary.getUpdateTime());
    }
    
    @Test
    @DisplayName("test getter and setter for enable")
    void testGetterAndSetterForEnable() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setEnable(true);
        assertTrue(summary.isEnable());
        summary.setEnable(false);
        assertFalse(summary.isEnable());
    }
    
    @Test
    @DisplayName("test getter and setter for bizTags")
    void testGetterAndSetterForBizTags() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setBizTags("[\"tag1\",\"tag2\"]");
        assertEquals("[\"tag1\",\"tag2\"]", summary.getBizTags());
    }
    
    @Test
    @DisplayName("test getter and setter for from")
    void testGetterAndSetterForFrom() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setFrom("local");
        assertEquals("local", summary.getFrom());
    }
    
    @Test
    @DisplayName("test getter and setter for scope")
    void testGetterAndSetterForScope() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setScope("PUBLIC");
        assertEquals("PUBLIC", summary.getScope());
    }
    
    @Test
    @DisplayName("test getter and setter for labels")
    void testGetterAndSetterForLabels() {
        AgentSpecSummary summary = new AgentSpecSummary();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "v1");
        labels.put("stable", "v0");
        summary.setLabels(labels);
        assertNotNull(summary.getLabels());
        assertEquals(2, summary.getLabels().size());
        assertEquals("v1", summary.getLabels().get("latest"));
        assertEquals("v0", summary.getLabels().get("stable"));
    }
    
    @Test
    @DisplayName("test getter and setter for editingVersion")
    void testGetterAndSetterForEditingVersion() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setEditingVersion("draft-v1");
        assertEquals("draft-v1", summary.getEditingVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for reviewingVersion")
    void testGetterAndSetterForReviewingVersion() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setReviewingVersion("review-v1");
        assertEquals("review-v1", summary.getReviewingVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for onlineCnt")
    void testGetterAndSetterForOnlineCnt() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setOnlineCnt(3);
        assertEquals(3, summary.getOnlineCnt());
    }
    
    @Test
    @DisplayName("test getter and setter for downloadCount")
    void testGetterAndSetterForDownloadCount() {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setDownloadCount(1000L);
        assertEquals(1000L, summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setNamespaceId("public");
        summary.setName("testAgentSpec");
        summary.setEnable(true);
        summary.setOnlineCnt(2);
        summary.setDownloadCount(500L);
        
        String json = mapper.writeValueAsString(summary);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testAgentSpec\""));
        assertTrue(json.contains("\"enable\":true"));
        assertTrue(json.contains("\"onlineCnt\":2"));
        assertTrue(json.contains("\"downloadCount\":500"));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json =
            "{\"namespaceId\":\"public\",\"name\":\"testAgentSpec\",\"description\":\"Test\","
                + "\"enable\":true,\"bizTags\":\"[\\\"tag1\\\"]\",\"from\":\"local\","
                + "\"scope\":\"PUBLIC\",\"onlineCnt\":2,\"downloadCount\":100}";
        
        AgentSpecSummary summary = mapper.readValue(json, AgentSpecSummary.class);
        assertNotNull(summary);
        assertEquals("public", summary.getNamespaceId());
        assertEquals("testAgentSpec", summary.getName());
        assertEquals("Test", summary.getDescription());
        assertTrue(summary.isEnable());
        assertEquals("local", summary.getFrom());
        assertEquals("PUBLIC", summary.getScope());
        assertEquals(2, summary.getOnlineCnt());
        assertEquals(100L, summary.getDownloadCount());
    }
}
