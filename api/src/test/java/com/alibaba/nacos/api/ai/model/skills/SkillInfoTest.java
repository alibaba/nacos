/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillInfoTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        SkillInfo info = new SkillInfo();
        assertNull(info.getVersion());
        assertFalse(info.isEnable());
        assertNull(info.getLabels());
        assertNull(info.getFrontMatter());
        assertNull(info.getFrontMatterTruncated());
    }
    
    @Test
    @DisplayName("test inherited fields from SkillBasicInfo")
    void testInheritedFieldsFromSkillBasicInfo() {
        SkillInfo info = new SkillInfo();
        info.setNamespaceId("public");
        info.setName("test-skill");
        info.setDescription("Test skill description");
        info.setUpdateTime(1234567890L);
        
        assertEquals("public", info.getNamespaceId());
        assertEquals("test-skill", info.getName());
        assertEquals("Test skill description", info.getDescription());
        assertEquals(1234567890L, info.getUpdateTime());
    }
    
    @Test
    @DisplayName("test getter and setter for runtime metadata")
    void testGetterAndSetterForRuntimeMetadata() {
        SkillInfo info = new SkillInfo();
        info.setVersion("v2");
        info.setEnable(true);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("latest", "v2");
        info.setLabels(labels);
        Map<String, String> frontMatter = new LinkedHashMap<>();
        frontMatter.put("version", "v2");
        info.setFrontMatter(frontMatter);
        info.setFrontMatterTruncated(Boolean.TRUE);
        
        assertEquals("v2", info.getVersion());
        assertTrue(info.isEnable());
        assertEquals("v2", info.getLabels().get("latest"));
        assertEquals("v2", info.getFrontMatter().get("version"));
        assertEquals(Boolean.TRUE, info.getFrontMatterTruncated());
    }
    
    @Test
    @DisplayName("test serialize and deserialize")
    void testSerializeAndDeserialize() throws JsonProcessingException {
        SkillInfo info = new SkillInfo();
        info.setNamespaceId("public");
        info.setName("test-skill");
        info.setVersion("v2");
        info.setDescription("Test skill description");
        info.setEnable(true);
        
        String json = mapper.writeValueAsString(info);
        SkillInfo actual = mapper.readValue(json, SkillInfo.class);
        assertEquals("public", actual.getNamespaceId());
        assertEquals("test-skill", actual.getName());
        assertEquals("v2", actual.getVersion());
        assertEquals("Test skill description", actual.getDescription());
        assertTrue(actual.isEnable());
    }
}
