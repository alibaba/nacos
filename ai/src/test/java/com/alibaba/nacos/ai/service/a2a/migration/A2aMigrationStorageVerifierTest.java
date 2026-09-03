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
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageTestUtils;
import com.alibaba.nacos.ai.service.agent.storage.PreparedAgentVersionWrite;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageChangeEvent;
import com.alibaba.nacos.plugin.ai.storage.model.AiResourceStorageConsistencyMode;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorageChangeListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationStorageVerifierTest {
    
    @Mock
    private AgentVersionStorageService storageService;
    
    private PreparedAgentVersionWrite prepared;
    
    private AgentVersionContent content;
    
    @BeforeEach
    void setUp() {
        content = content("0.3");
        prepared = AgentVersionStorageTestUtils.prepare("tenant-a", "research-agent", "1.0.0",
            content);
    }
    
    @Test
    void strongStorageShouldSaveAndVerifyOnceWithoutListener() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.STRONG);
        when(storageService.load(any())).thenReturn(content);
        AtomicInteger waits = new AtomicInteger();
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 3,
            (mode, hint) -> waits.incrementAndGet());
        
        verifier.saveAndVerify(prepared);
        
        verify(storageService).save(prepared);
        verify(storageService).load(any());
        verify(storageService, never()).addChangeListener(any());
        assertEquals(0, waits.get());
    }
    
    @Test
    void strongStorageShouldFailImmediatelyOnReadBackMismatch() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.STRONG);
        when(storageService.load(any())).thenReturn(content("0.4"));
        AtomicInteger waits = new AtomicInteger();
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 3,
            (mode, hint) -> waits.incrementAndGet());
        
        assertThrows(NacosException.class, () -> verifier.saveAndVerify(prepared));
        
        verify(storageService).load(any());
        assertEquals(0, waits.get());
    }
    
    @Test
    void eventualStorageShouldRetryUntilExactBytesAreVisible() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION);
        when(storageService.load(any())).thenThrow(failure()).thenReturn(content("0.4"))
            .thenThrow(failure()).thenReturn(content);
        AtomicInteger waits = new AtomicInteger();
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 4,
            (mode, hint) -> {
                assertEquals(AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION,
                    mode);
                assertFalse(hint.get());
                waits.incrementAndGet();
            });
        
        verifier.saveAndVerify(prepared);
        
        verify(storageService, times(4)).load(any());
        assertEquals(3, waits.get());
    }
    
    @Test
    void notifiedEventualStorageShouldUseMatchingNotificationAsWakeupHint()
        throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITH_NOTIFICATION);
        when(storageService.load(any())).thenThrow(failure()).thenReturn(content);
        AtomicReference<AiResourceStorageChangeListener> listener = new AtomicReference<>();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return null;
        }).when(storageService).addChangeListener(any());
        doAnswer(invocation -> {
            listener.get().onStorageChanged(new AiResourceStorageChangeEvent("other",
                Constants.Agent.RESOURCE_TYPE_AGENT, "ignored"));
            listener.get().onStorageChanged(new AiResourceStorageChangeEvent(
                prepared.getDescriptor().getProvider(), "skill", "ignored"));
            listener.get().onStorageChanged(new AiResourceStorageChangeEvent(
                prepared.getDescriptor().getProvider(), Constants.Agent.RESOURCE_TYPE_AGENT,
                "visible"));
            return null;
        }).when(storageService).save(prepared);
        AtomicBoolean observedHint = new AtomicBoolean(false);
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 3,
            (mode, hint) -> observedHint.set(hint.getAndSet(false)));
        
        verifier.saveAndVerify(prepared);
        
        assertTrue(observedHint.get());
        verify(storageService).removeChangeListener(listener.get());
    }
    
    @Test
    void notifiedEventualStorageShouldRemoveListenerWhenSaveFails() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITH_NOTIFICATION);
        AtomicReference<AiResourceStorageChangeListener> listener = new AtomicReference<>();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return null;
        }).when(storageService).addChangeListener(any());
        org.mockito.Mockito.doThrow(failure()).when(storageService).save(prepared);
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 2,
            (mode, hint) -> {
            });
        
        assertThrows(NacosException.class, () -> verifier.saveAndVerify(prepared));
        
        verify(storageService).removeChangeListener(listener.get());
    }
    
    @Test
    void eventualStorageShouldFailAfterBoundedAttempts() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION);
        when(storageService.load(any())).thenThrow(failure());
        AtomicInteger waits = new AtomicInteger();
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 3,
            (mode, hint) -> waits.incrementAndGet());
        
        assertThrows(NacosException.class, () -> verifier.saveAndVerify(prepared));
        
        verify(storageService, times(3)).load(any());
        assertEquals(2, waits.get());
    }
    
    @Test
    void defaultWaiterShouldPreserveInterruptStatus() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION);
        when(storageService.load(any())).thenThrow(failure());
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService);
        Thread.currentThread().interrupt();
        try {
            assertThrows(NacosException.class, () -> verifier.saveAndVerify(prepared));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
    
    @Test
    void defaultWaiterShouldConsumeMatchingVisibilityHintWithoutSleeping()
        throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITH_NOTIFICATION);
        AtomicReference<AiResourceStorageChangeListener> listener = new AtomicReference<>();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return null;
        }).when(storageService).addChangeListener(any());
        doAnswer(invocation -> {
            listener.get().onStorageChanged(new AiResourceStorageChangeEvent(
                prepared.getDescriptor().getProvider(), Constants.Agent.RESOURCE_TYPE_AGENT,
                "visible"));
            return null;
        }).when(storageService).save(prepared);
        when(storageService.load(any())).thenThrow(failure()).thenReturn(content);
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService);
        
        verifier.saveAndVerify(prepared);
        
        verify(storageService, times(2)).load(any());
        verify(storageService).removeChangeListener(listener.get());
    }
    
    @Test
    void defaultWaiterShouldRetryEventualStorageAfterDelay() throws NacosException {
        when(storageService.consistencyMode(any()))
            .thenReturn(AiResourceStorageConsistencyMode.EVENTUAL_WITHOUT_NOTIFICATION);
        when(storageService.load(any())).thenThrow(failure()).thenReturn(content);
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService);
        
        verifier.saveAndVerify(prepared);
        
        verify(storageService, times(2)).load(any());
    }
    
    @Test
    void shouldRejectInvalidConstructionAndNullPayload() {
        assertThrows(IllegalArgumentException.class,
            () -> new A2aMigrationStorageVerifier(storageService, 0, (mode, hint) -> {
            }));
        A2aMigrationStorageVerifier verifier = new A2aMigrationStorageVerifier(storageService, 1,
            (mode, hint) -> {
            });
        assertThrows(IllegalArgumentException.class, () -> verifier.saveAndVerify(null));
    }
    
    @Test
    void productionConstructorShouldDeclareSpringInjection() throws NoSuchMethodException {
        assertTrue(A2aMigrationStorageVerifier.class
            .getConstructor(AgentVersionStorageService.class).isAnnotationPresent(Autowired.class));
    }
    
    private AgentVersionContent content(String protocolVersion) {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion(protocolVersion);
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("name", "research-agent"));
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        return new AgentVersionContent(Collections.singletonList(callInterface));
    }
    
    private NacosException failure() {
        return new NacosException(NacosException.SERVER_ERROR, "not visible");
    }
}
