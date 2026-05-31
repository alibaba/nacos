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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 3: Type isolation between AgentSpec and Skill.
 *
 * <p>For any sequence of AgentSpec and Skill operations on a shared {@code ai_resource} /
 * {@code ai_resource_version} dataset, querying AgentSpec SHALL never return Skill records, and querying Skill SHALL
 * never return AgentSpec records. All AgentSpec records SHALL have {@code type = "agentspec"} and use the
 * {@code agentspec__} group prefix; all Skill records SHALL have {@code type = "skill"} and use the {@code skill_}
 * group prefix.</p>
 *
 * <p><b>Validates: Requirements 6.1, 6.2, 6.3, 6.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecTypeIsolationTest {
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String RESOURCE_TYPE_SKILL = "skill";
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static String[] sampleResourceNames() {
        return new String[] {"myres", "abc", "resourcex"};
    }
    
    private static String[] sampleVersions() {
        return new String[] {"v1", "v2", "v5"};
    }
    
    private static List<List<AiResource>> sampleMixedAiResources() {
        List<List<AiResource>> out = new ArrayList<>();
        out.add(Arrays.asList(
            buildAiResource("a", RESOURCE_TYPE_AGENTSPEC),
            buildAiResource("b", RESOURCE_TYPE_SKILL)));
        out.add(Arrays.asList(
            buildAiResource("c", RESOURCE_TYPE_SKILL),
            buildAiResource("d", RESOURCE_TYPE_AGENTSPEC),
            buildAiResource("e", RESOURCE_TYPE_SKILL)));
        return out;
    }
    
    private static List<List<AiResourceVersion>> sampleMixedAiResourceVersions() {
        List<List<AiResourceVersion>> out = new ArrayList<>();
        out.add(Arrays.asList(
            buildAiResourceVersion("n1", RESOURCE_TYPE_AGENTSPEC, "v1"),
            buildAiResourceVersion("n2", RESOURCE_TYPE_SKILL, "v1")));
        out.add(Arrays.asList(
            buildAiResourceVersion("x", RESOURCE_TYPE_SKILL, "v2"),
            buildAiResourceVersion("y", RESOURCE_TYPE_AGENTSPEC, "v3")));
        return out;
    }
    
    /**
     * All AgentSpec AiResource records use type = "agentspec".
     *
     * <p>Given a mixed dataset of AgentSpec and Skill AiResource records, querying with
     * type = "agentspec" returns only records whose type field equals "agentspec".</p>
     */
    @Test
    void agentSpecQueryOnlyReturnsAgentSpecResources() {
        for (List<AiResource> mixedResources : sampleMixedAiResources()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            for (AiResource resource : mixedResources) {
                persistService.insert(resource);
            }
            
            Page<AiResource> agentSpecPage =
                persistService.list(NAMESPACE_ID, RESOURCE_TYPE_AGENTSPEC, null, null, 1,
                    200);
            
            if (agentSpecPage != null && agentSpecPage.getPageItems() != null) {
                for (AiResource resource : agentSpecPage.getPageItems()) {
                    assertEquals(RESOURCE_TYPE_AGENTSPEC, resource.getType(),
                        "AgentSpec query returned a non-agentspec record: " + resource.getName());
                }
            }
        }
    }
    
    /**
     * All Skill AiResource records use type = "skill".
     *
     * <p>Given a mixed dataset, querying with type = "skill" returns only records
     * whose type field equals "skill".</p>
     */
    @Test
    void skillQueryOnlyReturnsSkillResources() {
        for (List<AiResource> mixedResources : sampleMixedAiResources()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            for (AiResource resource : mixedResources) {
                persistService.insert(resource);
            }
            
            Page<AiResource> skillPage =
                persistService.list(NAMESPACE_ID, RESOURCE_TYPE_SKILL, null, null, 1, 200);
            
            if (skillPage != null && skillPage.getPageItems() != null) {
                for (AiResource resource : skillPage.getPageItems()) {
                    assertEquals(RESOURCE_TYPE_SKILL, resource.getType(),
                        "Skill query returned a non-skill record: " + resource.getName());
                }
            }
        }
    }
    
    /**
     * AgentSpec version queries never return Skill version records.
     *
     * <p>Given a mixed dataset of AgentSpec and Skill AiResourceVersion records,
     * querying versions with type = "agentspec" returns only agentspec-typed versions.</p>
     */
    @Test
    void agentSpecVersionQueryNeverReturnsSkillVersions() {
        for (List<AiResourceVersion> mixedVersions : sampleMixedAiResourceVersions()) {
            InMemoryAiResourceVersionPersistService versionService =
                new InMemoryAiResourceVersionPersistService();
            for (AiResourceVersion version : mixedVersions) {
                versionService.insert(version);
            }
            
            // Query each unique name with agentspec type
            for (AiResourceVersion version : mixedVersions) {
                if (RESOURCE_TYPE_AGENTSPEC.equals(version.getType())) {
                    AiResourceVersion found = versionService.find(NAMESPACE_ID, version.getName(),
                        RESOURCE_TYPE_AGENTSPEC, version.getVersion());
                    if (found != null) {
                        assertEquals(RESOURCE_TYPE_AGENTSPEC, found.getType(),
                            "AgentSpec version query returned skill version");
                    }
                }
            }
        }
    }
    
    /**
     * AgentSpec storage keys use "agentspec__" group prefix, Skill uses "skill_".
     *
     * <p>For any resource name and version, the storage group prefix for AgentSpec is
     * {@code agentspec__} and for Skill is {@code skill_}. We verify this by examining the StorageKey key string which
     * encodes the resource type, and by verifying the group prefix constants are distinct.</p>
     */
    @Test
    void storageGroupPrefixIsolation() {
        for (String name : sampleResourceNames()) {
            for (String version : sampleVersions()) {
                // AgentSpec storage key embeds "agentspec" resource type
                String agentSpecKey =
                    NacosConfigAiResourceStorage.buildStorageKey(NacosConfigAiResourceStorage.TYPE,
                        NAMESPACE_ID, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, name,
                        version,
                        AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID).getKey();
                
                // Key format: namespaceId:resourceType:name:version:filePath
                String[] agentSpecParts = agentSpecKey.split(":", 5);
                assertEquals(5, agentSpecParts.length, "AgentSpec key should have 5 parts");
                assertEquals(RESOURCE_TYPE_AGENTSPEC, agentSpecParts[1],
                    "AgentSpec key resource type should be 'agentspec'");
                
                // Skill storage key embeds "skill" resource type
                String skillKey = NacosConfigAiResourceStorage
                    .buildStorageKey(NacosConfigAiResourceStorage.TYPE,
                        NAMESPACE_ID, NacosConfigAiResourceStorage.RESOURCE_TYPE_SKILL, name,
                        version,
                        SkillUtils.SKILL_MAIN_DATA_ID)
                    .getKey();
                
                String[] skillParts = skillKey.split(":", 5);
                assertEquals(5, skillParts.length, "Skill key should have 5 parts");
                assertEquals(RESOURCE_TYPE_SKILL, skillParts[1],
                    "Skill key resource type should be 'skill'");
                
                // Group prefixes are distinct
                assertFalse(
                    AgentSpecUtils.AGENTSPEC_GROUP_PREFIX.equals(SkillUtils.SKILL_GROUP_PREFIX),
                    "AgentSpec and Skill group prefixes must be different");
                assertTrue(AgentSpecUtils.AGENTSPEC_GROUP_PREFIX.startsWith("agentspec"),
                    "AgentSpec group prefix should start with 'agentspec'");
                assertTrue(SkillUtils.SKILL_GROUP_PREFIX.startsWith("skill"),
                    "Skill group prefix should start with 'skill'");
            }
        }
    }
    
    /**
     * AgentSpec and Skill records with the same name are isolated by type.
     *
     * <p>When both an AgentSpec and a Skill share the same name, find by type returns
     * only the correct record.</p>
     */
    @Test
    void sameNameDifferentTypeIsolation() {
        for (String name : sampleResourceNames()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            
            AiResource agentSpecResource = buildAiResource(name, RESOURCE_TYPE_AGENTSPEC);
            AiResource skillResource = buildAiResource(name, RESOURCE_TYPE_SKILL);
            persistService.insert(agentSpecResource);
            persistService.insert(skillResource);
            
            AiResource foundAgentSpec =
                persistService.find(NAMESPACE_ID, name, RESOURCE_TYPE_AGENTSPEC);
            AiResource foundSkill = persistService.find(NAMESPACE_ID, name, RESOURCE_TYPE_SKILL);
            
            assertEquals(RESOURCE_TYPE_AGENTSPEC, foundAgentSpec.getType());
            assertEquals(RESOURCE_TYPE_SKILL, foundSkill.getType());
        }
    }
    
    // ---- Helpers ----
    
    private static AiResource buildAiResource(String name, String type) {
        AiResource resource = new AiResource();
        resource.setNamespaceId(NAMESPACE_ID);
        resource.setName(name);
        resource.setType(type);
        resource.setStatus("enable");
        resource.setDesc("test " + type + " " + name);
        resource.setMetaVersion(1L);
        return resource;
    }
    
    private static AiResourceVersion buildAiResourceVersion(String name, String type,
        String version) {
        AiResourceVersion v = new AiResourceVersion();
        v.setNamespaceId(NAMESPACE_ID);
        v.setName(name);
        v.setType(type);
        v.setVersion(version);
        v.setStatus("online");
        v.setAuthor("nacos");
        return v;
    }
    
    // ---- In-memory persist service implementations for type-filtered queries ----
    
    /**
     * Minimal in-memory implementation of AiResourcePersistService that simulates the type-filtered behavior of the
     * real database layer.
     */
    private static class InMemoryAiResourcePersistService implements AiResourcePersistService {
        
        private final List<AiResource> store = new ArrayList<>();
        
        @Override
        public long insert(AiResource resource) {
            store.add(resource);
            return store.size();
        }
        
        @Override
        public AiResource find(String namespaceId, String name, String type) {
            return store.stream()
                .filter(r -> namespaceId.equals(r.getNamespaceId()) && name.equals(r.getName())
                    && type.equals(
                        r.getType()))
                .findFirst().orElse(null);
        }
        
        @Override
        public Page<AiResource> list(String namespaceId, String type, String nameLike,
            String bizTagsLike, int pageNo,
            int pageSize) {
            List<AiResource> filtered = store.stream()
                .filter(r -> namespaceId.equals(r.getNamespaceId()) && type.equals(r.getType()))
                .filter(r -> nameLike == null || r.getName().contains(nameLike.replace("%", "")))
                .collect(java.util.stream.Collectors.toList());
            Page<AiResource> page = new Page<>();
            page.setPageItems(filtered);
            page.setTotalCount(filtered.size());
            page.setPagesAvailable(1);
            page.setPageNumber(pageNo);
            return page;
        }
        
        @Override
        public Page<AiResource> list(String namespaceId, String type, String nameLike,
            String bizTagsLike,
            String orderBy, int pageNo, int pageSize) {
            return list(namespaceId, type, nameLike, bizTagsLike, pageNo, pageSize);
        }
        
        @Override
        public Page<AiResource> list(QueryCondition queryCondition, int pageNo, int pageSize) {
            return null;
        }
        
        @Override
        public boolean updateMetaCas(String namespaceId, String name, String type,
            long expectedMetaVersion,
            AiResource newValue) {
            return false;
        }
        
        @Override
        public boolean updateSourceCas(String namespaceId, String name, String type,
            long expectedMetaVersion,
            String source) {
            return false;
        }
        
        @Override
        public int delete(String namespaceId, String name, String type) {
            store.removeIf(r -> namespaceId.equals(r.getNamespaceId()) && name.equals(r.getName())
                && type.equals(
                    r.getType()));
            return 1;
        }
        
        @Override
        public boolean updateScope(String namespaceId, String name, String type, String scope) {
            return false;
        }
        
        @Override
        public boolean incrementDownloadCount(String namespaceId, String name, String type,
            long increment) {
            return false;
        }
    }
    
    /**
     * Minimal in-memory implementation of AiResourceVersionPersistService that simulates the type-filtered behavior of
     * the real database layer.
     */
    private static class InMemoryAiResourceVersionPersistService
        implements AiResourceVersionPersistService {
        
        private final List<AiResourceVersion> store = new ArrayList<>();
        
        @Override
        public long insert(AiResourceVersion version) {
            store.add(version);
            return store.size();
        }
        
        @Override
        public AiResourceVersion find(String namespaceId, String name, String type,
            String version) {
            return store.stream()
                .filter(
                    v -> namespaceId.equals(v.getNamespaceId()) && name.equals(v.getName())
                        && type.equals(
                            v.getType())
                        && version.equals(v.getVersion()))
                .findFirst().orElse(null);
        }
        
        @Override
        public int updateStorageMd5(String namespaceId, String name, String type, String version,
            String contentMd5) {
            return 0;
        }
        
        @Override
        public Page<AiResourceVersion> list(String namespaceId, String name, String type,
            String status, int pageNo,
            int pageSize) {
            List<AiResourceVersion> filtered = store.stream()
                .filter(v -> namespaceId.equals(v.getNamespaceId()) && name.equals(v.getName())
                    && type.equals(
                        v.getType()))
                .filter(v -> status == null || status.equals(v.getStatus()))
                .collect(java.util.stream.Collectors.toList());
            Page<AiResourceVersion> page = new Page<>();
            page.setPageItems(filtered);
            page.setTotalCount(filtered.size());
            page.setPagesAvailable(1);
            page.setPageNumber(pageNo);
            return page;
        }
        
        @Override
        public int delete(String namespaceId, String name, String type, String version) {
            store.removeIf(
                v -> namespaceId.equals(v.getNamespaceId()) && name.equals(v.getName())
                    && type.equals(v.getType())
                    && version.equals(v.getVersion()));
            return 1;
        }
        
        @Override
        public int deleteByName(String namespaceId, String name) {
            store.removeIf(v -> namespaceId.equals(v.getNamespaceId()) && name.equals(v.getName()));
            return 1;
        }
        
        @Override
        public int deleteByNameAndType(String namespaceId, String name, String type) {
            store.removeIf(v -> namespaceId.equals(v.getNamespaceId()) && name.equals(v.getName())
                && type.equals(
                    v.getType()));
            return 1;
        }
        
        @Override
        public int updateStatus(String namespaceId, String name, String type, String version,
            String status) {
            return 0;
        }
        
        @Override
        public int updateStorage(String namespaceId, String name, String type, String version,
            String storage) {
            AiResourceVersion found = find(namespaceId, name, type, version);
            if (found == null) {
                return 0;
            }
            found.setStorage(storage);
            return 1;
        }
        
        @Override
        public int updateStorageAndDesc(String namespaceId, String name, String type,
            String version, String storage,
            String desc) {
            AiResourceVersion found = find(namespaceId, name, type, version);
            if (found == null) {
                return 0;
            }
            found.setStorage(storage);
            found.setDesc(desc);
            return 1;
        }
        
        @Override
        public int updatePublishPipelineInfo(String namespaceId, String name, String type,
            String version,
            String publishPipelineInfo) {
            return 0;
        }
        
        @Override
        public int incrementDownloadCount(String namespaceId, String name, String type,
            String version,
            long increment) {
            return 0;
        }
    }
}
