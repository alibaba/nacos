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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityMode;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import com.alibaba.nacos.ai.storage.AiResourceStorageUtils;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Service
public class A2aMigrationStateService
    implements ApplicationListener<ApplicationReadyEvent> {
    
    public static final String LEGACY_NAMING_SHADOW_ENABLED_PROPERTY =
        "nacos.ai.a2a.migration.legacy-naming-shadow-enabled";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(A2aMigrationStateService.class);
    
    private static final long MARKER_REFRESH_INTERVAL_MILLIS = 3000L;
    
    private final A2aMigrationControlStore controlStore;
    
    private final ServerMemberManager serverMemberManager;
    
    private final LongSupplier clock;
    
    private final Supplier<String> generationSupplier;
    
    private final Supplier<Boolean> shadowPolicySupplier;
    
    private final Supplier<A2aCompatibilityMode> configuredModeSupplier;
    
    private final Supplier<String> storageProviderSupplier;
    
    private final AtomicBoolean canonicalLatched = new AtomicBoolean(false);
    
    private volatile VersionedValue<A2aMigrationMarker> cachedMarker;
    
    private volatile long nextMarkerRefreshAt;
    
    @Autowired
    public A2aMigrationStateService(A2aMigrationControlStore controlStore,
        ServerMemberManager serverMemberManager) {
        this(controlStore, serverMemberManager, System::currentTimeMillis,
            () -> UUID.randomUUID().toString(),
            () -> Boolean.parseBoolean(
                EnvUtil.getProperty(LEGACY_NAMING_SHADOW_ENABLED_PROPERTY, "false")),
            A2aMigrationStateService::configuredMode,
            A2aMigrationStateService::configuredStorageProvider);
    }
    
    A2aMigrationStateService(A2aMigrationControlStore controlStore,
        ServerMemberManager serverMemberManager, LongSupplier clock,
        Supplier<String> generationSupplier, Supplier<Boolean> shadowPolicySupplier,
        Supplier<A2aCompatibilityMode> configuredModeSupplier,
        Supplier<String> storageProviderSupplier) {
        this.controlStore = controlStore;
        this.serverMemberManager = serverMemberManager;
        this.clock = clock;
        this.generationSupplier = generationSupplier;
        this.shadowPolicySupplier = shadowPolicySupplier;
        this.configuredModeSupplier = configuredModeSupplier;
        this.storageProviderSupplier = storageProviderSupplier;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() == null) {
            advertiseLocalCapability(configuredModeSupplier.get());
        }
    }
    
    /**
     * Resolve migration state for one configured compatibility mode.
     *
     * <p>A terminal marker overrides every local mode. A non-terminal marker affects only
     * {@code AUTO}; explicit static modes retain their existing behavior.</p>
     *
     * @param configured configured compatibility mode
     * @return migration state for AUTO or terminal CANONICAL; otherwise {@code null}
     */
    public A2aMigrationState resolve(A2aCompatibilityMode configured) {
        return resolve(configured, false);
    }
    
    private A2aMigrationState resolve(A2aCompatibilityMode configured,
        boolean authoritative) {
        Objects.requireNonNull(configured, "configured");
        advertiseLocalCapability(configured);
        VersionedValue<A2aMigrationMarker> marker = refreshMarker(authoritative);
        A2aMigrationState result;
        if (canonicalLatched.get()) {
            result = A2aMigrationState.CANONICAL;
        } else if (A2aCompatibilityMode.AUTO != configured) {
            result = null;
        } else {
            if (marker == null) {
                marker = ensureSyncingPlan();
            }
            result = marker == null ? A2aMigrationState.SYNCING
                : marker.getValue().getState();
        }
        A2aMigrationMetrics.setState(result);
        return result;
    }
    
    /**
     * Resolve the configured migration state from an authoritative marker read.
     *
     * <p>TODO(remove in 4.0): the historical mutation fence uses this path so child and root
     * Spring contexts cannot admit a write from their independent bounded marker caches while
     * another context has already installed {@code QUIESCING}.</p>
     *
     * @return current migration state for AUTO or terminal CANONICAL; otherwise {@code null}
     */
    public A2aMigrationState resolveConfiguredAuthoritative() {
        return resolve(configuredModeSupplier.get(), true);
    }
    
    /**
     * Resolve migration state from the node's configured compatibility mode.
     *
     * @return migration state for AUTO or terminal CANONICAL; otherwise {@code null}
     */
    public A2aMigrationState resolveConfigured() {
        return resolve(configuredModeSupplier.get());
    }
    
    /**
     * Return the frozen legacy Naming shadow policy from the current migration plan.
     *
     * <p>TODO(remove in 4.0): remove with the historical A2A Runtime migration router.</p>
     *
     * @return whether the current valid marker enables the exact-Version legacy shadow
     */
    public boolean isLegacyNamingShadowEnabled() {
        VersionedValue<A2aMigrationMarker> marker = refreshMarker(false);
        return marker != null && marker.getValue().isValid()
            && marker.getValue().isLegacyNamingShadow();
    }
    
    /**
     * Check all current members against the frozen AUTO migration policy.
     *
     * @param marker current valid migration marker
     * @return {@code true} only when a non-empty member view has matching valid metadata
     */
    public boolean allMembersReady(A2aMigrationMarker marker) {
        return readyMemberView(marker) != null;
    }
    
    /**
     * Check whether this node can safely write targets for the frozen migration plan.
     *
     * <p>TODO(remove in 4.0): a member with a different local migration policy must not become a
     * reconciliation writer. Otherwise a lease-owner change could select another AI Storage
     * provider and create incompatible descriptors for the same historical Agent.</p>
     *
     * @param marker current migration marker
     * @return whether the complete local policy matches the frozen AUTO plan
     */
    public boolean isLocalPolicyCompatible(A2aMigrationMarker marker) {
        if (marker == null || !marker.isValid()) {
            return false;
        }
        return policyHash(A2aCompatibilityMode.AUTO, marker.isLegacyNamingShadow(),
            marker.getStorageProvider()).equals(
                policyHash(configuredModeSupplier.get(),
                    shadowPolicySupplier.get(), storageProviderSupplier.get()));
    }
    
    /**
     * Capture the complete, healthy, policy-compatible member set.
     *
     * @param marker current migration marker
     * @return stable member view, or {@code null} when any member is not ready
     */
    public A2aMigrationMemberView readyMemberView(A2aMigrationMarker marker) {
        MemberInspection inspection = inspectMembers(marker);
        return inspection == null ? null : inspection.view;
    }
    
    /**
     * Check exact-generation acknowledgements from the unchanged current member set.
     *
     * @param marker current quiescing marker
     * @param expectedView member view bound into the generation
     * @return whether every current member acknowledged this exact generation
     */
    public boolean allMembersAcknowledged(A2aMigrationMarker marker,
        A2aMigrationMemberView expectedView) {
        if (marker == null || A2aMigrationState.QUIESCING != marker.getState()
            || expectedView == null) {
            return false;
        }
        MemberInspection inspection = inspectMembers(marker);
        if (inspection == null || !expectedView.equals(inspection.view)) {
            return false;
        }
        String expectedAck = marker.getGeneration() + ":READY";
        for (Member member : inspection.members) {
            if (!expectedAck.equals(
                member.getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Publish this member's exact-generation readiness acknowledgement.
     *
     * @param marker locally verified quiescing marker
     * @return whether the marker remained current while the ACK was installed
     */
    public boolean advertiseLocalAck(A2aMigrationMarker marker) {
        if (marker == null || A2aMigrationState.QUIESCING != marker.getState()) {
            return false;
        }
        VersionedValue<A2aMigrationMarker> observed = refreshMarker(true);
        if (observed == null || A2aMigrationState.QUIESCING != observed.getValue().getState()
            || !marker.getGeneration().equals(observed.getValue().getGeneration())) {
            return false;
        }
        try {
            Member self = serverMemberManager.getSelf();
            if (self == null) {
                return false;
            }
            self.setExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK,
                marker.getGeneration() + ":READY");
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to advertise local A2A migration ACK", e);
            return false;
        }
    }
    
    /**
     * Remove this member's stale quiescing acknowledgement.
     */
    public void clearLocalAck() {
        try {
            Member self = serverMemberManager.getSelf();
            if (self != null) {
                self.delExtendVal(MemberMetaDataConstants.A2A_MIGRATION_ACK);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to clear local A2A migration ACK", e);
        }
    }
    
    /**
     * Force a current authoritative marker read for cutover orchestration.
     *
     * @return current marker and persistence MD5, or {@code null}
     */
    public VersionedValue<A2aMigrationMarker> currentMarker() {
        return refreshMarker(true);
    }
    
    /**
     * Create a fresh opaque generation token.
     *
     * @return unique generation token
     */
    public String newGeneration() {
        return generationSupplier.get();
    }
    
    /**
     * Return the state service clock for timeout checks.
     *
     * @return current epoch milliseconds
     */
    public long currentTimeMillis() {
        return clock.getAsLong();
    }
    
    /**
     * Compare and set one legal non-terminal state transition.
     *
     * @param expected currently observed marker and Config MD5
     * @param target target state
     * @param generation target generation
     * @return newly observed marker, or {@code null} when the CAS did not take effect
     */
    public synchronized VersionedValue<A2aMigrationMarker> transition(
        VersionedValue<A2aMigrationMarker> expected, A2aMigrationState target,
        String generation) {
        if (expected == null || target == null || StringUtils.isBlank(generation)
            || !legalTransition(expected.getValue().getState(), target)) {
            return null;
        }
        A2aMigrationMarker replacement = expected.getValue().transition(target, generation,
            clock.getAsLong());
        try {
            controlStore.compareAndSetMarker(replacement, expected.getMd5());
        } catch (Exception e) {
            LOGGER.warn("A2A migration marker CAS result is uncertain; rereading marker", e);
        }
        VersionedValue<A2aMigrationMarker> observed = refreshMarker(true);
        if (!sameMarker(replacement, observed)) {
            return null;
        }
        if (A2aMigrationState.CANONICAL == target) {
            latchCanonical(observed);
        }
        return observed;
    }
    
    /**
     * Acquire or take over the renewable single-writer reconciliation lease.
     *
     * @param owner unique owner identity
     * @param leaseDurationMillis positive lease duration
     * @return owned lease handle, or {@code null} when another owner has a live lease
     */
    public A2aMigrationLease tryAcquireLease(String owner, long leaseDurationMillis) {
        if (StringUtils.isBlank(owner) || leaseDurationMillis <= 0) {
            throw new IllegalArgumentException(
                "A2A migration lease owner and duration are required");
        }
        long now = clock.getAsLong();
        VersionedValue<A2aMigrationLeaseRecord> current = readLeaseSafely();
        if (current != null && current.getValue().isValid()
            && current.getValue().getExpiresAt() > now
            && !owner.equals(current.getValue().getOwner())) {
            return null;
        }
        A2aMigrationLeaseRecord replacement = A2aMigrationLeaseRecord.of(owner,
            now + leaseDurationMillis);
        try {
            if (current == null) {
                controlStore.createLease(replacement);
            } else if (current.getValue().isValid()) {
                controlStore.compareAndSetLease(replacement, current.getMd5());
            } else {
                return null;
            }
        } catch (Exception e) {
            LOGGER.warn("A2A migration lease write result is uncertain; rereading lease", e);
        }
        VersionedValue<A2aMigrationLeaseRecord> observed = readLeaseSafely();
        if (!ownedBy(observed, owner, now)) {
            return null;
        }
        return new A2aMigrationLease(controlStore, owner, leaseDurationMillis, clock,
            observed.getMd5());
    }
    
    /**
     * Persist bounded diagnostic progress. Failures never alter migration authority.
     *
     * @param progress bounded diagnostic value
     */
    public void persistProgress(A2aMigrationProgress progress) {
        try {
            controlStore.saveProgress(progress);
        } catch (Exception e) {
            LOGGER.warn("Failed to persist A2A migration progress", e);
        }
    }
    
    VersionedValue<A2aMigrationMarker> refreshMarkerForTest() {
        return refreshMarker(true);
    }
    
    String policyHash(A2aCompatibilityMode mode, boolean shadowEnabled) {
        return policyHash(mode, shadowEnabled, storageProviderSupplier.get());
    }
    
    String policyHash(A2aCompatibilityMode mode, boolean shadowEnabled,
        String storageProvider) {
        String policy = mode.name() + '|' + A2aMigrationMarker.SCHEMA_VERSION + '|'
            + shadowEnabled + '|' + storageProvider;
        return DigestUtils.sha256Hex(policy);
    }
    
    private synchronized VersionedValue<A2aMigrationMarker> ensureSyncingPlan() {
        VersionedValue<A2aMigrationMarker> current = refreshMarker(true);
        if (current != null) {
            return current;
        }
        long now = clock.getAsLong();
        A2aMigrationMarker marker = A2aMigrationMarker.syncing(generationSupplier.get(),
            shadowPolicySupplier.get(), storageProviderSupplier.get(), now);
        try {
            controlStore.createMarker(marker);
        } catch (Exception e) {
            LOGGER.warn("A2A migration marker creation result is uncertain; rereading marker", e);
        }
        VersionedValue<A2aMigrationMarker> observed = refreshMarker(true);
        if (observed == null) {
            LOGGER.warn("AUTO remains on historical A2A authority because no migration marker "
                + "could be confirmed");
        }
        return observed;
    }
    
    private synchronized VersionedValue<A2aMigrationMarker> refreshMarker(boolean force) {
        if (canonicalLatched.get()) {
            return cachedMarker;
        }
        long now = clock.getAsLong();
        if (!force && now < nextMarkerRefreshAt) {
            return cachedMarker;
        }
        nextMarkerRefreshAt = now + MARKER_REFRESH_INTERVAL_MILLIS;
        try {
            VersionedValue<A2aMigrationMarker> observed = controlStore.readMarker();
            if (observed != null && !observed.getValue().isValid()) {
                LOGGER.error("Ignored invalid A2A migration marker");
                return cachedMarker;
            }
            cachedMarker = observed;
            if (observed != null
                && A2aMigrationState.CANONICAL == observed.getValue().getState()) {
                latchCanonical(observed);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read A2A migration marker", e);
        }
        return cachedMarker;
    }
    
    private void latchCanonical(VersionedValue<A2aMigrationMarker> marker) {
        cachedMarker = marker;
        if (canonicalLatched.compareAndSet(false, true)) {
            LOGGER.info("A2A migration authority is permanently CANONICAL");
        }
    }
    
    private void advertiseLocalCapability(A2aCompatibilityMode mode) {
        try {
            Member self = serverMemberManager.getSelf();
            if (self == null) {
                return;
            }
            self.setExtendVal(MemberMetaDataConstants.SUPPORT_A2A_MIGRATION_V1, true);
            self.setExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH,
                policyHash(mode, shadowPolicySupplier.get(), storageProviderSupplier.get()));
        } catch (Exception e) {
            LOGGER.warn("Failed to advertise local A2A migration capability", e);
        }
    }
    
    private MemberInspection inspectMembers(A2aMigrationMarker marker) {
        if (marker == null || !marker.isValid()) {
            return null;
        }
        String expectedPolicy = policyHash(A2aCompatibilityMode.AUTO,
            marker.isLegacyNamingShadow(), marker.getStorageProvider());
        try {
            Collection<Member> current = serverMemberManager.allMembers();
            if (current == null || current.isEmpty()) {
                return null;
            }
            List<Member> members = new ArrayList<Member>(current.size());
            List<String> identities = new ArrayList<String>(current.size());
            for (Member member : current) {
                if (member == null || StringUtils.isBlank(member.getAddress())
                    || !NodeState.UP.equals(member.getState())
                    || !Boolean.TRUE.equals(member
                        .getExtendVal(MemberMetaDataConstants.SUPPORT_A2A_MIGRATION_V1))
                    || !expectedPolicy.equals(member
                        .getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH))) {
                    return null;
                }
                members.add(member);
                identities.add(member.getAddress());
            }
            Collections.sort(identities);
            StringBuilder canonical = new StringBuilder();
            for (String identity : identities) {
                canonical.append(identity.length()).append(':').append(identity);
            }
            A2aMigrationMemberView view = new A2aMigrationMemberView(
                DigestUtils.sha256Hex(canonical.toString()), members.size());
            return new MemberInspection(view, members);
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect A2A migration member capabilities", e);
            return null;
        }
    }
    
    private VersionedValue<A2aMigrationLeaseRecord> readLeaseSafely() {
        try {
            return controlStore.readLease();
        } catch (Exception e) {
            LOGGER.warn("Failed to read A2A migration reconciliation lease", e);
            return null;
        }
    }
    
    private boolean legalTransition(A2aMigrationState source, A2aMigrationState target) {
        if (source == null || A2aMigrationState.CANONICAL == source) {
            return false;
        }
        return A2aMigrationState.SYNCING == source
            ? A2aMigrationState.QUIESCING == target
            : A2aMigrationState.SYNCING == target || A2aMigrationState.CANONICAL == target;
    }
    
    private boolean sameMarker(A2aMigrationMarker expected,
        VersionedValue<A2aMigrationMarker> actual) {
        if (actual == null) {
            return false;
        }
        A2aMigrationMarker value = actual.getValue();
        return value.isValid() && expected.getState() == value.getState()
            && expected.isLegacyNamingShadow() == value.isLegacyNamingShadow()
            && Objects.equals(expected.getStorageProvider(), value.getStorageProvider())
            && expected.getGeneration().equals(value.getGeneration())
            && Objects.equals(expected.getCompletedAt(), value.getCompletedAt());
    }
    
    private boolean ownedBy(VersionedValue<A2aMigrationLeaseRecord> lease, String owner,
        long now) {
        return lease != null && lease.getValue().isValid()
            && owner.equals(lease.getValue().getOwner())
            && lease.getValue().getExpiresAt() > now;
    }
    
    private static A2aCompatibilityMode configuredMode() {
        String value = EnvUtil.getProperty("nacos.ai.a2a.compatibility.mode",
            A2aCompatibilityMode.CANONICAL.name());
        String normalized = StringUtils.isBlank(value) ? A2aCompatibilityMode.CANONICAL.name()
            : value.trim().toUpperCase(Locale.ROOT);
        return A2aCompatibilityMode.valueOf(normalized);
    }
    
    private static String configuredStorageProvider() {
        return AiResourceStorageUtils.resolveProvider(
            Constants.Agent.AGENT_STORAGE_PROVIDER_CONFIG_KEY,
            NacosConfigAiResourceStorage.TYPE);
    }
    
    private static final class MemberInspection {
        
        private final A2aMigrationMemberView view;
        
        private final List<Member> members;
        
        private MemberInspection(A2aMigrationMemberView view, List<Member> members) {
            this.view = view;
            this.members = members;
        }
    }
}
