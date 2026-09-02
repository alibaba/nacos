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

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationCutoverCoordinator {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(A2aMigrationCutoverCoordinator.class);
    
    private final A2aMigrationStateService stateService;
    
    private final A2aMigrationSearchReadinessGate searchReadinessGate;
    
    private final A2aMigrationRuntimeReadinessGate runtimeReadinessGate;
    
    private final A2aMigrationDefinitionReadinessValidator definitionValidator;
    
    public A2aMigrationCutoverCoordinator(A2aMigrationStateService stateService,
        A2aMigrationSearchReadinessGate searchReadinessGate,
        A2aMigrationRuntimeReadinessGate runtimeReadinessGate,
        A2aMigrationDefinitionReadinessValidator definitionValidator) {
        this.stateService = stateService;
        this.searchReadinessGate = searchReadinessGate;
        this.runtimeReadinessGate = runtimeReadinessGate;
        this.definitionValidator = definitionValidator;
    }
    
    /**
     * Credit one complete zero-difference reconciliation round and enter quiescing after two.
     *
     * @param marker syncing marker observed before the round
     * @param lease still-owned reconciliation lease
     * @param definitionReady whether the complete definition round changed nothing
     * @param sourceNames complete historical Agent identities by Namespace
     * @param pageSize bounded historical page size
     * @return whether this call installed a quiescing marker
     */
    public synchronized boolean afterSyncingRound(VersionedValue<A2aMigrationMarker> marker,
        A2aMigrationLease lease, boolean definitionReady,
        Map<String, Set<String>> sourceNames, int pageSize) {
        if (!definitionReady) {
            LOGGER.debug("Historical A2A cutover is waiting for a zero-difference Definition "
                + "round");
            recordBlockedGate();
            return false;
        }
        if (marker == null || lease == null
            || A2aMigrationState.SYNCING != marker.getValue().getState()) {
            LOGGER.debug("Historical A2A cutover skipped because the SYNCING marker or lease "
                + "is unavailable");
            recordBlockedGate();
            return false;
        }
        A2aMigrationMemberView memberView = stateService.readyMemberView(marker.getValue());
        if (memberView == null) {
            LOGGER.info("Historical A2A cutover remains SYNCING: waiting for a stable capable "
                + "member view");
            recordBlockedGate();
            return false;
        }
        if (!searchReadinessGate.isReady(sourceNames)) {
            LOGGER.info("Historical A2A cutover remains SYNCING: waiting for current Search "
                + "projections");
            recordBlockedGate();
            return false;
        }
        if (!runtimeReadinessGate.isReady()) {
            LOGGER.info("Historical A2A cutover remains SYNCING: waiting for Runtime mirror "
                + "convergence");
            recordBlockedGate();
            return false;
        }
        if (!lease.renew()) {
            LOGGER.debug("Historical A2A cutover stopped because the reconciliation lease was "
                + "lost before the second Definition round");
            recordBlockedGate();
            return false;
        }
        // The first zero-difference round was the reconciliation scan. Keep the same lease
        // handle and perform the second complete round read-only, so another owner cannot
        // interleave a conflicting round between the two cutover credits.
        A2aMigrationDefinitionReadinessValidator.Result secondRound =
            definitionValidator.validate(pageSize, lease);
        if (!secondRound.isReady()) {
            LOGGER.info("Historical A2A cutover remains SYNCING: second Definition round is "
                + "not ready: "
                + "differences={}, failed={}", secondRound.getDifferences(),
                secondRound.getFailed());
            recordBlockedGate();
            return false;
        }
        if (!searchReadinessGate.isReady(secondRound.getSourceNames())) {
            LOGGER.info("Historical A2A cutover remains SYNCING: second round is waiting for "
                + "current Search projections");
            recordBlockedGate();
            return false;
        }
        if (!runtimeReadinessGate.isReady()) {
            LOGGER.info("Historical A2A cutover remains SYNCING: second round is waiting for "
                + "Runtime mirror convergence");
            recordBlockedGate();
            return false;
        }
        VersionedValue<A2aMigrationMarker> current = stateService.currentMarker();
        A2aMigrationMemberView currentView = current == null ? null
            : stateService.readyMemberView(current.getValue());
        if (current == null || A2aMigrationState.SYNCING != current.getValue().getState()
            || !marker.getValue().getGeneration().equals(current.getValue().getGeneration())
            || !memberView.equals(currentView)
            || !searchReadinessGate.isReady(secondRound.getSourceNames())
            || !runtimeReadinessGate.isReady()) {
            LOGGER.info("Historical A2A cutover remains SYNCING: final recheck observed changed "
                + "authority, "
                + "membership, Search, or Runtime state");
            recordBlockedGate();
            return false;
        }
        String quiescingGeneration = A2aMigrationQuiescingGeneration.create(memberView,
            stateService.newGeneration());
        VersionedValue<A2aMigrationMarker> transitioned = stateService.transition(current,
            A2aMigrationState.QUIESCING, quiescingGeneration);
        if (transitioned == null) {
            LOGGER.debug("Historical A2A cutover lost the marker CAS while entering QUIESCING");
            recordBlockedGate();
            return false;
        }
        A2aMigrationMetrics.record(A2aMigrationMetrics.Event.ENTER_QUIESCING,
            A2aMigrationMetrics.Result.SUCCESS);
        LOGGER.info("Historical A2A migration entered QUIESCING: generationHash={}, members={}",
            hash(quiescingGeneration), memberView.getMemberCount());
        return true;
    }
    
    private String hash(String value) {
        return Integer.toHexString(value.hashCode());
    }
    
    private void recordBlockedGate() {
        A2aMigrationMetrics.record(A2aMigrationMetrics.Event.CUTOVER_GATE,
            A2aMigrationMetrics.Result.BLOCKED);
    }
}
