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

package com.alibaba.nacos.airegistry.model.skills;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillsModelTest {
    
    @Test
    void testSkillsSearchItemGetterAndSetter() {
        SkillsSearchItem item = new SkillsSearchItem();
        item.setId("skill-id");
        item.setName("deploy");
        item.setInstalls(10L);
        item.setSource("skills-sh");
        
        assertEquals("skill-id", item.getId());
        assertEquals("deploy", item.getName());
        assertEquals(10L, item.getInstalls());
        assertEquals("skills-sh", item.getSource());
    }
    
    @Test
    void testSkillsSearchResponseGetterAndSetter() {
        SkillsSearchItem item = new SkillsSearchItem();
        SkillsSearchResponse response = new SkillsSearchResponse();
        response.setSkills(Collections.singletonList(item));
        
        assertEquals(Collections.singletonList(item), response.getSkills());
    }
    
    @Test
    void testWellKnownSkillEntryGetterAndSetter() {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        entry.setName("deploy");
        entry.setType("skill");
        entry.setDescription("Deploy app");
        entry.setUrl("https://example.com/skill");
        entry.setDigest("sha256:123");
        entry.setVersion("1.0.0");
        entry.setFiles(Collections.singletonList("SKILL.md"));
        
        assertEquals("deploy", entry.getName());
        assertEquals("skill", entry.getType());
        assertEquals("Deploy app", entry.getDescription());
        assertEquals("https://example.com/skill", entry.getUrl());
        assertEquals("sha256:123", entry.getDigest());
        assertEquals("1.0.0", entry.getVersion());
        assertEquals(Collections.singletonList("SKILL.md"), entry.getFiles());
    }
    
    @Test
    void testWellKnownSkillsIndexGetterAndSetter() {
        WellKnownSkillEntry entry = new WellKnownSkillEntry();
        WellKnownSkillsIndex index = new WellKnownSkillsIndex();
        index.setSchema("https://schemas.example.com/skills.json");
        index.setSkills(Collections.singletonList(entry));
        
        assertEquals("https://schemas.example.com/skills.json", index.getSchema());
        assertEquals(Collections.singletonList(entry), index.getSkills());
    }
}
