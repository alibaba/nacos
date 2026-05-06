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
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ConnectionBasedClientManagerTest {
    
    private final String connectionId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    ConnectionBasedClientManager connectionBasedClientManager;
    
    @Mock
    private ConnectionBasedClient client;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    @Mock
    private ClientAttributes clientAttributes;
    
    @BeforeAll
    static void setUpBeforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @BeforeEach
    void setUp() throws Exception {
        connectionBasedClientManager = new ConnectionBasedClientManager();
        when(client.isNative()).thenReturn(true);
        when(connectionMeta.getConnectionId()).thenReturn(connectionId);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getLabel(RemoteConstants.LABEL_MODULE)).thenReturn(RemoteConstants.LABEL_MODULE_NAMING);
        
        when(clientAttributes.getClientAttribute(ClientConstants.REVISION, 0)).thenReturn(0);
        assertTrue(connectionBasedClientManager.syncClientConnected(connectionId, clientAttributes));
        assertTrue(connectionBasedClientManager.verifyClient(new DistroClientVerifyInfo(connectionId, 0)));
        connectionBasedClientManager.clientConnected(connection);
        
    }
    
    @Test
    void testAllClientId() {
        Collection<String> allClientIds = connectionBasedClientManager.allClientId();
        assertEquals(1, allClientIds.size());
        assertTrue(connectionBasedClientManager.verifyClient(new DistroClientVerifyInfo(connectionId, 0)));
        assertTrue(allClientIds.contains(connectionId));
    }
    
    @Test
    void testContainsConnectionId() {
        assertTrue(connectionBasedClientManager.verifyClient(new DistroClientVerifyInfo(connectionId, 0)));
        assertTrue(connectionBasedClientManager.contains(connectionId));
        String unUsedClientId = "127.0.0.1:8888#true";
        assertFalse(connectionBasedClientManager.verifyClient(new DistroClientVerifyInfo(unUsedClientId, 0)));
        assertFalse(connectionBasedClientManager.contains(unUsedClientId));
    }
    
    @Test
    void testIsResponsibleClient() {
        assertTrue(connectionBasedClientManager.isResponsibleClient(client));
    }
    
    @AfterEach
    void tearDown() {
        connectionBasedClientManager.clientDisConnected(connection);
    }
}
