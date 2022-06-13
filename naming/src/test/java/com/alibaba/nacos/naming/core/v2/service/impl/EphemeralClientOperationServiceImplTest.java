/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.service.impl;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EphemeralClientOperationServiceImplTest extends TestCase {
    
    private EphemeralClientOperationServiceImpl ephemeralClientOperationServiceImpl;
    
    @Mock
    private ClientManagerDelegate clientManager;
    
    @Mock
    private Service service;
    
    @Mock
    private Instance instance;
    
    @Mock
    private Subscriber subscriber;
    
    private Client client;
    
    private final String clientId = "1.1.1.1:80#true";
    
    private final String ip = "1.1.1.1";
    
    private final int port = 80;
    
    @Before
    public void setUp() throws Exception {
        when(instance.getIp()).thenReturn(ip);
        when(instance.getPort()).thenReturn(port);
        when(service.getNamespace()).thenReturn("public");
        when(service.getGroupedServiceName()).thenReturn("G@@S");
        when(service.isEphemeral()).thenReturn(true);
        ephemeralClientOperationServiceImpl = new EphemeralClientOperationServiceImpl(clientManager);
        client = new IpPortBasedClient(clientId, true);
        when(clientManager.getClient(clientId)).thenReturn(client);
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testRegisterPersistentInstance() {
        when(service.isEphemeral()).thenReturn(false);
        // Excepted exception
        ephemeralClientOperationServiceImpl.registerInstance(service, instance, clientId);
    }
    
    @Test
    public void testRegisterAndDeregisterInstance() throws Exception {
        // Test register instance
        ephemeralClientOperationServiceImpl.registerInstance(service, instance, clientId);
        assertTrue(client.getAllPublishedService().contains(service));
        assertEquals(client.getInstancePublishInfo(service).getIp(), ip);
        assertEquals(client.getInstancePublishInfo(service).getPort(), port);
        // Test deregister instance
        ephemeralClientOperationServiceImpl.deregisterInstance(service, instance, clientId);
        Collection<Service> allPublishService = client.getAllPublishedService();
        assertFalse(allPublishService.contains(service));
    }
    
    @Test
    public void testSubscribeAndUnsubscribeService() throws Exception {
        // Test subscribe instance
        ephemeralClientOperationServiceImpl.subscribeService(service, subscriber, clientId);
        assertTrue(client.getAllSubscribeService().contains(service));
        // Test unsubscribe instance
        ephemeralClientOperationServiceImpl.unsubscribeService(service, subscriber, clientId);
        assertFalse(client.getAllSubscribeService().contains(service));
    }
}
