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

package com.alibaba.nacos.naming.core.v2.client.manager.impl;

import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.factory.ClientFactory;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentIpPortClientManagerTest {
    
    private final String clientId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    private final String snapshotClientId = System.currentTimeMillis() + "_127.0.0.1_8080";
    
    PersistentIpPortClientManager persistentIpPortClientManager;
    
    @Mock
    private ClientAttributes clientAttributes;
    
    @Mock
    private IpPortBasedClient client;
    
    @Mock
    private IpPortBasedClient snapshotClient;
    
    @BeforeEach
    void setUp() throws Exception {
        persistentIpPortClientManager = new PersistentIpPortClientManager();
        when(client.getClientId()).thenReturn(clientId);
        persistentIpPortClientManager.clientConnected(client);
    }
    
    @Test
    void testAllClientId() {
        Collection<String> allClientIds = persistentIpPortClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(allClientIds.contains(clientId));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testClientConnectedByClientId() {
        String factoryClientId = "127.0.0.1:8848#false";
        ClientFactory<IpPortBasedClient> clientFactory = mock(ClientFactory.class);
        IpPortBasedClient factoryClient = mock(IpPortBasedClient.class);
        when(clientFactory.newClient(factoryClientId, clientAttributes)).thenReturn(factoryClient);
        when(factoryClient.getClientId()).thenReturn(factoryClientId);
        ReflectionTestUtils.setField(persistentIpPortClientManager, "clientFactory", clientFactory);
        
        assertTrue(
            persistentIpPortClientManager.clientConnected(factoryClientId, clientAttributes));
        
        assertSame(factoryClient, persistentIpPortClientManager.getClient(factoryClientId));
    }
    
    @Test
    void testContains() {
        assertTrue(persistentIpPortClientManager.contains(clientId));
        String unUsedClientId = "127.0.0.1:8888#true";
        assertFalse(persistentIpPortClientManager.contains(unUsedClientId));
    }
    
    @Test
    void testGetClient() {
        assertSame(client, persistentIpPortClientManager.getClient(clientId));
    }
    
    @Test
    void testShowClients() {
        assertTrue(persistentIpPortClientManager.showClients().containsKey(clientId));
        assertThrows(UnsupportedOperationException.class,
            () -> persistentIpPortClientManager.showClients().clear());
    }
    
    @Test
    void testIsResponsibleClient() {
        assertTrue(persistentIpPortClientManager.isResponsibleClient(client));
    }
    
    @Test
    void makeSureSyncClientConnected() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentIpPortClientManager.syncClientConnected(clientId, clientAttributes);
        });
    }
    
    @Test
    void makeSureNoVerify() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentIpPortClientManager.verifyClient(new DistroClientVerifyInfo(clientId, 0));
        });
    }
    
    @Test
    void testLoadFromSnapshot() {
        ConcurrentMap<String, IpPortBasedClient> snapshotClients = new ConcurrentHashMap<>();
        snapshotClients.put(snapshotClientId, snapshotClient);
        persistentIpPortClientManager.loadFromSnapshot(snapshotClients);
        Collection<String> allClientIds = persistentIpPortClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(allClientIds.contains(snapshotClientId));
    }
    
    @Test
    void testAddSyncClient() {
        when(snapshotClient.getClientId()).thenReturn(snapshotClientId);
        
        persistentIpPortClientManager.addSyncClient(snapshotClient);
        
        assertSame(snapshotClient, persistentIpPortClientManager.getClient(snapshotClientId));
    }
    
    @Test
    void testRemoveAndRelease() {
        persistentIpPortClientManager.removeAndRelease(clientId);
        
        assertFalse(persistentIpPortClientManager.contains(clientId));
        verify(client).release();
    }
    
    @Test
    void testRemoveAndReleaseWithoutClient() {
        persistentIpPortClientManager.removeAndRelease("missing");
        
        assertTrue(persistentIpPortClientManager.contains(clientId));
    }
    
    @AfterEach
    void tearDown() {
        persistentIpPortClientManager.clientDisconnected(clientId);
    }
}
