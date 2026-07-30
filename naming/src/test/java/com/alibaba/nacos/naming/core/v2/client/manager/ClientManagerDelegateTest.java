/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.client.manager;

import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.EphemeralIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.HttpConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientManagerDelegateTest {
    
    private final String connectionId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    private final String connectionIdForV6 = System.currentTimeMillis() + "_0:0:0:0:0:0:0:1_80";
    
    private final String ephemeralIpPortId = "127.0.0.1:80#true";
    
    private final String persistentIpPortId = "127.0.0.1:80#false";
    
    private final String httpClientId = "HTTP_CLIENT@@client";
    
    @Mock
    private ConnectionBasedClientManager connectionBasedClientManager;
    
    @Mock
    private HttpConnectionBasedClientManager httpConnectionBasedClientManager;
    
    @Mock
    private EphemeralIpPortClientManager ephemeralIpPortClientManager;
    
    @Mock
    private PersistentIpPortClientManager persistentIpPortClientManager;
    
    private ClientManagerDelegate delegate;
    
    @BeforeEach
    void setUp() throws Exception {
        delegate = new ClientManagerDelegate(connectionBasedClientManager,
            httpConnectionBasedClientManager, ephemeralIpPortClientManager,
            persistentIpPortClientManager);
        when(connectionBasedClientManager.contains(connectionId)).thenReturn(true);
        when(httpConnectionBasedClientManager.contains(httpClientId)).thenReturn(true);
        when(ephemeralIpPortClientManager.contains(ephemeralIpPortId)).thenReturn(true);
        when(persistentIpPortClientManager.contains(persistentIpPortId)).thenReturn(true);
        when(connectionBasedClientManager.allClientId())
            .thenReturn(Collections.singletonList(connectionId));
        when(httpConnectionBasedClientManager.allClientId())
            .thenReturn(Collections.singletonList(httpClientId));
        when(ephemeralIpPortClientManager.allClientId())
            .thenReturn(Collections.singletonList(ephemeralIpPortId));
        when(persistentIpPortClientManager.allClientId())
            .thenReturn(Collections.singletonList(persistentIpPortId));
    }
    
    @Test
    void testChooseConnectionClient() {
        delegate.getClient(connectionId);
        verify(connectionBasedClientManager).getClient(connectionId);
        verify(ephemeralIpPortClientManager, never()).getClient(connectionId);
        verify(persistentIpPortClientManager, never()).getClient(connectionId);
    }
    
    @Test
    void testChooseConnectionClientForV6() {
        delegate.getClient(connectionIdForV6);
        verify(connectionBasedClientManager).getClient(connectionIdForV6);
        verify(ephemeralIpPortClientManager, never()).getClient(connectionIdForV6);
        verify(persistentIpPortClientManager, never()).getClient(connectionIdForV6);
    }
    
    @Test
    void testChooseHttpConnectionClient() {
        delegate.getClient(httpClientId);
        verify(httpConnectionBasedClientManager).getClient(httpClientId);
        verify(connectionBasedClientManager, never()).getClient(httpClientId);
        verify(ephemeralIpPortClientManager, never()).getClient(httpClientId);
        verify(persistentIpPortClientManager, never()).getClient(httpClientId);
    }
    
    @Test
    void testChooseEphemeralIpPortClient() {
        DistroClientVerifyInfo verify = new DistroClientVerifyInfo(ephemeralIpPortId, 0);
        delegate.verifyClient(verify);
        verify(connectionBasedClientManager, never()).verifyClient(verify);
        verify(ephemeralIpPortClientManager).verifyClient(verify);
        verify(persistentIpPortClientManager, never()).verifyClient(verify);
    }
    
    @Test
    void testChoosePersistentIpPortClient() {
        DistroClientVerifyInfo verify = new DistroClientVerifyInfo(persistentIpPortId, 0);
        delegate.verifyClient(verify);
        verify(connectionBasedClientManager, never()).verifyClient(verify);
        verify(ephemeralIpPortClientManager, never()).verifyClient(verify);
        verify(persistentIpPortClientManager).verifyClient(verify);
    }
    
    @Test
    void testContainsConnectionId() {
        assertTrue(delegate.contains(connectionId));
    }
    
    @Test
    void testContainsConnectionIdFailed() {
        assertFalse(delegate.contains(connectionIdForV6));
    }
    
    @Test
    void testContainsHttpClientId() {
        assertTrue(delegate.contains(httpClientId));
    }
    
    @Test
    void testContainsEphemeralIpPortId() {
        assertTrue(delegate.contains(ephemeralIpPortId));
    }
    
    @Test
    void testContainsPersistentIpPortId() {
        assertTrue(delegate.contains(persistentIpPortId));
    }
    
    @Test
    void testAllClientId() {
        Collection<String> actual = delegate.allClientId();
        assertTrue(actual.contains(connectionId));
        assertTrue(actual.contains(httpClientId));
        assertTrue(actual.contains(ephemeralIpPortId));
        assertTrue(actual.contains(persistentIpPortId));
    }
    
    @Test
    void testDelegateLifecycleForConnectionClient() {
        ClientAttributes attributes = new ClientAttributes();
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(connectionId);
        when(connectionBasedClientManager.clientConnected(connectionId, attributes))
            .thenReturn(true);
        when(connectionBasedClientManager.clientConnected(client)).thenReturn(true);
        when(connectionBasedClientManager.syncClientConnected(connectionId, attributes))
            .thenReturn(true);
        when(connectionBasedClientManager.clientDisconnected(connectionId)).thenReturn(true);
        when(connectionBasedClientManager.isResponsibleClient(client)).thenReturn(true);
        
        assertTrue(delegate.clientConnected(connectionId, attributes));
        assertTrue(delegate.clientConnected(client));
        assertTrue(delegate.syncClientConnected(connectionId, attributes));
        assertTrue(delegate.clientDisconnected(connectionId));
        assertTrue(delegate.isResponsibleClient(client));
        
        verify(connectionBasedClientManager).clientConnected(connectionId, attributes);
        verify(connectionBasedClientManager).clientConnected(client);
        verify(connectionBasedClientManager).syncClientConnected(connectionId, attributes);
        verify(connectionBasedClientManager).clientDisconnected(connectionId);
        verify(connectionBasedClientManager).isResponsibleClient(client);
        verify(ephemeralIpPortClientManager, never()).clientConnected(connectionId, attributes);
        verify(persistentIpPortClientManager, never()).clientConnected(connectionId, attributes);
    }
    
    @Test
    void testDelegateLifecycleForHttpClient() {
        ClientAttributes attributes = new ClientAttributes();
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(httpClientId);
        when(httpConnectionBasedClientManager.clientConnected(httpClientId, attributes))
            .thenReturn(true);
        when(httpConnectionBasedClientManager.clientConnected(client)).thenReturn(true);
        when(httpConnectionBasedClientManager.syncClientConnected(httpClientId, attributes))
            .thenReturn(true);
        when(httpConnectionBasedClientManager.clientDisconnected(httpClientId)).thenReturn(true);
        when(httpConnectionBasedClientManager.isResponsibleClient(client)).thenReturn(true);
        
        assertTrue(delegate.clientConnected(httpClientId, attributes));
        assertTrue(delegate.clientConnected(client));
        assertTrue(delegate.syncClientConnected(httpClientId, attributes));
        assertTrue(delegate.clientDisconnected(httpClientId));
        assertTrue(delegate.isResponsibleClient(client));
        
        verify(httpConnectionBasedClientManager).clientConnected(httpClientId, attributes);
        verify(httpConnectionBasedClientManager).clientConnected(client);
        verify(httpConnectionBasedClientManager).syncClientConnected(httpClientId, attributes);
        verify(httpConnectionBasedClientManager).clientDisconnected(httpClientId);
        verify(httpConnectionBasedClientManager).isResponsibleClient(client);
        verify(connectionBasedClientManager, never()).clientConnected(httpClientId, attributes);
        verify(ephemeralIpPortClientManager, never()).clientConnected(httpClientId, attributes);
        verify(persistentIpPortClientManager, never()).clientConnected(httpClientId, attributes);
    }
}
