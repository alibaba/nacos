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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fault injections for the only ID/CAS publication disable primitive. */
class HubSkillPublicationInvalidatorTest {
    
    private final HubEntryStorage storage = mock(HubEntryStorage.class);
    
    private final HubSkillIndexNotifier notifier = mock(HubSkillIndexNotifier.class);
    
    private final HubSkillPublicationInvalidator subject =
        new HubSkillPublicationInvalidator(storage, notifier);
    
    @Test
    void disablesWithStrictCasAndPreservesProvenance() throws Exception {
        AiResource entry = entry(7L, "enable");
        entry.setExt("reference-v1");
        when(storage.findByEntryId(7L)).thenReturn(entry);
        when(storage.updateMetaCas(eq(entry), eq(3L), any(AiResource.class)))
            .thenAnswer(inv -> {
                AiResource desired = inv.getArgument(2);
                assertEquals("disable", desired.getStatus());
                assertEquals(entry.getExt(), desired.getExt());
                return true;
            });
        assertTrue(subject.disableIf(7L, current -> true));
        verify(notifier).entryChanged("find-skills-101");
    }
    
    @Test
    void aSecondAdministratorOrRetryCannotDoubleDisable() throws Exception {
        when(storage.findByEntryId(7L)).thenReturn(entry(7L, "disable"));
        assertFalse(subject.disableIf(7L, current -> true));
        verify(storage, never()).updateMetaCas(any(), anyLong(), any());
    }
    
    @Test
    void aCompetingAdjustWinningCasMustBeReevaluated() throws Exception {
        AiResource old = entry(7L, "enable");
        AiResource newer = entry(7L, "enable");
        newer.setMetaVersion(4L);
        when(storage.findByEntryId(7L)).thenReturn(old, newer);
        when(storage.updateMetaCas(eq(old), eq(3L), any(AiResource.class))).thenReturn(false);
        AtomicInteger predicates = new AtomicInteger();
        assertFalse(subject.disableIf(7L, current -> predicates.incrementAndGet() == 1));
        assertEquals(2, predicates.get());
        verify(notifier, never()).entryChanged(any());
    }
    
    @Test
    void retriesMustStopRatherThanClaimSuccessIfCasAlwaysLoses() throws Exception {
        when(storage.findByEntryId(7L)).thenReturn(entry(7L, "enable"));
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> subject.disableIf(7L, row -> true)).getErrCode());
        verify(notifier, never()).entryChanged(any());
    }
    
    @Test
    void neverDisableAnOrdinaryNamespaceRow() throws Exception {
        AiResource notHub = entry(7L, "enable");
        notHub.setNamespaceId("tenant-a");
        when(storage.findByEntryId(7L)).thenReturn(notHub);
        assertThrows(NacosException.class, () -> subject.disableIf(7L, row -> true));
        verify(storage, never()).updateMetaCas(any(), anyLong(), any());
    }
    
    @Test
    void absentReferenceIsAnIdempotentNoop() throws Exception {
        assertFalse(subject.disableIf(7L, row -> true));
    }
    
    @Test
    void storageFailureIsControlledAndDoesNotNotify() {
        when(storage.findByEntryId(7L)).thenThrow(new IllegalStateException("private SQL details"));
        NacosException failure = assertThrows(NacosException.class,
            () -> subject.disableIf(7L, current -> true));
        assertEquals(500, failure.getErrCode());
        assertFalse(failure.getMessage().contains("SQL"));
        verify(notifier, never()).entryChanged(any());
    }
    
    @Test
    void unavailableInvalidationReasonNeverWritesMetadata() throws Exception {
        when(storage.findByEntryId(7L)).thenReturn(entry(7L, "enable"));
        assertEquals(503,
            assertThrows(NacosException.class, () -> subject.disableIf(7L, current -> {
                throw new NacosException(503, "unavailable");
            })).getErrCode());
        verify(storage, never()).updateMetaCas(any(), anyLong(), any());
        verify(notifier, never()).entryChanged(any());
    }
    
    private AiResource entry(long id, String status) {
        AiResource e = new AiResource();
        e.setId(id);
        e.setName("find-skills-101");
        e.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        e.setType("skill");
        e.setFrom("hub");
        e.setMetaVersion(3L);
        e.setStatus(status);
        return e;
    }
}
