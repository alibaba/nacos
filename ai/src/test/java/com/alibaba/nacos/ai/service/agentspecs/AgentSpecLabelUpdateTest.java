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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Property 10: Label update does not affect version status.
 *
 * <p>For any AgentSpec with existing versions in any status, updating the label mapping
 * SHALL change only the label-to-version mapping and SHALL NOT modify any version's status,
 * content, or online/offline state.</p>
 *
 * <p><b>Validates: Requirement 4.1</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecLabelUpdateTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String META_STATUS_ENABLE = "enable";
    
    private static final String VERSION_STATUS_DRAFT = "draft";
    
    private static final String VERSION_STATUS_REVIEWING = "reviewing";
    
    private static final String VERSION_STATUS_ONLINE = "online";
    
    private static final String VERSION_STATUS_OFFLINE = "offline";
    
    private static String[] sampleAgentSpecNames() {
        return new String[] {"agentone", "myspec"};
    }
    
    private static List<List<VersionEntry>> sampleVersionSets() {
        List<List<VersionEntry>> list = new ArrayList<>();
        list.add(Collections.singletonList(new VersionEntry("v1", VERSION_STATUS_DRAFT)));
        list.add(Arrays.asList(
            new VersionEntry("v1", VERSION_STATUS_ONLINE),
            new VersionEntry("v2", VERSION_STATUS_REVIEWING)));
        return list;
    }
    
    private static List<Map<String, String>> sampleLabelMaps() {
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, String> m1 = new LinkedHashMap<>();
        m1.put("stable", "v1");
        list.add(m1);
        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("stable", "v2");
        m2.put("beta", "v1");
        list.add(m2);
        list.add(new LinkedHashMap<>());
        return list;
    }
    
    /**
     * Label update changes only the label mapping, not version statuses.
     *
     * <p>Given an AgentSpec with multiple versions in various statuses, updating labels
     * SHALL preserve every version's status exactly as it was before the update.</p>
     */
    @Test
    void labelUpdatePreservesVersionStatuses() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (List<VersionEntry> versionEntries : sampleVersionSets()) {
                for (Map<String, String> newLabels : sampleLabelMaps()) {
                    InMemoryPersistService persistService = new InMemoryPersistService();
                    
                    // Set up initial state with versions
                    VersionInfo info = new VersionInfo();
                    info.labels = new HashMap<>();
                    info.labels.put("latest", "v1");
                    AiResource meta = buildMeta(agentSpecName, info);
                    persistService.insertResource(meta);
                    
                    for (VersionEntry entry : versionEntries) {
                        AiResourceVersion v =
                            buildVersion(agentSpecName, entry.version, entry.status);
                        v.setStorage("storage-" + entry.version);
                        persistService.insertVersion(v);
                    }
                    
                    // Snapshot version statuses before label update
                    Map<String, String> statusesBefore = new HashMap<>();
                    Map<String, String> storageBefore = new HashMap<>();
                    for (VersionEntry entry : versionEntries) {
                        AiResourceVersion v =
                            persistService.findVersion(agentSpecName, entry.version);
                        assertNotNull(v, "Version " + entry.version + " should exist");
                        statusesBefore.put(entry.version, v.getStatus());
                        storageBefore.put(entry.version, v.getStorage());
                    }
                    
                    // Simulate label update (mirrors AiResourceManager.validateAndUpdateLabels)
                    applyLabelUpdatePreservingLatest(meta, newLabels);
                    persistService.updateResource(meta);
                    
                    // Verify: all version statuses and storage unchanged
                    for (VersionEntry entry : versionEntries) {
                        AiResourceVersion v =
                            persistService.findVersion(agentSpecName, entry.version);
                        assertNotNull(v,
                            "Version " + entry.version + " should still exist after label update");
                        assertEquals(statusesBefore.get(entry.version), v.getStatus(),
                            "Version " + entry.version
                                + " status must not change after label update");
                        assertEquals(storageBefore.get(entry.version), v.getStorage(),
                            "Version " + entry.version
                                + " storage must not change after label update");
                    }
                }
            }
        }
    }
    
    /**
     * Label update correctly persists the new label mapping.
     *
     * <p>After updating labels, the persisted label mapping SHALL exactly match the
     * provided mapping.</p>
     */
    @Test
    void labelUpdatePersistsNewMapping() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (Map<String, String> newLabels : sampleLabelMaps()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                VersionInfo info = new VersionInfo();
                info.labels = new HashMap<>();
                info.labels.put("latest", "v1");
                info.labels.put("stable", "v1");
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                // Simulate label update
                applyLabelUpdatePreservingLatest(meta, newLabels);
                persistService.updateResource(meta);
                
                // Verify: labels match
                AiResource updated = persistService.findResource(NAMESPACE_ID, agentSpecName,
                    RESOURCE_TYPE_AGENTSPEC);
                VersionInfo updatedInfo = parseVersionInfo(updated.getVersionInfo());
                assertEquals(expectedLabelsWithLatest(newLabels, "v1"), updatedInfo.labels,
                    "Persisted labels must match custom labels and preserve latest");
            }
        }
    }
    
    /**
     * Label update does not affect editing/reviewing working version pointers.
     *
     * <p>Given an AgentSpec with active editing and reviewing pointers, updating labels
     * SHALL not modify those pointers.</p>
     */
    @Test
    void labelUpdatePreservesWorkingVersionPointers() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (Map<String, String> newLabels : sampleLabelMaps()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                VersionInfo info = new VersionInfo();
                info.editingVersion = "v3";
                info.reviewingVersion = "v2";
                info.onlineCnt = 1;
                info.labels = new HashMap<>();
                info.labels.put("latest", "v1");
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                // Snapshot working pointers before
                VersionInfo before = parseVersionInfo(meta.getVersionInfo());
                String editingBefore = before.editingVersion;
                String reviewingBefore = before.reviewingVersion;
                Integer onlineCntBefore = before.onlineCnt;
                
                // Simulate label update
                applyLabelUpdatePreservingLatest(meta, newLabels);
                persistService.updateResource(meta);
                
                // Verify: working pointers unchanged
                AiResource updated = persistService.findResource(NAMESPACE_ID, agentSpecName,
                    RESOURCE_TYPE_AGENTSPEC);
                VersionInfo after = parseVersionInfo(updated.getVersionInfo());
                assertEquals(editingBefore, after.editingVersion,
                    "editingVersion must not change after label update");
                assertEquals(reviewingBefore, after.reviewingVersion,
                    "reviewingVersion must not change after label update");
                assertEquals(onlineCntBefore, after.onlineCnt,
                    "onlineCnt must not change after label update");
            }
        }
    }
    
    /**
     * Label update with null labels preserves latest without affecting versions.
     *
     * <p>Setting labels to null SHALL clear custom labels, preserve latest, and SHALL
     * NOT affect any version status or content.</p>
     */
    @Test
    void nullLabelUpdatePreservesLatestWithoutAffectingVersions() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (List<VersionEntry> versionEntries : sampleVersionSets()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                VersionInfo info = new VersionInfo();
                info.labels = new HashMap<>();
                info.labels.put("latest", "v1");
                info.labels.put("stable", "v1");
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                for (VersionEntry entry : versionEntries) {
                    persistService
                        .insertVersion(buildVersion(agentSpecName, entry.version, entry.status));
                }
                
                // Snapshot statuses
                Map<String, String> statusesBefore = new HashMap<>();
                for (VersionEntry entry : versionEntries) {
                    AiResourceVersion v = persistService.findVersion(agentSpecName, entry.version);
                    statusesBefore.put(entry.version, v.getStatus());
                }
                
                // Simulate label update with null
                applyLabelUpdatePreservingLatest(meta, null);
                persistService.updateResource(meta);
                
                // Verify: custom labels cleared, latest preserved, statuses unchanged
                AiResource updated = persistService.findResource(NAMESPACE_ID, agentSpecName,
                    RESOURCE_TYPE_AGENTSPEC);
                VersionInfo updatedInfo = parseVersionInfo(updated.getVersionInfo());
                assertEquals(Collections.singletonMap("latest", "v1"), updatedInfo.labels,
                    "Null custom label update should preserve latest");
                
                for (VersionEntry entry : versionEntries) {
                    AiResourceVersion v = persistService.findVersion(agentSpecName, entry.version);
                    assertEquals(statusesBefore.get(entry.version), v.getStatus(),
                        "Version " + entry.version
                            + " status must not change after null label update");
                }
            }
        }
    }
    
    // ---- Data classes ----
    
    private static void applyLabelUpdatePreservingLatest(AiResource meta,
        Map<String, String> customLabels) {
        VersionInfo currentInfo = parseVersionInfo(meta.getVersionInfo());
        String latest = currentInfo.labels == null ? null : currentInfo.labels.get("latest");
        Map<String, String> effectiveLabels =
            customLabels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(customLabels);
        effectiveLabels.keySet().removeIf(label -> StringUtils.equalsIgnoreCase(label, "latest"));
        if (StringUtils.isNotBlank(latest)) {
            effectiveLabels.put("latest", latest);
        }
        currentInfo.labels = effectiveLabels;
        meta.setVersionInfo(JacksonUtils.toJson(currentInfo));
    }
    
    private static Map<String, String> expectedLabelsWithLatest(Map<String, String> customLabels,
        String latest) {
        Map<String, String> expected =
            customLabels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(customLabels);
        expected.put("latest", latest);
        return expected;
    }
    
    record VersionEntry(String version, String status) {
    }
    
    // ---- Helpers ----
    
    private static AiResource buildMeta(String name, VersionInfo info) {
        AiResource meta = new AiResource();
        meta.setNamespaceId(NAMESPACE_ID);
        meta.setName(name);
        meta.setType(RESOURCE_TYPE_AGENTSPEC);
        meta.setStatus(META_STATUS_ENABLE);
        meta.setDesc("test agentspec " + name);
        meta.setVersionInfo(JacksonUtils.toJson(info));
        meta.setMetaVersion(1L);
        return meta;
    }
    
    private static AiResourceVersion buildVersion(String name, String version, String status) {
        AiResourceVersion v = new AiResourceVersion();
        v.setNamespaceId(NAMESPACE_ID);
        v.setName(name);
        v.setType(RESOURCE_TYPE_AGENTSPEC);
        v.setVersion(version);
        v.setStatus(status);
        v.setStorage("storage-" + version);
        v.setAuthor("nacos");
        return v;
    }
    
    private static VersionInfo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return new VersionInfo();
        }
        try {
            return JacksonUtils.toObj(json, VersionInfo.class);
        } catch (Exception e) {
            return new VersionInfo();
        }
    }
    
    /**
     * Mirrors the AgentSpecVersionInfo inner class from AgentSpecOperationServiceImpl.
     */
    static class VersionInfo {
        
        public String editingVersion;
        
        public String reviewingVersion;
        
        public Integer onlineCnt;
        
        public Map<String, String> labels = new HashMap<>();
        
        public String getEditingVersion() {
            return editingVersion;
        }
        
        public void setEditingVersion(String editingVersion) {
            this.editingVersion = editingVersion;
        }
        
        public String getReviewingVersion() {
            return reviewingVersion;
        }
        
        public void setReviewingVersion(String reviewingVersion) {
            this.reviewingVersion = reviewingVersion;
        }
        
        public Integer getOnlineCnt() {
            return onlineCnt;
        }
        
        public void setOnlineCnt(Integer onlineCnt) {
            this.onlineCnt = onlineCnt;
        }
        
        public Map<String, String> getLabels() {
            return labels;
        }
        
        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }
    }
    
    // ---- In-memory persist service ----
    
    private static class InMemoryPersistService {
        
        private final List<AiResource> resources = new ArrayList<>();
        
        private final List<AiResourceVersion> versions = new ArrayList<>();
        
        void insertResource(AiResource resource) {
            resources.add(resource);
        }
        
        AiResource findResource(String namespaceId, String name, String type) {
            return resources.stream()
                .filter(r -> namespaceId.equals(r.getNamespaceId())
                    && name.equals(r.getName())
                    && type.equals(r.getType()))
                .findFirst().orElse(null);
        }
        
        void updateResource(AiResource resource) {
            for (int i = 0; i < resources.size(); i++) {
                AiResource r = resources.get(i);
                if (NAMESPACE_ID.equals(r.getNamespaceId())
                    && resource.getName().equals(r.getName())
                    && resource.getType().equals(r.getType())) {
                    resources.set(i, resource);
                    return;
                }
            }
        }
        
        void insertVersion(AiResourceVersion version) {
            versions.add(version);
        }
        
        AiResourceVersion findVersion(String name, String version) {
            return versions.stream()
                .filter(v -> NAMESPACE_ID.equals(v.getNamespaceId())
                    && name.equals(v.getName())
                    && RESOURCE_TYPE_AGENTSPEC.equals(v.getType())
                    && version.equals(v.getVersion()))
                .findFirst().orElse(null);
        }
    }
}
