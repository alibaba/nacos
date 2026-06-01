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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMetaTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        SkillMeta meta = new SkillMeta();
        assertNull(meta.getVersions());
    }
    
    @Test
    @DisplayName("test inherited fields from SkillSummary")
    void testInheritedFieldsFromSkillSummary() {
        SkillMeta meta = new SkillMeta();
        meta.setNamespaceId("public");
        meta.setName("testSkill");
        meta.setOwner("admin");
        meta.setEnable(true);
        
        assertEquals("public", meta.getNamespaceId());
        assertEquals("testSkill", meta.getName());
        assertEquals("admin", meta.getOwner());
        assertTrue(meta.isEnable());
    }
    
    @Test
    @DisplayName("test getter and setter for versions")
    void testGetterAndSetterForVersions() {
        SkillMeta meta = new SkillMeta();
        List<SkillMeta.SkillVersionSummary> versions = new ArrayList<>();
        SkillMeta.SkillVersionSummary version1 = new SkillMeta.SkillVersionSummary();
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
        SkillMeta meta = new SkillMeta();
        meta.setNamespaceId("public");
        meta.setName("testSkill");
        meta.setOwner("admin");
        
        String json = mapper.writeValueAsString(meta);
        assertNotNull(json);
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"name\":\"testSkill\""));
        assertTrue(json.contains("\"owner\":\"admin\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"namespaceId\":\"public\",\"name\":\"testSkill\",\"owner\":\"admin\","
            + "\"enable\":true,\"versions\":[{\"version\":\"v1.0.0\",\"status\":\"online\"}]}";
        
        SkillMeta meta = mapper.readValue(json, SkillMeta.class);
        assertNotNull(meta);
        assertEquals("public", meta.getNamespaceId());
        assertEquals("testSkill", meta.getName());
        assertEquals("admin", meta.getOwner());
        assertTrue(meta.isEnable());
        assertNotNull(meta.getVersions());
        assertEquals(1, meta.getVersions().size());
        assertEquals("v1.0.0", meta.getVersions().get(0).getVersion());
        assertEquals("online", meta.getVersions().get(0).getStatus());
    }
    
    // ========== SkillVersionSummary Tests ==========
    
    @Test
    @DisplayName("test SkillVersionSummary default constructor")
    void testSkillVersionSummaryDefaultConstructor() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        assertNull(summary.getVersion());
        assertNull(summary.getStatus());
        assertNull(summary.getAuthor());
        assertNull(summary.getCommitMsg());
        assertNull(summary.getCreateTime());
        assertNull(summary.getUpdateTime());
        assertNull(summary.getPublishPipelineInfo());
        assertNull(summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for version")
    void testSkillVersionSummaryGetterAndSetterForVersion() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setVersion("v1.0.0");
        assertEquals("v1.0.0", summary.getVersion());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for status")
    void testSkillVersionSummaryGetterAndSetterForStatus() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setStatus("online");
        assertEquals("online", summary.getStatus());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for author")
    void testSkillVersionSummaryGetterAndSetterForAuthor() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setAuthor("developer");
        assertEquals("developer", summary.getAuthor());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for commitMsg")
    void testSkillVersionSummaryGetterAndSetterForCommitMsg() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setCommitMsg("Initial commit");
        assertEquals("Initial commit", summary.getCommitMsg());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for createTime")
    void testSkillVersionSummaryGetterAndSetterForCreateTime() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setCreateTime(1234567890L);
        assertEquals(1234567890L, summary.getCreateTime());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for updateTime")
    void testSkillVersionSummaryGetterAndSetterForUpdateTime() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setUpdateTime(1234567900L);
        assertEquals(1234567900L, summary.getUpdateTime());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for publishPipelineInfo")
    void testSkillVersionSummaryGetterAndSetterForPublishPipelineInfo() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setPublishPipelineInfo("pipeline-info");
        assertEquals("pipeline-info", summary.getPublishPipelineInfo());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary getter and setter for downloadCount")
    void testSkillVersionSummaryGetterAndSetterForDownloadCount() {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
        summary.setDownloadCount(100L);
        assertEquals(100L, summary.getDownloadCount());
    }
    
    @Test
    @DisplayName("test SkillVersionSummary serialize to json")
    void testSkillVersionSummarySerializeToJson() throws JsonProcessingException {
        SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
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
    @DisplayName("test SkillVersionSummary deserialize from json")
    void testSkillVersionSummaryDeserializeFromJson() throws JsonProcessingException {
        String json = "{\"version\":\"v1.0.0\",\"status\":\"online\",\"author\":\"admin\","
            + "\"commitMsg\":\"Initial\",\"createTime\":1234567890,\"updateTime\":1234567900,"
            + "\"downloadCount\":100}";
        
        SkillMeta.SkillVersionSummary summary =
            mapper.readValue(json, SkillMeta.SkillVersionSummary.class);
        assertNotNull(summary);
        assertEquals("v1.0.0", summary.getVersion());
        assertEquals("online", summary.getStatus());
        assertEquals("admin", summary.getAuthor());
        assertEquals("Initial", summary.getCommitMsg());
        assertEquals(1234567890L, summary.getCreateTime());
        assertEquals(1234567900L, summary.getUpdateTime());
        assertEquals(100L, summary.getDownloadCount());
    }
}
