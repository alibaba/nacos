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
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationQuiescingTaskTest {
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private A2aMigrationStateService stateService;
    
    @Mock
    private A2aMigrationDefinitionReadinessValidator definitionValidator;
    
    @Mock
    private A2aMigrationSearchReadinessGate searchGate;
    
    @Mock
    private A2aMigrationRuntimeReadinessGate runtimeGate;
    
    @Mock
    private A2aMigrationLease lease;
    
    private A2aMigrationQuiescingTask task;
    
    private A2aMigrationMarker syncing;
    
    private A2aMigrationMarker quiescing;
    
    private VersionedValue<A2aMigrationMarker> current;
    
    private A2aMigrationMemberView memberView;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        syncing = A2aMigrationMarker.syncing("syncing", false, "nacos_config", 1000L);
        memberView = new A2aMigrationMemberView("members", 3);
        quiescing = syncing.transition(A2aMigrationState.QUIESCING,
            A2aMigrationQuiescingGeneration.create(memberView, "nonce"), 1001L);
        current = new VersionedValue<>(quiescing, "quiescing-md5");
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner");
    }
    
    @AfterEach
    void tearDown() {
        task.destroy();
        System.clearProperty(A2aMigrationQuiescingTask.QUIESCING_TIMEOUT_SECONDS_PROPERTY);
        System.clearProperty(
            A2aMigrationQuiescingTask.QUIESCING_CHECK_INTERVAL_SECONDS_PROPERTY);
        System.clearProperty(A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY);
        System.clearProperty(A2aMigrationReconciliationTask.LEASE_DURATION_SECONDS_PROPERTY);
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void shouldIgnoreChildAndInactiveStartupAndConstructPublicOwner() {
        task.onApplicationEvent(event(mock(ConfigurableApplicationContext.class)));
        verify(stateService, never()).resolveConfigured();
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL);
        task.onApplicationEvent(event(null));
        verify(stateService).resolveConfigured();
        when(stateService.newGeneration()).thenReturn("owner-generation");
        A2aMigrationQuiescingTask publicTask = new A2aMigrationQuiescingTask(stateService,
            definitionValidator, searchGate, runtimeGate);
        publicTask.destroy();
    }
    
    @Test
    void publicConstructorShouldDeclareSpringInjection() throws NoSuchMethodException {
        assertTrue(A2aMigrationQuiescingTask.class.getConstructor(
            A2aMigrationStateService.class,
            A2aMigrationDefinitionReadinessValidator.class,
            A2aMigrationSearchReadinessGate.class,
            A2aMigrationRuntimeReadinessGate.class).isAnnotationPresent(Autowired.class));
    }
    
    @Test
    void shouldScheduleActiveStateAndStopAfterTerminalMarker() {
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        A2aMigrationMarker canonical = quiescing.transition(A2aMigrationState.CANONICAL,
            quiescing.getGeneration(), 1002L);
        when(stateService.currentMarker()).thenReturn(new VersionedValue<>(canonical, "done"));
        task.onApplicationEvent(event(null));
        verify(stateService, timeout(2000)).currentMarker();
        task.executeCheck();
    }
    
    @Test
    void shouldRejectInvalidCheckIntervalAtStartup() {
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        System.setProperty(A2aMigrationQuiescingTask.QUIESCING_CHECK_INTERVAL_SECONDS_PROPERTY,
            "0");
        assertThrows(IllegalArgumentException.class, () -> task.onApplicationEvent(event(null)));
    }
    
    @Test
    void safeWrapperShouldContainControlStoreFailure() {
        when(stateService.currentMarker()).thenThrow(new IllegalStateException("unavailable"));
        assertDoesNotThrow(task::safeExecuteCheck);
    }
    
    @Test
    void shouldAckOnlyAfterLocalReadAndRetryValidation() {
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY,
            "501");
        readyCurrent();
        when(runtimeGate.isLocalMirrorReady()).thenReturn(false, true, true);
        A2aMigrationDefinitionReadinessValidator.Result failed =
            mock(A2aMigrationDefinitionReadinessValidator.Result.class);
        when(failed.isReady()).thenReturn(false);
        A2aMigrationDefinitionReadinessValidator.Result ready =
            new A2aMigrationDefinitionReadinessValidator.Result();
        when(definitionValidator.validate(500, null)).thenReturn(failed, ready);
        when(stateService.advertiseLocalAck(quiescing)).thenReturn(true);
        when(stateService.allMembersAcknowledged(quiescing, memberView)).thenReturn(false);
        task.executeCheck();
        task.executeCheck();
        task.executeCheck();
        verify(stateService).advertiseLocalAck(quiescing);
        verify(definitionValidator, org.mockito.Mockito.times(2)).validate(500, null);
        verify(stateService, never()).tryAcquireLease(anyString(), anyLong());
        when(stateService.currentMarker()).thenReturn(null);
        task.executeCheck();
        verify(stateService).clearLocalAck();
    }
    
    @Test
    void shouldRollbackMemberPolicyAndTimeoutChanges() {
        when(stateService.currentMarker()).thenReturn(current);
        when(stateService.readyMemberView(quiescing)).thenReturn(null);
        rollbackSucceeds();
        task.executeCheck();
        verify(stateService).transition(current, A2aMigrationState.SYNCING, "new-generation");
        verify(stateService).clearLocalAck();
        
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner-2");
        when(stateService.readyMemberView(quiescing)).thenReturn(memberView);
        when(stateService.currentTimeMillis()).thenReturn(121001L);
        task.executeCheck();
        verify(stateService, org.mockito.Mockito.atLeast(2)).transition(current,
            A2aMigrationState.SYNCING, "new-generation");
    }
    
    @Test
    void shouldRejectInvalidTimeoutAndPageConfiguration() {
        readyCurrent();
        System.setProperty(A2aMigrationQuiescingTask.QUIESCING_TIMEOUT_SECONDS_PROPERTY, "0");
        assertThrows(IllegalArgumentException.class, task::executeCheck);
        System.setProperty(A2aMigrationQuiescingTask.QUIESCING_TIMEOUT_SECONDS_PROPERTY, "120");
        System.setProperty(A2aMigrationReconciliationTask.RECONCILIATION_PAGE_SIZE_PROPERTY,
            "0");
        when(runtimeGate.isLocalMirrorReady()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, task::executeCheck);
    }
    
    @Test
    void shouldCompleteAfterAllAckAndFreshFinalGates() {
        readyCurrent();
        A2aMigrationDefinitionReadinessValidator.Result ready =
            new A2aMigrationDefinitionReadinessValidator.Result();
        when(runtimeGate.isLocalMirrorReady()).thenReturn(true);
        when(definitionValidator.validate(100, null)).thenReturn(ready);
        when(stateService.advertiseLocalAck(quiescing)).thenReturn(true);
        when(stateService.allMembersAcknowledged(quiescing, memberView)).thenReturn(true);
        when(stateService.tryAcquireLease("owner", 10 * 60 * 1000L)).thenReturn(lease);
        when(definitionValidator.validate(100, lease)).thenReturn(ready);
        when(searchGate.isReady(ready.getSourceNames())).thenReturn(true);
        when(runtimeGate.isReady()).thenReturn(true);
        when(lease.renew()).thenReturn(true);
        A2aMigrationMarker canonical = quiescing.transition(A2aMigrationState.CANONICAL,
            quiescing.getGeneration(), 1002L);
        when(stateService.transition(current, A2aMigrationState.CANONICAL,
            quiescing.getGeneration())).thenReturn(new VersionedValue<>(canonical, "done"));
        task.executeCheck();
        verify(stateService).transition(current, A2aMigrationState.CANONICAL,
            quiescing.getGeneration());
        verify(stateService).clearLocalAck();
        verify(lease).close();
    }
    
    @Test
    void shouldUseConfiguredLeaseDurationForTerminalGate() {
        readyCurrent();
        localReadyAndAllAck();
        System.setProperty(A2aMigrationReconciliationTask.LEASE_DURATION_SECONDS_PROPERTY,
            "7");
        when(stateService.tryAcquireLease("owner", 7000L)).thenReturn(null);
        
        task.executeCheck();
        
        verify(stateService).tryAcquireLease("owner", 7000L);
    }
    
    @Test
    void shouldWaitWithoutLeaseAndRollbackChangedAck() {
        readyCurrent();
        localReadyAndAllAck();
        when(stateService.tryAcquireLease("owner", 10 * 60 * 1000L)).thenReturn(null);
        task.executeCheck();
        verify(definitionValidator, never()).validate(100, lease);
        
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner-2");
        when(stateService.tryAcquireLease("owner-2", 10 * 60 * 1000L)).thenReturn(lease);
        A2aMigrationMemberView changed = new A2aMigrationMemberView("changed", 3);
        when(stateService.readyMemberView(quiescing)).thenReturn(memberView, changed);
        rollbackSucceeds();
        task.executeCheck();
        verify(stateService).transition(current, A2aMigrationState.SYNCING, "new-generation");
        verify(lease).close();
    }
    
    @Test
    void shouldRollbackFinalDifferenceLeaseLossAndStateChange() {
        A2aMigrationDefinitionReadinessValidator.Result ready =
            new A2aMigrationDefinitionReadinessValidator.Result();
        A2aMigrationDefinitionReadinessValidator.Result failed =
            mock(A2aMigrationDefinitionReadinessValidator.Result.class);
        when(failed.isReady()).thenReturn(false);
        readyCurrent();
        localReadyAndAllAck();
        when(stateService.tryAcquireLease("owner", 10 * 60 * 1000L)).thenReturn(lease);
        when(definitionValidator.validate(100, lease)).thenReturn(failed);
        rollbackSucceeds();
        task.executeCheck();
        verify(stateService).transition(current, A2aMigrationState.SYNCING, "new-generation");
        
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner-2");
        when(stateService.tryAcquireLease("owner-2", 10 * 60 * 1000L)).thenReturn(lease);
        when(definitionValidator.validate(100, lease)).thenReturn(ready);
        when(searchGate.isReady(ready.getSourceNames())).thenReturn(true);
        when(runtimeGate.isReady()).thenReturn(true);
        when(lease.renew()).thenReturn(false);
        task.executeCheck();
        verify(stateService, org.mockito.Mockito.atLeast(2)).transition(current,
            A2aMigrationState.SYNCING, "new-generation");
        
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner-3");
        when(stateService.tryAcquireLease("owner-3", 10 * 60 * 1000L)).thenReturn(lease);
        when(lease.renew()).thenReturn(true);
        when(runtimeGate.isReady()).thenReturn(true, false);
        task.executeCheck();
        verify(stateService, org.mockito.Mockito.atLeast(3)).transition(current,
            A2aMigrationState.SYNCING, "new-generation");
    }
    
    @Test
    void shouldRollbackUnresolvedCanonicalCasAndIgnoreChangedMarker() {
        A2aMigrationDefinitionReadinessValidator.Result ready =
            new A2aMigrationDefinitionReadinessValidator.Result();
        readyCurrent();
        localReadyAndAllAck();
        when(stateService.tryAcquireLease("owner", 10 * 60 * 1000L)).thenReturn(lease);
        when(definitionValidator.validate(100, lease)).thenReturn(ready);
        when(searchGate.isReady(ready.getSourceNames())).thenReturn(true);
        when(runtimeGate.isReady()).thenReturn(true);
        when(lease.renew()).thenReturn(true);
        when(stateService.transition(current, A2aMigrationState.CANONICAL,
            quiescing.getGeneration())).thenReturn(null);
        when(stateService.newGeneration()).thenReturn("new-generation");
        when(stateService.transition(current, A2aMigrationState.SYNCING, "new-generation"))
            .thenReturn(new VersionedValue<>(syncing, "syncing"));
        task.executeCheck();
        verify(stateService).transition(current, A2aMigrationState.SYNCING, "new-generation");
        
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner-2");
        when(stateService.currentMarker()).thenReturn(current,
            new VersionedValue<>(syncing, "changed"));
        task.executeCheck();
        verify(lease, atLeastOnce()).close();
    }
    
    @Test
    void shouldIgnoreChangedGenerationAfterLeaseAndNullFinalMarker() {
        A2aMigrationDefinitionReadinessValidator.Result ready =
            new A2aMigrationDefinitionReadinessValidator.Result();
        readyCurrent();
        localReadyAndAllAck();
        when(stateService.tryAcquireLease("owner", 10 * 60 * 1000L)).thenReturn(lease);
        A2aMigrationMarker changedGeneration = quiescing.transition(A2aMigrationState.QUIESCING,
            A2aMigrationQuiescingGeneration.create(memberView, "changed"), 1003L);
        when(stateService.currentMarker()).thenReturn(current,
            new VersionedValue<>(changedGeneration, "changed-generation"));
        task.executeCheck();
        verify(definitionValidator, never()).validate(100, lease);
        
        task = new A2aMigrationQuiescingTask(stateService, definitionValidator, searchGate,
            runtimeGate, "owner-2");
        when(stateService.currentMarker()).thenReturn(current, current, null);
        when(stateService.tryAcquireLease("owner-2", 10 * 60 * 1000L)).thenReturn(lease);
        when(definitionValidator.validate(100, lease)).thenReturn(ready);
        when(searchGate.isReady(ready.getSourceNames())).thenReturn(true);
        when(runtimeGate.isReady()).thenReturn(true);
        when(lease.renew()).thenReturn(true);
        task.executeCheck();
        verify(stateService, never()).transition(current, A2aMigrationState.CANONICAL,
            quiescing.getGeneration());
    }
    
    @Test
    void rollbackShouldIgnoreNonQuiescingMarker() {
        VersionedValue<A2aMigrationMarker> nonQuiescing =
            new VersionedValue<>(syncing, "syncing");
        ReflectionTestUtils.invokeMethod(task, "rollback", nonQuiescing, "ignored");
        verify(stateService, never()).newGeneration();
        
        double failures = A2aMigrationMetrics.eventCount(
            A2aMigrationMetrics.Event.ROLLBACK, A2aMigrationMetrics.Result.FAILED);
        when(stateService.newGeneration()).thenReturn("new-generation");
        ReflectionTestUtils.invokeMethod(task, "rollback", current, "cas-failed");
        assertTrue(A2aMigrationMetrics.eventCount(A2aMigrationMetrics.Event.ROLLBACK,
            A2aMigrationMetrics.Result.FAILED) > failures);
    }
    
    private void readyCurrent() {
        when(stateService.currentMarker()).thenReturn(current);
        when(stateService.readyMemberView(quiescing)).thenReturn(memberView);
        when(stateService.currentTimeMillis()).thenReturn(1002L);
    }
    
    private void localReadyAndAllAck() {
        A2aMigrationDefinitionReadinessValidator.Result ready =
            new A2aMigrationDefinitionReadinessValidator.Result();
        when(runtimeGate.isLocalMirrorReady()).thenReturn(true);
        when(definitionValidator.validate(100, null)).thenReturn(ready);
        when(stateService.advertiseLocalAck(quiescing)).thenReturn(true);
        when(stateService.allMembersAcknowledged(quiescing, memberView)).thenReturn(true);
    }
    
    private void rollbackSucceeds() {
        when(stateService.newGeneration()).thenReturn("new-generation");
        when(stateService.transition(current, A2aMigrationState.SYNCING, "new-generation"))
            .thenReturn(new VersionedValue<>(syncing, "syncing"));
    }
    
    private ApplicationReadyEvent event(ConfigurableApplicationContext parent) {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(event.getApplicationContext()).thenReturn(context);
        when(context.getParent()).thenReturn(parent);
        return event;
    }
}
