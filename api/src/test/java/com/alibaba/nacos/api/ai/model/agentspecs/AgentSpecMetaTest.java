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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSpecMetaTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        AgentSpecMeta meta = new AgentSpecMeta();
        assertNull(meta.getVersions());
    }
    
    @Test
    @DisplayName("test inherited fields from AgentSpecSummary")
    void testInheritedFieldsFromAgentSpecSummary() {
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setNamespaceId("public");
        meta.setName("testAgentSpec");
        meta.setEnable(true);
        
        assertEquals("public", meta.getNamespaceId());
        assertEquals("testAgentSpec", meta.getName());
        assertTrue(meta.isEnable());
    }
    
    @Test
    @DisplayName("test getter and setter for versions")
    void testGetterAndSetterForVersions() {
        AgentSpecMeta meta = new AgentSpecMeta();
        List<AgentSpecMeta.AgentSpecVersionSummary> versions = new ArrayList<>();
        AgentSpecMeta.AgentSpecVersionSummary version1 =
            new AgentSpecMeta.AgentSpecVersionSummary();
        version1.setVersion("v1.0.0");
        version1.setStatus("online");
        versions.add(version1);
        meta.setVersions(versions);
        assertNotNull(meta.getVersions());
        assertEquals(1, meta.getVersions().size());
        assertEquals("v1.0.0", meta.getVersions().get(0).getVersion());
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        AgentSpecMeta meta = new AgentSpecMeta();
        meta.setNamespaceId("public");
        meta.setName("testAgentSpec");
        meta.setEnable(true);
        
        String json = mapper.writeValueAsString(meta);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testAgentSpec\""));
        assertTrue(json.contains("\"enable\":true"));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"namespaceId\":\"public\",\"name\":\"testAgentSpec\",\"enable\":true,"
            + "\"versions\":[{\"version\":\"v1.0.0\",\"status\":\"online\"}]}";
        
        AgentSpecMeta meta = mapper.readValue(json, AgentSpecMeta.class);
        assertNotNull(meta);
        assertEquals("public", meta.getNamespaceId());
        assertEquals("testAgentSpec", meta.getName());
        assertTrue(meta.isEnable());
        assertNotNull(meta.getVersions());
        assertEquals(1, meta.getVersions().size());
        assertEquals("v1.0.0", meta.getVersions().get(0).getVersion());
        assertEquals("online", meta.getVersions().get(0).getStatus());
    }
    
    // ========== AgentSpecVersionSummary Tests ==========
    
    @Test
    @DisplayName("test AgentSpecVersionSummary default constructor")
    void testAgentSpecVersionSummaryDefaultConstructor() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        assertNull(summary.getVersion());
        assertNull(summary.getStatus());
        assertNull(summary.getAuthor());
        assertNull(summary.getDescription());
        assertNull(summary.getCreateTime());
        assertNull(summary.getUpdateTime());
        assertNull(summary.getPublishPipelineInfo());
        assertNull(summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for version")
    void testAgentSpecVersionSummaryGetterAndSetterForVersion() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setVersion("v1.0.0");
        assertEquals("v1.0.0", summary.getVersion());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for status")
    void testAgentSpecVersionSummaryGetterAndSetterForStatus() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setStatus("online");
        assertEquals("online", summary.getStatus());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for author")
    void testAgentSpecVersionSummaryGetterAndSetterForAuthor() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setAuthor("developer");
        assertEquals("developer", summary.getAuthor());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for description")
    void testAgentSpecVersionSummaryGetterAndSetterForDescription() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setDescription("Version description");
        assertEquals("Version description", summary.getDescription());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for createTime")
    void testAgentSpecVersionSummaryGetterAndSetterForCreateTime() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setCreateTime(1234567890L);
        assertEquals(1234567890L, summary.getCreateTime());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for updateTime")
    void testAgentSpecVersionSummaryGetterAndSetterForUpdateTime() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setUpdateTime(1234567900L);
        assertEquals(1234567900L, summary.getUpdateTime());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for publishPipelineInfo")
    void testAgentSpecVersionSummaryGetterAndSetterForPublishPipelineInfo() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setPublishPipelineInfo("pipeline-info");
        assertEquals("pipeline-info", summary.getPublishPipelineInfo());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary getter and setter for downloadCount")
    void testAgentSpecVersionSummaryGetterAndSetterForDownloadCount() {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setDownloadCount(100L);
        assertEquals(100L, summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary serialize to json")
    void testAgentSpecVersionSummarySerializeToJson() throws JsonProcessingException {
        AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
        summary.setVersion("v1.0.0");
        summary.setStatus("online");
        summary.setAuthor("admin");
        summary.setDownloadCount(50L);
        
        String json = mapper.writeValueAsString(summary);
        assertNotNull(json);
        assertTrue(json.contains("\"version\":\"v1.0.0\""));
        assertTrue(json.contains("\"status\":\"online\""));
        assertTrue(json.contains("\"author\":\"admin\""));
        assertTrue(json.contains("\"downloadCount\":50"));
    }
    
    @Test
    @DisplayName("test AgentSpecVersionSummary deserialize from json")
    void testAgentSpecVersionSummaryDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"version\":\"v1.0.0\",\"status\":\"online\",\"author\":\"admin\","
            + "\"description\":\"Test version\",\"createTime\":1234567890,\"updateTime\":1234567900,"
            + "\"downloadCount\":100}";
        
        AgentSpecMeta.AgentSpecVersionSummary summary =
            mapper.readValue(json, AgentSpecMeta.AgentSpecVersionSummary.class);
        assertNotNull(summary);
        assertEquals("v1.0.0", summary.getVersion());
        assertEquals("online", summary.getStatus());
        assertEquals("admin", summary.getAuthor());
        assertEquals("Test version", summary.getDescription());
        assertEquals(1234567890L, summary.getCreateTime());
        assertEquals(1234567900L, summary.getUpdateTime());
        assertEquals(100L, summary.getDownloadCount());
    }
}
