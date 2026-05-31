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

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storage key naming convention.
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecStorageKeyTest {
    
    private static final String PROVIDER = NacosConfigAiResourceStorage.TYPE;
    
    private static String[] validNamespaceIds() {
        return new String[] {"public", "test-ns", "ns-1", "namespace_abc"};
    }
    
    private static String[] validNames() {
        return new String[] {"myAgent", "agent-1", "spec_v2", "a"};
    }
    
    private static String[] validVersions() {
        return new String[] {"1.0.0", "2.3.4", "10.0.99"};
    }
    
    private static String[] validResourceTypes() {
        return new String[] {"config", "skill", "cron", "dockerfile", "other"};
    }
    
    private static String[] validResourceNames() {
        return new String[] {"SOUL.md", "AGENTS.md", "MEMORY.md", "jobs.json",
            "my-skill.md", "Dockerfile", "tool-analysis.json"};
    }
    
    /**
     * AgentSpec storage group follows {@code agentspec__{name}__{version}} format.
     */
    @Test
    void agentSpecGroupFollowsNamingConvention() {
        for (String namespaceId : validNamespaceIds()) {
            for (String name : validNames()) {
                for (String version : validVersions()) {
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        PROVIDER, namespaceId,
                        NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                        name, version, AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
                    NacosConfigAiResourceStorage.KeyParts parts =
                        NacosConfigAiResourceStorage.parse(key);
                    
                    String expectedGroup = AgentSpecUtils.buildAgentSpecVersionGroup(name, version);
                    assertEquals(expectedGroup, parts.group(),
                        "Group must follow agentspec__{name}__{version} format");
                    assertTrue(parts.group().startsWith(AgentSpecUtils.AGENTSPEC_GROUP_PREFIX),
                        "Group must start with agentspec__ prefix");
                }
            }
        }
    }
    
    /**
     * AgentSpec main dataId is always {@code manifest.json}.
     */
    @Test
    void agentSpecMainDataIdIsManifestJson() {
        for (String namespaceId : validNamespaceIds()) {
            for (String name : validNames()) {
                for (String version : validVersions()) {
                    String mainFilePath = NacosConfigAiResourceStorage.getMainFilePath(
                        AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        PROVIDER, namespaceId,
                        NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                        name, version, mainFilePath);
                    NacosConfigAiResourceStorage.KeyParts parts =
                        NacosConfigAiResourceStorage.parse(key);
                    
                    assertEquals("manifest.json", parts.dataId(),
                        "Main dataId must be manifest.json");
                }
            }
        }
    }
    
    /**
     * AgentSpec resource dataId preserves original file path encoding.
     */
    @Test
    void agentSpecResourceDataIdPreservesFilePath() {
        for (String namespaceId : validNamespaceIds()) {
            for (String agentSpecName : validNames()) {
                for (String version : validVersions()) {
                    for (String resourceType : validResourceTypes()) {
                        for (String resourceName : validResourceNames()) {
                            String resourceFilePath =
                                NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(
                                    resourceType, resourceName);
                            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                                PROVIDER, namespaceId,
                                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                                agentSpecName, version, resourceFilePath);
                            NacosConfigAiResourceStorage.KeyParts parts =
                                NacosConfigAiResourceStorage.parse(key);
                            
                            assertEquals(resourceFilePath, parts.dataId(),
                                "Resource dataId must match the generated resource file path");
                            assertTrue(
                                parts.dataId().startsWith(AgentSpecUtils.RESOURCE_DATA_ID_PREFIX),
                                "Resource dataId must start with resource_ prefix");
                            assertTrue(
                                parts.dataId().endsWith(AgentSpecUtils.RESOURCE_DATA_ID_SUFFIX),
                                "Resource dataId must end with .json suffix");
                        }
                    }
                }
            }
        }
    }
    
    /**
     * AgentSpec group prefix is distinct from Skill group prefix.
     */
    @Test
    void agentSpecGroupPrefixIsDistinctFromSkill() {
        for (String namespaceId : validNamespaceIds()) {
            for (String name : validNames()) {
                for (String version : validVersions()) {
                    StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(
                        PROVIDER, namespaceId,
                        NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                        name, version, AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID);
                    NacosConfigAiResourceStorage.KeyParts parts =
                        NacosConfigAiResourceStorage.parse(key);
                    
                    assertTrue(parts.group().startsWith("agentspec__"),
                        "AgentSpec group must start with agentspec__");
                    assertTrue(
                        !parts.group().startsWith("skill_")
                            || parts.group().startsWith("agentspec__"),
                        "AgentSpec group must not use skill_ prefix");
                }
            }
        }
    }
}
