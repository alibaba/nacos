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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.alibaba.nacos.ai.service.hub.HubSkillPublicationInvalidator.InvalidationReason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orphan reconciliation runs without an index, user identity or direct source READ. */
class HubSkillOrphanReconciliationServiceTest {
    
    private final HubEntryStorage storage = mock(HubEntryStorage.class);
    private final HubSkillReferenceResolver resolver = mock(HubSkillReferenceResolver.class);
    private final HubSkillSourceReadValidator validator = mock(HubSkillSourceReadValidator.class);
    private final HubSkillPublicationInvalidator invalidator =
        mock(HubSkillPublicationInvalidator.class);
    private final HubSkillOrphanReconciliationService subject =
        new HubSkillOrphanReconciliationService(storage, resolver, validator, invalidator);
    
    @Test
    void offlineOrDeletedSourceTriggersDatabaseRevocationNotJustIndexCleanup() throws Exception {
        AiResource entry = entry(901L, "enable");
        single(entry);
        when(resolver.resolve(901L))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "source no longer online"));
        when(invalidator.disableIf(eq(901L), any())).thenAnswer(
            inv -> ((InvalidationReason) inv.<InvalidationReason>getArgument(1)).test(entry));
        HubSkillOrphanReconciliationService.Report report = subject.reconcile();
        assertEquals(1, report.getExamined());
        assertEquals(1, report.getRevoked());
        verify(invalidator).disableIf(eq(901L), any());
    }
    
    @Test
    void healthyPublicationNeverRevoked() throws Exception {
        AiResource entry = entry(901L, "enable");
        single(entry);
        ResolvedHubSkillReference snapshot = mock(ResolvedHubSkillReference.class);
        when(snapshot.getHubMetaVersion()).thenReturn(3L);
        when(snapshot.getInternalName()).thenReturn("entry-901");
        when(resolver.resolve(901L)).thenReturn(snapshot);
        when(invalidator.disableIf(eq(901L), any())).thenAnswer(
            inv -> ((InvalidationReason) inv.<InvalidationReason>getArgument(1)).test(entry));
        assertEquals(0, subject.reconcile().getRevoked());
        verify(validator).requireLiveSourceForIndex(snapshot);
    }
    
    @Test
    void alreadyDisabledIsNotReprocessedOrAutomaticallyReactivated() throws Exception {
        single(entry(901L, "disable"));
        assertEquals(0, subject.reconcile().getRevoked());
        verify(invalidator, never()).disableIf(eq(901L), any());
    }
    
    @Test
    void aMissingIndexRuntimeIsNotNeededForTheSafetyRepair() throws Exception {
        assertEquals(0, subject.reconcile().getExamined());
    }
    
    @Test
    void scanCursorLoopFailsRatherThanSilentlyReportingHealthy() throws Exception {
        when(storage.maxEntryId()).thenReturn(901L);
        when(storage.nextEntryId(0L, 901L)).thenReturn(0L);
        assertThrows(IllegalStateException.class, subject::reconcile);
    }
    
    @Test
    void referenceBackendFailureCannotWithdrawAHealthyPublication() throws Exception {
        AiResource entry = entry(901L, "enable");
        single(entry);
        when(storage.findByEntryId(901L)).thenReturn(entry);
        when(resolver.resolve(901L)).thenThrow(new NacosException(503, "temporary outage"));
        HubSkillOrphanReconciliationService actual = new HubSkillOrphanReconciliationService(
            storage, resolver, validator, new HubSkillPublicationInvalidator(storage,
                mock(HubSkillIndexNotifier.class)));
        assertEquals(503, assertThrows(NacosException.class, actual::reconcile).getErrCode());
        verify(storage, never()).updateMetaCas(any(), org.mockito.ArgumentMatchers.anyLong(),
            any());
    }
    
    @Test
    void sourceBackendFailureCannotBecomePermanentRevocation() throws Exception {
        AiResource entry = entry(901L, "enable");
        single(entry);
        when(storage.findByEntryId(901L)).thenReturn(entry);
        ResolvedHubSkillReference snapshot = mock(ResolvedHubSkillReference.class);
        when(snapshot.getHubMetaVersion()).thenReturn(3L);
        when(snapshot.getInternalName()).thenReturn("entry-901");
        when(resolver.resolve(901L)).thenReturn(snapshot);
        org.mockito.Mockito.doThrow(new NacosException(500, "source storage unavailable"))
            .when(validator).requireLiveSourceForIndex(snapshot);
        HubSkillOrphanReconciliationService actual = new HubSkillOrphanReconciliationService(
            storage, resolver, validator, new HubSkillPublicationInvalidator(storage,
                mock(HubSkillIndexNotifier.class)));
        assertEquals(500, assertThrows(NacosException.class, actual::reconcile).getErrCode());
        verify(storage, never()).updateMetaCas(any(), org.mockito.ArgumentMatchers.anyLong(),
            any());
    }
    
    @Test
    void concurrentHealthyAdjustIsRecheckedAfterLostCas() throws Exception {
        AiResource old = entry(901L, "enable");
        AiResource adjusted = entry(901L, "enable");
        adjusted.setMetaVersion(4L);
        single(old);
        when(storage.findByEntryId(901L)).thenReturn(old, adjusted);
        when(storage.updateMetaCas(eq(old), eq(3L), any())).thenReturn(false);
        ResolvedHubSkillReference snapshot = mock(ResolvedHubSkillReference.class);
        when(snapshot.getHubMetaVersion()).thenReturn(4L);
        when(snapshot.getInternalName()).thenReturn("entry-901");
        when(resolver.resolve(901L)).thenThrow(new NacosException(404, "old source offline"))
            .thenReturn(snapshot);
        HubSkillOrphanReconciliationService actual = new HubSkillOrphanReconciliationService(
            storage, resolver, validator, new HubSkillPublicationInvalidator(storage,
                mock(HubSkillIndexNotifier.class)));
        assertEquals(0, actual.reconcile().getRevoked());
        verify(storage, never()).updateMetaCas(eq(adjusted), eq(4L), any());
        verify(validator).requireLiveSourceForIndex(snapshot);
        verify(resolver).requireUnchanged(snapshot);
    }
    
    private void single(AiResource entry) {
        when(storage.maxEntryId()).thenReturn(901L);
        when(storage.nextEntryId(0L, 901L)).thenReturn(901L);
        when(storage.entriesInIdRange(901L, 901L)).thenReturn(List.of(entry));
    }
    
    private AiResource entry(long id, String status) {
        AiResource entry = new AiResource();
        entry.setId(id);
        entry.setNamespaceId(com.alibaba.nacos.api.ai.constant.AiConstants.Hub.NAMESPACE_ID);
        entry.setType("skill");
        entry.setFrom("hub");
        entry.setStatus(status);
        entry.setMetaVersion(3L);
        entry.setName("entry-901");
        return entry;
    }
}
