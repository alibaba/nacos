/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.plugin.model.PluginStateOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaftPluginStateSynchronizerTest {
    
    @Mock
    private CPProtocol cpProtocol;
    
    @Mock
    private PluginStateConsensusService consensusService;
    
    private RaftPluginStateSynchronizer synchronizer;
    
    @BeforeEach
    void setUp() {
        lenient().when(consensusService.getProtocol()).thenReturn(cpProtocol);
        synchronizer = new RaftPluginStateSynchronizer(consensusService);
    }
    
    @Test
    void syncStateChangeSuccess() throws Exception {
        Response success = Response.newBuilder().setSuccess(true).build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(success);
        
        synchronizer.syncStateChange("auth:nacos", true);
        
        verify(cpProtocol).write(any(WriteRequest.class));
    }
    
    @Test
    void lifecycleDelegatesToConsensusService() {
        when(consensusService.isAvailable()).thenReturn(true, false);
        
        synchronizer.initialize();
        
        verify(consensusService).initialize();
        assertTrue(synchronizer.isAvailable());
        assertFalse(synchronizer.isAvailable());
    }
    
    @Test
    void providerCreatesRaftSynchronizerLazily() {
        java.util.concurrent.atomic.AtomicBoolean requested =
            new java.util.concurrent.atomic.AtomicBoolean();
        RaftPluginStateSynchronizerProvider provider =
            new RaftPluginStateSynchronizerProvider(() -> {
                requested.set(true);
                return consensusService;
            });
        
        assertEquals("raft", provider.getName());
        assertFalse(requested.get());
        assertTrue(provider.createSynchronizer(
            org.mockito.Mockito.mock(
                PluginStateSynchronizationContext.class)) instanceof RaftPluginStateSynchronizer);
        assertTrue(requested.get());
    }
    
    @Test
    void providerRejectsMissingConsensusService() {
        RaftPluginStateSynchronizerProvider provider =
            new RaftPluginStateSynchronizerProvider(() -> null);
        
        assertThrows(IllegalStateException.class, () -> provider.createSynchronizer(
            org.mockito.Mockito.mock(PluginStateSynchronizationContext.class)));
        verify(consensusService, never()).initialize();
    }
    
    @Test
    void syncStateChangeFailure() throws Exception {
        Response failure = Response.newBuilder().setSuccess(false).setErrMsg("raft error").build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(failure);
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncStateChange("auth:nacos", false));
    }
    
    @Test
    void syncStateChangeException() throws Exception {
        when(cpProtocol.write(any(WriteRequest.class)))
            .thenThrow(new RuntimeException("network error"));
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncStateChange("trace:test", true));
    }
    
    @Test
    void syncConfigChangeSuccess() throws Exception {
        Response success = Response.newBuilder().setSuccess(true).build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(success);
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        
        synchronizer.syncConfigChange("trace:otel", config);
        
        verify(cpProtocol).write(any(WriteRequest.class));
    }
    
    @Test
    void syncConfigChangeFailure() throws Exception {
        Response failure =
            Response.newBuilder().setSuccess(false).setErrMsg("write failed").build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(failure);
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("trace:otel", Collections.singletonMap("k", "v")));
    }
    
    @Test
    void syncConfigChangePreservesInvalidParameterError() throws Exception {
        Response failure = Response.newBuilder().setSuccess(false)
            .setErrMsg(PluginStateOperation.INVALID_PARAM_ERROR_PREFIX + "invalid config")
            .build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(failure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("trace:otel", Collections.emptyMap()));
        
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    void syncConfigChangeReportsAcceptedConfigApplyFailure() throws Exception {
        Response failure = Response.newBuilder().setSuccess(false)
            .setErrMsg(PluginStateOperation.CONFIG_APPLY_ERROR_PREFIX
                + "config updated but apply failed")
            .build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(failure);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("trace:otel", Collections.emptyMap()));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        assertEquals("config updated but apply failed", exception.getErrMsg());
    }
    
    @Test
    void syncConfigChangeException() throws Exception {
        when(cpProtocol.write(any(WriteRequest.class))).thenThrow(new RuntimeException("io error"));
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("auth:nacos", Collections.emptyMap()));
    }
    
    @Test
    void syncConfigChangeFailsWhenConsensusGroupIsUnavailable() throws Exception {
        when(consensusService.getProtocol()).thenThrow(
            new IllegalStateException("group unavailable"));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("auth:nacos", Collections.emptyMap()));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        org.mockito.Mockito.verify(cpProtocol, org.mockito.Mockito.never())
            .write(any(WriteRequest.class));
    }
}
