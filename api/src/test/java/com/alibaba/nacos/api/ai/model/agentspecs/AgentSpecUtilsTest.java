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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link AgentSpecUtils}.
 *
 * @author nacos
 */
class AgentSpecUtilsTest {
    
    @Test
    void testConstants() {
        assertEquals("manifest.json", AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
        assertEquals("agentspec__", AgentSpecUtils.AGENTSPEC_GROUP_PREFIX);
        assertEquals("resource_", AgentSpecUtils.RESOURCE_DATA_ID_PREFIX);
        assertEquals(".json", AgentSpecUtils.RESOURCE_DATA_ID_SUFFIX);
        assertEquals("agentspec_index.json", AgentSpecUtils.AGENTSPEC_INDEX_DATA_ID);
    }
    
    @Test
    void testGenerateResourceIdWithTypeAndName() {
        String result = AgentSpecUtils.generateResourceId("config", "SOUL.md");
        assertEquals("config_SOUL__md", result);
    }
    
    @Test
    void testGenerateResourceIdWithSlashInType() {
        String result = AgentSpecUtils.generateResourceId("skills/my-skill", "SKILL.md");
        assertEquals("skills.my-skill_SKILL__md", result);
    }
    
    @Test
    void testGenerateResourceIdWithNullType() {
        String result = AgentSpecUtils.generateResourceId(null, "README.md");
        assertEquals("README__md", result);
    }
    
    @Test
    void testGenerateResourceIdWithEmptyType() {
        String result = AgentSpecUtils.generateResourceId("", "data.txt");
        assertEquals("data__txt", result);
    }
    
    @Test
    void testGenerateResourceIdWithNullName() {
        String result = AgentSpecUtils.generateResourceId("config", null);
        assertEquals("", result);
    }
    
    @Test
    void testGenerateResourceIdWithEmptyName() {
        String result = AgentSpecUtils.generateResourceId("config", "  ");
        assertEquals("", result);
    }
    
    @Test
    void testGenerateResourceIdWithNameWithoutExtension() {
        String result = AgentSpecUtils.generateResourceId("config", "Dockerfile");
        assertEquals("config_Dockerfile", result);
    }
    
    @Test
    void testBuildAgentSpecGroup() {
        assertEquals("agentspec__my-worker", AgentSpecUtils.buildAgentSpecGroup("my-worker"));
    }
    
    @Test
    void testBuildAgentSpecGroupWithBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecUtils.buildAgentSpecGroup(""));
    }
    
    @Test
    void testBuildAgentSpecGroupWithNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecUtils.buildAgentSpecGroup(null));
    }
    
    @Test
    void testBuildAgentSpecVersionGroup() {
        String group = AgentSpecUtils.buildAgentSpecVersionGroup("my-worker", "v1");
        assertTrue(group.startsWith(AgentSpecUtils.AGENTSPEC_GROUP_PREFIX));
        assertTrue(group.substring(AgentSpecUtils.AGENTSPEC_GROUP_PREFIX.length())
            .contains("__"));
    }
    
    @Test
    void testBuildAgentSpecVersionGroupWithBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecUtils.buildAgentSpecVersionGroup("", "v1"));
    }
    
    @Test
    void testBuildAgentSpecVersionGroupWithNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecUtils.buildAgentSpecVersionGroup(null, "v1"));
    }
    
    @Test
    void testBuildAgentSpecVersionGroupWithBlankVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecUtils.buildAgentSpecVersionGroup("my-worker", ""));
    }
    
    @Test
    void testBuildAgentSpecVersionGroupWithNullVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecUtils.buildAgentSpecVersionGroup("my-worker", null));
    }
    
}
