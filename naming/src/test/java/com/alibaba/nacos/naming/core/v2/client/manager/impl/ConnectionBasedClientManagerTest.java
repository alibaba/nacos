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

import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
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
    
    @Mock
    private ConnectionBasedClient client;
    
    private final String connectionId = "connection1";
    
    @Before
    public void setUp() throws Exception {
        connectionBasedClientManager = new ConnectionBasedClientManager();
        when(client.getClientId()).thenReturn(connectionId);
        connectionBasedClientManager.clientConnected(client);
    }
    
    @Test
    public void testGetClient() {
        Client fetchedClient = connectionBasedClientManager.getClient(connectionId);
        assertEquals(fetchedClient, client);
    }
    
    @Test
    public void testAllClientId() {
        Collection<String> allClientIds = connectionBasedClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(allClientIds.contains(connectionId));
    }
    
    @Test
    public void testContainsConnectionId() {
        assertTrue(connectionBasedClientManager.contains(connectionId));
        String unUsedClientId = "127.0.0.1:8888#true";
        assertFalse(connectionBasedClientManager.contains(unUsedClientId));
    }
}