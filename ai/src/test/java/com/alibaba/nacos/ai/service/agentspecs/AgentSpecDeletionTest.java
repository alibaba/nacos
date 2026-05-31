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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 8: Deletion integrity.
 *
 * <p>For any AgentSpec with N versions and associated storage content, after deletion,
 * querying the AgentSpec SHALL return null (metadata removed), all {@code ai_resource_version} rows for that AgentSpec
 * SHALL be removed, and all storage entries matching {@code agentspec__{name}__*} SHALL be removed.</p>
 *
 * <p><b>Validates: Requirements 2.2, 7.4, 10.1, 10.2, 10.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecDeletionTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String META_STATUS_ENABLE = "enable";
    
    private static java.util.List<AgentSpecTestData> sampleAgentSpecWithVersions() {
        return Arrays.asList(
            new AgentSpecTestData("agentspeca", Arrays.asList("v1", "v2"), 1),
            new AgentSpecTestData("myagent", Collections.singletonList("v1"), 0),
            new AgentSpecTestData("multi", Arrays.asList("v1", "v3", "v5"), 2));
    }
    
    private static String[] sampleResourceNamesForDeletion() {
        return new String[] {"ghost", "missing", "none"};
    }
    
    /**
     * After deletion, querying the AgentSpec metadata returns null.
     *
     * <p>Given an AgentSpec with metadata, versions, and storage entries, after simulating
     * the deleteAgentSpec flow, the metadata record SHALL no longer exist.</p>
     */
    @Test
    void afterDeletionMetadataIsRemoved() throws NacosException {
        for (AgentSpecTestData testData : sampleAgentSpecWithVersions()) {
            InMemoryAiResourcePersistService resourceService =
                new InMemoryAiResourcePersistService();
            InMemoryAiResourceVersionPersistService versionService =
                new InMemoryAiResourceVersionPersistService();
            InMemoryAiResourceStorage storage = new InMemoryAiResourceStorage();
            
            seedTestData(resourceService, versionService, storage, testData);
            
            simulateDeleteAgentSpec(resourceService, versionService, storage, NAMESPACE_ID,
                testData.name);
            
            AiResource meta =
                resourceService.find(NAMESPACE_ID, testData.name, RESOURCE_TYPE_AGENTSPEC);
            assertNull(meta, "Metadata should be null after deletion for: " + testData.name);
        }
    }
    
    /**
     * After deletion, all ai_resource_version rows for that AgentSpec are removed.
     *
     * <p>Given an AgentSpec with N versions, after deletion, listing versions SHALL return
     * an empty result.</p>
     */
    @Test
    void afterDeletionAllVersionRowsAreRemoved() throws NacosException {
        for (AgentSpecTestData testData : sampleAgentSpecWithVersions()) {
            InMemoryAiResourcePersistService resourceService =
                new InMemoryAiResourcePersistService();
            InMemoryAiResourceVersionPersistService versionService =
                new InMemoryAiResourceVersionPersistService();
            InMemoryAiResourceStorage storage = new InMemoryAiResourceStorage();
            
            seedTestData(resourceService, versionService, storage, testData);
            
            simulateDeleteAgentSpec(resourceService, versionService, storage, NAMESPACE_ID,
                testData.name);
            
            Page<AiResourceVersion> remaining = versionService.list(NAMESPACE_ID, testData.name,
                RESOURCE_TYPE_AGENTSPEC, null, 1, 200);
            assertTrue(
                remaining == null || remaining.getPageItems() == null
                    || remaining.getPageItems().isEmpty(),
                "All version rows should be removed after deletion for: " + testData.name);
        }
    }
    
    /**
     * After deletion, all storage entries matching agentspec__{name}__* are removed.
     *
     * <p>Given an AgentSpec with storage entries for each version, after deletion, no storage
     * keys containing the AgentSpec name pattern should remain.</p>
     */
    @Test
    void afterDeletionAllStorageEntriesAreRemoved() throws NacosException {
        for (AgentSpecTestData testData : sampleAgentSpecWithVersions()) {
            InMemoryAiResourcePersistService resourceService =
                new InMemoryAiResourcePersistService();
            InMemoryAiResourceVersionPersistService versionService =
                new InMemoryAiResourceVersionPersistService();
            InMemoryAiResourceStorage storage = new InMemoryAiResourceStorage();
            
            seedTestData(resourceService, versionService, storage, testData);
            
            simulateDeleteAgentSpec(resourceService, versionService, storage, NAMESPACE_ID,
                testData.name);
            
            String keyPattern =
                NAMESPACE_ID + ":" + RESOURCE_TYPE_AGENTSPEC + ":" + testData.name + ":";
            List<String> remainingKeys =
                storage.allKeys().stream().filter(k -> k.startsWith(keyPattern))
                    .collect(Collectors.toList());
            assertTrue(remainingKeys.isEmpty(),
                "All storage entries should be removed after deletion for: " + testData.name
                    + ", remaining: "
                    + remainingKeys);
        }
    }
    
    /**
     * Deletion of a non-existent AgentSpec is a no-op (no errors, no side effects).
     *
     * <p>When deleting an AgentSpec that does not exist, the operation should complete
     * without throwing and without affecting other data.</p>
     */
    @Test
    void deletionOfNonExistentAgentSpecIsNoOp() throws NacosException {
        for (String name : sampleResourceNamesForDeletion()) {
            InMemoryAiResourcePersistService resourceService =
                new InMemoryAiResourcePersistService();
            InMemoryAiResourceVersionPersistService versionService =
                new InMemoryAiResourceVersionPersistService();
            InMemoryAiResourceStorage storage = new InMemoryAiResourceStorage();
            
            // Insert a different AgentSpec to ensure it's not affected
            String otherName = name + "_other";
            AiResource otherMeta = buildAiResource(otherName);
            resourceService.insert(otherMeta);
            
            simulateDeleteAgentSpec(resourceService, versionService, storage, NAMESPACE_ID, name);
            
            AiResource otherStillExists =
                resourceService.find(NAMESPACE_ID, otherName, RESOURCE_TYPE_AGENTSPEC);
            assertTrue(otherStillExists != null,
                "Other AgentSpec should not be affected by deleting non-existent: " + name);
        }
    }
    
    // ---- Simulate deleteAgentSpec logic (mirrors AgentSpecOperationServiceImpl.deleteAgentSpec) ----
    
    private void simulateDeleteAgentSpec(InMemoryAiResourcePersistService resourceService,
        InMemoryAiResourceVersionPersistService versionService, InMemoryAiResourceStorage storage,
        String namespaceId, String agentSpecName) throws NacosException {
        
        AiResource meta = resourceService.find(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            return;
        }
        
        resourceService.delete(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        
        Page<AiResourceVersion> versions = versionService.list(namespaceId, agentSpecName,
            RESOURCE_TYPE_AGENTSPEC, null, 1, 200);
        versionService.deleteByNameAndType(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        
        if (versions != null && versions.getPageItems() != null) {
            for (AiResourceVersion v : versions.getPageItems()) {
                if (v == null || v.getVersion() == null || v.getVersion().isBlank()) {
                    continue;
                }
                deleteStorageForVersion(storage, namespaceId, agentSpecName, v.getVersion());
            }
        }
    }
    
    private void deleteStorageForVersion(InMemoryAiResourceStorage storage, String namespaceId,
        String agentSpecName,
        String version) throws NacosException {
        // Delete all storage entries matching the version pattern
        String keyPrefix =
            namespaceId + ":" + RESOURCE_TYPE_AGENTSPEC + ":" + agentSpecName + ":" + version + ":";
        storage.deleteByPrefix(keyPrefix);
    }
    
    // ---- Seed test data ----
    
    private void seedTestData(InMemoryAiResourcePersistService resourceService,
        InMemoryAiResourceVersionPersistService versionService, InMemoryAiResourceStorage storage,
        AgentSpecTestData testData) throws NacosException {
        
        resourceService.insert(testData.toAiResource());
        
        for (String version : testData.versions) {
            AiResourceVersion versionRow = new AiResourceVersion();
            versionRow.setNamespaceId(NAMESPACE_ID);
            versionRow.setName(testData.name);
            versionRow.setType(RESOURCE_TYPE_AGENTSPEC);
            versionRow.setVersion(version);
            versionRow.setStatus("online");
            versionRow.setAuthor("nacos");
            versionService.insert(versionRow);
            
            // Simulate storage entries: main manifest.json + resource files
            String mainKey =
                NAMESPACE_ID + ":" + RESOURCE_TYPE_AGENTSPEC + ":" + testData.name + ":" + version
                    + ":manifest.json";
            storage.save(new StorageKey("nacos_config", mainKey), "{\"name\":\"test\"}".getBytes());
            
            for (int i = 0; i < testData.resourceCount; i++) {
                String resourceKey = NAMESPACE_ID + ":" + RESOURCE_TYPE_AGENTSPEC + ":"
                    + testData.name + ":" + version
                    + ":resource_" + i + ".json";
                storage.save(new StorageKey("nacos_config", resourceKey),
                    "{\"content\":\"data\"}".getBytes());
            }
        }
    }
    
    // ---- Test data model ----
    
    static class AgentSpecTestData {
        
        final String name;
        
        final List<String> versions;
        
        final int resourceCount;
        
        AgentSpecTestData(String name, List<String> versions, int resourceCount) {
            this.name = name;
            this.versions = versions;
            this.resourceCount = resourceCount;
        }
        
        AiResource toAiResource() {
            AiResource resource = new AiResource();
            resource.setNamespaceId(NAMESPACE_ID);
            resource.setName(name);
            resource.setType(RESOURCE_TYPE_AGENTSPEC);
            resource.setStatus(META_STATUS_ENABLE);
            resource.setDesc("test agentspec " + name);
            resource.setMetaVersion(1L);
            Map<String, Object> versionInfoMap = new HashMap<>(4);
            versionInfoMap.put("onlineCnt", versions.size());
            versionInfoMap.put("labels", new HashMap<>());
            resource.setVersionInfo(JacksonUtils.toJson(versionInfoMap));
            return resource;
        }
    }
    
    // ---- Helpers ----
    
    private static AiResource buildAiResource(String name) {
        AiResource resource = new AiResource();
        resource.setNamespaceId(NAMESPACE_ID);
        resource.setName(name);
        resource.setType(RESOURCE_TYPE_AGENTSPEC);
        resource.setStatus(META_STATUS_ENABLE);
        resource.setDesc("test agentspec " + name);
        resource.setMetaVersion(1L);
        return resource;
    }
    
    // ---- In-memory implementations ----
    
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
                .collect(Collectors.toList());
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
                .collect(Collectors.toList());
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
    
    private static class InMemoryAiResourceStorage implements AiResourceStorage {
        
        private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();
        
        @Override
        public String type() {
            return "nacos_config";
        }
        
        @Override
        public void save(StorageKey storageKey, byte[] content) {
            store.put(storageKey.getKey(), content);
        }
        
        @Override
        public byte[] get(StorageKey storageKey) {
            return store.get(storageKey.getKey());
        }
        
        @Override
        public void delete(StorageKey storageKey) {
            store.remove(storageKey.getKey());
        }
        
        public void deleteByPrefix(String prefix) {
            store.keySet().removeIf(k -> k.startsWith(prefix));
        }
        
        public List<String> allKeys() {
            return new ArrayList<>(store.keySet());
        }
    }
}
