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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link OperatorV2Impl} unit tests.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
class OperatorV2ImplTest {
    
    @InjectMocks
    private OperatorV2Impl operatorV2;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private SwitchManager switchManager;
    
    @Mock
    private ServerStatusManager serverStatusManager;
    
    @Mock
    private ClientManager clientManager;
    
    @AfterEach
    void tearDown() {
        MetricsMonitor.getDomCountMonitor().set(0);
        MetricsMonitor.getIpCountMonitor().set(0);
        MetricsMonitor.getSubscriberCount().set(0);
    }
    
    @Test
    void testSwitches() {
        assertSame(switchDomain, operatorV2.switches());
    }
    
    @Test
    void testUpdateSwitch() throws Exception {
        operatorV2.updateSwitch("pushEnabled", "false", true);
        
        Mockito.verify(switchManager).update("pushEnabled", "false", true);
    }
    
    @Test
    void testMetricsOnlyStatus() {
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        
        MetricsInfoVo metricsInfo = operatorV2.metrics(true);
        
        assertEquals(ServerStatus.UP.name(), metricsInfo.getStatus());
        assertNull(metricsInfo.getClientCount());
    }
    
    @Test
    void testMetricsCountsClients() {
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        Collection<String> clientIds =
            Arrays.asList("connection-client", IpPortBasedClient.getClientId("1.1.1.1:8848", true),
                IpPortBasedClient.getClientId("2.2.2.2:8848", false));
        Mockito.when(clientManager.allClientId()).thenReturn(clientIds);
        Client connectionClient = Mockito.mock(Client.class);
        Client ephemeralClient = Mockito.mock(Client.class);
        Client persistentClient = Mockito.mock(Client.class);
        Mockito.when(clientManager.getClient("connection-client")).thenReturn(connectionClient);
        Mockito.when(clientManager.getClient(IpPortBasedClient.getClientId("1.1.1.1:8848", true)))
            .thenReturn(ephemeralClient);
        Mockito.when(clientManager.getClient(IpPortBasedClient.getClientId("2.2.2.2:8848", false)))
            .thenReturn(persistentClient);
        Mockito.when(clientManager.isResponsibleClient(connectionClient)).thenReturn(true);
        Mockito.when(clientManager.isResponsibleClient(ephemeralClient)).thenReturn(false);
        Mockito.when(clientManager.isResponsibleClient(persistentClient)).thenReturn(true);
        MetricsMonitor.getDomCountMonitor().set(7);
        MetricsMonitor.getIpCountMonitor().set(8);
        MetricsMonitor.getSubscriberCount().set(9);
        
        MetricsInfoVo metricsInfo = operatorV2.metrics(false);
        
        assertEquals(ServerStatus.UP.name(), metricsInfo.getStatus());
        assertEquals(7, metricsInfo.getServiceCount());
        assertEquals(8, metricsInfo.getInstanceCount());
        assertEquals(9, metricsInfo.getSubscribeCount());
        assertEquals(3, metricsInfo.getClientCount());
        assertEquals(1, metricsInfo.getConnectionBasedClientCount());
        assertEquals(1, metricsInfo.getEphemeralIpPortClientCount());
        assertEquals(1, metricsInfo.getPersistentIpPortClientCount());
        assertEquals(2, metricsInfo.getResponsibleClientCount());
    }
    
    @Test
    void testSetLogLevel() {
        operatorV2.setLogLevel("naming-main", "INFO");
    }
}
