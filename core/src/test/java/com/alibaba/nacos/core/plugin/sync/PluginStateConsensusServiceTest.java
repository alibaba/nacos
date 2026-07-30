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

import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.plugin.PluginStateProcessor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginStateConsensusServiceTest {
    
    @Test
    void springContextUsesInjectionConstructor() {
        Boolean previousStandalone =
            (Boolean) ReflectionTestUtils.getField(EnvUtil.class, "isStandalone");
        EnvUtil.setIsStandalone(false);
        try (AnnotationConfigApplicationContext applicationContext =
            new AnnotationConfigApplicationContext()) {
            applicationContext.registerBean(PluginStateProcessor.class,
                () -> mock(PluginStateProcessor.class));
            applicationContext.registerBean(ProtocolManager.class,
                () -> mock(ProtocolManager.class));
            applicationContext.register(PluginStateConsensusService.class);
            applicationContext.refresh();
            
            assertTrue(applicationContext.containsBean("pluginStateConsensusService"));
            assertFalse(applicationContext.getBean(PluginStateConsensusService.class)
                .isAvailable());
        } finally {
            EnvUtil.setIsStandalone(previousStandalone);
        }
    }
    
    @Test
    void productionConstructorDoesNotInitializeConsensus() {
        PluginStateConsensusService service = new PluginStateConsensusService(
            mock(PluginStateProcessor.class), mock(ProtocolManager.class));
        
        assertFalse(service.isAvailable());
        assertEquals(PluginStateConsensusService.RegistrationState.NEW, service.getState());
    }
    
    @Test
    void registersGroupOnceAndExposesProtocol() {
        PluginStateProcessor processor = mock(PluginStateProcessor.class);
        ProtocolManager protocolManager = mock(ProtocolManager.class);
        CPProtocol protocol = mock(CPProtocol.class);
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        PluginStateConsensusService service =
            new PluginStateConsensusService(processor, protocolManager, Runnable::run);
        
        service.initialize();
        service.initialize();
        
        assertTrue(service.isAvailable());
        assertSame(protocol, service.getProtocol());
        assertEquals(PluginStateConsensusService.RegistrationState.AVAILABLE,
            service.getState());
        verify(protocolManager, times(1)).getCpProtocol();
        verify(protocol).addRequestProcessors(anyList());
    }
    
    @Test
    void remainsInitializingUntilAsynchronousTaskRuns() {
        PluginStateProcessor processor = mock(PluginStateProcessor.class);
        ProtocolManager protocolManager = mock(ProtocolManager.class);
        CPProtocol protocol = mock(CPProtocol.class);
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        AtomicReference<Runnable> task = new AtomicReference<>();
        Executor executor = task::set;
        PluginStateConsensusService service =
            new PluginStateConsensusService(processor, protocolManager, executor);
        
        service.initialize();
        
        assertFalse(service.isAvailable());
        assertEquals(PluginStateConsensusService.RegistrationState.INITIALIZING,
            service.getState());
        assertThrows(IllegalStateException.class, service::getProtocol);
        
        task.get().run();
        assertTrue(service.isAvailable());
    }
    
    @Test
    void protocolInitializationFailureDoesNotEscape() {
        ProtocolManager protocolManager = mock(ProtocolManager.class);
        when(protocolManager.getCpProtocol()).thenThrow(
            new IllegalStateException("cp initialization failed"));
        PluginStateConsensusService service = new PluginStateConsensusService(
            mock(PluginStateProcessor.class), protocolManager, Runnable::run);
        
        service.initialize();
        
        assertUnavailable(service, "cp initialization failed");
    }
    
    @Test
    void nullProtocolMarksGroupUnavailable() {
        ProtocolManager protocolManager = mock(ProtocolManager.class);
        when(protocolManager.getCpProtocol()).thenReturn(null);
        PluginStateConsensusService service = new PluginStateConsensusService(
            mock(PluginStateProcessor.class), protocolManager, Runnable::run);
        
        service.initialize();
        
        assertUnavailable(service, "CP protocol is not available");
    }
    
    @Test
    void registrationFailureDoesNotEscape() {
        ProtocolManager protocolManager = mock(ProtocolManager.class);
        CPProtocol protocol = mock(CPProtocol.class);
        when(protocolManager.getCpProtocol()).thenReturn(protocol);
        doThrow(new IllegalStateException()).when(protocol).addRequestProcessors(anyList());
        PluginStateConsensusService service = new PluginStateConsensusService(
            mock(PluginStateProcessor.class), protocolManager, Runnable::run);
        
        service.initialize();
        
        assertUnavailable(service, IllegalStateException.class.getName());
    }
    
    @Test
    void executorRejectionMarksGroupUnavailable() {
        PluginStateConsensusService service = new PluginStateConsensusService(
            mock(PluginStateProcessor.class), mock(ProtocolManager.class),
            command -> {
                throw new IllegalStateException("executor rejected");
            });
        
        service.initialize();
        
        assertUnavailable(service, "executor rejected");
    }
    
    private void assertUnavailable(PluginStateConsensusService service, String message) {
        assertFalse(service.isAvailable());
        assertEquals(PluginStateConsensusService.RegistrationState.UNAVAILABLE,
            service.getState());
        IllegalStateException exception =
            assertThrows(IllegalStateException.class, service::getProtocol);
        assertTrue(exception.getMessage().contains(message));
    }
}
