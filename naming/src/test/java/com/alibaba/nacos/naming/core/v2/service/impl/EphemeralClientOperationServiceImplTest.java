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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EphemeralClientOperationServiceImplTest extends TestCase {
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    
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
    public void testRegisterPersistentInstance() throws NacosException {
        when(service.isEphemeral()).thenReturn(false);
        // Excepted exception
        ephemeralClientOperationServiceImpl.registerInstance(service, instance, clientId);
    }
    
    @Test
    public void testRegisterInstanceWithInvalidClusterName() throws NacosException {
        expectedException.expect(NacosException.class);
        expectedException.expectMessage(
                "Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: cluster1,cluster2)");
    
        when(instance.getClusterName()).thenReturn("cluster1,cluster2");
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
    public void testBatchRegisterAndDeregisterInstance() throws Exception {
        // test Batch register instance
       
        Instance instance1 = new Instance();
        instance1.setEphemeral(true);
        instance1.setIp("127.0.0.1");
        instance1.setPort(9087);
        instance1.setHealthy(true);
    
        Instance instance2 = new Instance();
        instance2.setEphemeral(true);
        instance2.setIp("127.0.0.2");
        instance2.setPort(9045);
        instance2.setHealthy(true);
    
        List<Instance> instances = new ArrayList<>();
        instances.add(instance1);
        instances.add(instance2);
        ephemeralClientOperationServiceImpl.batchRegisterInstance(service, instances, clientId);
        assertTrue(client.getAllPublishedService().contains(service));
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
