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
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EphemeralIpPortClientManagerTest {
    
    private final String ephemeralIpPortId = "127.0.0.1:80#true";
    
    private final String syncedClientId = "127.0.0.1:8080#true";
    
    @Mock
    private IpPortBasedClient client;
    
    @Mock
    private DistroMapper distroMapper;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private ClientAttributes attributes;
    
    EphemeralIpPortClientManager ephemeralIpPortClientManager;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Before
    public void setUp() throws Exception {
        ephemeralIpPortClientManager = new EphemeralIpPortClientManager(distroMapper, switchDomain);
        when(client.getClientId()).thenReturn(ephemeralIpPortId);
        when(client.getRevision()).thenReturn(1320L);
        ephemeralIpPortClientManager.clientConnected(client);
        when(attributes.getClientAttribute(ClientConstants.REVISION, 0)).thenReturn(5120);
        ephemeralIpPortClientManager.syncClientConnected(syncedClientId, attributes);
    }
    
    @Test
    public void testGetClient() {
        Client fetchedClient = ephemeralIpPortClientManager.getClient(ephemeralIpPortId);
        assertEquals(fetchedClient, client);
    }
    
    @Test
    public void testAllClientId() {
        Collection<String> allClientIds = ephemeralIpPortClientManager.allClientId();
        assertEquals(2, allClientIds.size());
        assertTrue(allClientIds.contains(ephemeralIpPortId));
        assertTrue(allClientIds.contains(syncedClientId));
    }
    
    @Test
    public void testContainsEphemeralIpPortId() {
        assertTrue(ephemeralIpPortClientManager.contains(ephemeralIpPortId));
        assertTrue(ephemeralIpPortClientManager.contains(syncedClientId));
        String unUsedClientId = "127.0.0.1:8888#true";
        assertFalse(ephemeralIpPortClientManager.contains(unUsedClientId));
    }
    
    @Test
    public void testVerifyClient0() {
        assertTrue(ephemeralIpPortClientManager.verifyClient(new DistroClientVerifyInfo(ephemeralIpPortId, 0)));
        assertTrue(ephemeralIpPortClientManager.verifyClient(new DistroClientVerifyInfo(syncedClientId, 0)));
    }
    
    @Test
    public void testVerifyClient() {
        assertFalse(ephemeralIpPortClientManager.verifyClient(new DistroClientVerifyInfo(ephemeralIpPortId, 1)));
        assertTrue(ephemeralIpPortClientManager.verifyClient(new DistroClientVerifyInfo(ephemeralIpPortId, 1320)));
        assertFalse(ephemeralIpPortClientManager.verifyClient(new DistroClientVerifyInfo(syncedClientId, 1)));
        assertTrue(ephemeralIpPortClientManager.verifyClient(new DistroClientVerifyInfo(syncedClientId, 5120)));
    }
}
