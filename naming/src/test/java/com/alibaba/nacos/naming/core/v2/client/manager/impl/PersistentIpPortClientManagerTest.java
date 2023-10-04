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
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class PersistentIpPortClientManagerTest {
    
    PersistentIpPortClientManager persistentIpPortClientManager;
    
    private final String clientId = System.currentTimeMillis() + "_127.0.0.1_80#false";
    
    private final String snapshotClientId = System.currentTimeMillis() + "_127.0.0.1_8080#false";
    
    @Mock
    private ClientAttributes clientAttributes;
    
    @Mock
    private IpPortBasedClient client;
    
    @Mock
    private IpPortBasedClient snapshotClient;
    
    @Mock
    ConfigurableApplicationContext context;
    
    @Before
    public void setUp() throws Exception {
        ApplicationUtils.injectContext(context);
        EnvUtil.setEnvironment(new MockEnvironment());
        persistentIpPortClientManager = new PersistentIpPortClientManager();
        persistentIpPortClientManager.clientConnected(clientId, clientAttributes);
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
        persistentIpPortClientManager.syncClientConnected(clientId, clientAttributes);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void makeSureNoVerify() {
        persistentIpPortClientManager.verifyClient(new DistroClientVerifyInfo(clientId, 0));
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
