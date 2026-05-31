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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosRuntimeConnectionEjectorTest {
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    private NacosRuntimeConnectionEjector ejector;
    
    @BeforeEach
    void setUp() {
        ejector = new NacosRuntimeConnectionEjector();
        ejector.setConnectionManager(connectionManager);
        ReflectionTestUtils.setField(connectionManager, "connections",
            new ConcurrentHashMap<String, Connection>());
    }
    
    @Test
    void testGetName() {
        assertEquals("nacos", ejector.getName());
    }
    
    @Test
    void testDoEjectWithEmptyConnections() {
        when(connectionManager.currentSdkClientCount()).thenReturn(0);
        ejector.doEject();
    }
    
    @Test
    void testDoEjectOverLimitPath() {
        when(connectionManager.currentSdkClientCount()).thenReturn(0);
        when(connectionManager.getCurrentConnectionCount()).thenReturn(5);
        ejector.setLoadClient(2);
        ejector.setRedirectAddress("127.0.0.1:8848");
        ejector.doEject();
    }
    
    @Test
    void testEjectOutdatedConnectionWhenConnectionNull() {
        Map<String, Connection> connections = new ConcurrentHashMap<>();
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getConnectionId()).thenReturn("conn-1");
        when(connectionMeta.getLastActiveTime()).thenReturn(0L);
        when(connectionMeta.pushQueueBlockTimesLastOver(300000L)).thenReturn(false);
        connections.put("conn-1", connection);
        ReflectionTestUtils.setField(connectionManager, "connections", connections);
        when(connectionManager.currentSdkClientCount()).thenReturn(1);
        when(connectionManager.getConnection("conn-1")).thenReturn(null);
        
        ejector.doEject();
        
        verify(connectionManager).unregister(eq("conn-1"));
    }
    
    @Test
    void testEjectOutdatedConnectionWhenConnectionAlreadyClosed() throws NacosException {
        Map<String, Connection> connections = new ConcurrentHashMap<>();
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getConnectionId()).thenReturn("conn-1");
        when(connectionMeta.getLastActiveTime()).thenReturn(0L);
        when(connectionMeta.pushQueueBlockTimesLastOver(300000L)).thenReturn(false);
        connections.put("conn-1", connection);
        ReflectionTestUtils.setField(connectionManager, "connections", connections);
        when(connectionManager.currentSdkClientCount()).thenReturn(1);
        when(connectionManager.getConnection("conn-1")).thenReturn(connection);
        doThrow(new ConnectionAlreadyClosedException()).when(connection).asyncRequest(any(), any());
        
        ejector.doEject();
        
        verify(connectionManager).unregister(eq("conn-1"));
    }
    
    @Test
    void testEjectOverLimitWithLoadSingleTrue() throws NacosException {
        when(connectionManager.currentSdkClientCount()).thenReturn(1);
        when(connectionManager.getCurrentConnectionCount()).thenReturn(5);
        when(connectionManager.getConnection(any())).thenReturn(connection);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getLastActiveTime()).thenReturn(System.currentTimeMillis());
        when(connectionMeta.pushQueueBlockTimesLastOver(300000L)).thenReturn(false);
        when(connectionMeta.isSdkSource()).thenReturn(true);
        when(connectionManager.loadSingle(any(), eq("127.0.0.1:8848"))).thenReturn(true);
        
        Map<String, Connection> connections = new ConcurrentHashMap<>();
        connections.put("c1", connection);
        connections.put("c2", connection);
        connections.put("c3", connection);
        ReflectionTestUtils.setField(connectionManager, "connections", connections);
        
        ejector.setLoadClient(2);
        ejector.setRedirectAddress("127.0.0.1:8848");
        ejector.doEject();
        
        verify(connectionManager, times(3)).loadSingle(any(), eq("127.0.0.1:8848"));
    }
}
