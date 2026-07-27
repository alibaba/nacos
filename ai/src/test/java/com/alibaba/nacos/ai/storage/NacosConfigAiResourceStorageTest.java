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
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.service.agent.identity.RadAsciiAgentIdCodec;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageKeyComposer;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NacosConfigAiResourceStorage} static helper methods and key parsing.
 *
 * <p>Validates: Requirements 6.4, 7.1, 7.2, 7.3</p>
 *
 * @author kiro
 * @since 3.2.0
 */
class NacosConfigAiResourceStorageTest {
    
    private ConfigQueryChainService configQueryChainService;
    
    private ConfigOperationService configOperationService;
    
    private SyncEffectService syncEffectService;
    
    private NacosConfigAiResourceStorage storage;
    
    @BeforeEach
    void setUp() {
        configQueryChainService = mock(ConfigQueryChainService.class);
        configOperationService = mock(ConfigOperationService.class);
        syncEffectService = mock(SyncEffectService.class);
        storage = new NacosConfigAiResourceStorage(configQueryChainService, configOperationService,
            syncEffectService);
    }
    
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
    void testBuildAndParseAgentVersionStorageKey() {
        StorageKey key = AgentVersionStorageKeyComposer.compose(
            NacosConfigAiResourceStorage.TYPE, "ns1", "Nacos Agent", "1.0.0-RC1");
        
        assertEquals(NacosConfigAiResourceStorage.TYPE, key.getProvider());
        assertEquals("ns1:agent-version:agent__enc-Nacos-032Agent__1.0.0-RC1.json",
            key.getKey());
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        assertEquals("ns1", parts.namespaceId());
        assertEquals("agent-version", parts.group());
        assertEquals("agent__enc-Nacos-032Agent__1.0.0-RC1.json", parts.dataId());
    }
    
    @Test
    void testBuildAgentVersionStorageKeyRejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageKeyComposer.compose(
                NacosConfigAiResourceStorage.TYPE, "invalid namespace", "Agent", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageKeyComposer.compose(
                NacosConfigAiResourceStorage.TYPE, "ns1", "Agent代理", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentVersionStorageKeyComposer.compose(
                NacosConfigAiResourceStorage.TYPE, "ns1", "Agent", "v1"));
    }
    
    @Test
    void testParseRejectsMalformedAgentVersionStorageKey() {
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent__Agent_01__1.0.0.json")));
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent__Agent__v1.json")));
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent__Agent__1.0.0")));
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent__Agent1.0.0.json")));
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent____1.0.0.json")));
        assertThrows(IllegalArgumentException.class,
            () -> NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent__" + repeat("a", 261) + "__1.0.0.json")));
        NacosConfigAiResourceStorage.KeyParts maximumParts =
            NacosConfigAiResourceStorage.parse(new StorageKey(
                NacosConfigAiResourceStorage.TYPE,
                "ns1:agent-version:agent__" + repeat("a", 260) + "__1.0.0.json"));
        assertEquals("agent-version", maximumParts.group());
    }
    
    @Test
    void testAgentVersionGroupTokenDoesNotBreakLegacySkillName() {
        StorageKey versionKey = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1", "agent-version", "v1", "skill.json");
        StorageKey manifestKey = NacosConfigAiResourceStorage.buildManifestStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1", "agent-version");
        StorageKey prefixedVersionKey = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1", "agent-version", "agent__legacy",
            "skill.json");
        
        NacosConfigAiResourceStorage.KeyParts versionParts =
            NacosConfigAiResourceStorage.parse(versionKey);
        NacosConfigAiResourceStorage.KeyParts manifestParts =
            NacosConfigAiResourceStorage.parse(manifestKey);
        NacosConfigAiResourceStorage.KeyParts prefixedVersionParts =
            NacosConfigAiResourceStorage.parse(prefixedVersionKey);
        
        assertEquals(SkillUtils.buildSkillVersionGroup("agent-version", "v1"),
            versionParts.group());
        assertEquals(SkillUtils.buildSkillGroup("agent-version"), manifestParts.group());
        assertEquals(SkillUtils.buildSkillVersionGroup("agent-version", "agent__legacy"),
            prefixedVersionParts.group());
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
        assertTrue(parts.group().startsWith(SkillUtils.SKILL_GROUP_PREFIX));
    }
    
    @Test
    void testPhysicalDataIdEncodedWhenLogicalHasInvalidChars() {
        StorageKey key = new StorageKey(NacosConfigAiResourceStorage.TYPE,
            "ns1:agentspec:worker:v1:resource_x y.json");
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        String physical = NacosAiConfigKeyCodec.toPhysicalDataId(parts.dataId());
        assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(physical));
        assertEquals("resource_x y.json", NacosAiConfigKeyCodec.decodeSegment(physical));
    }
    
    @Test
    void testPhysicalDataIdEncodedWhenLogicalContainsUnicode() {
        List<String> logicalDataIds = Arrays.asList(
            NacosConfigAiResourceStorage.getAgentSpecResourceFilePath("docs", "说明.md"),
            "说明.md");
        for (String logicalDataId : logicalDataIds) {
            String physicalDataId = NacosAiConfigKeyCodec.toPhysicalDataId(logicalDataId);
            assertTrue(physicalDataId.startsWith(NacosAiConfigKeyCodec.ENCODED_PREFIX));
            assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(physicalDataId));
            assertEquals(logicalDataId, NacosAiConfigKeyCodec.decodeSegment(physicalDataId));
        }
    }
    
    @Test
    void testStorageOperationsUseSameEncodedDataIdForUnicodeSkillPath() throws NacosException {
        ConfigQueryChainService queryChainService = mock(ConfigQueryChainService.class);
        ConfigOperationService operationService = mock(ConfigOperationService.class);
        NacosConfigAiResourceStorage storage =
            new NacosConfigAiResourceStorage(queryChainService, operationService, null);
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "public", "test-skill", "1.0.0", "说明.md");
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent("content");
        when(queryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        storage.save(key, content);
        assertArrayEquals(content, storage.get(key));
        storage.delete(key);
        
        ArgumentCaptor<ConfigForm> publishCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(operationService).publishConfig(publishCaptor.capture(), any(), isNull());
        ArgumentCaptor<ConfigQueryChainRequest> queryCaptor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        verify(queryChainService).handle(queryCaptor.capture());
        ArgumentCaptor<String> deleteDataIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deleteGroupCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationService).deleteConfig(deleteDataIdCaptor.capture(),
            deleteGroupCaptor.capture(), eq("public"), isNull(), isNull(), eq("nacos"), isNull());
        
        String physicalDataId = publishCaptor.getValue().getDataId();
        String physicalGroup = publishCaptor.getValue().getGroup();
        assertTrue(physicalDataId.startsWith(NacosAiConfigKeyCodec.ENCODED_PREFIX));
        assertTrue(NacosAiConfigKeyCodec.isValidNacosConfigParam(physicalDataId));
        assertEquals(physicalDataId, queryCaptor.getValue().getDataId());
        assertEquals(physicalDataId, deleteDataIdCaptor.getValue());
        assertEquals(SkillUtils.buildSkillVersionGroup("test-skill", "1.0.0"), physicalGroup);
        assertEquals(physicalGroup, queryCaptor.getValue().getGroup());
        assertEquals(physicalGroup, deleteGroupCaptor.getValue());
        assertEquals("说明.md", NacosAiConfigKeyCodec.decodeSegment(physicalDataId));
    }
    
    @Test
    void testStorageOperationsUseSameHashedCoordinatesForLongKeys() throws NacosException {
        List<String[]> resourceTypes = Arrays.asList(
            new String[] {NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL,
                SkillUtils.SKILL_GROUP_PREFIX},
            new String[] {NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                AgentSpecUtils.AGENTSPEC_GROUP_PREFIX},
            new String[] {NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT,
                PromptUtils.PROMPT_GROUP_PREFIX});
        for (String[] resourceType : resourceTypes) {
            assertStorageOperationsUseSameHashedCoordinates(resourceType[0], resourceType[1]);
        }
    }
    
    @Test
    void testAgentVersionStorageOperationsUseSpecialAgentNameCoordinates()
        throws NacosException {
        assertAgentVersionStorageOperations("Nacos Agent", "1.0.0-RC1", false);
    }
    
    @Test
    void testAgentVersionStorageOperationsHashMaximumEncodedAgentName()
        throws NacosException {
        String agentName = repeat("!", 64);
        String encodedAgentId = RadAsciiAgentIdCodec.encode(agentName);
        assertEquals(260, encodedAgentId.length());
        assertAgentVersionStorageOperations(agentName, "1.0.0", true);
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
    
    @Test
    void testTypeReturnsNacosConfig() {
        assertEquals(NacosConfigAiResourceStorage.TYPE, storage.type());
    }
    
    @Test
    void testSavePublishesConfigAndWaitsForSync() throws Exception {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "myPrompt", "1.0.0",
            "content.yaml");
        
        storage.save(key, "hello".getBytes(StandardCharsets.UTF_8));
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService).publishConfig(formCaptor.capture(),
            any(ConfigRequestInfo.class), isNull());
        ConfigForm form = formCaptor.getValue();
        assertEquals(NacosAiConfigKeyCodec.encodeSegment("content.yaml"), form.getDataId());
        assertEquals(PromptUtils.buildPromptVersionGroup("myPrompt", "1.0.0"), form.getGroup());
        assertEquals("ns1", form.getNamespaceId());
        assertEquals("hello", form.getContent());
        assertEquals(ConfigType.YAML.getType(), form.getType());
        verify(syncEffectService).toSync(same(form), anyLong());
    }
    
    @Test
    void testSaveRetriesWhenConfigAlreadyExists() throws Exception {
        doThrow(new ConfigAlreadyExistsException("exists")).doReturn(true)
            .when(configOperationService)
            .publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), isNull());
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL, "mySkill", "v1", "skill.json");
        
        storage.save(key, null);
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService, times(2)).publishConfig(formCaptor.capture(),
            any(ConfigRequestInfo.class), isNull());
        assertEquals("", formCaptor.getValue().getContent());
        assertEquals(ConfigType.JSON.getType(), formCaptor.getValue().getType());
    }
    
    @Test
    void testSaveGuessesConfigTypeFromDataId() throws Exception {
        NacosConfigAiResourceStorage storageWithoutSync =
            new NacosConfigAiResourceStorage(configQueryChainService, configOperationService, null);
        List<String> dataIds = Arrays.asList("content.json", "content.yml", "content.xml",
            "content.properties", "content.txt");
        List<String> expectedTypes = Arrays.asList(ConfigType.JSON.getType(),
            ConfigType.YAML.getType(), ConfigType.XML.getType(), ConfigType.PROPERTIES.getType(),
            ConfigType.TEXT.getType());
        
        for (String dataId : dataIds) {
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                NacosConfigAiResourceStorage.TYPE, "ns1",
                NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "myPrompt", "1.0.0", dataId);
            storageWithoutSync.save(key, "x".getBytes(StandardCharsets.UTF_8));
        }
        
        ArgumentCaptor<ConfigForm> formCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService, times(dataIds.size())).publishConfig(formCaptor.capture(),
            any(ConfigRequestInfo.class), isNull());
        for (int i = 0; i < expectedTypes.size(); i++) {
            assertEquals(expectedTypes.get(i), formCaptor.getAllValues().get(i).getType());
        }
    }
    
    @Test
    void testGetReturnsContent() throws Exception {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent("hello");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, "worker", "v1",
            "manifest.json");
        
        byte[] actual = storage.get(key);
        
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), actual);
        ArgumentCaptor<ConfigQueryChainRequest> requestCaptor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        verify(configQueryChainService).handle(requestCaptor.capture());
        assertEquals(NacosAiConfigKeyCodec.encodeSegment("manifest.json"),
            requestCaptor.getValue().getDataId());
        assertEquals(AgentSpecUtils.buildAgentSpecVersionGroup("worker", "v1"),
            requestCaptor.getValue().getGroup());
        assertEquals("ns1", requestCaptor.getValue().getTenant());
    }
    
    @Test
    void testGetReturnsNullWhenConfigNotFoundOrContentNull() throws Exception {
        ConfigQueryChainResponse notFound = new ConfigQueryChainResponse();
        notFound.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        ConfigQueryChainResponse noContent = new ConfigQueryChainResponse();
        noContent.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound, noContent);
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL, "mySkill", "v1", "skill.json");
        
        assertNull(storage.get(key));
        assertNull(storage.get(key));
    }
    
    @Test
    void testDeleteRemovesConfigWithoutHistory() throws Exception {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "ns1",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "myPrompt", "1.0.0",
            "content.json");
        
        storage.delete(key);
        
        verify(configOperationService).deleteConfig(
            NacosAiConfigKeyCodec.encodeSegment("content.json"),
            PromptUtils.buildPromptVersionGroup("myPrompt", "1.0.0"), "ns1", null, null, "nacos",
            null);
    }
    
    private void assertStorageOperationsUseSameHashedCoordinates(String resourceType,
        String groupPrefix)
        throws NacosException {
        ConfigQueryChainService queryChainService = mock(ConfigQueryChainService.class);
        ConfigOperationService operationService = mock(ConfigOperationService.class);
        NacosConfigAiResourceStorage storage =
            new NacosConfigAiResourceStorage(queryChainService, operationService, null);
        String name = repeat("long-name-", 10);
        String version = repeat("version-", 4);
        String logicalDataId = repeat("说明", 50) + ".json";
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
            NacosConfigAiResourceStorage.TYPE, "public", resourceType, name, version,
            logicalDataId);
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent("content");
        when(queryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        storage.save(key, content);
        assertArrayEquals(content, storage.get(key));
        storage.delete(key);
        
        ArgumentCaptor<ConfigForm> publishCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(operationService).publishConfig(publishCaptor.capture(), any(), isNull());
        ArgumentCaptor<ConfigQueryChainRequest> queryCaptor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        verify(queryChainService).handle(queryCaptor.capture());
        ArgumentCaptor<String> deleteDataIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deleteGroupCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationService).deleteConfig(deleteDataIdCaptor.capture(),
            deleteGroupCaptor.capture(), eq("public"), isNull(), isNull(), eq("nacos"), isNull());
        
        String expectedDataId = NacosAiConfigKeyCodec.toPhysicalDataId(logicalDataId);
        String expectedGroup =
            NacosAiConfigKeyCodec.toPhysicalGroup(parts.group(), groupPrefix);
        assertTrue(expectedDataId.startsWith(NacosAiConfigKeyCodec.HASHED_PREFIX));
        assertTrue(expectedGroup.startsWith(groupPrefix + NacosAiConfigKeyCodec.HASHED_PREFIX));
        assertTrue(expectedDataId.length() <= NacosAiConfigKeyCodec.MAX_DATA_ID_LENGTH);
        assertTrue(expectedGroup.length() <= NacosAiConfigKeyCodec.MAX_GROUP_LENGTH);
        assertEquals(expectedDataId, publishCaptor.getValue().getDataId());
        assertEquals(expectedGroup, publishCaptor.getValue().getGroup());
        assertEquals(expectedDataId, queryCaptor.getValue().getDataId());
        assertEquals(expectedGroup, queryCaptor.getValue().getGroup());
        assertEquals(expectedDataId, deleteDataIdCaptor.getValue());
        assertEquals(expectedGroup, deleteGroupCaptor.getValue());
    }
    
    private void assertAgentVersionStorageOperations(String agentName, String version,
        boolean expectHashedDataId) throws NacosException {
        StorageKey key = AgentVersionStorageKeyComposer.compose(
            NacosConfigAiResourceStorage.TYPE, "public", agentName, version);
        NacosConfigAiResourceStorage.KeyParts parts = NacosConfigAiResourceStorage.parse(key);
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContent("content");
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response);
        
        storage.save(key, content);
        assertArrayEquals(content, storage.get(key));
        storage.delete(key);
        
        ArgumentCaptor<ConfigForm> publishCaptor = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService).publishConfig(publishCaptor.capture(), any(), isNull());
        ArgumentCaptor<ConfigQueryChainRequest> queryCaptor =
            ArgumentCaptor.forClass(ConfigQueryChainRequest.class);
        verify(configQueryChainService).handle(queryCaptor.capture());
        ArgumentCaptor<String> deleteDataIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deleteGroupCaptor = ArgumentCaptor.forClass(String.class);
        verify(configOperationService).deleteConfig(deleteDataIdCaptor.capture(),
            deleteGroupCaptor.capture(), eq("public"), isNull(), isNull(), eq("nacos"), isNull());
        
        String expectedDataId = NacosAiConfigKeyCodec.toPhysicalDataId(parts.dataId());
        if (expectHashedDataId) {
            assertTrue(expectedDataId.startsWith(NacosAiConfigKeyCodec.HASHED_PREFIX));
        } else {
            assertEquals(parts.dataId(), expectedDataId);
        }
        assertEquals(expectedDataId, publishCaptor.getValue().getDataId());
        assertEquals(expectedDataId, queryCaptor.getValue().getDataId());
        assertEquals(expectedDataId, deleteDataIdCaptor.getValue());
        assertEquals("agent-version", publishCaptor.getValue().getGroup());
        assertEquals("agent-version", queryCaptor.getValue().getGroup());
        assertEquals("agent-version", deleteGroupCaptor.getValue());
    }
    
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
