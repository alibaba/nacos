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

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ServiceConfigurationError;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelegatingPluginStateSynchronizerTest {
    
    private final PluginStateSynchronizationContext context =
        mock(PluginStateSynchronizationContext.class);
    
    @Test
    void springContextUsesInjectionConstructor() {
        Boolean previousStandalone =
            (Boolean) ReflectionTestUtils.getField(EnvUtil.class, "isStandalone");
        EnvUtil.setIsStandalone(false);
        try (AnnotationConfigApplicationContext applicationContext =
            new AnnotationConfigApplicationContext()) {
            applicationContext.registerBean(PluginStatePersistenceService.class,
                () -> mock(PluginStatePersistenceService.class));
            applicationContext.register(DelegatingPluginStateSynchronizer.class);
            applicationContext.refresh();
            
            assertNotNull(applicationContext.getBean(DelegatingPluginStateSynchronizer.class));
        } finally {
            EnvUtil.setIsStandalone(previousStandalone);
        }
    }
    
    @Test
    void springConstructorDefersProviderAndResourceAccess() {
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginStateApplier> applierProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PluginStateConsensusService> consensusServiceProvider =
            mock(ObjectProvider.class);
        
        DelegatingPluginStateSynchronizer synchronizer =
            new DelegatingPluginStateSynchronizer(mock(PluginStatePersistenceService.class),
                applierProvider, consensusServiceProvider);
        
        verify(applierProvider, never()).getIfAvailable();
        verify(consensusServiceProvider, never()).getIfAvailable();
        
        @SuppressWarnings("unchecked")
        Supplier<Collection<PluginStateSynchronizerProvider>> providerSupplier =
            (Supplier<Collection<PluginStateSynchronizerProvider>>) ReflectionTestUtils.getField(
                synchronizer, "providerSupplier");
        assertNotNull(providerSupplier);
        assertNotNull(providerSupplier.get());
    }
    
    @Test
    void missingTypeUsesBuiltInRaftWithoutExternalDiscovery() throws Exception {
        PluginStateSynchronizer raft = availableSynchronizer();
        AtomicBoolean discovered = new AtomicBoolean();
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> null, () -> {
            discovered.set(true);
            return Collections.emptyList();
        }, provider("raft", raft), Runnable::run);
        
        synchronizer.initialize();
        synchronizer.initialize();
        synchronizer.syncStateChange("auth:nacos", true);
        
        assertEquals("raft", synchronizer.getSelectedName());
        assertEquals(DelegatingPluginStateSynchronizer.InitializationState.INITIALIZED,
            synchronizer.getState());
        assertTrue(synchronizer.isAvailable());
        assertFalse(discovered.get());
        verify(raft).initialize();
        verify(raft).syncStateChange("auth:nacos", true);
    }
    
    @Test
    void explicitRaftAlsoSkipsExternalDiscovery() {
        PluginStateSynchronizer raft = availableSynchronizer();
        AtomicBoolean discovered = new AtomicBoolean();
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> "raft", () -> {
            discovered.set(true);
            throw new ServiceConfigurationError("must not run");
        }, provider("raft", raft), Runnable::run);
        
        synchronizer.initialize();
        
        assertTrue(synchronizer.isAvailable());
        assertFalse(discovered.get());
    }
    
    @Test
    void explicitTypeSelectsExternalProvider() throws Exception {
        PluginStateSynchronizer external = availableSynchronizer();
        PluginStateSynchronizerProvider provider = provider("database", external);
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> " database ",
            () -> Collections.singletonList(provider),
            provider("raft", availableSynchronizer()), Runnable::run);
        
        synchronizer.initialize();
        synchronizer.syncConfigChange("trace:otel",
            Collections.singletonMap("endpoint", "value"));
        
        assertEquals("database", synchronizer.getSelectedName());
        assertTrue(synchronizer.isAvailable());
        verify(external).initialize();
        verify(external).syncConfigChange("trace:otel",
            Collections.singletonMap("endpoint", "value"));
    }
    
    @Test
    void duplicateExternalProvidersUseDeterministicFirstClass() {
        PluginStateSynchronizer first = availableSynchronizer();
        PluginStateSynchronizer later = availableSynchronizer();
        Collection<PluginStateSynchronizerProvider> providers =
            Arrays.asList(new ZProvider(later), new AProvider(first));
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> "database",
            () -> providers, provider("raft", availableSynchronizer()), Runnable::run);
        
        synchronizer.initialize();
        
        verify(first).initialize();
        verify(later, never()).initialize();
    }
    
    @Test
    void missingConfiguredProviderDoesNotFallBackToRaft() {
        PluginStateSynchronizer raft = availableSynchronizer();
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> "database",
            Collections::emptyList, provider("raft", raft), Runnable::run);
        
        synchronizer.initialize();
        
        assertEquals(DelegatingPluginStateSynchronizer.InitializationState.UNAVAILABLE,
            synchronizer.getState());
        assertFalse(synchronizer.isAvailable());
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncStateChange("auth:nacos", true));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        verify(raft, never()).initialize();
    }
    
    @Test
    void missingBuiltInProviderMakesDefaultRaftUnavailable() {
        assertUnavailable(synchronizer(() -> null, Collections::emptyList, null, Runnable::run));
    }
    
    @Test
    void discoveryAndMetadataFailuresMakeSynchronizerUnavailable() {
        assertUnavailable(synchronizer(() -> "database", () -> {
            throw new ServiceConfigurationError("discovery failed");
        }, provider("raft", availableSynchronizer()), Runnable::run));
        assertUnavailable(synchronizer(() -> "database",
            () -> Collections.singletonList(null),
            provider("raft", availableSynchronizer()), Runnable::run));
        assertUnavailable(synchronizer(() -> "database",
            () -> Collections.singletonList(provider("", availableSynchronizer())),
            provider("raft", availableSynchronizer()), Runnable::run));
        assertUnavailable(synchronizer(() -> {
            throw new IllegalStateException("property failed");
        }, Collections::emptyList, provider("raft", availableSynchronizer()), Runnable::run));
    }
    
    @Test
    void creationAndInitializationFailuresAreIsolated() {
        PluginStateSynchronizerProvider nullProvider =
            provider("database", null);
        assertUnavailable(synchronizer(() -> "database",
            () -> Collections.singletonList(nullProvider),
            provider("raft", availableSynchronizer()), Runnable::run));
        
        PluginStateSynchronizer broken = availableSynchronizer();
        doThrow(new IllegalStateException("init failed")).when(broken).initialize();
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> "database",
            () -> Collections.singletonList(provider("database", broken)),
            provider("raft", availableSynchronizer()), Runnable::run);
        
        assertUnavailable(synchronizer);
        verify(broken).shutdown();
    }
    
    @Test
    void rejectedExecutorDoesNotEscapeStartup() {
        Executor rejectingExecutor = command -> {
            throw new IllegalStateException("rejected");
        };
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> null,
            Collections::emptyList, provider("raft", availableSynchronizer()),
            rejectingExecutor);
        
        synchronizer.initialize();
        
        assertEquals(DelegatingPluginStateSynchronizer.InitializationState.UNAVAILABLE,
            synchronizer.getState());
    }
    
    @Test
    void shutdownDuringInitializationCannotReenableSynchronizer() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        PluginStateSynchronizer delegate = availableSynchronizer();
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> null,
            Collections::emptyList, provider("raft", delegate), task::set);
        
        synchronizer.initialize();
        synchronizer.shutdown();
        task.get().run();
        
        assertFalse(synchronizer.isAvailable());
        assertEquals(DelegatingPluginStateSynchronizer.InitializationState.UNAVAILABLE,
            synchronizer.getState());
        verify(delegate).shutdown();
    }
    
    @Test
    void unavailableAndBrokenDelegatesFailExplicitly() throws Exception {
        PluginStateSynchronizer unavailable = mock(PluginStateSynchronizer.class);
        DelegatingPluginStateSynchronizer unavailableWrapper = synchronizer(() -> null,
            Collections::emptyList, provider("raft", unavailable), Runnable::run);
        unavailableWrapper.initialize();
        
        assertFalse(unavailableWrapper.isAvailable());
        assertThrows(NacosApiException.class,
            () -> unavailableWrapper.syncConfigChange("trace:otel", Collections.emptyMap()));
        
        PluginStateSynchronizer brokenAvailability = mock(PluginStateSynchronizer.class);
        when(brokenAvailability.isAvailable()).thenThrow(new IllegalStateException("broken"));
        DelegatingPluginStateSynchronizer brokenAvailabilityWrapper = synchronizer(() -> null,
            Collections::emptyList, provider("raft", brokenAvailability), Runnable::run);
        brokenAvailabilityWrapper.initialize();
        
        assertFalse(brokenAvailabilityWrapper.isAvailable());
        
        PluginStateSynchronizer broken = availableSynchronizer();
        doThrow(new IllegalStateException("state sync failed")).when(broken)
            .syncStateChange("trace:otel", false);
        doThrow(new IllegalStateException("sync failed")).when(broken)
            .syncConfigChange("trace:otel", Collections.emptyMap());
        DelegatingPluginStateSynchronizer brokenWrapper = synchronizer(() -> null,
            Collections::emptyList, provider("raft", broken), Runnable::run);
        brokenWrapper.initialize();
        
        assertThrows(NacosApiException.class,
            () -> brokenWrapper.syncStateChange("trace:otel", false));
        assertThrows(NacosApiException.class,
            () -> brokenWrapper.syncConfigChange("trace:otel", Collections.emptyMap()));
    }
    
    @Test
    void delegateApiExceptionIsPreservedAndShutdownFailureIsContained() throws Exception {
        PluginStateSynchronizer delegate = availableSynchronizer();
        NacosApiException expected = new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "invalid");
        doThrow(expected).when(delegate).syncStateChange("auth:nacos", false);
        doThrow(expected).when(delegate)
            .syncConfigChange("auth:nacos", Collections.emptyMap());
        doThrow(new IllegalStateException("shutdown failed")).when(delegate).shutdown();
        DelegatingPluginStateSynchronizer synchronizer = synchronizer(() -> null,
            Collections::emptyList, provider("raft", delegate), Runnable::run);
        synchronizer.initialize();
        
        assertEquals(expected, assertThrows(NacosApiException.class,
            () -> synchronizer.syncStateChange("auth:nacos", false)));
        assertEquals(expected, assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("auth:nacos", Collections.emptyMap())));
        synchronizer.shutdown();
        
        assertFalse(synchronizer.isAvailable());
    }
    
    private DelegatingPluginStateSynchronizer synchronizer(
        java.util.function.Supplier<String> typeSupplier,
        java.util.function.Supplier<Collection<PluginStateSynchronizerProvider>> providerSupplier,
        PluginStateSynchronizerProvider raftProvider, Executor executor) {
        return new DelegatingPluginStateSynchronizer(typeSupplier, providerSupplier, raftProvider,
            context, executor);
    }
    
    private PluginStateSynchronizer availableSynchronizer() {
        PluginStateSynchronizer result = mock(PluginStateSynchronizer.class);
        when(result.isAvailable()).thenReturn(true);
        return result;
    }
    
    private PluginStateSynchronizerProvider provider(String name,
        PluginStateSynchronizer synchronizer) {
        PluginStateSynchronizerProvider result = mock(PluginStateSynchronizerProvider.class);
        when(result.getName()).thenReturn(name);
        when(result.createSynchronizer(context)).thenReturn(synchronizer);
        return result;
    }
    
    private void assertUnavailable(DelegatingPluginStateSynchronizer synchronizer) {
        synchronizer.initialize();
        assertEquals(DelegatingPluginStateSynchronizer.InitializationState.UNAVAILABLE,
            synchronizer.getState());
        assertFalse(synchronizer.isAvailable());
    }
    
    private class AProvider implements PluginStateSynchronizerProvider {
        
        private final PluginStateSynchronizer synchronizer;
        
        AProvider(PluginStateSynchronizer synchronizer) {
            this.synchronizer = synchronizer;
        }
        
        @Override
        public String getName() {
            return "database";
        }
        
        @Override
        public PluginStateSynchronizer createSynchronizer(
            PluginStateSynchronizationContext context) {
            return synchronizer;
        }
    }
    
    private class ZProvider extends AProvider {
        
        ZProvider(PluginStateSynchronizer synchronizer) {
            super(synchronizer);
        }
    }
}
