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
            A2aMigrationStateService::configuredMode);
    }
    
    A2aMigrationStateService(A2aMigrationControlStore controlStore,
        ServerMemberManager serverMemberManager, LongSupplier clock,
        Supplier<String> generationSupplier, Supplier<Boolean> shadowPolicySupplier,
        Supplier<A2aCompatibilityMode> configuredModeSupplier) {
        this.controlStore = controlStore;
        this.serverMemberManager = serverMemberManager;
        this.clock = clock;
        this.generationSupplier = generationSupplier;
        this.shadowPolicySupplier = shadowPolicySupplier;
        this.configuredModeSupplier = configuredModeSupplier;
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
        Objects.requireNonNull(configured, "configured");
        advertiseLocalCapability(configured);
        VersionedValue<A2aMigrationMarker> marker = refreshMarker(false);
        if (canonicalLatched.get()) {
            return A2aMigrationState.CANONICAL;
        }
        if (A2aCompatibilityMode.AUTO != configured) {
            return null;
        }
        if (marker == null) {
            marker = ensureSyncingPlan();
        }
        return marker == null ? A2aMigrationState.SYNCING : marker.getValue().getState();
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
     * Check all current members against the frozen AUTO migration policy.
     *
     * @param marker current valid migration marker
     * @return {@code true} only when a non-empty member view has matching valid metadata
     */
    public boolean allMembersReady(A2aMigrationMarker marker) {
        if (marker == null || !marker.isValid()) {
            return false;
        }
        String expectedPolicy = policyHash(A2aCompatibilityMode.AUTO,
            marker.isLegacyNamingShadow());
        try {
            Collection<Member> members = serverMemberManager.allMembers();
            if (members == null || members.isEmpty()) {
                return false;
            }
            for (Member member : members) {
                if (member == null || !Boolean.TRUE.equals(member
                    .getExtendVal(MemberMetaDataConstants.SUPPORT_A2A_MIGRATION_V1))
                    || !expectedPolicy.equals(member
                        .getExtendVal(MemberMetaDataConstants.A2A_MIGRATION_POLICY_HASH))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to inspect A2A migration member capabilities", e);
            return false;
        }
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
        String policy = mode.name() + '|' + A2aMigrationMarker.SCHEMA_VERSION + '|'
            + shadowEnabled;
        return DigestUtils.sha256Hex(policy);
    }
    
    private synchronized VersionedValue<A2aMigrationMarker> ensureSyncingPlan() {
        VersionedValue<A2aMigrationMarker> current = refreshMarker(true);
        if (current != null) {
            return current;
        }
        long now = clock.getAsLong();
        A2aMigrationMarker marker = A2aMigrationMarker.syncing(generationSupplier.get(),
            shadowPolicySupplier.get(), now);
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
                policyHash(mode, shadowPolicySupplier.get()));
        } catch (Exception e) {
            LOGGER.warn("Failed to advertise local A2A migration capability", e);
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
}
