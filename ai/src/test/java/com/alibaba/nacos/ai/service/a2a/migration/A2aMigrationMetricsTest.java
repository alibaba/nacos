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

import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class A2aMigrationMetricsTest {
    
    @AfterEach
    void tearDown() {
        A2aMigrationMetrics.resetGaugesForTest();
    }
    
    @Test
    void shouldExposeOneLowCardinalityStateAndBoundPendingGauge() throws Exception {
        Constructor<A2aMigrationMetrics> constructor = A2aMigrationMetrics.class
            .getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
        
        A2aMigrationMetrics.setState(A2aMigrationState.SYNCING);
        assertEquals(0, A2aMigrationMetrics.stateValue(A2aMigrationMetrics.State.INACTIVE));
        assertEquals(1, A2aMigrationMetrics.stateValue(A2aMigrationMetrics.State.SYNCING));
        A2aMigrationMetrics.setState(A2aMigrationState.QUIESCING);
        assertEquals(0, A2aMigrationMetrics.stateValue(A2aMigrationMetrics.State.SYNCING));
        assertEquals(1, A2aMigrationMetrics.stateValue(A2aMigrationMetrics.State.QUIESCING));
        A2aMigrationMetrics.setState(A2aMigrationState.CANONICAL);
        assertEquals(1, A2aMigrationMetrics.stateValue(A2aMigrationMetrics.State.CANONICAL));
        A2aMigrationMetrics.setState(null);
        assertEquals(1, A2aMigrationMetrics.stateValue(A2aMigrationMetrics.State.INACTIVE));
        
        A2aMigrationMetrics.setPendingEndpointRetries(3);
        assertEquals(3, A2aMigrationMetrics.pendingEndpointRetries());
        A2aMigrationMetrics.adjustPendingEndpointRetries(2);
        assertEquals(5, A2aMigrationMetrics.pendingEndpointRetries());
        A2aMigrationMetrics.adjustPendingEndpointRetries(-9);
        assertEquals(0, A2aMigrationMetrics.pendingEndpointRetries());
        A2aMigrationMetrics.setPendingEndpointRetries(-1);
        assertEquals(0, A2aMigrationMetrics.pendingEndpointRetries());
        assertEquals(4, A2aMigrationMetrics.State.values().length);
    }
    
    @Test
    void shouldRecordClosedMigrationEventsAndReconciliationItems() {
        double cutoverBefore = A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.CUTOVER, A2aMigrationMetrics.Result.SUCCESS);
        A2aMigrationMetrics.record(A2aMigrationMetrics.Event.CUTOVER,
            A2aMigrationMetrics.Result.SUCCESS);
        A2aMigrationMetrics.record(A2aMigrationMetrics.Event.CUTOVER,
            A2aMigrationMetrics.Result.SUCCESS, 2D);
        A2aMigrationMetrics.record(A2aMigrationMetrics.Event.CUTOVER,
            A2aMigrationMetrics.Result.SUCCESS, 0D);
        assertEquals(cutoverBefore + 3D, A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.CUTOVER, A2aMigrationMetrics.Result.SUCCESS));
        
        double scannedBefore = A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.SCANNED);
        double migratedBefore = A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.MIGRATED);
        double conflictBefore = A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.CONFLICT);
        double failedBefore = A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.FAILED);
        double successBefore = A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.RECONCILIATION,
            A2aMigrationMetrics.Result.SUCCESS);
        double blockedBefore = A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.RECONCILIATION,
            A2aMigrationMetrics.Result.BLOCKED);
        double failureBefore = A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.RECONCILIATION,
            A2aMigrationMetrics.Result.FAILED);
        
        assertDoesNotThrow(() -> A2aMigrationMetrics.recordReconciliation(5, 2, 0, 0, 10));
        A2aMigrationMetrics.recordReconciliation(3, 0, 1, 0, -1);
        A2aMigrationMetrics.recordReconciliation(1, 0, 0, 1, 1);
        assertEquals(scannedBefore + 9D, A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.SCANNED));
        assertEquals(migratedBefore + 2D, A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.MIGRATED));
        assertEquals(conflictBefore + 1D, A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.CONFLICT));
        assertEquals(failedBefore + 1D, A2aMigrationMetrics.reconciliationItemCount(
            A2aMigrationMetrics.ReconciliationItem.FAILED));
        assertEquals(successBefore + 1D, A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.RECONCILIATION,
            A2aMigrationMetrics.Result.SUCCESS));
        assertEquals(blockedBefore + 1D, A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.RECONCILIATION,
            A2aMigrationMetrics.Result.BLOCKED));
        assertEquals(failureBefore + 1D, A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.RECONCILIATION,
            A2aMigrationMetrics.Result.FAILED));
        assertEquals(7, A2aMigrationMetrics.Event.values().length);
        assertEquals(4, A2aMigrationMetrics.Result.values().length);
        assertEquals(4, A2aMigrationMetrics.ReconciliationItem.values().length);
    }
    
    @Test
    void shouldRecordEndpointWriteByClosedRoleTargetOperationAndResult() {
        double primaryBefore = A2aMigrationMetrics.endpointWriteCount(
            A2aMigrationMetrics.Role.PRIMARY, A2aMigrationMetrics.Target.CANONICAL,
            A2aMigrationMetrics.Operation.REGISTER, A2aMigrationMetrics.Result.SUCCESS);
        double retryBefore = A2aMigrationMetrics.endpointWriteCount(
            A2aMigrationMetrics.Role.RETRY, A2aMigrationMetrics.Target.LEGACY,
            A2aMigrationMetrics.Operation.DEREGISTER, A2aMigrationMetrics.Result.FAILED);
        
        assertDoesNotThrow(() -> A2aMigrationMetrics.recordEndpointWrite(
            A2aMigrationMetrics.Role.PRIMARY, A2aMigrationMetrics.Target.CANONICAL,
            A2aMigrationMetrics.Operation.REGISTER, A2aMigrationMetrics.Result.SUCCESS, 10));
        A2aMigrationMetrics.recordEndpointWrite(A2aMigrationMetrics.Role.RETRY,
            A2aMigrationMetrics.Target.LEGACY, A2aMigrationMetrics.Operation.DEREGISTER,
            A2aMigrationMetrics.Result.FAILED, -1);
        assertEquals(primaryBefore + 1D, A2aMigrationMetrics.endpointWriteCount(
            A2aMigrationMetrics.Role.PRIMARY, A2aMigrationMetrics.Target.CANONICAL,
            A2aMigrationMetrics.Operation.REGISTER, A2aMigrationMetrics.Result.SUCCESS));
        assertEquals(retryBefore + 1D, A2aMigrationMetrics.endpointWriteCount(
            A2aMigrationMetrics.Role.RETRY, A2aMigrationMetrics.Target.LEGACY,
            A2aMigrationMetrics.Operation.DEREGISTER, A2aMigrationMetrics.Result.FAILED));
        assertEquals(3, A2aMigrationMetrics.Role.values().length);
        assertEquals(2, A2aMigrationMetrics.Target.values().length);
        assertEquals(2, A2aMigrationMetrics.Operation.values().length);
    }
    
    @Test
    void shouldNeverChangeMigrationBehaviorWhenMetricRegistryFails() throws Exception {
        Method registerGauge = A2aMigrationMetrics.class.getDeclaredMethod("registerGauge",
            String.class, String.class, String.class, Number.class);
        registerGauge.setAccessible(true);
        try (MockedStatic<NacosMeterRegistryCenter> registry = Mockito.mockStatic(
            NacosMeterRegistryCenter.class)) {
            registry.when(() -> NacosMeterRegistryCenter.counter(anyString(), anyString(),
                any(String[].class))).thenThrow(new IllegalStateException("counter failed"));
            registry.when(() -> NacosMeterRegistryCenter.timer(anyString(), anyString(),
                any(String[].class))).thenThrow(new IllegalStateException("timer failed"));
            registry.when(() -> NacosMeterRegistryCenter.gauge(anyString(), anyString(),
                any(List.class), any(AtomicInteger.class)))
                .thenThrow(new IllegalStateException("gauge failed"));
            
            assertDoesNotThrow(() -> A2aMigrationMetrics.record(
                A2aMigrationMetrics.Event.CUTOVER, A2aMigrationMetrics.Result.SUCCESS));
            assertDoesNotThrow(() -> A2aMigrationMetrics.recordReconciliation(1, 1, 0, 0,
                1));
            assertDoesNotThrow(() -> A2aMigrationMetrics.recordEndpointWrite(
                A2aMigrationMetrics.Role.PRIMARY, A2aMigrationMetrics.Target.CANONICAL,
                A2aMigrationMetrics.Operation.REGISTER, A2aMigrationMetrics.Result.SUCCESS,
                1));
            assertDoesNotThrow(() -> registerGauge.invoke(null, "test", "kind", "value",
                new AtomicInteger()));
        }
    }
}
