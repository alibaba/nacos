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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityMode;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aMigrationStateServiceTest {
    
    private static final String STORAGE_PROVIDER = "nacos_config";
    
    private final AtomicLong now = new AtomicLong(1000L);
    
    private InMemoryControlStore controlStore;
    
    private ServerMemberManager memberManager;
    
    private Member self;
    
    private A2aMigrationStateService service;
    
    private int memberSequence;
    
    @BeforeEach
    void setUp() {
        controlStore = new InMemoryControlStore();
        memberManager = mock(ServerMemberManager.class);
        self = mock(Member.class);
        when(memberManager.getSelf()).thenReturn(self);
        memberSequence = 0;
        service = newService(false, A2aCompatibilityMode.AUTO);
    }
    
    @Test
    void autoShouldCreateSyncingPlanAndStaticModesShouldIgnoreIt() {
        assertEquals(A2aMigrationState.SYNCING, service.resolve(A2aCompatibilityMode.AUTO));
        assertNotNull(controlStore.marker);
        assertEquals("generation-1", controlStore.marker.getValue().getGeneration());
        assertFalse(controlStore.marker.getValue().isLegacyNamingShadow());
        assertEquals(STORAGE_PROVIDER, controlStore.marker.getValue().getStorageProvider());
        assertNull(service.resolve(A2aCompatibilityMode.LEGACY));
        assertNull(service.resolve(A2aCompatibilityMode.CANONICAL));
    }
    
    @Test
    void shouldResolveConfiguredCompatibilityMode() {
        assertEquals(A2aMigrationState.SYNCING, service.resolveConfigured());
        service = newService(false, A2aCompatibilityMode.LEGACY);
        assertNull(service.resolveConfigured());
    }
    
    @Test
    void authoritativeResolveShouldBypassIndependentContextMarkerCache() {
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        controlStore.marker = versioned(syncing, "syncing");
        assertEquals(A2aMigrationState.SYNCING, service.resolveConfigured());
        A2aMigrationMarker quiescing = syncing.transition(A2aMigrationState.QUIESCING,
            "q", now.incrementAndGet());
        controlStore.marker = versioned(quiescing, "quiescing");
        assertEquals(A2aMigrationState.SYNCING, service.resolveConfigured());
        assertEquals(A2aMigrationState.QUIESCING,
            service.resolveConfiguredAuthoritative());
        assertEquals(A2aMigrationState.QUIESCING, service.resolveConfigured());
    }
    
    @Test
    void shouldReadFrozenLegacyNamingShadowPolicy() {
        assertFalse(service.isLegacyNamingShadowEnabled());
        controlStore.marker = versioned(
            A2aMigrationMarker.syncing("shadow", true, STORAGE_PROVIDER, now.get()),
            "shadow-md5");
        service = newService(false, A2aCompatibilityMode.AUTO);
        assertTrue(service.isLegacyNamingShadowEnabled());
        controlStore.marker.getValue().setUpdatedAt(0L);
        service = newService(false, A2aCompatibilityMode.AUTO);
        assertFalse(service.isLegacyNamingShadowEnabled());
    }
    
    @Test
    void terminalMarkerShouldOverrideEveryModeAndLatchInProcess() {
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        A2aMigrationMarker canonical = syncing.transition(A2aMigrationState.CANONICAL, "g",
            now.incrementAndGet());
        controlStore.marker = versioned(canonical, "marker-1");
        assertEquals(A2aMigrationState.CANONICAL,
            service.resolve(A2aCompatibilityMode.LEGACY));
        controlStore.marker = null;
        now.addAndGet(5000L);
        assertEquals(A2aMigrationState.CANONICAL,
            service.resolve(A2aCompatibilityMode.LEGACY));
        assertEquals(A2aMigrationState.CANONICAL,
            service.resolve(A2aCompatibilityMode.AUTO));
    }
    
    @Test
    void invalidMarkerShouldNeverCutOver() {
        A2aMigrationMarker invalid = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        invalid.setState(A2aMigrationState.CANONICAL);
        controlStore.marker = versioned(invalid, "invalid");
        assertEquals(A2aMigrationState.SYNCING,
            service.resolve(A2aCompatibilityMode.AUTO));
        assertNull(service.resolve(A2aCompatibilityMode.LEGACY));
    }
    
    @Test
    void localCapabilityShouldBePublishedAtStartupAndResolve() {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getParent()).thenReturn(null);
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getApplicationContext()).thenReturn(context);
        service.onApplicationEvent(event);
        verify(self).setExtendVal(MemberMetaDataConstants.SUPPORT_A2A_MIGRATION_V1, true);
        verify(self).setExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH,
            service.policyHash(A2aCompatibilityMode.AUTO, false));
        service.resolve(A2aCompatibilityMode.LEGACY);
        verify(self).setExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH,
            service.policyHash(A2aCompatibilityMode.LEGACY, false));
        ConfigurableApplicationContext child = mock(ConfigurableApplicationContext.class);
        when(child.getParent()).thenReturn(context);
        ApplicationReadyEvent childEvent = mock(ApplicationReadyEvent.class);
        when(childEvent.getApplicationContext()).thenReturn(child);
        service.onApplicationEvent(childEvent);
    }
    
    @Test
    void publicConstructorShouldUseNormalizedEnvironmentPolicy() {
        try (MockedStatic<EnvUtil> envUtil = org.mockito.Mockito.mockStatic(EnvUtil.class)) {
            envUtil.when(() -> EnvUtil.getProperty("nacos.ai.a2a.compatibility.mode",
                A2aCompatibilityMode.CANONICAL.name())).thenReturn("  ");
            envUtil.when(() -> EnvUtil.getProperty(
                A2aMigrationStateService.LEGACY_NAMING_SHADOW_ENABLED_PROPERTY, "false"))
                .thenReturn("true");
            A2aMigrationStateService environmentService =
                new A2aMigrationStateService(controlStore, memberManager);
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
            when(context.getParent()).thenReturn(null);
            ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
            when(event.getApplicationContext()).thenReturn(context);
            environmentService.onApplicationEvent(event);
            verify(self).setExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH,
                environmentService.policyHash(A2aCompatibilityMode.CANONICAL, true));
            assertEquals(A2aMigrationState.SYNCING,
                environmentService.resolve(A2aCompatibilityMode.AUTO));
            assertTrue(controlStore.marker.getValue().isLegacyNamingShadow());
            assertFalse(controlStore.marker.getValue().getGeneration().isEmpty());
        }
    }
    
    @Test
    void memberReadinessShouldRejectIncompleteOrMismatchedMetadata() {
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("g", true,
            STORAGE_PROVIDER, now.get());
        assertFalse(service.allMembersReady(null));
        marker.setUpdatedAt(0L);
        assertFalse(service.allMembersReady(marker));
        marker.setUpdatedAt(now.get());
        when(memberManager.allMembers()).thenReturn(null, Collections.emptyList());
        assertFalse(service.allMembersReady(marker));
        assertFalse(service.allMembersReady(marker));
        Member missing = mock(Member.class);
        Member invalidAbility = member("true", service.policyHash(A2aCompatibilityMode.AUTO, true));
        Member mismatched = member(true, "different");
        Member readyOne = member(true, service.policyHash(A2aCompatibilityMode.AUTO, true));
        Member readyTwo = member(true, service.policyHash(A2aCompatibilityMode.AUTO, true));
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(missing),
            Collections.singletonList(invalidAbility), Collections.singletonList(mismatched),
            Arrays.asList(readyOne, readyTwo));
        assertFalse(service.allMembersReady(marker));
        assertFalse(service.allMembersReady(marker));
        assertFalse(service.allMembersReady(marker));
        assertTrue(service.allMembersReady(marker));
    }
    
    @Test
    void storageProviderShouldBeFrozenAndGuardReconciliationWriters() {
        service = newService(false, A2aCompatibilityMode.AUTO, "object_storage");
        assertEquals(A2aMigrationState.SYNCING, service.resolveConfigured());
        A2aMigrationMarker marker = controlStore.marker.getValue();
        assertEquals("object_storage", marker.getStorageProvider());
        assertTrue(service.isLocalPolicyCompatible(marker));
        assertNotEquals(service.policyHash(A2aCompatibilityMode.AUTO, false,
            STORAGE_PROVIDER),
            service.policyHash(A2aCompatibilityMode.AUTO, false,
                "object_storage"));
        
        A2aMigrationStateService mismatched = newService(false,
            A2aCompatibilityMode.AUTO, STORAGE_PROVIDER);
        assertFalse(mismatched.isLocalPolicyCompatible(marker));
        marker.setStorageProvider(" ");
        assertFalse(mismatched.isLocalPolicyCompatible(marker));
        assertFalse(mismatched.isLocalPolicyCompatible(null));
    }
    
    @Test
    void memberViewAndAcknowledgementsShouldRequireExactHealthyGeneration() {
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        Member first = member(true, service.policyHash(A2aCompatibilityMode.AUTO, false));
        Member second = member(true, service.policyHash(A2aCompatibilityMode.AUTO, false));
        when(memberManager.allMembers()).thenReturn(Arrays.asList(first, second));
        A2aMigrationMemberView view = service.readyMemberView(syncing);
        assertNotNull(view);
        assertEquals(2, view.getMemberCount());
        String generation = A2aMigrationQuiescingGeneration.create(view, "nonce");
        A2aMigrationMarker quiescing = syncing.transition(A2aMigrationState.QUIESCING,
            generation, now.incrementAndGet());
        assertFalse(service.allMembersAcknowledged(null, view));
        assertFalse(service.allMembersAcknowledged(syncing, view));
        assertFalse(service.allMembersAcknowledged(quiescing, null));
        assertFalse(service.allMembersAcknowledged(quiescing, view));
        when(first.getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK))
            .thenReturn(generation + ":READY");
        when(second.getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK))
            .thenReturn("old:READY", generation + ":READY");
        assertFalse(service.allMembersAcknowledged(quiescing, view));
        assertTrue(service.allMembersAcknowledged(quiescing, view));
        
        Member replacement = member(true,
            service.policyHash(A2aCompatibilityMode.AUTO, false));
        when(replacement.getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK))
            .thenReturn(generation + ":READY");
        when(memberManager.allMembers()).thenReturn(Arrays.asList(first, replacement));
        assertFalse(service.allMembersAcknowledged(quiescing, view));
        when(replacement.getState()).thenReturn(NodeState.DOWN);
        assertNull(service.readyMemberView(quiescing));
    }
    
    @Test
    void localAcknowledgementShouldRecheckMarkerAndClearStaleValue() {
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        A2aMigrationMarker quiescing = syncing.transition(A2aMigrationState.QUIESCING,
            "q", now.incrementAndGet());
        assertFalse(service.advertiseLocalAck(null));
        assertFalse(service.advertiseLocalAck(syncing));
        controlStore.marker = versioned(quiescing, "quiescing");
        assertTrue(service.advertiseLocalAck(quiescing));
        verify(self).setExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK, "q:READY");
        service.clearLocalAck();
        verify(self).delExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK);
        
        controlStore.marker = versioned(syncing, "syncing");
        assertFalse(service.advertiseLocalAck(quiescing));
        controlStore.marker = versioned(quiescing, "quiescing-2");
        when(memberManager.getSelf()).thenReturn(null).thenThrow(
            new IllegalStateException("self unavailable"));
        assertFalse(service.advertiseLocalAck(quiescing));
        assertFalse(service.advertiseLocalAck(quiescing));
        service.clearLocalAck();
    }
    
    @Test
    void shouldExposeFreshMarkerGenerationAndClockForCoordinator() {
        assertEquals("generation-1", service.newGeneration());
        assertEquals(now.get(), service.currentTimeMillis());
        assertNull(service.currentMarker());
    }
    
    @Test
    void memberReadinessAndCapabilityFailuresShouldFailClosed() {
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        when(memberManager.allMembers()).thenThrow(new IllegalStateException("member failure"));
        assertFalse(service.allMembersReady(marker));
        when(memberManager.getSelf()).thenReturn(null).thenThrow(
            new IllegalStateException("self failure"));
        assertNull(service.resolve(A2aCompatibilityMode.LEGACY));
        assertNull(service.resolve(A2aCompatibilityMode.LEGACY));
    }
    
    @Test
    void markerRefreshShouldBeBoundedAndFailClosed() {
        assertNull(service.resolve(A2aCompatibilityMode.LEGACY));
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g", false,
            STORAGE_PROVIDER, now.get());
        A2aMigrationMarker canonical = syncing.transition(A2aMigrationState.CANONICAL, "g",
            now.incrementAndGet());
        controlStore.marker = versioned(canonical, "canonical");
        assertNull(service.resolve(A2aCompatibilityMode.LEGACY));
        assertEquals(A2aMigrationState.CANONICAL,
            service.refreshMarkerForTest().getValue().getState());
        assertEquals(A2aMigrationState.CANONICAL,
            service.refreshMarkerForTest().getValue().getState());
        
        A2aMigrationStateService failingService = newService(false, A2aCompatibilityMode.AUTO);
        controlStore.marker = null;
        controlStore.markerReadFailures = 1;
        assertNull(failingService.refreshMarkerForTest());
    }
    
    @Test
    void markerCasShouldResolveUncertainResultAndRejectIllegalTransitions() {
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g1", false,
            STORAGE_PROVIDER, now.get());
        controlStore.marker = versioned(syncing, "marker-1");
        controlStore.throwAfterMarkerWrite = true;
        VersionedValue<A2aMigrationMarker> quiescing = service.transition(controlStore.marker,
            A2aMigrationState.QUIESCING, "g2");
        assertNotNull(quiescing);
        assertEquals(A2aMigrationState.QUIESCING, quiescing.getValue().getState());
        VersionedValue<A2aMigrationMarker> canonical = service.transition(quiescing,
            A2aMigrationState.CANONICAL, "g2");
        assertNotNull(canonical);
        assertEquals(A2aMigrationState.CANONICAL, canonical.getValue().getState());
        assertTrue(canonical.getValue().getCompletedAt() > 0);
        assertNull(service.transition(canonical, A2aMigrationState.SYNCING, "g3"));
        assertNull(service.transition(null, A2aMigrationState.SYNCING, "g3"));
        assertNull(service.transition(quiescing, null, "g3"));
        assertNull(service.transition(quiescing, A2aMigrationState.SYNCING, " "));
    }
    
    @Test
    void markerCasConflictShouldNotPretendTransitionSucceeded() {
        A2aMigrationMarker syncing = A2aMigrationMarker.syncing("g1", false,
            STORAGE_PROVIDER, now.get());
        controlStore.marker = versioned(syncing, "marker-1");
        VersionedValue<A2aMigrationMarker> stale = versioned(syncing, "stale");
        assertNull(service.transition(stale, A2aMigrationState.QUIESCING, "g2"));
        assertEquals(A2aMigrationState.SYNCING, controlStore.marker.getValue().getState());
        
        controlStore.marker = versioned(syncing, "marker-2");
        controlStore.dropMarkerAfterWrite = true;
        assertNull(service.transition(controlStore.marker, A2aMigrationState.QUIESCING, "g3"));
    }
    
    @Test
    void concurrentMarkerCreationShouldBeAdoptedWithoutOverwrite() {
        A2aMigrationMarker peerMarker = A2aMigrationMarker.syncing("peer", true,
            STORAGE_PROVIDER, now.get());
        controlStore.markerToExpose = versioned(peerMarker, "peer-md5");
        controlStore.exposeMarkerOnRead = 2;
        assertEquals(A2aMigrationState.SYNCING, service.resolve(A2aCompatibilityMode.AUTO));
        assertEquals("peer", controlStore.marker.getValue().getGeneration());
    }
    
    @Test
    void leaseShouldAcquireRenewDetectLossAndRelease() {
        A2aMigrationLease lease = service.tryAcquireLease("owner-a", 1000L);
        assertNotNull(lease);
        assertTrue(lease.isOwned());
        now.addAndGet(100L);
        assertTrue(lease.renew());
        controlStore.lease = versioned(A2aMigrationLeaseRecord.of("owner-b", now.get() + 1000L),
            "lease-taken");
        assertFalse(lease.renew());
        assertFalse(lease.renew());
        assertFalse(lease.isOwned());
        assertThrows(IllegalStateException.class, lease::assertOwned);
        lease.close();
        
        controlStore.lease = versioned(A2aMigrationLeaseRecord.of("owner-b", now.get() + 1000L),
            "lease-live");
        assertNull(service.tryAcquireLease("owner-a", 1000L));
        controlStore.lease = versioned(A2aMigrationLeaseRecord.of("owner-b", now.get() - 1L),
            "lease-expired");
        A2aMigrationLease takeover = service.tryAcquireLease("owner-a", 1000L);
        assertNotNull(takeover);
        takeover.close();
        assertEquals(0L, controlStore.lease.getValue().getExpiresAt());
    }
    
    @Test
    void leaseFailuresShouldNeverPreserveUnverifiedOwnership() {
        A2aMigrationLease lease = service.tryAcquireLease("owner-a", 1000L);
        assertNotNull(lease);
        lease.assertOwned();
        controlStore.leaseReadFailures = 1;
        assertFalse(lease.renew());
        assertFalse(lease.isOwned());
        
        controlStore.lease = null;
        controlStore.leaseReadFailures = 2;
        assertNull(service.tryAcquireLease("owner-b", 1000L));
        
        controlStore.leaseReadFailures = 0;
        controlStore.lease = null;
        A2aMigrationLease closeFailure = service.tryAcquireLease("owner-c", 1000L);
        assertNotNull(closeFailure);
        controlStore.failLeaseWrites = true;
        closeFailure.close();
        assertFalse(closeFailure.isOwned());
        closeFailure.close();
    }
    
    @Test
    void leaseShouldResolveUncertainCreateAndRejectInvalidArguments() {
        controlStore.throwAfterLeaseWrite = true;
        assertNotNull(service.tryAcquireLease("owner-a", 1000L));
        assertThrows(IllegalArgumentException.class,
            () -> service.tryAcquireLease(" ", 1000L));
        assertThrows(IllegalArgumentException.class,
            () -> service.tryAcquireLease("owner", 0L));
        A2aMigrationLeaseRecord invalid = A2aMigrationLeaseRecord.of("owner", 1L);
        invalid.setSchemaVersion(0);
        controlStore.lease = versioned(invalid, "invalid");
        assertNull(service.tryAcquireLease("owner", 1000L));
    }
    
    @Test
    void diagnosticProgressFailureShouldNotEscape() throws Exception {
        A2aMigrationProgress progress = new A2aMigrationProgress();
        service.persistProgress(progress);
        assertEquals(progress, controlStore.progress);
        controlStore.failProgress = true;
        service.persistProgress(progress);
    }
    
    private A2aMigrationStateService newService(boolean shadow, A2aCompatibilityMode mode) {
        return newService(shadow, mode, STORAGE_PROVIDER);
    }
    
    private A2aMigrationStateService newService(boolean shadow, A2aCompatibilityMode mode,
        String storageProvider) {
        return new A2aMigrationStateService(controlStore, memberManager, now::get,
            () -> "generation-1", () -> shadow, () -> mode, () -> storageProvider);
    }
    
    private Member member(Object ability, Object policy) {
        Member result = mock(Member.class);
        when(result.getAddress()).thenReturn("127.0.0." + ++memberSequence + ":8848");
        when(result.getState()).thenReturn(NodeState.UP);
        when(result.getExtendVal(MemberMetaDataConstants.SUPPORT_A2A_MIGRATION_V1))
            .thenReturn(ability);
        when(result.getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH))
            .thenReturn(policy);
        return result;
    }
    
    private static <T> VersionedValue<T> versioned(T value, String md5) {
        return new VersionedValue<>(value, md5);
    }
    
    private static final class InMemoryControlStore extends A2aMigrationControlStore {
        
        private VersionedValue<A2aMigrationMarker> marker;
        
        private VersionedValue<A2aMigrationLeaseRecord> lease;
        
        private A2aMigrationProgress progress;
        
        private int revision;
        
        private boolean throwAfterMarkerWrite;
        
        private boolean throwAfterLeaseWrite;
        
        private boolean failProgress;
        
        private int markerReadFailures;
        
        private int leaseReadFailures;
        
        private boolean failLeaseWrites;
        
        private int markerReadCount;
        
        private int exposeMarkerOnRead;
        
        private VersionedValue<A2aMigrationMarker> markerToExpose;
        
        private boolean dropMarkerAfterWrite;
        
        private InMemoryControlStore() {
            super(mock(ConfigInfoPersistService.class), mock(ConfigOperationService.class));
        }
        
        @Override
        public VersionedValue<A2aMigrationMarker> readMarker() {
            markerReadCount++;
            if (markerReadFailures-- > 0) {
                throw new IllegalStateException("marker read failure");
            }
            if (exposeMarkerOnRead > 0 && markerReadCount == exposeMarkerOnRead) {
                marker = markerToExpose;
            }
            return marker;
        }
        
        @Override
        public boolean createMarker(A2aMigrationMarker value) throws NacosException {
            if (marker != null) {
                throw new NacosException(NacosException.SERVER_ERROR, "marker exists");
            }
            marker = versioned(value, nextMd5("marker"));
            maybeThrowMarker();
            return true;
        }
        
        @Override
        public boolean compareAndSetMarker(A2aMigrationMarker value, String expectedMd5)
            throws NacosException {
            if (marker == null || !marker.getMd5().equals(expectedMd5)) {
                throw new NacosException(NacosException.SERVER_ERROR, "marker conflict");
            }
            marker = versioned(value, nextMd5("marker"));
            maybeThrowMarker();
            if (dropMarkerAfterWrite) {
                marker = null;
            }
            return true;
        }
        
        @Override
        public VersionedValue<A2aMigrationLeaseRecord> readLease() {
            if (leaseReadFailures-- > 0) {
                throw new IllegalStateException("lease read failure");
            }
            return lease;
        }
        
        @Override
        public boolean createLease(A2aMigrationLeaseRecord value) throws NacosException {
            if (lease != null) {
                throw new NacosException(NacosException.SERVER_ERROR, "lease exists");
            }
            lease = versioned(value, nextMd5("lease"));
            maybeThrowLease();
            return true;
        }
        
        @Override
        public boolean compareAndSetLease(A2aMigrationLeaseRecord value, String expectedMd5)
            throws NacosException {
            if (failLeaseWrites) {
                throw new NacosException(NacosException.SERVER_ERROR, "lease write failure");
            }
            if (lease == null || !lease.getMd5().equals(expectedMd5)) {
                throw new NacosException(NacosException.SERVER_ERROR, "lease conflict");
            }
            lease = versioned(value, nextMd5("lease"));
            maybeThrowLease();
            return true;
        }
        
        @Override
        public boolean saveProgress(A2aMigrationProgress value) throws NacosException {
            if (failProgress) {
                throw new NacosException(NacosException.SERVER_ERROR, "progress failure");
            }
            progress = value;
            return true;
        }
        
        private void maybeThrowMarker() throws NacosException {
            if (throwAfterMarkerWrite) {
                throwAfterMarkerWrite = false;
                throw new NacosException(NacosException.SERVER_ERROR, "uncertain marker result");
            }
        }
        
        private void maybeThrowLease() throws NacosException {
            if (throwAfterLeaseWrite) {
                throwAfterLeaseWrite = false;
                throw new NacosException(NacosException.SERVER_ERROR, "uncertain lease result");
            }
        }
        
        private String nextMd5(String prefix) {
            return prefix + '-' + ++revision;
        }
    }
}
