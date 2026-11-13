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
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * S1-7 lifecycle-level tests. The S1-4 repository tests cover SQL-backed CAS.
 */
class HubSkillUnshareServiceTest {
    
    @Test
    void ordinaryUnshareDisablesWithoutDeletingSourceOrChangingEntryId() throws Exception {
        Fixture f = new Fixture();
        HubShareResult result = f.service.unshareSkill(f.unshare(7));
        assertEquals(HubShareOutcome.UNSHARED, result.getOutcome());
        assertFalse(result.isActive());
        assertEquals(Long.valueOf(901), result.getHubEntryId());
        assertEquals(Long.valueOf(8), result.getMetaVersion());
        assertEquals(Long.valueOf(501), result.getSourceVersionId());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        assertEquals(1, f.repository.disableCount);
        verify(f.audit).recordUnshare(eq(901L), any(), eq(8L), eq(false), eq(null));
        verify(f.authorization, times(2)).requireUnsharePermission(f.source);
    }
    
    @Test
    void retryWithStaleExpectedVersionIsNoOpWhenAlreadyDisabled() throws Exception {
        Fixture f = new Fixture();
        f.service.unshareSkill(f.unshare(7));
        HubShareResult repeated = f.service.unshareSkill(f.unshare(7));
        assertEquals(HubShareOutcome.ALREADY_UNSHARED, repeated.getOutcome());
        assertEquals(Long.valueOf(8L), repeated.getMetaVersion());
        assertEquals(1, f.repository.disableCount);
        verify(f.audit, times(1)).recordUnshare(eq(901L), any(), eq(8L),
            eq(false), eq(null));
    }
    
    @Test
    void staleVersionForStillActiveEntryIsConflict() throws Exception {
        Fixture f = new Fixture();
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(6))).getErrCode());
        assertEquals(0, f.repository.disableCount);
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void offlineDisabledPrivateSourceStillAllowsAuthorizedUnshare() throws Exception {
        Fixture f = new Fixture();
        f.source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        f.source.setScope("PRIVATE");
        assertEquals(HubShareOutcome.UNSHARED,
            f.service.unshareSkill(f.unshare(7)).getOutcome());
    }
    
    @Test
    void sourceWriteDenialNeverDisables() throws Exception {
        Fixture f = new Fixture();
        doThrow(new NacosException(NacosException.NO_RIGHT, "WRITE denied"))
            .when(f.authorization).requireUnsharePermission(any());
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))).getErrCode());
        assertEquals(0, f.repository.disableCount);
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void sourceRecreatedWithSameNameCannotUnshare() throws Exception {
        Fixture f = new Fixture();
        when(f.eligibility.requireReferencedSource(any()))
            .thenThrow(new NacosException(NacosException.CONFLICT, "source ID changed"));
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))).getErrCode());
        assertEquals(0, f.repository.disableCount);
    }
    
    @Test
    void sourceDeletedOrdinaryUnshareFailsButAdminCanStillTakeDown() throws Exception {
        Fixture f = new Fixture();
        when(f.eligibility.requireReferencedSource(any()))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "source deleted"));
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))).getErrCode());
        assertEquals(HubShareOutcome.FORCE_UNSHARED,
            f.service.forceUnshare(f.force(7, "source deleted")).getOutcome());
        verify(f.audit).recordUnshare(eq(901L), any(), eq(8L), eq(true),
            eq("source deleted"));
    }
    
    @Test
    void forceUnshareChecksHubManageBeforeEntryLookup() throws Exception {
        Fixture f = new Fixture();
        doThrow(new NacosException(NacosException.NO_RIGHT, "Hub manage denied"))
            .when(f.authorization).requireForceUnsharePermission();
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> f.service.forceUnshare(f.force(7, "policy violation"))).getErrCode());
        assertEquals(0, f.repository.disableCount);
        assertEquals(0, f.repository.readCount);
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void forceUnshareNeedsNonBlankReason() throws Exception {
        Fixture f = new Fixture();
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> f.service.forceUnshare(f.force(7, "  "))).getErrCode());
        assertEquals(0, f.repository.readCount);
    }
    
    @Test
    void forceUnshareWithReasonTrimsAndAuditsOnce() throws Exception {
        Fixture f = new Fixture();
        HubShareResult result = f.service.forceUnshare(f.force(7, "  security violation  "));
        assertEquals(HubShareOutcome.FORCE_UNSHARED, result.getOutcome());
        assertFalse(result.isActive());
        verify(f.authorization, times(2)).requireForceUnsharePermission();
        verify(f.audit).recordUnshare(eq(901L), any(), eq(8L), eq(true),
            eq("security violation"));
        HubShareResult repeated = f.service.forceUnshare(f.force(7, "repeat"));
        assertEquals(HubShareOutcome.ALREADY_UNSHARED, repeated.getOutcome());
        assertEquals(1, f.repository.disableCount);
        verify(f.audit, times(1)).recordUnshare(eq(901L), any(), eq(8L),
            eq(true), eq("security violation"));
    }
    
    @Test
    void missingOrForgedHubEntryIsRejected() throws Exception {
        Fixture missing = new Fixture();
        missing.repository.entry = null;
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> missing.service.forceUnshare(missing.force(7, "missing"))).getErrCode());
        Fixture forged = new Fixture();
        forged.repository.entry.setFrom("local");
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> forged.service.forceUnshare(forged.force(7, "forged"))).getErrCode());
        assertEquals(0, forged.repository.disableCount);
    }
    
    @Test
    void adjustmentRaceReturnsConflictAndNeverOverwritesNewVersion() throws Exception {
        Fixture f = new Fixture();
        f.repository.entry.setMetaVersion(8L); // another adjust already won
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
    }
    
    @Test
    void staleAdministratorRequestCannotOverrideActiveAdjustment() throws Exception {
        Fixture f = new Fixture();
        f.repository.entry.setMetaVersion(9L);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.forceUnshare(f.force(7, "outdated decision"))).getErrCode());
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void lostCasToAnotherUnshareCanReturnAlreadyUnshared() throws Exception {
        Fixture f = new Fixture();
        f.repository.loseNextCasToUnshare = true;
        HubShareResult result = f.service.unshareSkill(f.unshare(7));
        assertEquals(HubShareOutcome.ALREADY_UNSHARED, result.getOutcome());
        assertEquals(Long.valueOf(8L), result.getMetaVersion());
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void lostCasToAdjustmentMustReturnConflict() throws Exception {
        Fixture f = new Fixture();
        f.repository.loseNextCasToAdjust = true;
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void tooLongAdministrativeReasonIsRejectedBeforeReadingState() throws Exception {
        Fixture f = new Fixture();
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> f.service.forceUnshare(f.force(7, "x".repeat(513)))).getErrCode());
        assertEquals(0, f.repository.readCount);
    }
    
    @Test
    void concurrentUnshareRequestsProduceOneMutationAndOneNoOp() throws Exception {
        Fixture f = new Fixture();
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HubShareOutcome> a = threads.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return f.service.unshareSkill(f.unshare(7)).getOutcome();
            });
            Future<HubShareOutcome> b = threads.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return f.service.unshareSkill(f.unshare(7)).getOutcome();
            });
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, f.repository.disableCount);
            assertEquals(AiResourceConstants.META_STATUS_DISABLE, f.repository.entry.getStatus());
        } finally {
            start.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    
    @Test
    void sourceWriteRevokedAtMutationBoundaryDoesNotDisableOrAudit() throws Exception {
        Fixture f = new Fixture();
        NacosException denial = new NacosException(NacosException.NO_RIGHT, "WRITE revoked");
        doNothing().doThrow(denial).when(f.authorization).requireUnsharePermission(any());
        
        assertSame(denial, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))));
        assertEquals(0, f.repository.disableCount);
        assertEquals(Long.valueOf(7L), f.repository.entry.getMetaVersion());
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void hubManageRevokedAtMutationBoundaryDoesNotDisableOrAudit() throws Exception {
        Fixture f = new Fixture();
        NacosException denial = new NacosException(NacosException.NO_RIGHT, "Hub manage revoked");
        doNothing().doThrow(denial).when(f.authorization).requireForceUnsharePermission();
        
        assertSame(denial, assertThrows(NacosException.class,
            () -> f.service.forceUnshare(f.force(7, "policy violation"))));
        assertEquals(1, f.repository.readCount);
        assertEquals(0, f.repository.disableCount);
        assertEquals(Long.valueOf(7L), f.repository.entry.getMetaVersion());
        verifyNoInteractions(f.audit);
    }
    
    @Test
    void reactivationAfterCommittedUnshareReturnsConflictAndAuditsCommittedState()
        throws Exception {
        Fixture f = new Fixture();
        f.repository.afterDisable = () -> {
            f.repository.entry.setStatus(AiResourceConstants.META_STATUS_ENABLE);
            f.repository.entry.setMetaVersion(9L);
        };
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.service.unshareSkill(f.unshare(7))).getErrCode());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, f.repository.entry.getStatus());
        assertEquals(Long.valueOf(9L), f.repository.entry.getMetaVersion());
        assertEquals(1, f.repository.disableCount);
        verify(f.audit, times(1)).recordUnshare(eq(901L), any(), eq(8L), eq(false), eq(null));
    }
    
    private static final class Fixture {
        
        private final InMemoryRepository repository = new InMemoryRepository();
        
        private final HubSkillEligibilityValidator eligibility =
            mock(HubSkillEligibilityValidator.class);
        
        private final HubSkillAuthorizationService authorization =
            mock(HubSkillAuthorizationService.class);
        
        private final HubTakedownAuditService audit = mock(HubTakedownAuditService.class);
        
        private final HubSkillUnshareService service = new HubSkillUnshareService(repository,
            eligibility, authorization, audit);
        
        private final AiResource source = new AiResource();
        
        private Fixture() throws Exception {
            repository.entry = new AiResource();
            repository.entry.setId(901L);
            repository.entry.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
            repository.entry.setName("skill-a-101");
            repository.entry.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
            repository.entry.setFrom("hub");
            repository.entry.setStatus(AiResourceConstants.META_STATUS_ENABLE);
            repository.entry.setMetaVersion(7L);
            repository.entry.setExt(HubResourceReferenceJsonUtil.toJson(
                HubResourceReferenceJsonUtil.build("ns-a", 101L, "skill-a", 501L, "1.0.0")));
            source.setId(101L);
            source.setNamespaceId("ns-a");
            source.setName("skill-a");
            source.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
            when(eligibility.requireReferencedSource(any())).thenReturn(source);
        }
        
        private HubUnshareRequest unshare(long expected) {
            HubUnshareRequest request = new HubUnshareRequest();
            request.setHubEntryId(901L);
            request.setExpectedMetaVersion(expected);
            return request;
        }
        
        private HubForceUnshareRequest force(long expected, String reason) {
            HubForceUnshareRequest request = new HubForceUnshareRequest();
            request.setHubEntryId(901L);
            request.setExpectedMetaVersion(expected);
            request.setReason(reason);
            return request;
        }
    }
    
    /** Minimal synchronized repository with a real compare-and-set state change. */
    private static class InMemoryRepository implements HubShareRepository {
        
        private AiResource entry;
        
        private int disableCount;
        
        private int readCount;
        
        private boolean loseNextCasToAdjust;
        
        private boolean loseNextCasToUnshare;
        
        private Runnable afterDisable;
        
        @Override
        public synchronized AiResource findByEntryId(long hubEntryId) {
            readCount++;
            return entry == null || !Long.valueOf(hubEntryId).equals(entry.getId())
                ? null : snapshot(entry);
        }
        
        @Override
        public synchronized boolean disable(long hubEntryId, long expectedMetaVersion) {
            if (entry == null || !Long.valueOf(hubEntryId).equals(entry.getId())
                || !Long.valueOf(expectedMetaVersion).equals(entry.getMetaVersion())) {
                return false;
            }
            if (loseNextCasToAdjust || loseNextCasToUnshare) {
                entry.setStatus(loseNextCasToAdjust ? AiResourceConstants.META_STATUS_ENABLE
                    : AiResourceConstants.META_STATUS_DISABLE);
                entry.setMetaVersion(expectedMetaVersion + 1L);
                loseNextCasToAdjust = false;
                loseNextCasToUnshare = false;
                return false;
            }
            entry.setStatus(AiResourceConstants.META_STATUS_DISABLE);
            entry.setMetaVersion(expectedMetaVersion + 1L);
            disableCount++;
            if (afterDisable != null) {
                Runnable hook = afterDisable;
                afterDisable = null;
                hook.run();
            }
            return true;
        }
        
        @Override
        public AiResource reserveEntry(HubResourceReference reference, String description) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public AiResource findBySource(HubResourceReference source) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public AiResourceVersion ensureReferenceVersion(long hubEntryId,
            HubResourceReference reference) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public AiResourceVersion requireReferenceVersion(long hubEntryId,
            HubResourceReference reference) {
            throw new UnsupportedOperationException("Unshare does not read Hub version rows");
        }
        
        @Override
        public boolean publish(long hubEntryId, long expectedMetaVersion,
            HubResourceReference reference) {
            throw new UnsupportedOperationException();
        }
        
        private static AiResource snapshot(AiResource item) {
            AiResource copy = new AiResource();
            copy.setId(item.getId());
            copy.setNamespaceId(item.getNamespaceId());
            copy.setName(item.getName());
            copy.setType(item.getType());
            copy.setFrom(item.getFrom());
            copy.setStatus(item.getStatus());
            copy.setMetaVersion(item.getMetaVersion());
            copy.setExt(item.getExt());
            return copy;
        }
    }
}
