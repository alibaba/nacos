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

package com.alibaba.nacos.api.ai.model.skills;

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

class SkillSummaryTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        SkillSummary summary = new SkillSummary();
        assertNull(summary.getOwner());
        assertFalse(summary.isEnable());
        assertNull(summary.getBizTags());
        assertNull(summary.getFrom());
        assertNull(summary.getScope());
        assertNull(summary.getLabels());
    }
    
    @Test
    @DisplayName("test inherited fields from SkillBasicInfo")
    void testInheritedFieldsFromSkillBasicInfo() {
        SkillSummary summary = new SkillSummary();
        summary.setNamespaceId("public");
        summary.setName("testSkill");
        summary.setDescription("Test skill description");
        summary.setUpdateTime(1234567890L);
        
        assertEquals("public", summary.getNamespaceId());
        assertEquals("testSkill", summary.getName());
        assertEquals("Test skill description", summary.getDescription());
        assertEquals(1234567890L, summary.getUpdateTime());
    }
    
    @Test
    @DisplayName("test getter and setter for owner")
    void testGetterAndSetterForOwner() {
        SkillSummary summary = new SkillSummary();
        summary.setOwner("admin");
        assertEquals("admin", summary.getOwner());
    }
    
    @Test
    @DisplayName("test getter and setter for enable")
    void testGetterAndSetterForEnable() {
        SkillSummary summary = new SkillSummary();
        summary.setEnable(true);
        assertTrue(summary.isEnable());
        summary.setEnable(false);
        assertFalse(summary.isEnable());
    }
    
    @Test
    @DisplayName("test getter and setter for bizTags")
    void testGetterAndSetterForBizTags() {
        SkillSummary summary = new SkillSummary();
        summary.setBizTags("[\"tag1\",\"tag2\"]");
        assertEquals("[\"tag1\",\"tag2\"]", summary.getBizTags());
    }
    
    @Test
    @DisplayName("test getter and setter for from")
    void testGetterAndSetterForFrom() {
        SkillSummary summary = new SkillSummary();
        summary.setFrom("local");
        assertEquals("local", summary.getFrom());
    }
    
    @Test
    @DisplayName("test getter and setter for scope")
    void testGetterAndSetterForScope() {
        SkillSummary summary = new SkillSummary();
        summary.setScope("PUBLIC");
        assertEquals("PUBLIC", summary.getScope());
    }
    
    @Test
    @DisplayName("test getter and setter for labels")
    void testGetterAndSetterForLabels() {
        SkillSummary summary = new SkillSummary();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "v3");
        labels.put("stable", "v2");
        summary.setLabels(labels);
        assertNotNull(summary.getLabels());
        assertEquals(2, summary.getLabels().size());
        assertEquals("v3", summary.getLabels().get("latest"));
        assertEquals("v2", summary.getLabels().get("stable"));
    }
    
    @Test
    @DisplayName("test getter and setter for editingVersion")
    void testGetterAndSetterForEditingVersion() {
        SkillSummary summary = new SkillSummary();
        summary.setEditingVersion("draft-v1");
        assertEquals("draft-v1", summary.getEditingVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for reviewingVersion")
    void testGetterAndSetterForReviewingVersion() {
        SkillSummary summary = new SkillSummary();
        summary.setReviewingVersion("review-v1");
        assertEquals("review-v1", summary.getReviewingVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for onlineCnt")
    void testGetterAndSetterForOnlineCnt() {
        SkillSummary summary = new SkillSummary();
        summary.setOnlineCnt(3);
        assertEquals(3, summary.getOnlineCnt());
    }
    
    @Test
    @DisplayName("test getter and setter for downloadCount")
    void testGetterAndSetterForDownloadCount() {
        SkillSummary summary = new SkillSummary();
        summary.setDownloadCount(1000L);
        assertEquals(1000L, summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        SkillSummary summary = new SkillSummary();
        summary.setNamespaceId("public");
        summary.setName("testSkill");
        summary.setOwner("admin");
        summary.setEnable(true);
        summary.setOnlineCnt(2);
        summary.setDownloadCount(500L);
        
        String json = mapper.writeValueAsString(summary);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testSkill\""));
        assertTrue(json.contains("\"owner\":\"admin\""));
        assertTrue(json.contains("\"enable\":true"));
        assertTrue(json.contains("\"onlineCnt\":2"));
        assertTrue(json.contains("\"downloadCount\":500"));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"namespaceId\":\"public\",\"name\":\"testSkill\",\"description\":\"Test\","
            + "\"owner\":\"admin\",\"enable\":true,\"bizTags\":\"[\\\"tag1\\\"]\",\"from\":\"local\","
            + "\"scope\":\"PUBLIC\",\"onlineCnt\":2,\"downloadCount\":100}";
        
        SkillSummary summary = mapper.readValue(json, SkillSummary.class);
        assertNotNull(summary);
        assertEquals("public", summary.getNamespaceId());
        assertEquals("testSkill", summary.getName());
        assertEquals("Test", summary.getDescription());
        assertEquals("admin", summary.getOwner());
        assertTrue(summary.isEnable());
        assertEquals("local", summary.getFrom());
        assertEquals("PUBLIC", summary.getScope());
        assertEquals(2, summary.getOnlineCnt());
        assertEquals(100L, summary.getDownloadCount());
    }
}
