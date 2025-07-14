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

package com.alibaba.nacos.ai.remote.manager;

import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiConnectionBasedClientManagerTest {
    
    private static final String CONNECTION_ID = "1111111111_127.0.0.1_12345";
    
    @Mock
    ConnectionBasedClientManager delegate;
    
    @Mock
    Connection connection;
    
    ConnectionMeta connectionMeta;
    
    AiConnectionBasedClientManager connectionBasedClientManager;
    
    @BeforeEach
    void setUp() {
        connectionBasedClientManager = new AiConnectionBasedClientManager(delegate);
        connectionMeta = new ConnectionMeta(CONNECTION_ID, "127.0.0.1", "127.0.0.1", 12345, 12345,
                ConnectionType.GRPC.getType(), "3.0.0", null, new HashMap<>());
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void clientConnectedNotAiConnection() {
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        connectionBasedClientManager.clientConnected(connection);
        verify(delegate, never()).clientConnected(anyString(), any(ClientAttributes.class));
    }
    
    @Test
    void clientConnected() {
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        connectionMeta.getLabels().put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_AI);
        connectionBasedClientManager.clientConnected(connection);
        verify(delegate).clientConnected(eq(CONNECTION_ID), any(ClientAttributes.class));
    }
    
    @Test
    void clientConnectedByClient() {
        ConnectionBasedClient client = new ConnectionBasedClient(CONNECTION_ID, true, 0L);
        connectionBasedClientManager.clientConnected(client);
        verify(delegate).clientConnected(client);
    }
    
    @Test
    void syncClientConnected() {
        ClientAttributes clientAttributes = new ClientAttributes();
        connectionBasedClientManager.syncClientConnected(CONNECTION_ID, clientAttributes);
        verify(delegate).syncClientConnected(CONNECTION_ID, clientAttributes);
    }
    
    @Test
    void clientDisConnectedNotAiConnection() {
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        connectionBasedClientManager.clientDisConnected(connection);
        verify(delegate, never()).clientDisconnected(anyString());
    }
    
    @Test
    void clientDisconnected() {
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        connectionMeta.getLabels().put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_AI);
        connectionBasedClientManager.clientDisConnected(connection);
        verify(delegate).clientDisconnected(CONNECTION_ID);
    }
    
    @Test
    void getClient() {
        ConnectionBasedClient client = new ConnectionBasedClient(CONNECTION_ID, true, 0L);
        when(delegate.getClient(CONNECTION_ID)).thenReturn(client);
        assertEquals(client, connectionBasedClientManager.getClient(CONNECTION_ID));
    }
    
    @Test
    void contains() {
        assertFalse(connectionBasedClientManager.contains(CONNECTION_ID));
        when(delegate.contains(CONNECTION_ID)).thenReturn(true);
        assertTrue(connectionBasedClientManager.contains(CONNECTION_ID));
    }
    
    @Test
    void allClientId() {
        when(delegate.allClientId()).thenReturn(Collections.singleton(CONNECTION_ID));
        assertEquals(1, connectionBasedClientManager.allClientId().size());
        assertEquals(CONNECTION_ID, connectionBasedClientManager.allClientId().iterator().next());
    }
    
    @Test
    void isResponsibleClient() {
        ConnectionBasedClient client = new ConnectionBasedClient(CONNECTION_ID, true, 0L);
        assertFalse(connectionBasedClientManager.isResponsibleClient(client));
        when(delegate.isResponsibleClient(client)).thenReturn(true);
        assertTrue(connectionBasedClientManager.isResponsibleClient(client));
    }
    
    @Test
    void verifyClient() {
        DistroClientVerifyInfo verifyData = new DistroClientVerifyInfo(CONNECTION_ID, 0L);
        assertFalse(connectionBasedClientManager.verifyClient(verifyData));
        when(delegate.verifyClient(verifyData)).thenReturn(true);
        assertTrue(connectionBasedClientManager.verifyClient(verifyData));
    }
}