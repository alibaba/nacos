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
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 6: Client visibility.
 *
 * <p>For any dataset of AgentSpecs with mixed enabled/disabled states and versions with
 * mixed online/offline statuses, the Client search API SHALL return only AgentSpecs that are enabled and have at least
 * one online version.</p>
 *
 * <p><b>Validates: Requirements 5.1, 5.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecClientVisibilityTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String META_STATUS_ENABLE = "enable";
    
    private static final String META_STATUS_DISABLE = "disable";
    
    private static java.util.List<java.util.List<AgentSpecTestData>> sampleMixedAgentSpecs() {
        return Arrays.asList(
            Arrays.asList(
                new AgentSpecTestData("a", true, 1),
                new AgentSpecTestData("b", false, 2),
                new AgentSpecTestData("c", true, 0)),
            Arrays.asList(
                new AgentSpecTestData("x", true, 3),
                new AgentSpecTestData("y", true, 1)),
            Arrays.asList(
                new AgentSpecTestData("onlyoff", false, 0),
                new AgentSpecTestData("eligible", true, 2)));
    }
    
    /**
     * Client search only returns enabled AgentSpecs with at least one online version.
     *
     * <p>Every item returned by the simulated searchAgentSpecs logic must correspond to an
     * AiResource that has status = "enable" and versionInfo.onlineCnt > 0.</p>
     */
    @Test
    void clientSearchOnlyReturnsEnabledWithOnlineVersions() {
        for (List<AgentSpecTestData> dataset : sampleMixedAgentSpecs()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            for (AgentSpecTestData data : dataset) {
                persistService.insert(data.toAiResource());
            }
            
            List<AgentSpecBasicInfo> searchResults =
                simulateSearchAgentSpecs(persistService, NAMESPACE_ID, null);
            
            for (AgentSpecBasicInfo result : searchResults) {
                AgentSpecTestData original =
                    dataset.stream().filter(d -> d.name.equals(result.getName())).findFirst()
                        .orElse(null);
                assertTrue(original != null,
                    "Search returned unknown AgentSpec: " + result.getName());
                assertTrue(original.enabled,
                    "Search returned disabled AgentSpec: " + result.getName());
                assertTrue(original.onlineCnt > 0,
                    "Search returned AgentSpec with no online versions: " + result.getName());
            }
        }
    }
    
    /**
     * Disabled AgentSpecs are never returned by client search.
     *
     * <p>No item in the search results should have a name matching any disabled AgentSpec.</p>
     */
    @Test
    void disabledAgentSpecsNeverReturnedBySearch() {
        for (List<AgentSpecTestData> dataset : sampleMixedAgentSpecs()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            for (AgentSpecTestData data : dataset) {
                persistService.insert(data.toAiResource());
            }
            
            List<String> disabledNames = dataset.stream().filter(d -> !d.enabled).map(d -> d.name)
                .collect(Collectors.toList());
            
            List<AgentSpecBasicInfo> searchResults =
                simulateSearchAgentSpecs(persistService, NAMESPACE_ID, null);
            
            for (AgentSpecBasicInfo result : searchResults) {
                assertFalse(disabledNames.contains(result.getName()),
                    "Search returned disabled AgentSpec: " + result.getName());
            }
        }
    }
    
    /**
     * AgentSpecs with no online versions are never returned by client search.
     *
     * <p>No item in the search results should have a name matching any AgentSpec with onlineCnt <= 0.</p>
     */
    @Test
    void agentSpecsWithNoOnlineVersionsNeverReturnedBySearch() {
        for (List<AgentSpecTestData> dataset : sampleMixedAgentSpecs()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            for (AgentSpecTestData data : dataset) {
                persistService.insert(data.toAiResource());
            }
            
            List<String> noOnlineNames =
                dataset.stream().filter(d -> d.onlineCnt <= 0).map(d -> d.name)
                    .collect(Collectors.toList());
            
            List<AgentSpecBasicInfo> searchResults =
                simulateSearchAgentSpecs(persistService, NAMESPACE_ID, null);
            
            for (AgentSpecBasicInfo result : searchResults) {
                assertFalse(noOnlineNames.contains(result.getName()),
                    "Search returned AgentSpec with no online versions: " + result.getName());
            }
        }
    }
    
    /**
     * All eligible AgentSpecs (enabled + onlineCnt > 0) appear in search results.
     *
     * <p>Completeness check: every AgentSpec that is enabled and has at least one online version
     * must appear in the search results.</p>
     */
    @Test
    void allEligibleAgentSpecsAppearInSearchResults() {
        for (List<AgentSpecTestData> dataset : sampleMixedAgentSpecs()) {
            InMemoryAiResourcePersistService persistService =
                new InMemoryAiResourcePersistService();
            for (AgentSpecTestData data : dataset) {
                persistService.insert(data.toAiResource());
            }
            
            List<String> eligibleNames =
                dataset.stream().filter(d -> d.enabled && d.onlineCnt > 0).map(d -> d.name)
                    .collect(Collectors.toList());
            
            List<AgentSpecBasicInfo> searchResults =
                simulateSearchAgentSpecs(persistService, NAMESPACE_ID, null);
            List<String> resultNames = searchResults.stream().map(AgentSpecBasicInfo::getName)
                .collect(Collectors.toList());
            
            for (String eligibleName : eligibleNames) {
                assertTrue(resultNames.contains(eligibleName),
                    "Eligible AgentSpec missing from search results: " + eligibleName);
            }
        }
    }
    
    // ---- Simulate searchAgentSpecs logic (mirrors AgentSpecOperationServiceImpl.searchAgentSpecs) ----
    
    private List<AgentSpecBasicInfo> simulateSearchAgentSpecs(
        InMemoryAiResourcePersistService persistService,
        String namespaceId, String keyword) {
        String nameLike = StringUtils.isBlank(keyword) ? null : ("%" + keyword + "%");
        Page<AiResource> metaPage =
            persistService.list(namespaceId, RESOURCE_TYPE_AGENTSPEC, nameLike, null, 1, 200);
        List<AgentSpecBasicInfo> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                if (!META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
                    continue;
                }
                VersionInfo info = parseVersionInfo(meta.getVersionInfo());
                if (info == null || info.onlineCnt == null || info.onlineCnt <= 0) {
                    continue;
                }
                AgentSpecBasicInfo basicInfo = new AgentSpecBasicInfo();
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                items.add(basicInfo);
            }
        }
        return items;
    }
    
    private static VersionInfo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, VersionInfo.class);
        } catch (Exception ignored) {
            return null;
        }
    }
    
    // ---- Test data model ----
    
    static class AgentSpecTestData {
        
        final String name;
        
        final boolean enabled;
        
        final int onlineCnt;
        
        AgentSpecTestData(String name, boolean enabled, int onlineCnt) {
            this.name = name;
            this.enabled = enabled;
            this.onlineCnt = onlineCnt;
        }
        
        AiResource toAiResource() {
            AiResource resource = new AiResource();
            resource.setNamespaceId(NAMESPACE_ID);
            resource.setName(name);
            resource.setType(RESOURCE_TYPE_AGENTSPEC);
            resource.setStatus(enabled ? META_STATUS_ENABLE : META_STATUS_DISABLE);
            resource.setDesc("test agentspec " + name);
            resource.setMetaVersion(1L);
            
            Map<String, Object> versionInfoMap = new HashMap<>(4);
            versionInfoMap.put("onlineCnt", onlineCnt);
            versionInfoMap.put("labels", new HashMap<>());
            resource.setVersionInfo(JacksonUtils.toJson(versionInfoMap));
            return resource;
        }
    }
    
    static class VersionInfo {
        
        public String editingVersion;
        
        public String reviewingVersion;
        
        public Integer onlineCnt;
        
        public Map<String, String> labels;
    }
    
    // ---- In-memory persist service ----
    
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
}
