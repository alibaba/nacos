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
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Behavior tests for the Hub persistence adapter with deterministic DB fakes. */
class HubShareRepositoryImplTest {
    
    @Test
    void reserveEntryMustBeDisabledAndStableAcrossRetries() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference one = ref(101L, 501L, "1.0.0");
        HubResourceReference two = ref(101L, 601L, "2.0.0");
        AiResource first = fixture.repository.reserveEntry(one, "demo");
        AiResource repeated = fixture.repository.reserveEntry(two, "ignored");
        assertEquals(first.getId(), repeated.getId());
        assertEquals(1, fixture.metaByName.size());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, repeated.getStatus());
        assertEquals("hub", repeated.getFrom());
        assertEquals(AiConstants.Hub.NAMESPACE_ID, repeated.getNamespaceId());
        assertEquals(Long.valueOf(501L), HubResourceReferenceJsonUtil.parse(repeated.getExt())
            .getSourceVersionId());
    }
    
    @Test
    void differentSourceCannotReuseAnExistingEntry() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference first = ref(101L, 501L, "1.0.0");
        fixture.repository.reserveEntry(first, "demo");
        HubResourceReference forged = ref(101L, 501L, "1.0.0");
        forged.setSourceNamespaceId("another-namespace");
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class,
                () -> fixture.repository.reserveEntry(forged, "another")).getErrCode());
    }
    
    @Test
    void sameVersionCanBeReusedButNotOverwritten() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference first = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(first, "demo");
        AiResourceVersion original =
            fixture.repository.ensureReferenceVersion(entry.getId(), first);
        AiResourceVersion again = fixture.repository.ensureReferenceVersion(entry.getId(), first);
        assertEquals(original.getId(), again.getId());
        assertEquals(1, fixture.versionByKey.size());
        HubResourceReference replacedId = ref(101L, 777L, "1.0.0");
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> fixture.repository.ensureReferenceVersion(entry.getId(), replacedId))
            .getErrCode());
        assertEquals(Long.valueOf(501L), HubResourceReferenceJsonUtil.parse(original.getStorage())
            .getSourceVersionId());
    }
    
    @Test
    void requireReferenceVersionMustRejectMissingRowsWithoutInserting() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference reference = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(reference, "demo");
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> fixture.repository.requireReferenceVersion(entry.getId(), reference))
            .getErrCode());
        verify(fixture.versions, never()).insert(any(AiResourceVersion.class));
        assertTrue(fixture.versionByKey.isEmpty());
        assertEquals(Long.valueOf(1L),
            fixture.repository.findByEntryId(entry.getId()).getMetaVersion());
    }
    
    @Test
    void requireReferenceVersionMustValidateExistingDescriptorWithoutWriting() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference reference = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(reference, "demo");
        AiResourceVersion version =
            fixture.repository.ensureReferenceVersion(entry.getId(), reference);
        clearInvocations(fixture.versions);
        
        assertSame(version, fixture.repository.requireReferenceVersion(entry.getId(), reference));
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> fixture.repository.requireReferenceVersion(entry.getId(),
                ref(101L, 999L, "1.0.0")))
            .getErrCode());
        verify(fixture.versions, never()).insert(any(AiResourceVersion.class));
        assertEquals(reference.getSourceVersionId(),
            HubResourceReferenceJsonUtil.parse(version.getStorage())
                .getSourceVersionId());
    }
    
    @Test
    void requireReferenceVersionMustRejectOfflineRowsWithoutRepair() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference reference = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(reference, "demo");
        AiResourceVersion version =
            fixture.repository.ensureReferenceVersion(entry.getId(), reference);
        version.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        clearInvocations(fixture.versions);
        
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> fixture.repository.requireReferenceVersion(entry.getId(), reference))
            .getErrCode());
        verify(fixture.versions, never()).insert(any(AiResourceVersion.class));
        assertEquals(AiResourceConstants.VERSION_STATUS_OFFLINE, version.getStatus());
    }
    
    @Test
    void publishRequiresPreparedVersionAndUpdatesPointerAtomically() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference ref = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(ref, "demo");
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> fixture.repository.publish(entry.getId(), 1L, ref)).getErrCode());
        fixture.repository.ensureReferenceVersion(entry.getId(), ref);
        assertTrue(fixture.repository.publish(entry.getId(), 1L, ref));
        AiResource published = fixture.repository.findByEntryId(entry.getId());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, published.getStatus());
        assertEquals(Long.valueOf(2L), published.getMetaVersion());
        assertEquals(ref.getSourceVersionId(), HubResourceReferenceJsonUtil.parse(
            published.getExt()).getSourceVersionId());
        assertTrue(published.getVersionInfo().contains("1.0.0"));
        assertFalse(fixture.repository.publish(entry.getId(), 1L, ref));
    }
    
    @Test
    void disablePreservesStableIdAndOriginalSource() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference ref = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(ref, "demo");
        fixture.repository.ensureReferenceVersion(entry.getId(), ref);
        assertTrue(fixture.repository.publish(entry.getId(), 1L, ref));
        assertTrue(fixture.repository.disable(entry.getId(), 2L));
        AiResource disabled = fixture.repository.findByEntryId(entry.getId());
        assertEquals(entry.getId(), disabled.getId());
        assertEquals(AiResourceConstants.META_STATUS_DISABLE, disabled.getStatus());
        assertEquals(Long.valueOf(3L), disabled.getMetaVersion());
        assertEquals("1.0.0", HubResourceReferenceJsonUtil.parse(disabled.getExt())
            .getSourceVersion());
        assertEquals(1, fixture.versionByKey.size());
    }
    
    @Test
    void failedVersionPrepareDoesNotActivateEntryAndIsRetryable() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference ref = ref(101L, 501L, "1.0.0");
        AiResource entry = fixture.repository.reserveEntry(ref, "demo");
        fixture.failNextVersionInsert.set(true);
        assertThrows(IllegalStateException.class,
            () -> fixture.repository.ensureReferenceVersion(entry.getId(), ref));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE,
            fixture.repository.findByEntryId(entry.getId()).getStatus());
        fixture.repository.ensureReferenceVersion(entry.getId(), ref);
        assertTrue(fixture.repository.publish(entry.getId(), 1L, ref));
    }
    
    @Test
    void competingAdjustAndDisableCannotBothCommitSameExpectedVersion() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference first = ref(101L, 501L, "1.0.0");
        HubResourceReference second = ref(101L, 601L, "2.0.0");
        AiResource entry = fixture.repository.reserveEntry(first, "demo");
        fixture.repository.ensureReferenceVersion(entry.getId(), first);
        assertTrue(fixture.repository.publish(entry.getId(), 1L, first));
        fixture.repository.ensureReferenceVersion(entry.getId(), second);
        assertTrue(fixture.repository.disable(entry.getId(), 2L));
        assertFalse(fixture.repository.publish(entry.getId(), 2L, second));
        assertEquals(AiResourceConstants.META_STATUS_DISABLE,
            fixture.repository.findByEntryId(entry.getId()).getStatus());
    }
    
    @Test
    void concurrentReservationsMustConvergeToOneEntry() throws Exception {
        Fixture fixture = new Fixture();
        HubResourceReference reference = ref(101L, 501L, "1.0.0");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Long> one =
                workers.submit(() -> reserveInParallel(fixture, reference, ready, start));
            Future<Long> two =
                workers.submit(() -> reserveInParallel(fixture, reference, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertEquals(1, fixture.metaByName.size());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    
    private long reserveInParallel(Fixture fixture, HubResourceReference ref,
        CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return fixture.repository.reserveEntry(ref, "demo").getId();
    }
    
    private static HubResourceReference ref(Long resourceId, Long versionId, String version)
        throws NacosException {
        return HubResourceReferenceJsonUtil.build("source-a", resourceId, "find-skills",
            versionId, version);
    }
    
    private static final class Fixture {
        
        private final Map<String, AiResource> metaByName = new HashMap<>();
        
        private final Map<String, AiResourceVersion> versionByKey = new HashMap<>();
        
        private final Object mutex = new Object();
        
        private final AtomicLong id = new AtomicLong(1000);
        
        private final AtomicBoolean failNextVersionInsert = new AtomicBoolean();
        
        private final HubShareRepository repository;
        
        private final AiResourceVersionPersistService versions =
            mock(AiResourceVersionPersistService.class);
        
        private Fixture() {
            HubEntryStorage hubStorage = mock(HubEntryStorage.class);
            AiResourcePersistService resources = mock(AiResourcePersistService.class);
            when(hubStorage.findByInternalName(anyString())).thenAnswer(invocation -> {
                synchronized (mutex) {
                    return copy(metaByName.get(invocation.getArgument(0)));
                }
            });
            when(hubStorage.findByEntryId(anyLong())).thenAnswer(invocation -> {
                synchronized (mutex) {
                    long key = invocation.getArgument(0);
                    return metaByName.values().stream().filter(r -> r.getId() == key)
                        .findFirst().map(Fixture::copy).orElse(null);
                }
            });
            doAnswer(invocation -> {
                synchronized (mutex) {
                    AiResource newRow = invocation.getArgument(0);
                    if (metaByName.containsKey(newRow.getName())) {
                        throw new IllegalStateException("duplicate meta");
                    }
                    newRow.setId(id.incrementAndGet());
                    metaByName.put(newRow.getName(), copy(newRow));
                    return newRow.getId();
                }
            }).when(resources).insert(any(AiResource.class));
            when(hubStorage.updateMetaCas(any(AiResource.class), anyLong(),
                any(AiResource.class))).thenAnswer(invocation -> {
                    synchronized (mutex) {
                        AiResource old = invocation.getArgument(0);
                        long expected = invocation.getArgument(1);
                        AiResource next = invocation.getArgument(2);
                        AiResource current = metaByName.get(old.getName());
                        if (current == null || !Objects.equals(current.getId(), old.getId())
                            || !Objects.equals(current.getMetaVersion(), expected)) {
                            return false;
                        }
                        current.setStatus(next.getStatus());
                        current.setExt(next.getExt());
                        current.setDesc(next.getDesc());
                        current.setBizTags(next.getBizTags());
                        current.setVersionInfo(next.getVersionInfo());
                        current.setMetaVersion(expected + 1);
                        return true;
                    }
                });
            when(versions.find(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    synchronized (mutex) {
                        return versionByKey.get(invocation.getArgument(1) + ":"
                            + invocation.getArgument(3));
                    }
                });
            doAnswer(invocation -> {
                synchronized (mutex) {
                    if (failNextVersionInsert.getAndSet(false)) {
                        throw new IllegalStateException("injected version insert failure");
                    }
                    AiResourceVersion row = invocation.getArgument(0);
                    String key = row.getName() + ":" + row.getVersion();
                    if (versionByKey.containsKey(key)) {
                        throw new IllegalStateException("duplicate version");
                    }
                    row.setId(id.incrementAndGet());
                    versionByKey.put(key, row);
                    return row.getId();
                }
            }).when(versions).insert(any(AiResourceVersion.class));
            repository = new HubShareRepositoryImpl(hubStorage, resources, versions);
        }
        
        private static AiResource copy(AiResource source) {
            if (source == null) {
                return null;
            }
            AiResource next = new AiResource();
            next.setId(source.getId());
            next.setName(source.getName());
            next.setType(source.getType());
            next.setNamespaceId(source.getNamespaceId());
            next.setFrom(source.getFrom());
            next.setOwner(source.getOwner());
            next.setScope(source.getScope());
            next.setStatus(source.getStatus());
            next.setExt(source.getExt());
            next.setMetaVersion(source.getMetaVersion());
            next.setVersionInfo(source.getVersionInfo());
            next.setDesc(source.getDesc());
            next.setBizTags(source.getBizTags());
            return next;
        }
    }
}
