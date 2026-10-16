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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S1-6 orchestration tests. The S1-4 repository tests independently verify SQL/CAS.
 */
class HubSkillAdjustServiceTest {
    
    @Test
    void adjustsVersionWithStableEntryIdAndImmutableHistory() throws Exception {
        Fixture f = new Fixture();
        String originalStorage = f.repository.versions.get("1.0.0");
        HubShareResult result = f.service.adjustShare(f.request("2.0.0", 7));
        assertEquals(HubShareOutcome.ADJUSTED, result.getOutcome());
        assertTrue(result.isActive());
        assertEquals(Long.valueOf(901L), result.getHubEntryId());
        assertEquals(Long.valueOf(8L), result.getMetaVersion());
        assertEquals(Long.valueOf(502L), result.getSourceVersionId());
        assertEquals("2.0.0", f.repository.latest());
        assertEquals(originalStorage, f.repository.versions.get("1.0.0"));
        assertEquals(2, f.repository.versions.size());
        assertEquals(1, f.repository.publishCount);
    }
    
    @Test
    void selectingSameVersionIsUnchangedWithoutMetaCas() throws Exception {
        Fixture f = new Fixture();
        HubShareResult result = f.service.adjustShare(f.request("1.0.0", 7));
        assertEquals(HubShareOutcome.UNCHANGED, result.getOutcome());
        assertEquals(Long.valueOf(7L), result.getMetaVersion());
        assertEquals(0, f.repository.publishCount);
        assertEquals(0, f.repository.prepareCount);
        assertEquals(1, f.repository.versions.size());
    }
    
    @Test
    void staleVersionIsConflictEvenIfTargetAlreadyMatches() throws Exception {
        Fixture f = new Fixture();
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("1.0.0", 6))).getErrCode());
        assertEquals(0, f.repository.publishCount);
    }
    
    @Test
    void disabledEntryCannotBeAdjusted() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.repository.disable(901L, 7L));
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 8))).getErrCode());
        assertFalse(AiResourceConstants.META_STATUS_ENABLE.equals(f.repository.entry.getStatus()));
    }
    
    @Test
    void missingEntryIsNotFound() throws Exception {
        Fixture f = new Fixture();
        f.repository.entry = null;
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
    }
    
    @Test
    void missingSourceIdentityFailsBeforePreparingTarget() throws Exception {
        Fixture f = new Fixture();
        when(f.eligibility.requireReferencedSource(any())).thenThrow(
            new NacosException(NacosException.NOT_FOUND, "source deleted"));
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(1, f.repository.versions.size());
    }
    
    @Test
    void writeDeniedBeforeAnyVersionPreparation() throws Exception {
        Fixture f = new Fixture();
        doAnswer(invocation -> {
            throw new NacosException(NacosException.NO_RIGHT, "WRITE denied");
        }).when(f.authorization).requireAdjustPermission(any());
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(1, f.repository.versions.size());
        assertEquals(0, f.repository.publishCount);
    }
    
    @Test
    void targetOfflineOrPrivateLeavesOldVersionActive() throws Exception {
        Fixture f = new Fixture();
        f.failEligibilityAt = 1;
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals("1.0.0", f.repository.latest());
        assertEquals(1, f.repository.versions.size());
    }
    
    @Test
    void deletedAndRecreatedSourceIdCannotRebind() throws Exception {
        Fixture f = new Fixture();
        f.overrideSourceId = 999L;
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals("1.0.0", f.repository.latest());
    }
    
    @Test
    void changedVersionIdentityCannotOverwriteExistingVersionRow() throws Exception {
        Fixture f = new Fixture();
        String immutableOld = HubResourceReferenceJsonUtil.toJson(f.reference("2.0.0", 502L));
        f.repository.versions.put("2.0.0", immutableOld);
        f.overrideVersionId = 999L;
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(immutableOld, f.repository.versions.get("2.0.0"));
        assertEquals("1.0.0", f.repository.latest());
    }
    
    @Test
    void sourceVersionIdChangedForSameVersionIsNotUnchanged() throws Exception {
        Fixture f = new Fixture();
        f.overrideVersionId = 999L;
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("1.0.0", 7))).getErrCode());
        assertEquals(0, f.repository.publishCount);
    }
    
    @Test
    void writeRevokedBeforeCasNeverSwitchesPointer() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 3) {
                throw new NacosException(NacosException.NO_RIGHT, "WRITE revoked");
            }
            return null;
        }).when(f.authorization).requireAdjustPermission(any());
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals("1.0.0", f.repository.latest());
        assertEquals(Long.valueOf(7L), f.repository.entry.getMetaVersion());
        assertTrue(f.repository.versions.containsKey("2.0.0"));
    }
    
    @Test
    void unshareBetweenReadAndCasWinsAndAdjustReturnsConflict() throws Exception {
        Fixture f = new Fixture();
        f.repository.beforePublish = () -> {
            if (!f.repository.disable(901L, 7L)) {
                throw new IllegalStateException("concurrent unshare failed");
            }
        };
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(8L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void anotherAdjustmentBetweenReadAndCasReturnsConflict() throws Exception {
        Fixture f = new Fixture();
        f.repository.beforePublish = () -> f.repository.simulateCompeting("3.0.0", 503L);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals("3.0.0", f.repository.latest());
        assertEquals(Long.valueOf(8L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void postCasSourceRevocationDisablesOnlyOurVersion() throws Exception {
        Fixture f = new Fixture();
        f.failEligibilityAt = 3;
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void postCasSourceRevocationDoesNotDisableNewerCompetingAdjustment() throws Exception {
        Fixture f = new Fixture();
        f.failEligibilityAt = 3;
        f.repository.afterPublish = () -> f.repository.simulateCompeting("3.0.0", 503L);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
        assertEquals("3.0.0", f.repository.latest());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void twoConcurrentAdjustmentsWithSameExpectedVersionHaveOneWinner() throws Exception {
        Fixture f = new Fixture();
        f.repository.publishBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> a = executor.submit(() -> callAdjust(f, "2.0.0", start));
            Future<Boolean> b = executor.submit(() -> callAdjust(f, "3.0.0", start));
            start.countDown();
            boolean aWon = a.get(15, TimeUnit.SECONDS);
            boolean bWon = b.get(15, TimeUnit.SECONDS);
            assertNotEquals(aWon, bWon);
            assertEquals(Long.valueOf(8L), f.repository.entry.getMetaVersion());
            assertEquals(1, f.repository.publishCount);
            assertTrue("2.0.0".equals(f.repository.latest())
                || "3.0.0".equals(f.repository.latest()));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
    
    @Test
    void unchangedWithMissingVersionRowIsConflictWithoutRepair() throws Exception {
        Fixture f = new Fixture();
        f.repository.versions.clear();
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("1.0.0", 7))).getErrCode());
        assertTrue(f.repository.versions.isEmpty());
        assertEquals(0, f.repository.prepareCount);
        assertEquals(0, f.repository.publishCount);
        assertEquals(Long.valueOf(7L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void unchangedWithMismatchedVersionRowIsConflictWithoutOverwrite() throws Exception {
        Fixture f = new Fixture();
        String conflictingStorage = HubResourceReferenceJsonUtil.toJson(f.reference("1.0.0", 999L));
        f.repository.versions.put("1.0.0", conflictingStorage);
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("1.0.0", 7))).getErrCode());
        assertEquals(conflictingStorage, f.repository.versions.get("1.0.0"));
        assertEquals(0, f.repository.prepareCount);
        assertEquals(Long.valueOf(7L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void sameVersionWithNewSourceIdAndMissingHubRowCannotRebind() throws Exception {
        Fixture f = new Fixture();
        f.repository.versions.clear();
        f.overrideVersionId = 999L;
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("1.0.0", 7))).getErrCode());
        assertTrue(f.repository.versions.isEmpty());
        assertEquals(0, f.repository.prepareCount);
        assertEquals(0, f.repository.publishCount);
        assertEquals(Long.valueOf(7L), f.repository.entry.getMetaVersion());
        assertEquals(Long.valueOf(501L),
            HubResourceReferenceJsonUtil.parse(f.repository.entry.getExt()).getSourceVersionId());
    }
    
    @Test
    void unchangedReadRejectsConcurrentUnshare() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger checks = new AtomicInteger();
        doAnswer(invocation -> {
            if (checks.incrementAndGet() == 2) {
                assertTrue(f.repository.disable(901L, 7L));
            }
            return f.qualified(invocation.getArgument(1));
        }).when(f.eligibility).requireShareableVersion(any(), anyString());
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("1.0.0", 7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(8L), f.repository.entry.getMetaVersion());
        assertEquals(0, f.repository.prepareCount);
        assertEquals(0, f.repository.publishCount);
    }
    
    @Test
    void lostCasToSameTargetStillReturnsConflict() throws Exception {
        Fixture f = new Fixture();
        f.repository.beforePublish = () -> f.repository.simulateCompeting("2.0.0", 502L);
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals("2.0.0", f.repository.latest());
        assertEquals(Long.valueOf(8L), f.repository.entry.getMetaVersion());
        assertEquals(0, f.repository.publishCount);
    }
    
    @Test
    void finalReadRejectsNewerMetaVersionEvenWithSameTarget() throws Exception {
        Fixture f = new Fixture();
        f.repository.afterPublish = () -> f.repository.simulateCompeting("2.0.0", 502L);
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
        assertEquals("2.0.0", f.repository.latest());
        assertEquals(1, f.repository.publishCount);
    }
    
    @Test
    void runtimeFailureAfterCasDisablesOwnPublicationAndPreservesException() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("source database unavailable");
        f.failEligibilityAt = 3;
        f.eligibilityFailure = failure;
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
        assertEquals("2.0.0", f.repository.latest());
    }
    
    @Test
    void runtimeFailureAfterCasDoesNotDisableNewerAdjustment() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("source database unavailable");
        f.failEligibilityAt = 3;
        f.eligibilityFailure = failure;
        f.repository.afterPublish = () -> f.repository.simulateCompeting("3.0.0", 503L);
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))));
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
        assertEquals("3.0.0", f.repository.latest());
    }
    
    @Test
    void failedCompensationPreservesOriginalRuntimeFailure() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("source database unavailable");
        f.failEligibilityAt = 3;
        f.eligibilityFailure = failure;
        f.repository.disableFailure = new IllegalStateException("Hub database unavailable");
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))));
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(8L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void writeRevokedAfterCasDisablesOnlyOwnPublication() throws Exception {
        Fixture f = new Fixture();
        NacosException failure = new NacosException(NacosException.NO_RIGHT, "WRITE revoked");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 4) {
                throw failure;
            }
            return null;
        }).when(f.authorization).requireAdjustPermission(any());
        
        assertSame(failure, assertThrows(NacosException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
    }
    
    @Test
    void authorizationRuntimeFailureAfterCasDisablesOwnPublication() throws Exception {
        Fixture f = new Fixture();
        IllegalStateException failure =
            new IllegalStateException("authorization service unavailable");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 4) {
                throw failure;
            }
            return null;
        }).when(f.authorization).requireAdjustPermission(any());
        
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> f.service.adjustShare(f.request("2.0.0", 7))));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
    }
    
    private boolean callAdjust(Fixture fixture, String version, CountDownLatch start)
        throws Exception {
        start.await();
        try {
            return fixture.service.adjustShare(fixture.request(version, 7))
                .getOutcome() == HubShareOutcome.ADJUSTED;
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.CONFLICT) {
                return false;
            }
            throw e;
        }
    }
    
    private static final class Fixture {
        
        final FakeRepository repository = new FakeRepository();
        
        final HubSkillEligibilityValidator eligibility = mock(HubSkillEligibilityValidator.class);
        
        final HubSkillAuthorizationService authorization = mock(HubSkillAuthorizationService.class);
        
        final HubSkillAdjustService service =
            new HubSkillAdjustService(repository, eligibility, authorization);
        
        final AiResource source = new AiResource();
        
        int failEligibilityAt;
        
        RuntimeException eligibilityFailure;
        
        Long overrideSourceId;
        
        Long overrideVersionId;
        
        final AtomicInteger eligibilityCalls = new AtomicInteger();
        
        Fixture() throws Exception {
            source.setId(101L);
            source.setName("find-skills");
            source.setNamespaceId("team-a");
            source.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
            when(eligibility.requireReferencedSource(any())).thenReturn(source);
            when(eligibility.requireShareableVersion(any(), anyString()))
                .thenAnswer(invocation -> {
                    int n = eligibilityCalls.incrementAndGet();
                    if (n == failEligibilityAt) {
                        if (eligibilityFailure != null) {
                            throw eligibilityFailure;
                        }
                        throw new NacosException(NacosException.CONFLICT,
                            "injected eligibility revocation");
                    }
                    return qualified((String) invocation.getArgument(1));
                });
        }
        
        HubResourceReference reference(String version, long id) throws NacosException {
            return HubResourceReferenceJsonUtil.build("team-a", 101L,
                "find-skills", id, version);
        }
        
        HubEligibleSkillVersion qualified(String version) throws NacosException {
            AiResourceVersion row = new AiResourceVersion();
            row.setVersion(version);
            row.setId(overrideVersionId == null ? versionId(version) : overrideVersionId);
            HubResourceReference ref = HubResourceReferenceJsonUtil.build("team-a",
                overrideSourceId == null ? 101L : overrideSourceId, "find-skills",
                row.getId(), version);
            return new HubEligibleSkillVersion(source, row, ref);
        }
        
        long versionId(String version) {
            if ("1.0.0".equals(version)) {
                return 501;
            }
            if ("2.0.0".equals(version)) {
                return 502;
            }
            return 503;
        }
        
        HubAdjustShareRequest request(String targetVersion, long expected) {
            HubAdjustShareRequest request = new HubAdjustShareRequest();
            request.setHubEntryId(901L);
            request.setTargetVersion(targetVersion);
            request.setExpectedMetaVersion(expected);
            return request;
        }
    }
    
    private static final class FakeRepository implements HubShareRepository {
        
        volatile AiResource entry;
        
        final Map<String, String> versions = new HashMap<>();
        
        int publishCount;
        
        int prepareCount;
        
        RuntimeException disableFailure;
        
        Runnable beforePublish;
        
        Runnable afterPublish;
        
        CyclicBarrier publishBarrier;
        
        FakeRepository() throws NacosException {
            entry = new AiResource();
            entry.setId(901L);
            entry.setMetaVersion(7L);
            entry.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
            entry.setName("find-skills-101");
            entry.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
            entry.setFrom("hub");
            entry.setStatus(AiResourceConstants.META_STATUS_ENABLE);
            HubResourceReference initial = ref("1.0.0", 501L);
            entry.setExt(HubResourceReferenceJsonUtil.toJson(initial));
            entry.setVersionInfo(versionInfo("1.0.0"));
            versions.put("1.0.0", HubResourceReferenceJsonUtil.toJson(initial));
        }
        
        @Override
        public AiResource reserveEntry(HubResourceReference reference, String description) {
            throw new UnsupportedOperationException("S1-6 does not create entries");
        }
        
        @Override
        public synchronized AiResource findByEntryId(long hubEntryId) {
            return entry == null || hubEntryId != 901 ? null : copy(entry);
        }
        
        @Override
        public AiResource findBySource(HubResourceReference reference) {
            throw new UnsupportedOperationException("not needed for adjustment");
        }
        
        @Override
        public synchronized AiResourceVersion ensureReferenceVersion(long id,
            HubResourceReference reference) throws NacosException {
            if (id != 901L) {
                throw new NacosException(NacosException.NOT_FOUND, "invalid entry id");
            }
            prepareCount++;
            String version = reference.getSourceVersion();
            String storage = HubResourceReferenceJsonUtil.toJson(reference);
            String previous = versions.putIfAbsent(version, storage);
            if (previous != null && !previous.equals(storage)) {
                throw new NacosException(NacosException.CONFLICT, "immutable version conflict");
            }
            AiResourceVersion row = new AiResourceVersion();
            row.setVersion(version);
            row.setStorage(storage);
            row.setId(801L);
            return row;
        }
        
        @Override
        public synchronized AiResourceVersion requireReferenceVersion(long id,
            HubResourceReference reference) throws NacosException {
            if (entry == null || id != 901L) {
                throw new NacosException(NacosException.NOT_FOUND, "invalid entry id");
            }
            String storage = versions.get(reference.getSourceVersion());
            if (storage == null
                || !storage.equals(HubResourceReferenceJsonUtil.toJson(reference))) {
                throw new NacosException(NacosException.CONFLICT,
                    "missing or inconsistent Hub version");
            }
            AiResourceVersion row = new AiResourceVersion();
            row.setVersion(reference.getSourceVersion());
            row.setStorage(storage);
            row.setId(801L);
            return row;
        }
        
        @Override
        public boolean publish(long id, long expected, HubResourceReference reference)
            throws NacosException {
            if (publishBarrier != null) {
                try {
                    publishBarrier.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("concurrent test barrier failed", e);
                }
            }
            synchronized (this) {
                if (beforePublish != null) {
                    Runnable hook = beforePublish;
                    beforePublish = null;
                    hook.run();
                }
                if (entry == null || entry.getId() != id
                    || !Objects.equals(entry.getMetaVersion(), expected)) {
                    return false;
                }
                setActive(reference);
                publishCount++;
                if (afterPublish != null) {
                    Runnable hook = afterPublish;
                    afterPublish = null;
                    hook.run();
                }
                return true;
            }
        }
        
        @Override
        public synchronized boolean disable(long id, long expected) {
            if (disableFailure != null) {
                throw disableFailure;
            }
            if (entry == null || entry.getId() != id
                || !Objects.equals(entry.getMetaVersion(), expected)) {
                return false;
            }
            entry.setStatus(AiResourceConstants.META_STATUS_DISABLE);
            entry.setMetaVersion(expected + 1L);
            return true;
        }
        
        synchronized void simulateCompeting(String version, long id) {
            try {
                setActive(ref(version, id));
            } catch (NacosException e) {
                throw new IllegalStateException(e);
            }
        }
        
        private void setActive(HubResourceReference reference) throws NacosException {
            entry.setStatus(AiResourceConstants.META_STATUS_ENABLE);
            entry.setExt(HubResourceReferenceJsonUtil.toJson(reference));
            entry.setVersionInfo(versionInfo(reference.getSourceVersion()));
            entry.setMetaVersion(entry.getMetaVersion() + 1);
        }
        
        synchronized String latest() {
            ResourceVersionInfo info = JacksonUtils.toObj(entry.getVersionInfo(),
                ResourceVersionInfo.class);
            return info.getLabels().get(AiResourceConstants.LABEL_LATEST);
        }
        
        private String versionInfo(String latest) {
            ResourceVersionInfo info = new ResourceVersionInfo();
            info.setOnlineCnt(1);
            Map<String, String> labels = new HashMap<>();
            labels.put(AiResourceConstants.LABEL_LATEST, latest);
            info.setLabels(labels);
            return JacksonUtils.toJson(info);
        }
        
        private HubResourceReference ref(String version, long versionId) throws NacosException {
            return HubResourceReferenceJsonUtil.build("team-a", 101L,
                "find-skills", versionId, version);
        }
        
        private AiResource copy(AiResource value) {
            AiResource result = new AiResource();
            result.setId(value.getId());
            result.setNamespaceId(value.getNamespaceId());
            result.setName(value.getName());
            result.setType(value.getType());
            result.setFrom(value.getFrom());
            result.setMetaVersion(value.getMetaVersion());
            result.setStatus(value.getStatus());
            result.setExt(value.getExt());
            result.setVersionInfo(value.getVersionInfo());
            return result;
        }
    }
}
