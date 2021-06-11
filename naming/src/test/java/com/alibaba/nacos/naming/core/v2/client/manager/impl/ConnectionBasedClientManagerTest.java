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

import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConnectionBasedClientManagerTest {
    
    ConnectionBasedClientManager connectionBasedClientManager;
    
    private final String connectionId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    @Mock
    private ConnectionBasedClient client;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    @Mock
    private ClientSyncAttributes clientSyncAttributes;
    
    @Before
    public void setUp() throws Exception {
        connectionBasedClientManager = new ConnectionBasedClientManager();
        when(client.isNative()).thenReturn(true);
        when(connectionMeta.getConnectionId()).thenReturn(connectionId);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getLabel(RemoteConstants.LABEL_MODULE)).thenReturn(RemoteConstants.LABEL_MODULE_NAMING);
        
        assertTrue(connectionBasedClientManager.syncClientConnected(connectionId, clientSyncAttributes));
        assertTrue(connectionBasedClientManager.verifyClient(connectionId));
        connectionBasedClientManager.clientConnected(connection);
        
    }
    
    @Test
    public void testAllClientId() {
        Collection<String> allClientIds = connectionBasedClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(connectionBasedClientManager.verifyClient(connectionId));
        assertTrue(allClientIds.contains(connectionId));
    }
    
    @Test
    public void testContainsConnectionId() {
        assertTrue(connectionBasedClientManager.verifyClient(connectionId));
        assertTrue(connectionBasedClientManager.contains(connectionId));
        String unUsedClientId = "127.0.0.1:8888#true";
        assertFalse(connectionBasedClientManager.verifyClient(unUsedClientId));
        assertFalse(connectionBasedClientManager.contains(unUsedClientId));
    }
    
    @Test
    public void testIsResponsibleClient() {
        assertTrue(connectionBasedClientManager.isResponsibleClient(client));
    }
    
    @After
    public void tearDown() {
        connectionBasedClientManager.clientDisConnected(connection);
    }
}