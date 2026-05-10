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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaftPluginStateSynchronizerTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    private RaftPluginStateSynchronizer synchronizer;
    
    @BeforeEach
    void setUp() {
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        synchronizer = new RaftPluginStateSynchronizer(protocolManager);
    }
    
    @Test
    void syncStateChangeSuccess() throws Exception {
        Response success = Response.newBuilder().setSuccess(true).build();
        when(cpProtocol.write(any(WriteRequest.class))).thenReturn(success);
        
        synchronizer.syncStateChange("auth:nacos", true);
        
        verify(cpProtocol).write(any(WriteRequest.class));
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
    void syncConfigChangeException() throws Exception {
        when(cpProtocol.write(any(WriteRequest.class))).thenThrow(new RuntimeException("io error"));
        
        assertThrows(NacosApiException.class,
            () -> synchronizer.syncConfigChange("auth:nacos", Collections.emptyMap()));
    }
}
