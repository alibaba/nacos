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

import com.alibaba.nacos.naming.core.v2.client.manager.impl.ConnectionBasedClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.EphemeralIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientManagerDelegateTest {
    
    private final String connectionId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    private final String connectionIdForV6 = System.currentTimeMillis() + "_0:0:0:0:0:0:0:1_80";
    
    private final String ephemeralIpPortId = "127.0.0.1:80#true";
    
    private final String persistentIpPortId = "127.0.0.1:80#false";
    
    @Mock
    private ConnectionBasedClientManager connectionBasedClientManager;
    
    @Mock
    private EphemeralIpPortClientManager ephemeralIpPortClientManager;
    
    @Mock
    private PersistentIpPortClientManager persistentIpPortClientManager;
    
    private ClientManagerDelegate delegate;
    
    @Before
    public void setUp() throws Exception {
        delegate = new ClientManagerDelegate(connectionBasedClientManager, ephemeralIpPortClientManager,
                persistentIpPortClientManager);
        when(connectionBasedClientManager.contains(connectionId)).thenReturn(true);
        when(ephemeralIpPortClientManager.contains(ephemeralIpPortId)).thenReturn(true);
        when(persistentIpPortClientManager.contains(persistentIpPortId)).thenReturn(true);
        when(connectionBasedClientManager.allClientId()).thenReturn(Collections.singletonList(connectionId));
        when(ephemeralIpPortClientManager.allClientId()).thenReturn(Collections.singletonList(ephemeralIpPortId));
        when(persistentIpPortClientManager.allClientId()).thenReturn(Collections.singletonList(persistentIpPortId));
    }
    
    @Test
    public void testChooseConnectionClient() {
        delegate.getClient(connectionId);
        verify(connectionBasedClientManager).getClient(connectionId);
        verify(ephemeralIpPortClientManager, never()).getClient(connectionId);
        verify(persistentIpPortClientManager, never()).getClient(connectionId);
    }
    
    @Test
    public void testChooseConnectionClientForV6() {
        delegate.getClient(connectionIdForV6);
        verify(connectionBasedClientManager).getClient(connectionIdForV6);
        verify(ephemeralIpPortClientManager, never()).getClient(connectionIdForV6);
        verify(persistentIpPortClientManager, never()).getClient(connectionIdForV6);
    }
    
    @Test
    public void testChooseEphemeralIpPortClient() {
        delegate.verifyClient(ephemeralIpPortId);
        verify(connectionBasedClientManager, never()).verifyClient(ephemeralIpPortId);
        verify(ephemeralIpPortClientManager).verifyClient(ephemeralIpPortId);
        verify(persistentIpPortClientManager, never()).verifyClient(ephemeralIpPortId);
    }
    
    @Test
    public void testChoosePersistentIpPortClient() {
        delegate.verifyClient(persistentIpPortId);
        verify(connectionBasedClientManager, never()).verifyClient(persistentIpPortId);
        verify(ephemeralIpPortClientManager, never()).verifyClient(persistentIpPortId);
        verify(persistentIpPortClientManager).verifyClient(persistentIpPortId);
    }
    
    @Test
    public void testContainsConnectionId() {
        assertTrue(delegate.contains(connectionId));
    }
    
    @Test
    public void testContainsConnectionIdFailed() {
        assertFalse(delegate.contains(connectionIdForV6));
    }
    
    @Test
    public void testContainsEphemeralIpPortId() {
        assertTrue(delegate.contains(ephemeralIpPortId));
    }
    
    @Test
    public void testContainsPersistentIpPortId() {
        assertTrue(delegate.contains(persistentIpPortId));
    }
    
    @Test
    public void testAllClientId() {
        Collection<String> actual = delegate.allClientId();
        assertTrue(actual.contains(connectionId));
        assertTrue(actual.contains(ephemeralIpPortId));
        assertTrue(actual.contains(persistentIpPortId));
    }
}
