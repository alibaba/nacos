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

package com.alibaba.nacos.ai.storage;

import com.alibaba.nacos.api.ai.model.NacosAiConfigKeyCodec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NacosConfigAiResourceStorage} static helper methods and key parsing.
 *
 * <p>Validates: Requirements 6.4, 7.1, 7.2, 7.3</p>
 *
 * @author kiro
 * @since 3.2.0
 */
class NacosConfigAiResourceStorageTest {
    
    // ---- Legacy 4-part Skill key format (backward compatibility) ----
    
    @Test
    void testBuildStorageKeyLegacySkillFormat() {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1", "mySkill", "v1", "skill.json");
        assertNotNull(key);
        assertEquals(NacosConfigAiResourceStorage.TYPE, key.getProvider());
        assertEquals("ns1:mySkill:v1:skill.json", key.getKey());
    }
    
    @Test
    void testParseLegacySkillKeyProducesSkillGroupPrefix() {
        StorageKey key =
            new StorageKey(NacosConfigAiResourceStorage.TYPE, "ns1:mySkill:v1:skill.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals("ns1", parts.namespaceId());
        assertEquals(SkillUtils.buildSkillVersionGroup("mySkill", "v1"), parts.group());
        assertEquals("skill.json", parts.dataId());
    }
    
    // ---- 5-part typed key format: Skill ----
    
    @Test
    void testBuildStorageKeyTypedSkillFormat() {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL, "mySkill", "v2", "skill.json");
        assertNotNull(key);
        assertEquals("ns1:skill:mySkill:v2:skill.json", key.getKey());
    }
    
    @Test
    void testParseTypedSkillKeyProducesSkillGroupPrefix() {
        StorageKey key =
            new StorageKey(NacosConfigAiResourceStorage.TYPE, "ns1:skill:mySkill:v2:skill.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals("ns1", parts.namespaceId());
        assertEquals(SkillUtils.buildSkillVersionGroup("mySkill", "v2"), parts.group());
        assertEquals("skill.json", parts.dataId());
    }
    
    // ---- 5-part typed key format: AgentSpec ----
    
    @Test
    void testBuildStorageKeyTypedAgentSpecFormat() {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, "myWorker", "v1",
            "manifest.json");
        assertNotNull(key);
        assertEquals("ns1:agentspec:myWorker:v1:manifest.json", key.getKey());
    }
    
    @Test
    void testParseTypedAgentSpecKeyProducesAgentSpecGroupPrefix() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE,
            "ns1:agentspec:myWorker:v1:manifest.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals("ns1", parts.namespaceId());
        assertEquals(AgentSpecUtils.buildAgentSpecVersionGroup("myWorker", "v1"), parts.group());
        assertEquals("manifest.json", parts.dataId());
    }
    
    @Test
    void testParseAgentSpecResourceFilePath() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE,
            "ns1:agentspec:myWorker:v1:resource_config_SOUL__md.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals("ns1", parts.namespaceId());
        assertEquals(AgentSpecUtils.buildAgentSpecVersionGroup("myWorker", "v1"), parts.group());
        assertEquals("resource_config_SOUL__md.json", parts.dataId());
    }
    
    // ---- getMainFilePath ----
    
    @Test
    void testGetMainFilePathDefaultReturnsSkillMainDataId() {
        assertEquals(SkillUtils.SKILL_MAIN_DATA_ID, NacosConfigAiResourceStorage.getMainFilePath());
    }
    
    @Test
    void testGetMainFilePathWithCustomDataId() {
        assertEquals(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID,
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
    }
    
    // ---- getResourceFilePath / getAgentSpecResourceFilePath ----
    
    @Test
    void testGetResourceFilePathUsesSkillUtils() {
        String path = NacosConfigAiResourceStorage.getResourceFilePath("config", "SOUL.md");
        String expected = SkillUtils.RESOURCE_DATA_ID_PREFIX
            + SkillUtils.generateResourceId("config", "SOUL.md")
            + SkillUtils.RESOURCE_DATA_ID_SUFFIX;
        assertEquals(expected, path);
    }
    
    @Test
    void testGetAgentSpecResourceFilePathUsesAgentSpecUtils() {
        String path =
            NacosConfigAiResourceStorage.getAgentSpecResourceFilePath("config", "SOUL.md");
        String expected = AgentSpecUtils.RESOURCE_DATA_ID_PREFIX
            + AgentSpecUtils.generateResourceId("config", "SOUL.md")
            + AgentSpecUtils.RESOURCE_DATA_ID_SUFFIX;
        assertEquals(expected, path);
    }
    
    // ---- Error cases ----
    
    @Test
    void testParseNullStorageKeyThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(null));
    }
    
    @Test
    void testParseBlankKeyThrows() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE, "");
        assertThrows(IllegalArgumentException.class, () -> NacosConfigAiResourceStorage.parse(key));
    }
    
    @Test
    void testParseInvalidFormatThrows() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE, "only:two");
        assertThrows(IllegalArgumentException.class, () -> NacosConfigAiResourceStorage.parse(key));
    }
    
    @Test
    void testParseUnknownResourceTypeThrows() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE,
            "ns1:unknown:name:v1:file.json");
        assertThrows(IllegalArgumentException.class, () -> NacosConfigAiResourceStorage.parse(key));
    }
    
    // ---- Backward compatibility: legacy Skill keys still produce correct groups ----
    
    @Test
    void testLegacyAndTypedSkillKeysProduceSameGroup() {
        StorageKey legacyKey = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1", "mySkill", "v3", "skill.json");
        StorageKey typedKey = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL, "mySkill", "v3", "skill.json");
        
        NacosConfigAiResourceStorage.KeyParts legacyParts =
            NacosConfigAiResourceStorage.parse(legacyKey);
        NacosConfigAiResourceStorage.KeyParts typedParts =
            NacosConfigAiResourceStorage.parse(typedKey);
        
        assertEquals(legacyParts.group(), typedParts.group());
        assertEquals(legacyParts.namespaceId(), typedParts.namespaceId());
        assertEquals(legacyParts.dataId(), typedParts.dataId());
    }
    
    @Test
    void testParseTypedSkillKeyDecodesSpecialName() {
        String skillName = "my skill";
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL, skillName, "v2", "skill.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals(SkillUtils.buildSkillVersionGroup(skillName, "v2"), parts.group());
        String[] decoded = SkillUtils.decodeSkillGroupToNameAndVersion(parts.group());
        assertEquals(skillName, decoded[0]);
        assertEquals("v2", decoded[1]);
    }
    
    @Test
    void testPhysicalDataIdEncodedWhenLogicalHasInvalidChars() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE,
            "ns1:agentspec:worker:v1:resource_x y.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        String physical = NacosAiConfigKeyCodec.encodeSegment(parts.dataId());
        assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(physical));
        assertEquals("resource_x y.json", NacosAiConfigKeyCodec.decodeSegment(physical));
    }
    
    @Test
    void testAgentSpecGroupNamingConventionWithDeterministicInputs() {
        List<String> namespaceIds = Arrays.asList("public", "test-ns", "ns-1", "namespace_abc");
        List<String> names = Arrays.asList("worker", "worker-1", "worker_alpha");
        List<String> versions = Arrays.asList("1.0.0", "2.3.4", "10.20.30");
        for (String namespaceId : namespaceIds) {
            for (String name : names) {
                for (String version : versions) {
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        NacosConfigAiResourceStorage.TYPE, namespaceId,
                        NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                        name, version, AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
                    NacosConfigAiResourceStorage.KeyParts parts =
                        NacosConfigAiResourceStorage.parse(key);
                    assertEquals(AgentSpecUtils.buildAgentSpecVersionGroup(name, version),
                        parts.group());
                    assertTrue(parts.group().startsWith(AgentSpecUtils.AGENTSPEC_GROUP_PREFIX));
                }
            }
        }
    }
    
    @Test
    void testAgentSpecMainDataIdIsManifestJsonWithDeterministicInputs() {
        List<String> namespaceIds = Arrays.asList("public", "test-ns");
        List<String> names = Arrays.asList("worker", "worker-1");
        List<String> versions = Arrays.asList("1.0.0", "3.2.1");
        String mainFilePath =
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
        for (String namespaceId : namespaceIds) {
            for (String name : names) {
                for (String version : versions) {
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        NacosConfigAiResourceStorage.TYPE, namespaceId,
                        NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                        name, version, mainFilePath);
                    NacosConfigAiResourceStorage.KeyParts parts =
                        NacosConfigAiResourceStorage.parse(key);
                    assertEquals(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID, parts.dataId());
                }
            }
        }
    }
    
    @Test
    void testAgentSpecResourceDataIdPreservesGeneratedPath() {
        List<String> resourceTypes =
            Arrays.asList("config", "skill", "cron", "dockerfile", "other");
        List<String> resourceNames =
            Arrays.asList("SOUL.md", "AGENTS.md", "jobs.json", "Dockerfile");
        for (String resourceType : resourceTypes) {
            for (String resourceName : resourceNames) {
                String expectedPath = NacosConfigAiResourceStorage
                    .getAgentSpecResourceFilePath(resourceType, resourceName);
                StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                    NacosConfigAiResourceStorage.TYPE, "public",
                    NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                    "worker", "1.0.0", expectedPath);
                NacosConfigAiResourceStorage.KeyParts parts =
                    NacosConfigAiResourceStorage.parse(key);
                assertEquals(expectedPath, parts.dataId());
                assertTrue(parts.dataId().startsWith(AgentSpecUtils.RESOURCE_DATA_ID_PREFIX));
                assertTrue(parts.dataId().endsWith(AgentSpecUtils.RESOURCE_DATA_ID_SUFFIX));
            }
        }
    }
    
    // ---- 5-part typed key format: Prompt ----
    
    @Test
    void testBuildStorageKeyTypedPromptFormat() {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "myPrompt", "1.0.0", "content.json");
        assertNotNull(key);
        assertEquals("ns1:prompt:myPrompt:1.0.0:content.json", key.getKey());
    }
    
    @Test
    void testParseTypedPromptKeyProducesPromptGroupPrefix() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE,
            "ns1:prompt:myPrompt:1.0.0:content.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals("ns1", parts.namespaceId());
        assertEquals(PromptUtils.buildPromptVersionGroup("myPrompt", "1.0.0"), parts.group());
        assertEquals("content.json", parts.dataId());
    }
    
    @Test
    void testPromptGroupNamingConventionWithDeterministicInputs() {
        List<String> namespaceIds = Arrays.asList("public", "test-ns", "ns-1");
        List<String> names = Arrays.asList("greeting", "code-review", "translate_en");
        List<String> versions = Arrays.asList("1.0.0", "2.3.4", "10.20.30");
        for (String namespaceId : namespaceIds) {
            for (String name : names) {
                for (String version : versions) {
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        NacosConfigAiResourceStorage.TYPE, namespaceId,
                        NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT,
                        name, version, PromptUtils.PROMPT_MAIN_DATA_ID);
                    NacosConfigAiResourceStorage.KeyParts parts =
                        NacosConfigAiResourceStorage.parse(key);
                    assertEquals(PromptUtils.buildPromptVersionGroup(name, version), parts.group());
                    assertTrue(parts.group().startsWith(PromptUtils.PROMPT_GROUP_PREFIX));
                }
            }
        }
    }
}
