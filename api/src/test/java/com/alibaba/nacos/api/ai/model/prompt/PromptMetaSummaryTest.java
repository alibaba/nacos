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

package com.alibaba.nacos.api.ai.model.prompt;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptMetaSummaryTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor initializes default values")
    void testDefaultConstructor() {
        PromptMetaSummary summary = new PromptMetaSummary();
        assertEquals(1, summary.getSchemaVersion());
        assertNotNull(summary.getBizTags());
        assertTrue(summary.getBizTags().isEmpty());
    }
    
    @Test
    @DisplayName("test getter and setter for schemaVersion")
    void testGetterAndSetterForSchemaVersion() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setSchemaVersion(2);
        assertEquals(2, summary.getSchemaVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for promptKey")
    void testGetterAndSetterForPromptKey() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setPromptKey("testPrompt");
        assertEquals("testPrompt", summary.getPromptKey());
    }
    
    @Test
    @DisplayName("test getter and setter for description")
    void testGetterAndSetterForDescription() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setDescription("Test description");
        assertEquals("Test description", summary.getDescription());
    }
    
    @Test
    @DisplayName("test getter and setter for bizTags")
    void testGetterAndSetterForBizTags() {
        PromptMetaSummary summary = new PromptMetaSummary();
        List<String> tags = new ArrayList<>();
        tags.add("tag1");
        tags.add("tag2");
        summary.setBizTags(tags);
        assertNotNull(summary.getBizTags());
        assertEquals(2, summary.getBizTags().size());
        assertEquals("tag1", summary.getBizTags().get(0));
        assertEquals("tag2", summary.getBizTags().get(1));
    }
    
    @Test
    @DisplayName("test getter and setter for bizTagsStr")
    void testGetterAndSetterForBizTagsStr() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setBizTagsStr("[\"tag1\",\"tag2\"]");
        assertEquals("[\"tag1\",\"tag2\"]", summary.getBizTagsStr());
    }
    
    @Test
    @DisplayName("test getter and setter for latestVersion")
    void testGetterAndSetterForLatestVersion() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setLatestVersion("1.0.0");
        assertEquals("1.0.0", summary.getLatestVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for gmtModified")
    void testGetterAndSetterForGmtModified() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setGmtModified(1234567890L);
        assertEquals(1234567890L, summary.getGmtModified());
    }
    
    @Test
    @DisplayName("test getter and setter for editingVersion")
    void testGetterAndSetterForEditingVersion() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setEditingVersion("draft-1.0");
        assertEquals("draft-1.0", summary.getEditingVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for reviewingVersion")
    void testGetterAndSetterForReviewingVersion() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setReviewingVersion("review-1.0");
        assertEquals("review-1.0", summary.getReviewingVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for onlineCnt")
    void testGetterAndSetterForOnlineCnt() {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setOnlineCnt(5);
        assertEquals(5, summary.getOnlineCnt());
    }
    
    @Test
    @DisplayName("test getter and setter for labels")
    void testGetterAndSetterForLabels() {
        PromptMetaSummary summary = new PromptMetaSummary();
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", "1.0.0");
        labels.put("stable", "0.9.0");
        summary.setLabels(labels);
        assertNotNull(summary.getLabels());
        assertEquals(2, summary.getLabels().size());
        assertEquals("1.0.0", summary.getLabels().get("latest"));
        assertEquals("0.9.0", summary.getLabels().get("stable"));
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        PromptMetaSummary summary = new PromptMetaSummary();
        summary.setPromptKey("testPrompt");
        summary.setLatestVersion("1.0.0");
        summary.setDescription("Test description");
        summary.setOnlineCnt(3);
        
        String json = mapper.writeValueAsString(summary);
        assertNotNull(json);
        assertTrue(json.contains("\"promptKey\":\"testPrompt\""));
        assertTrue(json.contains("\"latestVersion\":\"1.0.0\""));
        assertTrue(json.contains("\"description\":\"Test description\""));
        assertTrue(json.contains("\"onlineCnt\":3"));
        assertTrue(json.contains("\"schemaVersion\":1"));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"schemaVersion\":1,\"promptKey\":\"testPrompt\",\"description\":\"Test\","
            + "\"bizTags\":[\"tag1\"],\"latestVersion\":\"1.0.0\",\"gmtModified\":1234567890,"
            + "\"editingVersion\":\"draft\",\"reviewingVersion\":\"review\",\"onlineCnt\":2}";
        
        PromptMetaSummary summary = mapper.readValue(json, PromptMetaSummary.class);
        assertNotNull(summary);
        assertEquals(1, summary.getSchemaVersion());
        assertEquals("testPrompt", summary.getPromptKey());
        assertEquals("Test", summary.getDescription());
        assertEquals(1, summary.getBizTags().size());
        assertEquals("tag1", summary.getBizTags().get(0));
        assertEquals("1.0.0", summary.getLatestVersion());
        assertEquals(1234567890L, summary.getGmtModified());
        assertEquals("draft", summary.getEditingVersion());
        assertEquals("review", summary.getReviewingVersion());
        assertEquals(2, summary.getOnlineCnt());
    }
}
