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
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class EphemeralClientOperationServiceImplTest {
    
    private final String ipPortBasedClientId = "1.1.1.1:80#true";
    
    private final String connectionBasedClientId = "test";
    
    private final String ip = "1.1.1.1";
    
    private final int port = 80;
    
    private EphemeralClientOperationServiceImpl ephemeralClientOperationServiceImpl;
    
    @Mock
    private ClientManagerDelegate clientManager;
    
    @Mock
    private Service service;
    
    @Mock
    private Instance instance;
    
    @Mock
    private Subscriber subscriber;
    
    private Client ipPortBasedClient;
    
    private Client connectionBasedClient;
    
    @BeforeEach
    void setUp() throws Exception {
        when(instance.getIp()).thenReturn(ip);
        when(instance.getPort()).thenReturn(port);
        when(service.getNamespace()).thenReturn("public");
        when(service.getGroupedServiceName()).thenReturn("G@@S");
        when(service.isEphemeral()).thenReturn(true);
        ephemeralClientOperationServiceImpl = new EphemeralClientOperationServiceImpl(clientManager);
        ipPortBasedClient = new IpPortBasedClient(ipPortBasedClientId, true);
        connectionBasedClient = new ConnectionBasedClient(connectionBasedClientId, true, 1L);
        when(clientManager.getClient(connectionBasedClientId)).thenReturn(connectionBasedClient);
        when(clientManager.getClient(ipPortBasedClientId)).thenReturn(ipPortBasedClient);
    }
    
    @Test
    void testRegisterPersistentInstance() throws NacosException {
        assertThrows(NacosRuntimeException.class, () -> {
            when(service.isEphemeral()).thenReturn(false);
            // Excepted exception
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
        });
    }
    
    @Test
    void testRegisterInstanceWithInvalidClusterName() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            when(instance.getClusterName()).thenReturn("cluster1,cluster2");
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
        });
        assertTrue(exception.getMessage()
                .contains("Instance 'clusterName' should be characters with only 0-9a-zA-Z-. (current: cluster1,cluster2)"));
    }
    
    @Test
    void testRegisterAndDeregisterInstance() throws Exception {
        // Test register instance
        ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
        assertTrue(ipPortBasedClient.getAllPublishedService().contains(service));
        assertEquals(ipPortBasedClient.getInstancePublishInfo(service).getIp(), ip);
        assertEquals(ipPortBasedClient.getInstancePublishInfo(service).getPort(), port);
        // Test deregister instance
        ephemeralClientOperationServiceImpl.deregisterInstance(service, instance, ipPortBasedClientId);
        Collection<Service> allPublishService = ipPortBasedClient.getAllPublishedService();
        assertFalse(allPublishService.contains(service));
    }
    
    @Test
    void testBatchRegisterAndDeregisterInstance() throws Exception {
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
        ephemeralClientOperationServiceImpl.batchRegisterInstance(service, instances, connectionBasedClientId);
        assertTrue(connectionBasedClient.getAllPublishedService().contains(service));
    }
    
    @Test
    void testSubscribeAndUnsubscribeService() throws Exception {
        // Test subscribe instance
        ephemeralClientOperationServiceImpl.subscribeService(service, subscriber, ipPortBasedClientId);
        assertTrue(ipPortBasedClient.getAllSubscribeService().contains(service));
        // Test unsubscribe instance
        ephemeralClientOperationServiceImpl.unsubscribeService(service, subscriber, ipPortBasedClientId);
        assertFalse(ipPortBasedClient.getAllSubscribeService().contains(service));
    }
    
    @Test
    void testRegisterWhenClientNull() throws NacosException {
        assertThrows(NacosRuntimeException.class, () -> {
            when(clientManager.getClient(anyString())).thenReturn(null);
            // Excepted exception
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
        });
    }
    
    @Test
    void testRegisterWhenClientPersistent() throws NacosException {
        assertThrows(NacosRuntimeException.class, () -> {
            Client persistentClient = new IpPortBasedClient(ipPortBasedClientId, false);
            when(clientManager.getClient(anyString())).thenReturn(persistentClient);
            // Excepted exception
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
        });
    }
    
    @Test
    void testBatchRegisterWhenClientNull() {
        assertThrows(NacosRuntimeException.class, () -> {
            when(clientManager.getClient(anyString())).thenReturn(null);
            // Excepted exception
            List<Instance> instances = new ArrayList<>();
            ephemeralClientOperationServiceImpl.batchRegisterInstance(service, instances, ipPortBasedClientId);
        });
    }
    
    @Test
    void testBatchRegisterWhenClientPersistent() {
        assertThrows(NacosRuntimeException.class, () -> {
            Client persistentClient = new IpPortBasedClient(ipPortBasedClientId, false);
            when(clientManager.getClient(anyString())).thenReturn(persistentClient);
            // Excepted exception
            List<Instance> instances = new ArrayList<>();
            ephemeralClientOperationServiceImpl.batchRegisterInstance(service, instances, ipPortBasedClientId);
        });
    }
    
    @Test
    void testDeRegisterWhenClientNull() throws NacosException {
        assertThrows(NacosRuntimeException.class, () -> {
            // Test register instance
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
            when(clientManager.getClient(anyString())).thenReturn(null);
            // Excepted exception
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
        });
    }
    
    @Test
    void testDeRegisterWhenClientPersistent() throws NacosException {
        assertThrows(NacosRuntimeException.class, () -> {
            ephemeralClientOperationServiceImpl.registerInstance(service, instance, ipPortBasedClientId);
            Client persistentClient = new IpPortBasedClient(ipPortBasedClientId, false);
            when(clientManager.getClient(anyString())).thenReturn(persistentClient);
            // Excepted exception
            ephemeralClientOperationServiceImpl.deregisterInstance(service, instance, ipPortBasedClientId);
        });
    }
    
    @Test
    void testSubscribeWhenClientNull() {
        assertThrows(NacosRuntimeException.class, () -> {
            when(clientManager.getClient(anyString())).thenReturn(null);
            // Excepted exception
            ephemeralClientOperationServiceImpl.subscribeService(service, subscriber, ipPortBasedClientId);
        });
    }
    
    @Test
    void testSubscribeWhenClientPersistent() {
        assertThrows(NacosRuntimeException.class, () -> {
            Client persistentClient = new IpPortBasedClient(ipPortBasedClientId, false);
            when(clientManager.getClient(anyString())).thenReturn(persistentClient);
            // Excepted exception
            ephemeralClientOperationServiceImpl.subscribeService(service, subscriber, ipPortBasedClientId);
        });
    }
    
    @Test
    void testUnSubscribeWhenClientNull() {
        assertThrows(NacosRuntimeException.class, () -> {
            when(clientManager.getClient(anyString())).thenReturn(null);
            // Excepted exception
            ephemeralClientOperationServiceImpl.unsubscribeService(service, subscriber, ipPortBasedClientId);
        });
    }
    
    @Test
    void testUnSubscribeWhenClientPersistent() {
        assertThrows(NacosRuntimeException.class, () -> {
            Client persistentClient = new IpPortBasedClient(ipPortBasedClientId, false);
            when(clientManager.getClient(anyString())).thenReturn(persistentClient);
            // Excepted exception
            ephemeralClientOperationServiceImpl.unsubscribeService(service, subscriber, ipPortBasedClientId);
        });
    }
}
