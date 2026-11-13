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
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Simulates two independent admin nodes sharing only the publication's DB/CAS. */
class HubSkillAdminTakedownS4Test {
    
    private final HubShareRepository repository = mock(HubShareRepository.class);
    private final HubSkillEligibilityValidator eligibility =
        mock(HubSkillEligibilityValidator.class);
    private final HubSkillAuthorizationService permission =
        mock(HubSkillAuthorizationService.class);
    private final HubTakedownAuditService audit = mock(HubTakedownAuditService.class);
    private final HubSkillUnshareService subject =
        new HubSkillUnshareService(repository, eligibility, permission, audit);
    
    @Test
    void twoConcurrentAdministratorsCannotDoubleCommitTheSameTakedown() throws Exception {
        AtomicReference<AiResource> record = new AtomicReference<>(entry(1L, "enable"));
        when(repository.findByEntryId(901L)).thenAnswer(inv -> record.get());
        when(repository.disable(901L, 1L)).thenAnswer(inv -> {
            synchronized (record) {
                if (record.get().getMetaVersion() != 1L) {
                    return false;
                }
                record.set(entry(2L, "disable"));
                return true;
            }
        });
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<HubShareResult> a = threads.submit(() -> {
                go.await();
                return subject.forceUnshare(request());
            });
            Future<HubShareResult> b = threads.submit(() -> {
                go.await();
                return subject.forceUnshare(request());
            });
            go.countDown();
            HubShareOutcome first = a.get(10L, TimeUnit.SECONDS).getOutcome();
            HubShareOutcome second = b.get(10L, TimeUnit.SECONDS).getOutcome();
            assertTrue(first == HubShareOutcome.FORCE_UNSHARED
                || first == HubShareOutcome.ALREADY_UNSHARED);
            assertTrue(second == HubShareOutcome.FORCE_UNSHARED
                || second == HubShareOutcome.ALREADY_UNSHARED);
            assertEquals("disable", record.get().getStatus());
            assertEquals(2L, record.get().getMetaVersion().longValue());
            verify(audit, times(1)).recordUnshare(anyLong(), any(), anyLong(),
                anyBoolean(), anyString());
            verify(eligibility, never()).requireReferencedSource(any());
        } finally {
            threads.shutdownNow();
        }
    }
    
    @Test
    void adminTakedownMustNotOverwriteAConcurrentVersionAdjust() throws Exception {
        when(repository.findByEntryId(901L)).thenReturn(entry(1L, "enable"),
            entry(2L, "enable"));
        when(repository.disable(901L, 1L)).thenReturn(false);
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> subject.forceUnshare(request())).getErrCode());
        verify(audit, never()).recordUnshare(anyLong(), any(), anyLong(), anyBoolean(),
            anyString());
    }
    
    private HubForceUnshareRequest request() {
        HubForceUnshareRequest result = new HubForceUnshareRequest();
        result.setHubEntryId(901L);
        result.setExpectedMetaVersion(1L);
        result.setReason("Security takedown");
        return result;
    }
    
    private AiResource entry(long metaVersion, String status) throws NacosException {
        AiResource entry = new AiResource();
        entry.setId(901L);
        entry.setMetaVersion(metaVersion);
        entry.setStatus(status);
        entry.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        entry.setName("find-skills-101");
        entry.setType("skill");
        entry.setFrom("hub");
        entry.setExt(HubResourceReferenceJsonUtil.toJson(
            HubResourceReferenceJsonUtil.build("team-a", 101L,
                "find-skills", 501L, "1.0.0")));
        return entry;
    }
}
