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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptMetaInfoTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor initializes default values")
    void testDefaultConstructor() {
        PromptMetaInfo info = new PromptMetaInfo();
        assertNotNull(info.getVersions());
        assertTrue(info.getVersions().isEmpty());
        assertNotNull(info.getVersionDetails());
        assertTrue(info.getVersionDetails().isEmpty());
        // Inherited from PromptMetaSummary
        assertEquals(1, info.getSchemaVersion());
        assertNotNull(info.getBizTags());
    }
    
    @Test
    @DisplayName("test inherited fields from PromptMetaSummary")
    void testInheritedFieldsFromPromptMetaSummary() {
        PromptMetaInfo info = new PromptMetaInfo();
        info.setPromptKey("testPrompt");
        info.setDescription("Test description");
        info.setLatestVersion("1.0.0");
        info.setOnlineCnt(5);
        
        assertEquals("testPrompt", info.getPromptKey());
        assertEquals("Test description", info.getDescription());
        assertEquals("1.0.0", info.getLatestVersion());
        assertEquals(5, info.getOnlineCnt());
    }
    
    @Test
    @DisplayName("test getter and setter for versions")
    void testGetterAndSetterForVersions() {
        PromptMetaInfo info = new PromptMetaInfo();
        List<String> versions = new ArrayList<>();
        versions.add("1.0.0");
        versions.add("1.1.0");
        info.setVersions(versions);
        assertNotNull(info.getVersions());
        assertEquals(2, info.getVersions().size());
        assertEquals("1.0.0", info.getVersions().get(0));
        assertEquals("1.1.0", info.getVersions().get(1));
    }
    
    @Test
    @DisplayName("test getter and setter for versionDetails")
    void testGetterAndSetterForVersionDetails() {
        PromptMetaInfo info = new PromptMetaInfo();
        List<PromptVersionSummary> versionDetails = new ArrayList<>();
        PromptVersionSummary detail1 = new PromptVersionSummary();
        detail1.setVersion("1.0.0");
        detail1.setStatus("online");
        versionDetails.add(detail1);
        info.setVersionDetails(versionDetails);
        assertNotNull(info.getVersionDetails());
        assertEquals(1, info.getVersionDetails().size());
        assertEquals("1.0.0", info.getVersionDetails().get(0).getVersion());
        assertEquals("online", info.getVersionDetails().get(0).getStatus());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        PromptMetaInfo info = new PromptMetaInfo();
        info.setPromptKey("testPrompt");
        info.setLatestVersion("1.0.0");
        List<String> versions = new ArrayList<>();
        versions.add("1.0.0");
        info.setVersions(versions);
        
        String json = mapper.writeValueAsString(info);
        assertNotNull(json);
        assertTrue(json.contains("\"promptKey\":\"testPrompt\""));
        assertTrue(json.contains("\"latestVersion\":\"1.0.0\""));
        assertTrue(json.contains("\"versions\""));
        assertTrue(json.contains("\"versionDetails\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"schemaVersion\":1,\"promptKey\":\"testPrompt\",\"description\":\"Test\","
            + "\"latestVersion\":\"1.0.0\",\"versions\":[\"1.0.0\",\"1.1.0\"],"
            + "\"versionDetails\":[{\"version\":\"1.0.0\",\"status\":\"online\"}]}";
        
        PromptMetaInfo info = mapper.readValue(json, PromptMetaInfo.class);
        assertNotNull(info);
        assertEquals("testPrompt", info.getPromptKey());
        assertEquals("Test", info.getDescription());
        assertEquals("1.0.0", info.getLatestVersion());
        assertEquals(2, info.getVersions().size());
        assertEquals("1.0.0", info.getVersions().get(0));
        assertEquals("1.1.0", info.getVersions().get(1));
        assertEquals(1, info.getVersionDetails().size());
        assertEquals("1.0.0", info.getVersionDetails().get(0).getVersion());
        assertEquals("online", info.getVersionDetails().get(0).getStatus());
    }
}
