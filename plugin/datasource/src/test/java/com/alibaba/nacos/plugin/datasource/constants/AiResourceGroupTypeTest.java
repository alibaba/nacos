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

package com.alibaba.nacos.plugin.datasource.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceGroupTypeTest {
    
    @Test
    void testMatchesNullGroup() {
        assertFalse(AiResourceGroupType.matches(null, "any"));
    }
    
    @Test
    void testMatchesNonAiGroup() {
        assertFalse(AiResourceGroupType.matches("DEFAULT_GROUP", "some.json"));
    }
    
    @Test
    void testMatchesSkillManifestExact() {
        assertTrue(AiResourceGroupType.matches("skill_mySkill", "skill_index.json"));
        assertTrue(AiResourceGroupType.matches("skill_mySkill", "skill.json"));
    }
    
    @Test
    void testMatchesSkillManifestNonMatchingDataId() {
        assertFalse(AiResourceGroupType.matches("skill_mySkill", "other.json"));
    }
    
    @Test
    void testMatchesSkillVersionGroupOnly() {
        // SKILL_VERSION uses group-only filtering (dataIdMatchers == null)
        assertTrue(AiResourceGroupType.matches("skill_enc.6d79__enc.312e", "SKILL.md"));
        assertTrue(AiResourceGroupType.matches("skill_enc.6d79__enc.312e", "README.md"));
        assertTrue(AiResourceGroupType.matches("skill_enc.6d79__enc.312e", "enc.7265666572"));
        assertTrue(AiResourceGroupType.matches("skill_enc.6d79__enc.312e", null));
    }
    
    @Test
    void testMatchesAgentspecCompound() {
        assertTrue(AiResourceGroupType.matches("agentspec__myAgent", "resource_foo"));
        assertTrue(AiResourceGroupType.matches("agentspec__myAgent", "manifest.json"));
        assertTrue(AiResourceGroupType.matches("agentspec__myAgent", "agentspec_index.json"));
    }
    
    @Test
    void testMatchesAgentspecNonMatchingDataId() {
        assertFalse(AiResourceGroupType.matches("agentspec__myAgent", "other.json"));
    }
    
    @Test
    void testMatchesPromptCompound() {
        assertTrue(AiResourceGroupType.matches("prompt__myPrompt", "content.json"));
    }
    
    @Test
    void testMatchesPromptNonMatchingDataId() {
        assertFalse(AiResourceGroupType.matches("prompt__myPrompt", "other.json"));
    }
    
    @Test
    void testMatchesCompoundWithNullDataId() {
        // Compound types with null dataId should not match
        assertFalse(AiResourceGroupType.matches("skill_mySkill", null));
        assertFalse(AiResourceGroupType.matches("agentspec__myAgent", null));
        assertFalse(AiResourceGroupType.matches("prompt__myPrompt", null));
    }
}
