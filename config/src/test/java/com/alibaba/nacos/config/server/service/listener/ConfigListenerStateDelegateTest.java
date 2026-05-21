/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.listener;

import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigListenerStateDelegateTest {
    
    @Mock
    private LocalConfigListenerStateServiceImpl localService;
    
    @Mock
    private RemoteConfigListenerStateServiceImpl remoteService;
    
    private ConfigListenerStateDelegate delegate;
    
    @BeforeEach
    void setUp() {
        delegate = new ConfigListenerStateDelegate(localService, remoteService);
    }
    
    @Test
    void testGetListenerStateWithoutAggregation() {
        ConfigListenerInfo localInfo = new ConfigListenerInfo();
        localInfo.setListenersStatus(new HashMap<>());
        when(localService.getListenerState("d", "g", "ns"))
            .thenReturn(localInfo);
        ConfigListenerInfo result =
            delegate.getListenerState("d", "g", "ns", false);
        assertNotNull(result);
        verifyNoInteractions(remoteService);
    }
    
    @Test
    void testGetListenerStateWithAggregation() {
        ConfigListenerInfo localInfo = new ConfigListenerInfo();
        localInfo.setListenersStatus(new HashMap<>());
        ConfigListenerInfo remoteInfo = new ConfigListenerInfo();
        remoteInfo.setListenersStatus(new HashMap<>());
        when(localService.getListenerState("d", "g", "ns"))
            .thenReturn(localInfo);
        when(remoteService.getListenerState("d", "g", "ns"))
            .thenReturn(remoteInfo);
        ConfigListenerInfo result =
            delegate.getListenerState("d", "g", "ns", true);
        assertNotNull(result);
        verify(remoteService).getListenerState("d", "g", "ns");
    }
    
    @Test
    void testGetListenerStateByIpWithoutAggregation() {
        ConfigListenerInfo localInfo = new ConfigListenerInfo();
        localInfo.setListenersStatus(new HashMap<>());
        when(localService.getListenerStateByIp("1.2.3.4"))
            .thenReturn(localInfo);
        ConfigListenerInfo result =
            delegate.getListenerStateByIp("1.2.3.4", false);
        assertNotNull(result);
        verifyNoInteractions(remoteService);
    }
    
    @Test
    void testGetListenerStateByIpWithAggregation() {
        ConfigListenerInfo localInfo = new ConfigListenerInfo();
        localInfo.setListenersStatus(new HashMap<>());
        ConfigListenerInfo remoteInfo = new ConfigListenerInfo();
        remoteInfo.setListenersStatus(new HashMap<>());
        when(localService.getListenerStateByIp("1.2.3.4"))
            .thenReturn(localInfo);
        when(remoteService.getListenerStateByIp("1.2.3.4"))
            .thenReturn(remoteInfo);
        ConfigListenerInfo result =
            delegate.getListenerStateByIp("1.2.3.4", true);
        assertNotNull(result);
        verify(remoteService).getListenerStateByIp("1.2.3.4");
    }
}
