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

import com.alibaba.nacos.naming.core.v2.client.ClientSyncAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PersistentIpPortClientManagerTest {
    
    PersistentIpPortClientManager persistentIpPortClientManager;
    
    private final String clientId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    private final String snapshotClientId = System.currentTimeMillis() + "_127.0.0.1_8080";
    
    @Mock
    private ClientSyncAttributes clientSyncAttributes;
    
    @Mock
    private IpPortBasedClient client;
    
    @Mock
    private IpPortBasedClient snapshotClient;
    
    @Before
    public void setUp() throws Exception {
        persistentIpPortClientManager = new PersistentIpPortClientManager();
        when(client.getClientId()).thenReturn(clientId);
        persistentIpPortClientManager.clientConnected(client);
    }
    
    @Test
    public void testAllClientId() {
        Collection<String> allClientIds = persistentIpPortClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(allClientIds.contains(clientId));
    }
    
    @Test
    public void testContains() {
        assertTrue(persistentIpPortClientManager.contains(clientId));
        String unUsedClientId = "127.0.0.1:8888#true";
        assertFalse(persistentIpPortClientManager.contains(unUsedClientId));
    }
    
    @Test
    public void testIsResponsibleClient() {
        assertTrue(persistentIpPortClientManager.isResponsibleClient(client));
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void makeSureSyncClientConnected() {
        persistentIpPortClientManager.syncClientConnected(clientId, clientSyncAttributes);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void makeSureNoVerify() {
        persistentIpPortClientManager.verifyClient(clientId);
    }
    
    @Test
    public void testLoadFromSnapshot() {
        ConcurrentMap<String, IpPortBasedClient> snapshotClients = new ConcurrentHashMap<>();
        snapshotClients.put(snapshotClientId, snapshotClient);
        persistentIpPortClientManager.loadFromSnapshot(snapshotClients);
        Collection<String> allClientIds = persistentIpPortClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(allClientIds.contains(snapshotClientId));
    }
    
    @After
    public void tearDown() {
        persistentIpPortClientManager.clientDisconnected(clientId);
    }
}