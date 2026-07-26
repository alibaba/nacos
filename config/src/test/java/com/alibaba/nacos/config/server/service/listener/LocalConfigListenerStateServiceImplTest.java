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
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.remote.ConfigChangeListenContext;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalConfigListenerStateServiceImplTest {
    
    @Mock
    private LongPollingService longPollingService;
    
    @Mock
    private ConfigChangeListenContext configChangeListenContext;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    private LocalConfigListenerStateServiceImpl service;
    
    @BeforeEach
    void setUp() {
        service = new LocalConfigListenerStateServiceImpl(
            longPollingService, configChangeListenContext, connectionManager);
    }
    
    @Test
    void testGetListenerStateWithEmptyListeners() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfo("d", "g", "ns"))
            .thenReturn(sample);
        String groupKey = GroupKey2.getKey("d", "g", "ns");
        when(configChangeListenContext.getListeners(groupKey))
            .thenReturn(null);
        
        ConfigListenerInfo result = service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_CONFIG, result.getQueryType());
    }
    
    @Test
    void testGetListenerStateWithRpcListeners() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfo("d", "g", "ns"))
            .thenReturn(sample);
        String groupKey = GroupKey2.getKey("d", "g", "ns");
        Set<String> listeners = new HashSet<>();
        listeners.add("conn-1");
        when(configChangeListenContext.getListeners(groupKey))
            .thenReturn(listeners);
        when(connectionManager.getConnection("conn-1")).thenReturn(connection);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getClientIp()).thenReturn("1.2.3.4");
        when(configChangeListenContext.getListenKeyMd5("conn-1", groupKey))
            .thenReturn("abc123");
        
        ConfigListenerInfo result = service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals("abc123", result.getListenersStatus().get("1.2.3.4"));
    }
    
    @Test
    void testGetListenerStateWithNullConnection() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfo("d", "g", "ns"))
            .thenReturn(sample);
        String groupKey = GroupKey2.getKey("d", "g", "ns");
        Set<String> listeners = new HashSet<>();
        listeners.add("conn-1");
        when(configChangeListenContext.getListeners(groupKey))
            .thenReturn(listeners);
        when(connectionManager.getConnection("conn-1")).thenReturn(null);
        
        ConfigListenerInfo result = service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals(0, result.getListenersStatus().size());
    }
    
    @Test
    void testGetListenerStateWithNullMd5() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfo("d", "g", "ns"))
            .thenReturn(sample);
        String groupKey = GroupKey2.getKey("d", "g", "ns");
        Set<String> listeners = new HashSet<>();
        listeners.add("conn-1");
        when(configChangeListenContext.getListeners(groupKey))
            .thenReturn(listeners);
        when(connectionManager.getConnection("conn-1")).thenReturn(connection);
        when(configChangeListenContext.getListenKeyMd5("conn-1", groupKey))
            .thenReturn(null);
        
        ConfigListenerInfo result = service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals(0, result.getListenersStatus().size());
    }
    
    @Test
    void testGetListenerStateByIp() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfoByIp("1.2.3.4"))
            .thenReturn(sample);
        List<Connection> connections = new ArrayList<>();
        connections.add(connection);
        when(connectionManager.getConnectionByIp("1.2.3.4"))
            .thenReturn(connections);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getConnectionId()).thenReturn("conn-1");
        Map<String, String> listenKeys = new HashMap<>();
        listenKeys.put("gk1", "md5-1");
        when(configChangeListenContext.getListenKeys("conn-1"))
            .thenReturn(listenKeys);
        
        ConfigListenerInfo result = service.getListenerStateByIp("1.2.3.4");
        assertNotNull(result);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP, result.getQueryType());
        assertEquals("md5-1", result.getListenersStatus().get("gk1"));
    }
    
    @Test
    void testGetListenerStateByIpWithNullListenKeys() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfoByIp("1.2.3.4"))
            .thenReturn(sample);
        List<Connection> connections = new ArrayList<>();
        connections.add(connection);
        when(connectionManager.getConnectionByIp("1.2.3.4"))
            .thenReturn(connections);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getConnectionId()).thenReturn("conn-1");
        when(configChangeListenContext.getListenKeys("conn-1"))
            .thenReturn(null);
        
        ConfigListenerInfo result = service.getListenerStateByIp("1.2.3.4");
        assertNotNull(result);
        assertEquals(0, result.getListenersStatus().size());
    }
    
    @Test
    void testGetListenerStateByIpWithEmptyConnections() {
        SampleResult sample = new SampleResult();
        sample.setLisentersGroupkeyStatus(new HashMap<>());
        when(longPollingService.getCollectSubscribleInfoByIp("1.2.3.4"))
            .thenReturn(sample);
        when(connectionManager.getConnectionByIp("1.2.3.4"))
            .thenReturn(Collections.emptyList());
        
        ConfigListenerInfo result = service.getListenerStateByIp("1.2.3.4");
        assertNotNull(result);
        assertEquals(0, result.getListenersStatus().size());
    }
}
