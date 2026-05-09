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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptVersionSummaryTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        PromptVersionSummary summary = new PromptVersionSummary();
        assertNull(summary.getPromptKey());
        assertNull(summary.getVersion());
        assertNull(summary.getStatus());
        assertNull(summary.getCommitMsg());
        assertNull(summary.getSrcUser());
        assertNull(summary.getGmtModified());
        assertNull(summary.getPublishPipelineInfo());
    }
    
    @Test
    @DisplayName("test getter and setter for promptKey")
    void testGetterAndSetterForPromptKey() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setPromptKey("testPrompt");
        assertEquals("testPrompt", summary.getPromptKey());
    }
    
    @Test
    @DisplayName("test getter and setter for version")
    void testGetterAndSetterForVersion() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setVersion("1.0.0");
        assertEquals("1.0.0", summary.getVersion());
    }
    
    @Test
    @DisplayName("test getter and setter for status")
    void testGetterAndSetterForStatus() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setStatus("online");
        assertEquals("online", summary.getStatus());
    }
    
    @Test
    @DisplayName("test getter and setter for commitMsg")
    void testGetterAndSetterForCommitMsg() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setCommitMsg("Initial commit");
        assertEquals("Initial commit", summary.getCommitMsg());
    }
    
    @Test
    @DisplayName("test getter and setter for srcUser")
    void testGetterAndSetterForSrcUser() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setSrcUser("admin");
        assertEquals("admin", summary.getSrcUser());
    }
    
    @Test
    @DisplayName("test getter and setter for gmtModified")
    void testGetterAndSetterForGmtModified() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setGmtModified(1234567890L);
        assertEquals(1234567890L, summary.getGmtModified());
    }
    
    @Test
    @DisplayName("test getter and setter for publishPipelineInfo")
    void testGetterAndSetterForPublishPipelineInfo() {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setPublishPipelineInfo("{\"pipeline\":\"info\"}");
        assertEquals("{\"pipeline\":\"info\"}", summary.getPublishPipelineInfo());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        PromptVersionSummary summary = new PromptVersionSummary();
        summary.setPromptKey("testPrompt");
        summary.setVersion("1.0.0");
        summary.setStatus("online");
        summary.setCommitMsg("Initial commit");
        summary.setSrcUser("admin");
        summary.setGmtModified(1234567890L);
        
        String json = mapper.writeValueAsString(summary);
        assertNotNull(json);
        assertTrue(json.contains("\"promptKey\":\"testPrompt\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"status\":\"online\""));
        assertTrue(json.contains("\"commitMsg\":\"Initial commit\""));
        assertTrue(json.contains("\"srcUser\":\"admin\""));
        assertTrue(json.contains("\"gmtModified\":1234567890"));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"promptKey\":\"testPrompt\",\"version\":\"1.0.0\",\"status\":\"online\","
            + "\"commitMsg\":\"Initial commit\",\"srcUser\":\"admin\",\"gmtModified\":1234567890,"
            + "\"publishPipelineInfo\":\"pipeline-info\"}";
        
        PromptVersionSummary summary = mapper.readValue(json, PromptVersionSummary.class);
        assertNotNull(summary);
        assertEquals("testPrompt", summary.getPromptKey());
        assertEquals("1.0.0", summary.getVersion());
        assertEquals("online", summary.getStatus());
        assertEquals("Initial commit", summary.getCommitMsg());
        assertEquals("admin", summary.getSrcUser());
        assertEquals(1234567890L, summary.getGmtModified());
        assertEquals("pipeline-info", summary.getPublishPipelineInfo());
    }
}
