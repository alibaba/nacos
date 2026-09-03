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

import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationReconciliationTaskTest {
    
    private static final String NAMESPACE_ID = "public";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private A2aHistoricalDefinitionScanner scanner;
    
    @Mock
    private A2aHistoricalDefinitionReconciler reconciler;
    
    @Mock
    private A2aMigrationTargetStore targetStore;
    
    @Mock
    private A2aMigrationStateService stateService;
    
    @Mock
    private A2aMigrationCutoverCoordinator cutoverCoordinator;
    
    @Mock
    private A2aMigrationLease lease;
    
    private A2aMigrationReconciliationTask task;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        lenient().when(stateService.resolveConfigured())
            .thenReturn(A2aMigrationState.SYNCING);
        lenient().when(stateService.tryAcquireLease(anyString(), anyLong())).thenReturn(lease);
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("syncing", false,
            "nacos_config", 1L);
        lenient().when(stateService.currentMarker())
            .thenReturn(new VersionedValue<A2aMigrationMarker>(marker, "marker-md5"));
        lenient().when(stateService.isLocalPolicyCompatible(marker)).thenReturn(true);
        lenient().when(lease.renew()).thenReturn(true);
        lenient().when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(new Namespace(NAMESPACE_ID, NAMESPACE_ID)));
        task = new A2aMigrationReconciliationTask(namespaceOperationService, scanner, reconciler,
            targetStore, stateService, cutoverCoordinator);
    }
    
    @AfterEach
    void tearDown() {
        if (task != null) {
            task.destroy();
        }
        System.clearProperty(
            A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY);
        System.clearProperty(
            A2aMigrationReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_PROPERTY);
        System.clearProperty(A2aMigrationReconciliationTask.LEASE_DURATION_SECONDS_PROPERTY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void shouldSkipWhenStateOrLeaseDoesNotPermitReconciliation() {
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL,
            A2aMigrationState.SYNCING);
        when(stateService.tryAcquireLease(anyString(), anyLong())).thenReturn(null);
        
        task.executeReconciliation();
        task.executeReconciliation();
        
        verify(namespaceOperationService, never()).getNamespaceList();
        verify(stateService, never()).persistProgress(any());
    }
    
    @Test
    void shouldNotAcquireLeaseWhenLocalPolicyDiffersFromFrozenPlan() {
        when(stateService.isLocalPolicyCompatible(any())).thenReturn(false);
        
        task.executeReconciliation();
        
        verify(stateService, never()).tryAcquireLease(anyString(), anyLong());
        verify(namespaceOperationService, never()).getNamespaceList();
    }
    
    @Test
    void shouldUseConfiguredLeaseDurationAndRejectInvalidValue() {
        System.setProperty(A2aMigrationReconciliationTask.LEASE_DURATION_SECONDS_PROPERTY,
            "7");
        when(stateService.tryAcquireLease(anyString(), eq(7000L))).thenReturn(null);
        
        task.executeReconciliation();
        
        verify(stateService).tryAcquireLease(anyString(), eq(7000L));
        System.setProperty(A2aMigrationReconciliationTask.LEASE_DURATION_SECONDS_PROPERTY,
            "0");
        assertThrows(IllegalArgumentException.class, task::executeReconciliation);
    }
    
    @Test
    void shouldSkipQuiescingAndMissingOrStaleSyncingMarker() {
        A2aMigrationMarker quiescing = A2aMigrationMarker.syncing("syncing", false,
            "nacos_config", 1L)
            .transition(A2aMigrationState.QUIESCING, "quiescing", 2L);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.QUIESCING,
            A2aMigrationState.SYNCING, A2aMigrationState.SYNCING);
        when(stateService.currentMarker()).thenReturn(null,
            new VersionedValue<A2aMigrationMarker>(quiescing, "quiescing-md5"));
        
        task.executeReconciliation();
        task.executeReconciliation();
        task.executeReconciliation();
        
        verify(stateService, never()).tryAcquireLease(anyString(), anyLong());
        verify(namespaceOperationService, never()).getNamespaceList();
    }
    
    @Test
    void scheduledWrapperShouldContainPreScanFailureAndPermitNextCycle() {
        when(stateService.resolveConfigured()).thenThrow(new IllegalStateException(
            "control store temporarily unavailable")).thenReturn(A2aMigrationState.CANONICAL);
        
        task.safeExecuteReconciliation();
        task.safeExecuteReconciliation();
        
        verify(stateService, times(2)).resolveConfigured();
        verify(namespaceOperationService, never()).getNamespaceList();
    }
    
    @Test
    void shouldReconcileEveryPageAndReportConflictsAndFailures() throws Exception {
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY,
            "2");
        A2aHistoricalDefinitionSnapshot created = snapshot("created");
        A2aHistoricalDefinitionSnapshot conflict = snapshot("conflict");
        A2aHistoricalDefinitionSnapshot failed = snapshot("failed");
        A2aHistoricalDefinitionSnapshot equivalent = snapshot("equivalent");
        when(scanner.scanPage(NAMESPACE_ID, 1, 2)).thenReturn(page(4, 2,
            created, conflict));
        when(scanner.scanPage(NAMESPACE_ID, 2, 2)).thenReturn(page(4, 2,
            failed, equivalent));
        when(scanner.isCurrent(any())).thenReturn(true);
        when(reconciler.reconcile(eq(created), any())).thenAnswer(invocation -> {
            assertTrue(invocation.getArgument(1, BooleanSupplier.class).getAsBoolean());
            return A2aMigrationTargetStore.Result.CREATED;
        });
        when(reconciler.reconcile(eq(conflict), any())).thenThrow(new NacosApiException(
            NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, "target conflict"));
        when(reconciler.reconcile(eq(failed), any()))
            .thenThrow(new IllegalStateException("storage unavailable"));
        when(reconciler.reconcile(eq(equivalent), any()))
            .thenReturn(A2aMigrationTargetStore.Result.EQUIVALENT);
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.singleton("created"));
        
        task.executeReconciliation();
        
        ArgumentCaptor<A2aMigrationProgress> progress =
            ArgumentCaptor.forClass(A2aMigrationProgress.class);
        verify(stateService).persistProgress(progress.capture());
        assertEquals(A2aMigrationState.SYNCING, progress.getValue().getState());
        assertFalse(progress.getValue().getGeneration().isEmpty());
        assertEquals(4L, progress.getValue().getScanned());
        assertEquals(1L, progress.getValue().getMigrated());
        assertEquals(1L, progress.getValue().getConflicts());
        assertEquals(1L, progress.getValue().getFailed());
        assertEquals("storage unavailable", progress.getValue().getLastError());
        verify(lease).close();
        verify(cutoverCoordinator).afterSyncingRound(any(), eq(lease), eq(false), any(), eq(2));
    }
    
    @Test
    void shouldDerivePageCountWhenRepositoryOmitsPagesAvailable() throws Exception {
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY,
            "1");
        A2aHistoricalDefinitionSnapshot first = snapshot("first");
        A2aHistoricalDefinitionSnapshot second = snapshot("second");
        when(scanner.scanPage(NAMESPACE_ID, 1, 1)).thenReturn(page(2, 0, first));
        when(scanner.scanPage(NAMESPACE_ID, 2, 1)).thenReturn(page(2, 0, second));
        when(reconciler.reconcile(any(), any()))
            .thenReturn(A2aMigrationTargetStore.Result.REPAIRED);
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.emptySet());
        
        task.executeReconciliation();
        
        verify(scanner).scanPage(NAMESPACE_ID, 2, 1);
        ArgumentCaptor<A2aMigrationProgress> progress =
            ArgumentCaptor.forClass(A2aMigrationProgress.class);
        verify(stateService).persistProgress(progress.capture());
        assertEquals(2L, progress.getValue().getScanned());
        assertEquals(2L, progress.getValue().getMigrated());
    }
    
    @Test
    void completeZeroDifferenceRoundShouldBeOfferedToCutoverCoordinator() throws Exception {
        A2aHistoricalDefinitionSnapshot equivalent = snapshot("equivalent");
        when(scanner.scanPage(NAMESPACE_ID, 1, 100)).thenReturn(page(1, 1, equivalent));
        when(reconciler.reconcile(eq(equivalent), any()))
            .thenReturn(A2aMigrationTargetStore.Result.EQUIVALENT);
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.emptySet());
        
        task.executeReconciliation();
        
        verify(cutoverCoordinator).afterSyncingRound(any(), eq(lease), eq(true),
            eq(Collections.singletonMap(NAMESPACE_ID,
                Collections.singleton("equivalent"))),
            eq(100));
    }
    
    @Test
    void shouldDeleteOnlyAfterTwoCompleteOrphanScans() throws Exception {
        when(scanner.scanPage(NAMESPACE_ID, 1, 100)).thenReturn(emptyPage());
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.singleton("orphan"));
        when(targetStore.deleteConfirmedOrphan(NAMESPACE_ID, "orphan")).thenReturn(true);
        
        task.executeReconciliation();
        verify(targetStore, never()).deleteConfirmedOrphan(anyString(), anyString());
        task.executeReconciliation();
        
        verify(targetStore).deleteConfirmedOrphan(NAMESPACE_ID, "orphan");
        ArgumentCaptor<A2aMigrationProgress> progress =
            ArgumentCaptor.forClass(A2aMigrationProgress.class);
        verify(stateService, times(2)).persistProgress(progress.capture());
        assertEquals(1L, progress.getAllValues().get(1).getMigrated());
        verify(lease, times(2)).close();
    }
    
    @Test
    void failedNamespaceScanShouldResetOrphanConfirmation() throws Exception {
        when(scanner.scanPage(NAMESPACE_ID, 1, 100)).thenReturn(emptyPage())
            .thenThrow(new IllegalStateException("source unavailable"))
            .thenReturn(emptyPage(), emptyPage());
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.singleton("orphan"));
        when(targetStore.deleteConfirmedOrphan(NAMESPACE_ID, "orphan")).thenReturn(true);
        
        task.executeReconciliation();
        task.executeReconciliation();
        task.executeReconciliation();
        verify(targetStore, never()).deleteConfirmedOrphan(anyString(), anyString());
        task.executeReconciliation();
        
        verify(targetStore).deleteConfirmedOrphan(NAMESPACE_ID, "orphan");
        verify(targetStore, times(3)).listMigratedAgentNames(NAMESPACE_ID);
    }
    
    @Test
    void shouldFailClosedWhenNamespaceListOrLeaseIsLost() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(null)
            .thenReturn(Collections.singletonList(new Namespace(NAMESPACE_ID, NAMESPACE_ID)));
        when(lease.renew()).thenReturn(false);
        
        task.executeReconciliation();
        task.executeReconciliation();
        
        ArgumentCaptor<A2aMigrationProgress> progress =
            ArgumentCaptor.forClass(A2aMigrationProgress.class);
        verify(stateService, times(2)).persistProgress(progress.capture());
        assertEquals(1L, progress.getAllValues().get(0).getFailed());
        assertEquals("Namespace listing is unavailable",
            progress.getAllValues().get(0).getLastError());
        assertEquals(1L, progress.getAllValues().get(1).getFailed());
        assertEquals("Historical A2A reconciliation lease lost",
            progress.getAllValues().get(1).getLastError());
        verify(scanner, never()).scanPage(anyString(), anyInt(), anyInt());
    }
    
    @Test
    void namespacePageShouldFailClosedWhenLeaseRenewalIsLost() {
        when(lease.renew()).thenReturn(true, false);
        
        task.executeReconciliation();
        
        ArgumentCaptor<A2aMigrationProgress> progress =
            ArgumentCaptor.forClass(A2aMigrationProgress.class);
        verify(stateService).persistProgress(progress.capture());
        assertEquals(1L, progress.getValue().getFailed());
        assertEquals("Historical A2A reconciliation lease lost",
            progress.getValue().getLastError());
        verify(scanner, never()).scanPage(anyString(), anyInt(), anyInt());
        verify(cutoverCoordinator).afterSyncingRound(any(), eq(lease), eq(false), any(), eq(100));
    }
    
    @Test
    void unresolvedOrphanDeleteShouldKeepRoundNonZeroDifference() throws Exception {
        when(scanner.scanPage(NAMESPACE_ID, 1, 100)).thenReturn(emptyPage());
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.singleton("orphan"));
        when(targetStore.deleteConfirmedOrphan(NAMESPACE_ID, "orphan")).thenReturn(false);
        
        task.executeReconciliation();
        task.executeReconciliation();
        
        verify(targetStore).deleteConfirmedOrphan(NAMESPACE_ID, "orphan");
        verify(cutoverCoordinator, times(2)).afterSyncingRound(any(), eq(lease), eq(false),
            any(), eq(100));
    }
    
    @Test
    void canonicalStateShouldCancelExistingPeriodicSchedule() {
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> scheduledFuture = mock(ScheduledFuture.class);
        ReflectionTestUtils.setField(task, "scheduledFuture", scheduledFuture);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL);
        
        task.executeReconciliation();
        
        verify(scheduledFuture).cancel(false);
        verify(namespaceOperationService, never()).getNamespaceList();
    }
    
    @Test
    void startupShouldIgnoreChildDuplicateAndInactiveContexts() {
        ConfigurableApplicationContext parent = mock(ConfigurableApplicationContext.class);
        ApplicationReadyEvent childEvent = event(parent);
        task.onApplicationEvent(childEvent);
        verify(stateService, never()).resolveConfigured();
        
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL);
        ApplicationReadyEvent rootEvent = event(null);
        task.onApplicationEvent(rootEvent);
        task.onApplicationEvent(rootEvent);
        
        verify(stateService).resolveConfigured();
        verify(namespaceOperationService, never()).getNamespaceList();
    }
    
    @Test
    void startupShouldScheduleActiveReconciliationWithConfiguredInterval() {
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_PROPERTY,
            "3600");
        when(scanner.scanPage(NAMESPACE_ID, 1, 100)).thenReturn(emptyPage());
        when(targetStore.listMigratedAgentNames(NAMESPACE_ID))
            .thenReturn(Collections.emptySet());
        
        task.onApplicationEvent(event(null));
        
        verify(namespaceOperationService, timeout(2000)).getNamespaceList();
    }
    
    @Test
    void startupShouldRejectInvalidReconciliationInterval() {
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_INTERVAL_SECONDS_PROPERTY,
            "0");
        assertThrows(IllegalArgumentException.class,
            () -> task.onApplicationEvent(event(null)));
    }
    
    @Test
    void invalidPageConfigurationShouldBeReportedWithoutScanning() {
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY,
            "0");
        
        task.executeReconciliation();
        
        ArgumentCaptor<A2aMigrationProgress> progress =
            ArgumentCaptor.forClass(A2aMigrationProgress.class);
        verify(stateService).persistProgress(progress.capture());
        assertEquals(1L, progress.getValue().getFailed());
        assertEquals("Historical A2A migration page size must be positive",
            progress.getValue().getLastError());
        verify(scanner, never()).scanPage(anyString(), anyInt(), anyInt());
    }
    
    private ApplicationReadyEvent event(ConfigurableApplicationContext parent) {
        ApplicationReadyEvent result = mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(result.getApplicationContext()).thenReturn(context);
        when(context.getParent()).thenReturn(parent);
        return result;
    }
    
    private A2aHistoricalDefinitionSnapshot snapshot(String name) {
        AgentCardVersionInfo summary = new AgentCardVersionInfo();
        summary.setName(name);
        summary.setLatestPublishedVersion("1.0.0");
        return new A2aHistoricalDefinitionSnapshot(NAMESPACE_ID, name, "summary", "md5",
            summary, new LinkedHashMap<String, A2aHistoricalDefinitionSnapshot.VersionSnapshot>(),
            "fingerprint-" + name);
    }
    
    @SafeVarargs
    private final Page<A2aHistoricalDefinitionSnapshot> page(long total, int pages,
        A2aHistoricalDefinitionSnapshot... items) {
        Page<A2aHistoricalDefinitionSnapshot> result = new Page<A2aHistoricalDefinitionSnapshot>();
        result.setTotalCount((int) total);
        result.setPagesAvailable(pages);
        result.setPageItems(Arrays.asList(items));
        return result;
    }
    
    private Page<A2aHistoricalDefinitionSnapshot> emptyPage() {
        return page(0, 0);
    }
    
}
