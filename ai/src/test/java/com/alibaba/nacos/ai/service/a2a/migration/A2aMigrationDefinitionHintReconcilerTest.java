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
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationDefinitionHintReconcilerTest {
    
    @Mock
    private A2aHistoricalDefinitionScanner scanner;
    
    @Mock
    private A2aHistoricalDefinitionReconciler reconciler;
    
    @Mock
    private A2aMigrationStateService stateService;
    
    @Test
    void shouldBoundAndCoalesceHintsBeforeOneWorkerDrain() throws NacosException {
        ControlledExecutor executor = new ControlledExecutor();
        A2aMigrationDefinitionHintReconciler service = service(executor, 2);
        A2aHistoricalDefinitionSnapshot first = snapshot();
        A2aHistoricalDefinitionSnapshot second = snapshot();
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        when(scanner.scanOne("ns", "agent-a")).thenReturn(Optional.of(first));
        when(scanner.scanOne("ns", "agent-b")).thenReturn(Optional.of(second));
        when(scanner.isCurrent(first)).thenReturn(true);
        when(scanner.isCurrent(second)).thenReturn(true);
        when(reconciler.reconcile(any(), any())).thenAnswer(invocation -> {
            BooleanSupplier sourceCurrent = invocation.getArgument(1);
            assertTrue(sourceCurrent.getAsBoolean());
            return A2aMigrationTargetStore.Result.EQUIVALENT;
        });
        
        assertTrue(service.submit("ns", "agent-a"));
        assertTrue(service.submit("ns", "agent-a"));
        assertTrue(service.submit("ns", "agent-b"));
        assertFalse(service.submit("ns", "agent-c"));
        assertEquals(2, service.pendingCount());
        assertEquals(1, executor.size());
        
        executor.runNext();
        assertEquals(0, service.pendingCount());
        verify(reconciler, org.mockito.Mockito.times(2)).reconcile(any(), any());
        service.destroy();
        assertTrue(executor.isShutdown());
    }
    
    @Test
    void shouldIgnoreInactiveAndDeletedSources() throws NacosException {
        ControlledExecutor executor = new ControlledExecutor();
        A2aMigrationDefinitionHintReconciler service = service(executor, 2);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL,
            A2aMigrationState.SYNCING, A2aMigrationState.SYNCING);
        assertFalse(service.submit("ns", "inactive-agent"));
        assertTrue(service.submit("ns", "deleted-agent"));
        when(scanner.scanOne("ns", "deleted-agent")).thenReturn(Optional.empty());
        executor.runNext();
        verify(reconciler, never()).reconcile(any(), any());
    }
    
    @Test
    void shouldDropHintFailureForPeriodicRepair() throws NacosException {
        ControlledExecutor executor = new ControlledExecutor();
        A2aMigrationDefinitionHintReconciler service = service(executor, 2);
        A2aHistoricalDefinitionSnapshot snapshot = snapshot();
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        when(scanner.scanOne("ns", "broken-agent")).thenReturn(Optional.of(snapshot));
        when(reconciler.reconcile(any(), any())).thenThrow(
            new NacosException(NacosException.SERVER_ERROR, "retry later"));
        assertTrue(service.submit("ns", "broken-agent"));
        executor.runNext();
        assertEquals(0, service.pendingCount());
    }
    
    @Test
    void shouldStopQueuedHintWhenStateChangesBeforeDrain() {
        ControlledExecutor executor = new ControlledExecutor();
        A2aMigrationDefinitionHintReconciler service = service(executor, 2);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING,
            A2aMigrationState.CANONICAL);
        assertTrue(service.submit("ns", "agent"));
        executor.runNext();
        verify(scanner, never()).scanOne(any(), any());
    }
    
    @Test
    void shouldStopQueuedHintWhenLocalPolicyDiffersFromFrozenPlan() {
        ControlledExecutor executor = new ControlledExecutor();
        A2aMigrationDefinitionHintReconciler service = service(executor, 2);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        when(stateService.isLocalPolicyCompatible(any())).thenReturn(false);
        
        assertTrue(service.submit("ns", "agent"));
        executor.runNext();
        
        verify(scanner, never()).scanOne(any(), any());
    }
    
    @Test
    void shouldRejectInvalidCapacityAndExecutorRejection() {
        ControlledExecutor executor = new ControlledExecutor();
        assertThrows(IllegalArgumentException.class,
            () -> service(executor, 0));
        executor.reject = true;
        A2aMigrationDefinitionHintReconciler service = service(executor, 1);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        assertFalse(service.submit("ns", "agent"));
        assertEquals(0, service.pendingCount());
    }
    
    @Test
    void productionConstructorShouldOwnAndShutdownExecutor() {
        A2aMigrationDefinitionHintReconciler service =
            new A2aMigrationDefinitionHintReconciler(scanner, reconciler, stateService);
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.CANONICAL);
        assertFalse(service.submit("ns", "agent"));
        service.destroy();
    }
    
    private A2aMigrationDefinitionHintReconciler service(ControlledExecutor executor,
        int capacity) {
        A2aMigrationMarker marker = A2aMigrationMarker.syncing("syncing", false,
            "nacos_config", 1L);
        org.mockito.Mockito.lenient().when(stateService.currentMarker())
            .thenReturn(new VersionedValue<A2aMigrationMarker>(marker, "marker-md5"));
        org.mockito.Mockito.lenient().when(stateService.isLocalPolicyCompatible(marker))
            .thenReturn(true);
        return new A2aMigrationDefinitionHintReconciler(scanner, reconciler, stateService,
            executor, capacity);
    }
    
    private A2aHistoricalDefinitionSnapshot snapshot() {
        return org.mockito.Mockito.mock(A2aHistoricalDefinitionSnapshot.class);
    }
    
    private static final class ControlledExecutor extends AbstractExecutorService {
        
        private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
        
        private boolean shutdown;
        
        private boolean reject;
        
        @Override
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            tasks.clear();
            return Collections.emptyList();
        }
        
        @Override
        public boolean isShutdown() {
            return shutdown;
        }
        
        @Override
        public boolean isTerminated() {
            return shutdown;
        }
        
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
        
        @Override
        public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("rejected");
            }
            tasks.add(command);
        }
        
        private int size() {
            return tasks.size();
        }
        
        private void runNext() {
            tasks.remove().run();
        }
    }
}
