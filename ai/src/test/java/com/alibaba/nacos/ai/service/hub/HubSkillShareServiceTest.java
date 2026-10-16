/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests of the S1-5 sharing orchestration; S1-4 tests cover real SQL contracts. */
class HubSkillShareServiceTest {
    
    @Test
    void firstSharePublishesOnePinnedVersion() throws Exception {
        Fixture f = new Fixture();
        HubShareResult result = f.service.shareSkill(f.request("1.0.0"));
        assertEquals(HubShareOutcome.CREATED, result.getOutcome());
        assertEquals(Long.valueOf(901L), result.getHubEntryId());
        assertEquals(Long.valueOf(2L), result.getMetaVersion());
        assertEquals(Long.valueOf(501L), result.getSourceVersionId());
        assertTrue(result.isActive());
        assertEquals(1, f.repo.versions.size());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repo.entry.getStatus());
        assertEquals("1.0.0", f.repo.latest());
    }
    
    @Test
    void sameVersionRepeatedShareIsIdempotent() throws Exception {
        Fixture f = new Fixture();
        HubShareResult original = f.service.shareSkill(f.request("1.0.0"));
        int preparations = f.repo.prepareCalls;
        HubShareResult again = f.service.shareSkill(f.request("1.0.0"));
        assertEquals(preparations, f.repo.prepareCalls);
        assertEquals(HubShareOutcome.ALREADY_SHARED, again.getOutcome());
        assertEquals(original.getHubEntryId(), again.getHubEntryId());
        assertEquals(original.getMetaVersion(), again.getMetaVersion());
        assertEquals(1, f.repo.versions.size());
    }
    
    @Test
    void activeShareWithMissingVersionMustConflictWithoutRepair() throws Exception {
        Fixture f = new Fixture();
        f.service.shareSkill(f.request("1.0.0"));
        f.repo.versions.clear();
        int preparations = f.repo.prepareCalls;
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertTrue(f.repo.versions.isEmpty());
        assertEquals(preparations, f.repo.prepareCalls);
        assertEquals(Long.valueOf(2L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void activeShareWithMismatchedVersionMustConflictWithoutOverwrite() throws Exception {
        Fixture f = new Fixture();
        f.service.shareSkill(f.request("1.0.0"));
        String unrelated = HubResourceReferenceJsonUtil.toJson(HubResourceReferenceJsonUtil.build(
            "team-a", 101L, "find-skills", 999L, "1.0.0"));
        f.repo.versions.put("1.0.0", unrelated);
        int preparations = f.repo.prepareCalls;
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals(unrelated, f.repo.versions.get("1.0.0"));
        assertEquals(preparations, f.repo.prepareCalls);
        assertEquals(Long.valueOf(2L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void lostCasToSameVersionWithMissingRowMustNotRepairIt() throws Exception {
        Fixture f = new Fixture();
        f.repo.beforePublish = () -> {
            f.repo.simulateCompetingVersion("1.0.0", 501L);
            f.repo.versions.clear();
        };
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertTrue(f.repo.versions.isEmpty());
        assertEquals(1, f.repo.prepareCalls);
        assertEquals(Long.valueOf(2L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void differentVersionRequiresAdjustInsteadOfOverwrite() throws Exception {
        Fixture f = new Fixture();
        f.service.shareSkill(f.request("1.0.0"));
        f.sourceVersion.setVersion("2.0.0");
        f.sourceVersion.setId(502L);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("2.0.0"))).getErrCode());
        assertEquals("1.0.0", f.repo.latest());
        assertEquals(1, f.repo.versions.size());
    }
    
    @Test
    void disabledEntryIsReactivatedWithStableId() throws Exception {
        Fixture f = new Fixture();
        f.service.shareSkill(f.request("1.0.0"));
        assertTrue(f.repo.disable(901L, 2L));
        HubShareResult again = f.service.shareSkill(f.request("1.0.0"));
        assertEquals(HubShareOutcome.REACTIVATED, again.getOutcome());
        assertEquals(Long.valueOf(901L), again.getHubEntryId());
        assertEquals(Long.valueOf(4L), again.getMetaVersion());
    }
    
    @Test
    void sourceReadOrWriteDeniedBeforeReservation() throws Exception {
        Fixture f = new Fixture();
        doThrow(new NacosException(NacosException.NO_RIGHT, "no read"))
            .when(f.auth).requireSharePermission(any());
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertNull(f.repo.entry);
    }
    
    @Test
    void prepareFailureKeepsEntryDisabledForRetry() throws Exception {
        Fixture f = new Fixture();
        f.repo.failNextPrepare = true;
        assertThrows(IllegalStateException.class,
            () -> f.service.shareSkill(f.request("1.0.0")));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repo.entry.getStatus());
        HubShareResult retry = f.service.shareSkill(f.request("1.0.0"));
        assertEquals(HubShareOutcome.CREATED, retry.getOutcome());
        assertEquals(Long.valueOf(901L), retry.getHubEntryId());
    }
    
    @Test
    void sourceIdentityChangeBeforeCasLeavesEntryDisabled() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger checks = new AtomicInteger();
        when(f.eligibility.requireShareableVersion(any(), anyString())).thenAnswer(inv -> {
            if (checks.incrementAndGet() == 2) {
                f.sourceVersion.setId(502L);
            }
            return f.eligible();
        });
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repo.entry.getStatus());
    }
    
    @Test
    void reactivationMaySelectAnotherOnlineVersion() throws Exception {
        Fixture f = new Fixture();
        f.service.shareSkill(f.request("1.0.0"));
        assertTrue(f.repo.disable(901L, 2L));
        f.sourceVersion.setId(502L);
        f.sourceVersion.setVersion("2.0.0");
        HubShareResult result = f.service.shareSkill(f.request("2.0.0"));
        assertEquals(HubShareOutcome.REACTIVATED, result.getOutcome());
        assertEquals(Long.valueOf(901L), result.getHubEntryId());
        assertEquals("2.0.0", f.repo.latest());
        assertEquals(2, f.repo.versions.size());
    }
    
    @Test
    void reusedVersionStringWithNewSourceVersionIdCannotOverwrite() throws Exception {
        Fixture f = new Fixture();
        f.service.shareSkill(f.request("1.0.0"));
        assertTrue(f.repo.disable(901L, 2L));
        String immutableStorage = f.repo.versions.get("1.0.0");
        f.sourceVersion.setId(999L);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals(immutableStorage, f.repo.versions.get("1.0.0"));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repo.entry.getStatus());
    }
    
    @Test
    void missingWriteDuringFinalCheckNeverPublishes() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger authorizations = new AtomicInteger();
        doAnswer(invocation -> {
            if (authorizations.incrementAndGet() == 3) {
                throw new NacosException(NacosException.NO_RIGHT, "WRITE revoked");
            }
            return null;
        }).when(f.auth).requireSharePermission(any());
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repo.entry.getStatus());
        assertEquals(Long.valueOf(1L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void lostCasToDifferentVersionIsConflict() throws Exception {
        Fixture f = new Fixture();
        f.repo.beforePublish = () -> f.repo.simulateCompetingVersion("2.0.0", 502L);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals("2.0.0", f.repo.latest());
    }
    
    @Test
    void lostCasToSameVersionReturnsAlreadyShared() throws Exception {
        Fixture f = new Fixture();
        f.repo.beforePublish = () -> f.repo.simulateCompetingVersion("1.0.0", 501L);
        HubShareResult result = f.service.shareSkill(f.request("1.0.0"));
        assertEquals(HubShareOutcome.ALREADY_SHARED, result.getOutcome());
        assertEquals(Long.valueOf(2L), result.getMetaVersion());
    }
    
    @Test
    void sourceOfflineAfterCasOnlyDisablesOwnPublication() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger checks = new AtomicInteger();
        when(f.eligibility.requireShareableVersion(any(), anyString())).thenAnswer(inv -> {
            if (checks.incrementAndGet() == 3) {
                throw new NacosException(NacosException.CONFLICT, "source offline");
            }
            return f.eligible();
        });
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repo.entry.getStatus());
        assertEquals(Long.valueOf(3L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void postCasInvalidationDoesNotDisableConcurrentAdjustment() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger checks = new AtomicInteger();
        when(f.eligibility.requireShareableVersion(any(), anyString())).thenAnswer(inv -> {
            if (checks.incrementAndGet() == 3) {
                f.repo.simulateCompetingVersion("2.0.0", 502L);
                throw new NacosException(NacosException.CONFLICT, "source offline");
            }
            return f.eligible();
        });
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repo.entry.getStatus());
        assertEquals("2.0.0", f.repo.latest());
    }
    
    @Test
    void runtimeFailureAfterCasDisablesOwnPublicationAndPreservesException() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("source database unavailable");
        failFinalEligibility(f, failure, () -> {
        });
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repo.entry.getStatus());
        assertEquals(Long.valueOf(3L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void runtimeFailureAfterCasDoesNotDisableConcurrentAdjustment() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("source database unavailable");
        failFinalEligibility(f, failure, () -> f.repo.simulateCompetingVersion("2.0.0", 502L));
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))));
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repo.entry.getStatus());
        assertEquals("2.0.0", f.repo.latest());
        assertEquals(Long.valueOf(3L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void failedRuntimeCompensationMustNotMaskOriginalFailure() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("source database unavailable");
        f.repo.disableFailure = new IllegalStateException("Hub database unavailable");
        failFinalEligibility(f, failure, () -> {
        });
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.shareSkill(f.request("1.0.0"))));
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repo.entry.getStatus());
        assertEquals(Long.valueOf(2L), f.repo.entry.getMetaVersion());
    }
    
    @Test
    void concurrentInitialShareProducesOneCreatedAndOneIdempotent() throws Exception {
        Fixture f = new Fixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<HubShareResult> a = pool.submit(() -> {
                start.await();
                return f.service.shareSkill(f.request("1.0.0"));
            });
            Future<HubShareResult> b = pool.submit(() -> {
                start.await();
                return f.service.shareSkill(f.request("1.0.0"));
            });
            start.countDown();
            HubShareOutcome x = a.get(15, TimeUnit.SECONDS).getOutcome();
            HubShareOutcome y = b.get(15, TimeUnit.SECONDS).getOutcome();
            assertNotEquals(x, y);
            assertTrue(x == HubShareOutcome.CREATED || y == HubShareOutcome.CREATED);
            assertTrue(x == HubShareOutcome.ALREADY_SHARED
                || y == HubShareOutcome.ALREADY_SHARED);
            assertEquals(1, f.repo.versions.size());
            assertEquals(Long.valueOf(2L), f.repo.entry.getMetaVersion());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
    
    private void failFinalEligibility(Fixture fixture, RuntimeException failure,
        Runnable beforeFailure) throws Exception {
        AtomicInteger checks = new AtomicInteger();
        when(fixture.eligibility.requireShareableVersion(any(), anyString())).thenAnswer(inv -> {
            if (checks.incrementAndGet() == 3) {
                beforeFailure.run();
                throw failure;
            }
            return fixture.eligible();
        });
    }
    
    private static final class Fixture {
        
        final HubShareRepositoryFake repo = new HubShareRepositoryFake();
        
        final HubSkillEligibilityValidator eligibility = mock(HubSkillEligibilityValidator.class);
        
        final HubSkillAuthorizationService auth = mock(HubSkillAuthorizationService.class);
        
        final HubSkillShareService service = new HubSkillShareService(eligibility, auth, repo);
        
        final AiResource source = new AiResource();
        
        final AiResourceVersion sourceVersion = new AiResourceVersion();
        
        Fixture() throws Exception {
            source.setId(101L);
            source.setNamespaceId("team-a");
            source.setName("find-skills");
            source.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
            source.setDesc("desc");
            sourceVersion.setId(501L);
            sourceVersion.setVersion("1.0.0");
            when(eligibility.requireSourceForShare(any())).thenReturn(source);
            when(eligibility.requireShareableVersion(any(), anyString()))
                .thenAnswer(inv -> eligible());
        }
        
        HubEligibleSkillVersion eligible() throws Exception {
            HubResourceReference ref = HubResourceReferenceJsonUtil.build("team-a", source.getId(),
                source.getName(), sourceVersion.getId(), sourceVersion.getVersion());
            return new HubEligibleSkillVersion(source, sourceVersion, ref);
        }
        
        HubShareSkillRequest request(String version) {
            HubShareSkillRequest r = new HubShareSkillRequest();
            r.setSourceNamespaceId("team-a");
            r.setSourceName("find-skills");
            r.setSourceVersion(version);
            r.setExpectedSourceResourceId(101L);
            return r;
        }
    }
    
    private static final class HubShareRepositoryFake implements HubShareRepository {
        
        AiResource entry;
        
        final Map<String, String> versions = new HashMap<>();
        
        boolean failNextPrepare;
        
        int prepareCalls;
        
        RuntimeException disableFailure;
        
        Runnable beforePublish;
        
        @Override
        public synchronized AiResource reserveEntry(HubResourceReference ref, String desc)
            throws NacosException {
            if (entry == null) {
                entry = new AiResource();
                entry.setId(901L);
                entry.setMetaVersion(1L);
                entry.setStatus(AiResourceConstants.META_STATUS_DISABLE);
                entry.setExt(HubResourceReferenceJsonUtil.toJson(ref));
                entry.setVersionInfo(info(null));
                entry.setDesc(desc);
            }
            return copy(entry);
        }
        
        @Override
        public synchronized AiResource findByEntryId(long id) {
            return entry == null || id != 901L ? null : copy(entry);
        }
        
        @Override
        public synchronized AiResource findBySource(HubResourceReference source) {
            return entry == null ? null : copy(entry);
        }
        
        @Override
        public synchronized AiResourceVersion ensureReferenceVersion(long id,
            HubResourceReference ref) throws NacosException {
            prepareCalls++;
            if (failNextPrepare) {
                failNextPrepare = false;
                throw new IllegalStateException("test injected insert failure");
            }
            String storage = HubResourceReferenceJsonUtil.toJson(ref);
            String existed = versions.putIfAbsent(ref.getSourceVersion(), storage);
            if (existed != null && !Objects.equals(existed, storage)) {
                throw new NacosException(NacosException.CONFLICT, "version key collision");
            }
            AiResourceVersion row = new AiResourceVersion();
            row.setId(801L);
            row.setStorage(storage);
            return row;
        }
        
        @Override
        public synchronized AiResourceVersion requireReferenceVersion(long id,
            HubResourceReference ref) throws NacosException {
            String existing = versions.get(ref.getSourceVersion());
            if (id != 901L || existing == null
                || !Objects.equals(existing, HubResourceReferenceJsonUtil.toJson(ref))) {
                throw new NacosException(NacosException.CONFLICT,
                    "missing or inconsistent version");
            }
            AiResourceVersion row = new AiResourceVersion();
            row.setId(801L);
            row.setStorage(existing);
            return row;
        }
        
        @Override
        public synchronized boolean publish(long id, long expected,
            HubResourceReference ref) throws NacosException {
            if (beforePublish != null) {
                Runnable hook = beforePublish;
                beforePublish = null;
                hook.run();
            }
            if (!Objects.equals(entry.getMetaVersion(), expected)) {
                return false;
            }
            publishState(ref);
            return true;
        }
        
        @Override
        public synchronized boolean disable(long id, long expected) {
            if (disableFailure != null) {
                throw disableFailure;
            }
            if (!Objects.equals(entry.getMetaVersion(), expected)) {
                return false;
            }
            entry.setStatus(AiResourceConstants.META_STATUS_DISABLE);
            entry.setMetaVersion(expected + 1);
            return true;
        }
        
        synchronized void simulateCompetingVersion(String version, long versionId) {
            try {
                HubResourceReference ref = HubResourceReferenceJsonUtil.build("team-a", 101L,
                    "find-skills", versionId, version);
                publishState(ref);
            } catch (NacosException e) {
                throw new IllegalStateException(e);
            }
        }
        
        String latest() {
            ResourceVersionInfo info = JacksonUtils.toObj(entry.getVersionInfo(),
                ResourceVersionInfo.class);
            return info.getLabels().get(AiResourceConstants.LABEL_LATEST);
        }
        
        private void publishState(HubResourceReference ref) throws NacosException {
            entry.setStatus(AiResourceConstants.META_STATUS_ENABLE);
            entry.setExt(HubResourceReferenceJsonUtil.toJson(ref));
            entry.setVersionInfo(info(ref.getSourceVersion()));
            entry.setMetaVersion(entry.getMetaVersion() + 1);
        }
        
        private String info(String latest) {
            ResourceVersionInfo value = new ResourceVersionInfo();
            value.setOnlineCnt(latest == null ? 0 : 1);
            Map<String, String> labels = new HashMap<>();
            if (latest != null) {
                labels.put(AiResourceConstants.LABEL_LATEST, latest);
            }
            value.setLabels(labels);
            return JacksonUtils.toJson(value);
        }
        
        private AiResource copy(AiResource from) {
            AiResource to = new AiResource();
            to.setId(from.getId());
            to.setMetaVersion(from.getMetaVersion());
            to.setStatus(from.getStatus());
            to.setExt(from.getExt());
            to.setVersionInfo(from.getVersionInfo());
            to.setDesc(from.getDesc());
            return to;
        }
    }
}
