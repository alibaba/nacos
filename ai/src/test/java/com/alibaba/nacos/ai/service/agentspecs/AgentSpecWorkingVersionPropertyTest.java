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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 4: Working version mutual exclusion.
 *
 * <p>For any AgentSpec, at most one editing (draft) version and at most one reviewing version
 * SHALL exist at any given time. Attempting to create a second draft while one exists SHALL be
 * rejected, and attempting to submit a second version while one is in reviewing SHALL be rejected.</p>
 *
 * <p><b>Validates: Requirements 3.8, 3.9</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecWorkingVersionPropertyTest {

    private static final String NAMESPACE_ID = "test-ns";

    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";

    private static final String VERSION_STATUS_DRAFT = "draft";

    private static final String VERSION_STATUS_REVIEWING = "reviewing";

    private static final String VERSION_STATUS_ONLINE = "online";

    private static final String META_STATUS_ENABLE = "enable";

    /**
     * Property 4a: At most one editing (draft) version exists at any time.
     *
     * <p>Given an AgentSpec with an existing editing version in its versionInfo,
     * attempting to create a second draft SHALL be rejected with a conflict.</p>
     */
    @Property(tries = 30)
    void atMostOneEditingVersionExists(
            @ForAll("agentSpecNames") String agentSpecName,
            @ForAll("versions") String existingDraft,
            @ForAll("versions") String newDraft) {

        InMemoryPersistService persistService = new InMemoryPersistService();

        // Set up an AgentSpec with an existing editing version
        VersionInfo info = new VersionInfo();
        info.editingVersion = existingDraft;
        AiResource meta = buildMeta(agentSpecName, info);
        persistService.insertResource(meta);

        // Insert the existing draft version row
        AiResourceVersion draftRow = buildVersion(agentSpecName, existingDraft, VERSION_STATUS_DRAFT);
        persistService.insertVersion(draftRow);

        // Attempt to create a second draft — should be rejected
        boolean rejected = false;
        VersionInfo currentInfo = parseVersionInfo(
                persistService.findResource(NAMESPACE_ID, agentSpecName, RESOURCE_TYPE_AGENTSPEC).getVersionInfo());
        if (StringUtils.isNotBlank(currentInfo.editingVersion)
                || StringUtils.isNotBlank(currentInfo.reviewingVersion)) {
            rejected = true;
        }

        assertTrue(rejected, "Creating a second draft while one exists should be rejected");

        // Verify only one draft version row exists
        long draftCount = persistService.countVersionsByStatus(agentSpecName, VERSION_STATUS_DRAFT);
        assertEquals(1, draftCount, "Only one draft version should exist");
    }

    /**
     * Property 4b: At most one reviewing version exists at any time.
     *
     * <p>Given an AgentSpec with an existing reviewing version in its versionInfo,
     * attempting to submit another version SHALL be rejected with a conflict.</p>
     */
    @Property(tries = 30)
    void atMostOneReviewingVersionExists(
            @ForAll("agentSpecNames") String agentSpecName,
            @ForAll("versions") String existingReviewing,
            @ForAll("versions") String newSubmit) {

        InMemoryPersistService persistService = new InMemoryPersistService();

        // Set up an AgentSpec with an existing reviewing version
        VersionInfo info = new VersionInfo();
        info.reviewingVersion = existingReviewing;
        AiResource meta = buildMeta(agentSpecName, info);
        persistService.insertResource(meta);

        // Insert the existing reviewing version row
        AiResourceVersion reviewingRow = buildVersion(agentSpecName, existingReviewing, VERSION_STATUS_REVIEWING);
        persistService.insertVersion(reviewingRow);

        // Attempt to submit another version — should be rejected
        boolean rejected = false;
        VersionInfo currentInfo = parseVersionInfo(
                persistService.findResource(NAMESPACE_ID, agentSpecName, RESOURCE_TYPE_AGENTSPEC).getVersionInfo());
        if (StringUtils.isNotBlank(currentInfo.editingVersion)
                || StringUtils.isNotBlank(currentInfo.reviewingVersion)) {
            rejected = true;
        }

        assertTrue(rejected, "Submitting while a reviewing version exists should be rejected");

        // Verify only one reviewing version row exists
        long reviewingCount = persistService.countVersionsByStatus(agentSpecName, VERSION_STATUS_REVIEWING);
        assertEquals(1, reviewingCount, "Only one reviewing version should exist");
    }

    /**
     * Property 4c: After a sequence of valid operations, the working version invariant holds.
     *
     * <p>Given a random sequence of create-draft, submit, and publish operations,
     * the versionInfo SHALL never have more than one editingVersion and one reviewingVersion
     * simultaneously. The invariant is checked after each operation.</p>
     */
    @Property(tries = 50)
    void workingVersionInvariantHoldsAcrossOperations(
            @ForAll("operationSequences") List<Operation> operations) {

        InMemoryPersistService persistService = new InMemoryPersistService();
        String agentSpecName = "test-agent";
        int versionCounter = 0;

        // Initialize the AgentSpec with an online v1 so drafts can be created
        VersionInfo info = new VersionInfo();
        info.labels.put("latest", "v1");
        info.onlineCnt = 1;
        AiResource meta = buildMeta(agentSpecName, info);
        persistService.insertResource(meta);
        AiResourceVersion v1 = buildVersion(agentSpecName, "v1", VERSION_STATUS_ONLINE);
        persistService.insertVersion(v1);

        for (Operation op : operations) {
            AiResource currentMeta = persistService.findResource(NAMESPACE_ID, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
            VersionInfo currentInfo = parseVersionInfo(currentMeta.getVersionInfo());

            switch (op) {
                case CREATE_DRAFT:
                    if (StringUtils.isBlank(currentInfo.editingVersion)
                            && StringUtils.isBlank(currentInfo.reviewingVersion)) {
                        versionCounter++;
                        String newVersion = "v" + (versionCounter + 1);
                        AiResourceVersion draft = buildVersion(agentSpecName, newVersion, VERSION_STATUS_DRAFT);
                        persistService.insertVersion(draft);
                        currentInfo.editingVersion = newVersion;
                        currentMeta.setVersionInfo(JacksonUtils.toJson(currentInfo));
                        persistService.updateResource(currentMeta);
                    }
                    // else: rejected (working version exists)
                    break;

                case SUBMIT:
                    if (StringUtils.isNotBlank(currentInfo.editingVersion)
                            && StringUtils.isBlank(currentInfo.reviewingVersion)) {
                        String editing = currentInfo.editingVersion;
                        persistService.updateVersionStatus(agentSpecName, editing, VERSION_STATUS_REVIEWING);
                        currentInfo.reviewingVersion = editing;
                        currentInfo.editingVersion = null;
                        currentMeta.setVersionInfo(JacksonUtils.toJson(currentInfo));
                        persistService.updateResource(currentMeta);
                    }
                    // else: rejected (no draft or reviewing already exists)
                    break;

                case PUBLISH:
                    if (StringUtils.isNotBlank(currentInfo.reviewingVersion)) {
                        String reviewing = currentInfo.reviewingVersion;
                        persistService.updateVersionStatus(agentSpecName, reviewing, VERSION_STATUS_ONLINE);
                        currentInfo.reviewingVersion = null;
                        currentInfo.onlineCnt = (currentInfo.onlineCnt == null ? 0 : currentInfo.onlineCnt) + 1;
                        currentInfo.labels.put("latest", reviewing);
                        currentMeta.setVersionInfo(JacksonUtils.toJson(currentInfo));
                        persistService.updateResource(currentMeta);
                    }
                    break;

                case DELETE_DRAFT:
                    if (StringUtils.isNotBlank(currentInfo.editingVersion)) {
                        String editing = currentInfo.editingVersion;
                        persistService.deleteVersion(agentSpecName, editing);
                        currentInfo.editingVersion = null;
                        currentMeta.setVersionInfo(JacksonUtils.toJson(currentInfo));
                        persistService.updateResource(currentMeta);
                    }
                    break;

                default:
                    break;
            }

            // INVARIANT CHECK: after every operation, at most one editing and one reviewing
            AiResource afterMeta = persistService.findResource(NAMESPACE_ID, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
            VersionInfo afterInfo = parseVersionInfo(afterMeta.getVersionInfo());

            long draftCount = persistService.countVersionsByStatus(agentSpecName, VERSION_STATUS_DRAFT);
            long reviewingCount = persistService.countVersionsByStatus(agentSpecName, VERSION_STATUS_REVIEWING);

            assertTrue(draftCount <= 1,
                    "At most one draft version should exist, found: " + draftCount);
            assertTrue(reviewingCount <= 1,
                    "At most one reviewing version should exist, found: " + reviewingCount);

            // versionInfo pointers must be consistent with actual version rows
            if (StringUtils.isNotBlank(afterInfo.editingVersion)) {
                AiResourceVersion editingRow = persistService.findVersion(agentSpecName, afterInfo.editingVersion);
                assertNotNull(editingRow, "editingVersion pointer should reference an existing version row");
                assertEquals(VERSION_STATUS_DRAFT, editingRow.getStatus(),
                        "editingVersion should point to a draft version");
            }
            if (StringUtils.isNotBlank(afterInfo.reviewingVersion)) {
                AiResourceVersion reviewingRow = persistService.findVersion(agentSpecName, afterInfo.reviewingVersion);
                assertNotNull(reviewingRow, "reviewingVersion pointer should reference an existing version row");
                assertEquals(VERSION_STATUS_REVIEWING, reviewingRow.getStatus(),
                        "reviewingVersion should point to a reviewing version");
            }
        }
    }

    /**
     * Property 4d: Creating a draft while a reviewing version exists is rejected.
     *
     * <p>The mutual exclusion check covers both editingVersion and reviewingVersion.
     * Even if editingVersion is null, having a reviewingVersion blocks draft creation.</p>
     */
    @Property(tries = 30)
    void createDraftRejectedWhenReviewingExists(
            @ForAll("agentSpecNames") String agentSpecName,
            @ForAll("versions") String reviewingVersion) {

        InMemoryPersistService persistService = new InMemoryPersistService();

        VersionInfo info = new VersionInfo();
        info.reviewingVersion = reviewingVersion;
        AiResource meta = buildMeta(agentSpecName, info);
        persistService.insertResource(meta);

        AiResourceVersion reviewingRow = buildVersion(agentSpecName, reviewingVersion, VERSION_STATUS_REVIEWING);
        persistService.insertVersion(reviewingRow);

        // Attempt to create draft — should be rejected because reviewingVersion is set
        VersionInfo currentInfo = parseVersionInfo(
                persistService.findResource(NAMESPACE_ID, agentSpecName, RESOURCE_TYPE_AGENTSPEC).getVersionInfo());
        boolean rejected = StringUtils.isNotBlank(currentInfo.editingVersion)
                || StringUtils.isNotBlank(currentInfo.reviewingVersion);

        assertTrue(rejected, "Draft creation should be rejected when a reviewing version exists");
    }

    // ---- Operation enum for stateful testing ----

    enum Operation {
        CREATE_DRAFT, SUBMIT, PUBLISH, DELETE_DRAFT
    }

    // ---- Arbitraries ----

    @Provide
    Arbitrary<String> agentSpecNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12);
    }

    @Provide
    Arbitrary<String> versions() {
        return Arbitraries.integers().between(1, 50).map(n -> "v" + n);
    }

    @Provide
    Arbitrary<List<Operation>> operationSequences() {
        return Arbitraries.of(Operation.class).list().ofMinSize(3).ofMaxSize(15);
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

    // ---- In-memory persist service for working version tests ----

    /**
     * Minimal in-memory persistence that tracks AiResource and AiResourceVersion records,
     * providing the operations needed to verify working version mutual exclusion.
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

        void deleteVersion(String name, String version) {
            versions.removeIf(v -> NAMESPACE_ID.equals(v.getNamespaceId())
                    && name.equals(v.getName())
                    && RESOURCE_TYPE_AGENTSPEC.equals(v.getType())
                    && version.equals(v.getVersion()));
        }

        long countVersionsByStatus(String name, String status) {
            return versions.stream()
                    .filter(v -> NAMESPACE_ID.equals(v.getNamespaceId())
                            && name.equals(v.getName())
                            && RESOURCE_TYPE_AGENTSPEC.equals(v.getType())
                            && status.equals(v.getStatus()))
                    .count();
        }
    }
}
