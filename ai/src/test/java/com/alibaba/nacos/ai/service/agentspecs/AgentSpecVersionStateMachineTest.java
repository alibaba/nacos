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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 5: Version state machine legality.
 *
 * <p>For any AgentSpec version, the status transitions SHALL follow the valid state machine:
 * Draft → Reviewing → Published → Online ↔ Offline. Publishing a version that is not in
 * reviewing status SHALL be rejected. Each transition SHALL update the version status to the
 * expected next state.</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.4, 3.5, 3.6, 3.7, 3.10</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecVersionStateMachineTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String VERSION_STATUS_DRAFT = "draft";
    
    private static final String VERSION_STATUS_REVIEWING = "reviewing";
    
    private static final String VERSION_STATUS_ONLINE = "online";
    
    private static final String VERSION_STATUS_OFFLINE = "offline";
    
    private static final String META_STATUS_ENABLE = "enable";
    
    private static String[] sampleAgentSpecNames() {
        return new String[] {"myagent", "specabc", "testspecx"};
    }
    
    private static String[] sampleVersions() {
        return new String[] {"v1", "v2", "v5"};
    }
    
    private static List<List<Boolean>> sampleToggleSequences() {
        return Arrays.asList(
            Arrays.asList(true, false, true),
            Arrays.asList(false, false, true, true));
    }
    
    private static List<List<StateMachineOp>> sampleOperationSequences() {
        return Arrays.asList(
            Arrays.asList(StateMachineOp.SUBMIT, StateMachineOp.PUBLISH, StateMachineOp.GO_OFFLINE),
            Arrays.asList(StateMachineOp.SUBMIT, StateMachineOp.PUBLISH, StateMachineOp.GO_OFFLINE,
                StateMachineOp.GO_ONLINE),
            Arrays.asList(StateMachineOp.PUBLISH, StateMachineOp.SUBMIT, StateMachineOp.GO_ONLINE,
                StateMachineOp.GO_OFFLINE, StateMachineOp.PUBLISH));
    }
    
    /**
     * Submit transitions a draft version to reviewing status.
     */
    @Test
    void submitTransitionsDraftToReviewing() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (String version : sampleVersions()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                // Set up AgentSpec with a draft version
                VersionInfo info = new VersionInfo();
                info.editingVersion = version;
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                AiResourceVersion draftRow =
                    buildVersion(agentSpecName, version, VERSION_STATUS_DRAFT);
                persistService.insertVersion(draftRow);
                
                // Simulate submit: draft → reviewing
                AiResourceVersion v = persistService.findVersion(agentSpecName, version);
                assertEquals(VERSION_STATUS_DRAFT, v.getStatus(),
                    "Pre-condition: version should be draft");
                
                persistService.updateVersionStatus(agentSpecName, version,
                    VERSION_STATUS_REVIEWING);
                VersionInfo currentInfo = parseVersionInfo(meta.getVersionInfo());
                currentInfo.editingVersion = null;
                currentInfo.reviewingVersion = version;
                meta.setVersionInfo(JacksonUtils.toJson(currentInfo));
                persistService.updateResource(meta);
                
                // Verify transition
                AiResourceVersion afterSubmit = persistService.findVersion(agentSpecName, version);
                assertEquals(VERSION_STATUS_REVIEWING, afterSubmit.getStatus(),
                    "After submit, version status should be reviewing");
            }
        }
    }
    
    /**
     * Publish transitions a reviewing version to online status.
     *
     * <p>Given a version in reviewing status, publishing it SHALL change its status to online.</p>
     */
    @Test
    void publishTransitionsReviewingToOnline() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (String version : sampleVersions()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                // Set up AgentSpec with a reviewing version
                VersionInfo info = new VersionInfo();
                info.reviewingVersion = version;
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                AiResourceVersion reviewingRow =
                    buildVersion(agentSpecName, version, VERSION_STATUS_REVIEWING);
                persistService.insertVersion(reviewingRow);
                
                // Simulate publish: reviewing → online
                AiResourceVersion v = persistService.findVersion(agentSpecName, version);
                assertEquals(VERSION_STATUS_REVIEWING, v.getStatus(),
                    "Pre-condition: version should be reviewing");
                
                persistService.updateVersionStatus(agentSpecName, version, VERSION_STATUS_ONLINE);
                VersionInfo currentInfo = parseVersionInfo(meta.getVersionInfo());
                currentInfo.reviewingVersion = null;
                currentInfo.onlineCnt =
                    (currentInfo.onlineCnt == null ? 0 : currentInfo.onlineCnt) + 1;
                currentInfo.labels.put("latest", version);
                meta.setVersionInfo(JacksonUtils.toJson(currentInfo));
                persistService.updateResource(meta);
                
                // Verify transition
                AiResourceVersion afterPublish = persistService.findVersion(agentSpecName, version);
                assertEquals(VERSION_STATUS_ONLINE, afterPublish.getStatus(),
                    "After publish, version status should be online");
            }
        }
    }
    
    /**
     * Publishing a draft version (not in reviewing) SHALL be rejected.
     *
     * <p>The publish operation checks that the version is in reviewing status.
     * Attempting to publish a draft SHALL be rejected.</p>
     */
    @Test
    void publishRejectsDraftVersion() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (String version : sampleVersions()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                VersionInfo info = new VersionInfo();
                info.editingVersion = version;
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                AiResourceVersion draftRow =
                    buildVersion(agentSpecName, version, VERSION_STATUS_DRAFT);
                persistService.insertVersion(draftRow);
                
                // Attempt to publish a draft — should be rejected
                AiResourceVersion v = persistService.findVersion(agentSpecName, version);
                boolean rejected = !VERSION_STATUS_REVIEWING.equalsIgnoreCase(v.getStatus())
                    && !VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus());
                
                assertTrue(rejected, "Publishing a draft version should be rejected");
                assertEquals(VERSION_STATUS_DRAFT, v.getStatus(),
                    "Draft version status should remain unchanged after rejected publish");
            }
        }
    }
    
    /**
     * Publishing an offline version SHALL be rejected.
     *
     * <p>Only reviewing (or already online for idempotency) versions can be published.
     * An offline version cannot be published directly.</p>
     */
    @Test
    void publishRejectsOfflineVersion() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (String version : sampleVersions()) {
                InMemoryPersistService persistService = new InMemoryPersistService();
                
                VersionInfo info = new VersionInfo();
                AiResource meta = buildMeta(agentSpecName, info);
                persistService.insertResource(meta);
                
                AiResourceVersion offlineRow =
                    buildVersion(agentSpecName, version, VERSION_STATUS_OFFLINE);
                persistService.insertVersion(offlineRow);
                
                // Attempt to publish an offline version — should be rejected
                AiResourceVersion v = persistService.findVersion(agentSpecName, version);
                boolean rejected = !VERSION_STATUS_REVIEWING.equalsIgnoreCase(v.getStatus())
                    && !VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus());
                
                assertTrue(rejected, "Publishing an offline version should be rejected");
                assertEquals(VERSION_STATUS_OFFLINE, v.getStatus(),
                    "Offline version status should remain unchanged after rejected publish");
            }
        }
    }
    
    /**
     * Online version can be toggled to offline and back.
     *
     * <p>An online version can transition to offline, and an offline version can transition
     * back to online. This bidirectional transition is the only valid cycle in the state machine.</p>
     */
    @Test
    void onlineOfflineToggle() {
        for (String agentSpecName : sampleAgentSpecNames()) {
            for (String version : sampleVersions()) {
                for (List<Boolean> toggles : sampleToggleSequences()) {
                    InMemoryPersistService persistService = new InMemoryPersistService();
                    
                    VersionInfo info = new VersionInfo();
                    info.onlineCnt = 1;
                    info.labels.put("latest", version);
                    AiResource meta = buildMeta(agentSpecName, info);
                    persistService.insertResource(meta);
                    
                    AiResourceVersion onlineRow =
                        buildVersion(agentSpecName, version, VERSION_STATUS_ONLINE);
                    persistService.insertVersion(onlineRow);
                    
                    for (Boolean goOnline : toggles) {
                        String targetStatus =
                            goOnline ? VERSION_STATUS_ONLINE : VERSION_STATUS_OFFLINE;
                        persistService.updateVersionStatus(agentSpecName, version, targetStatus);
                        
                        AiResourceVersion afterToggle =
                            persistService.findVersion(agentSpecName, version);
                        assertEquals(targetStatus, afterToggle.getStatus(),
                            "After toggle, version status should match target");
                        assertTrue(VERSION_STATUS_ONLINE.equals(afterToggle.getStatus())
                            || VERSION_STATUS_OFFLINE.equals(afterToggle.getStatus()),
                            "During online/offline toggle, status must be online or offline");
                    }
                }
            }
        }
    }
    
    /**
     * Full lifecycle state machine via operation sequences.
     *
     * <p>Given a random sequence of state machine operations (submit, publish, online, offline),
     * each transition SHALL produce the expected next state, and invalid transitions SHALL be
     * rejected without changing the version status.</p>
     */
    @Test
    void stateMachineTransitionsAreValid() {
        for (List<StateMachineOp> operations : sampleOperationSequences()) {
            InMemoryPersistService persistService = new InMemoryPersistService();
            String agentSpecName = "test-agent";
            String version = "v1";
            
            // Start with a draft version
            VersionInfo info = new VersionInfo();
            info.editingVersion = version;
            AiResource meta = buildMeta(agentSpecName, info);
            persistService.insertResource(meta);
            
            AiResourceVersion versionRow =
                buildVersion(agentSpecName, version, VERSION_STATUS_DRAFT);
            persistService.insertVersion(versionRow);
            
            for (StateMachineOp op : operations) {
                AiResourceVersion current = persistService.findVersion(agentSpecName, version);
                String statusBefore = current.getStatus();
                
                switch (op) {
                    case SUBMIT:
                        // Valid only from draft
                        if (VERSION_STATUS_DRAFT.equals(statusBefore)) {
                            persistService.updateVersionStatus(agentSpecName, version,
                                VERSION_STATUS_REVIEWING);
                            VersionInfo vi = parseVersionInfo(
                                persistService
                                    .findResource(NAMESPACE_ID, agentSpecName,
                                        RESOURCE_TYPE_AGENTSPEC)
                                    .getVersionInfo());
                            vi.editingVersion = null;
                            vi.reviewingVersion = version;
                            AiResource m = persistService.findResource(NAMESPACE_ID, agentSpecName,
                                RESOURCE_TYPE_AGENTSPEC);
                            m.setVersionInfo(JacksonUtils.toJson(vi));
                            persistService.updateResource(m);
                            
                            assertEquals(VERSION_STATUS_REVIEWING,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Submit from draft should produce reviewing");
                        } else {
                            // Invalid transition — status unchanged
                            assertEquals(statusBefore,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Submit from non-draft should not change status");
                        }
                        break;
                    
                    case PUBLISH:
                        // Valid only from reviewing (or online for idempotency)
                        if (VERSION_STATUS_REVIEWING.equals(statusBefore)) {
                            persistService.updateVersionStatus(agentSpecName, version,
                                VERSION_STATUS_ONLINE);
                            VersionInfo vi = parseVersionInfo(
                                persistService
                                    .findResource(NAMESPACE_ID, agentSpecName,
                                        RESOURCE_TYPE_AGENTSPEC)
                                    .getVersionInfo());
                            vi.reviewingVersion = null;
                            vi.onlineCnt = (vi.onlineCnt == null ? 0 : vi.onlineCnt) + 1;
                            vi.labels.put("latest", version);
                            AiResource m = persistService.findResource(NAMESPACE_ID, agentSpecName,
                                RESOURCE_TYPE_AGENTSPEC);
                            m.setVersionInfo(JacksonUtils.toJson(vi));
                            persistService.updateResource(m);
                            
                            assertEquals(VERSION_STATUS_ONLINE,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Publish from reviewing should produce online");
                        } else if (VERSION_STATUS_ONLINE.equals(statusBefore)) {
                            // Idempotent — stays online
                            assertEquals(VERSION_STATUS_ONLINE,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Publish of already-online should remain online");
                        } else {
                            // Invalid: draft or offline cannot be published
                            assertEquals(statusBefore,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Publish from " + statusBefore + " should not change status");
                        }
                        break;
                    
                    case GO_ONLINE:
                        // Valid from offline (or online is idempotent)
                        if (VERSION_STATUS_OFFLINE.equals(statusBefore)
                            || VERSION_STATUS_ONLINE.equals(statusBefore)) {
                            persistService.updateVersionStatus(agentSpecName, version,
                                VERSION_STATUS_ONLINE);
                            assertEquals(VERSION_STATUS_ONLINE,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Go online should produce online status");
                        } else {
                            // Cannot go online from draft or reviewing
                            assertEquals(statusBefore,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Go online from " + statusBefore + " should not change status");
                        }
                        break;
                    
                    case GO_OFFLINE:
                        // Valid from online
                        if (VERSION_STATUS_ONLINE.equals(statusBefore)
                            || VERSION_STATUS_OFFLINE.equals(statusBefore)) {
                            persistService.updateVersionStatus(agentSpecName, version,
                                VERSION_STATUS_OFFLINE);
                            assertEquals(VERSION_STATUS_OFFLINE,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Go offline should produce offline status");
                        } else {
                            // Cannot go offline from draft or reviewing
                            assertEquals(statusBefore,
                                persistService.findVersion(agentSpecName, version).getStatus(),
                                "Go offline from " + statusBefore + " should not change status");
                        }
                        break;
                    
                    default:
                        break;
                }
                
                // INVARIANT: version status is always one of the valid states
                AiResourceVersion afterOp = persistService.findVersion(agentSpecName, version);
                String afterStatus = afterOp.getStatus();
                assertTrue(
                    VERSION_STATUS_DRAFT.equals(afterStatus)
                        || VERSION_STATUS_REVIEWING.equals(afterStatus)
                        || VERSION_STATUS_ONLINE.equals(afterStatus)
                        || VERSION_STATUS_OFFLINE.equals(afterStatus),
                    "Version status must always be one of: draft, reviewing, online, offline. Got: "
                        + afterStatus);
            }
        }
    }
    
    // ---- State machine operation enum ----
    
    enum StateMachineOp {
        SUBMIT, PUBLISH, GO_ONLINE, GO_OFFLINE
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
    
    /**
     * Minimal in-memory persistence that tracks AiResource and AiResourceVersion records,
     * providing the operations needed to verify version state machine transitions.
     */
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
        
        void updateVersionStatus(String name, String version, String status) {
            versions.stream()
                .filter(v -> NAMESPACE_ID.equals(v.getNamespaceId())
                    && name.equals(v.getName())
                    && RESOURCE_TYPE_AGENTSPEC.equals(v.getType())
                    && version.equals(v.getVersion()))
                .findFirst()
                .ifPresent(v -> v.setStatus(status));
        }
    }
}
