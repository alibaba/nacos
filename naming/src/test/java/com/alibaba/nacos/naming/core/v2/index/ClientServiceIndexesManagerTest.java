/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@RunWith(MockitoJUnitRunner.class)
public class ClientServiceIndexesManagerTest {
    
    @Mock
    private Service service;
    
    @Mock
    private ClientEvent.ClientDisconnectEvent clientDisconnectEvent;
    
    @Mock
    private ClientOperationEvent clientOperationEvent;
    
    @Mock
    private Client client;
    
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    private static final String NACOS = "nacos";
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        clientServiceIndexesManager = new ClientServiceIndexesManager();
        
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass = ClientServiceIndexesManager.class;
        Field publisherIndexesField = clientServiceIndexesManagerClass.getDeclaredField("publisherIndexes");
        publisherIndexesField.setAccessible(true);
        ConcurrentMap<Service, Set<String>> publisherIndexes = (ConcurrentMap<Service, Set<String>>) publisherIndexesField
                .get(clientServiceIndexesManager);
        publisherIndexes.put(service, new HashSet<>(Collections.singletonList(NACOS)));
        
        Field subscriberIndexesField = clientServiceIndexesManagerClass.getDeclaredField("subscriberIndexes");
        subscriberIndexesField.setAccessible(true);
        ConcurrentMap<Service, Set<String>> subscriberIndexes = (ConcurrentMap<Service, Set<String>>) subscriberIndexesField
                .get(clientServiceIndexesManager);
        subscriberIndexes.put(service, new HashSet<>(Collections.singletonList(NACOS)));
    }
    
    @Test
    public void testGetAllClientsRegisteredService() {
        Collection<String> allClientsRegisteredService = clientServiceIndexesManager
                .getAllClientsRegisteredService(service);
        
        Assert.assertNotNull(allClientsRegisteredService);
        Assert.assertEquals(allClientsRegisteredService.size(), 1);
    }
    
    @Test
    public void testGetAllClientsSubscribeService() {
        
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsSubscribeService(service);
        
        Assert.assertNotNull(allClientsSubscribeService);
        Assert.assertEquals(allClientsSubscribeService.size(), 1);
    }
    
    @Test
    public void testGetSubscribedService() {
        Collection<Service> subscribedService = clientServiceIndexesManager.getSubscribedService();
        
        Assert.assertNotNull(subscribedService);
        Assert.assertEquals(subscribedService.size(), 1);
    }
    
    @Test
    public void testRemovePublisherIndexesByEmptyService() throws NoSuchFieldException, IllegalAccessException {
        clientServiceIndexesManager.removePublisherIndexesByEmptyService(service);
        
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass = ClientServiceIndexesManager.class;
        Field publisherIndexesField = clientServiceIndexesManagerClass.getDeclaredField("publisherIndexes");
        publisherIndexesField.setAccessible(true);
        ConcurrentMap<Service, Set<String>> publisherIndexes = (ConcurrentMap<Service, Set<String>>) publisherIndexesField
                .get(clientServiceIndexesManager);
        
        Assert.assertEquals(publisherIndexes.size(), 1);
    }
    
    @Test
    public void testSubscribeTypes() {
        List<Class<? extends Event>> classes = clientServiceIndexesManager.subscribeTypes();
        
        Assert.assertNotNull(classes);
        Assert.assertEquals(classes.size(), 5);
    }
    
    @Test
    public void testOnEvent() {
        Mockito.when(clientDisconnectEvent.getClient()).thenReturn(client);
        clientServiceIndexesManager.onEvent(clientDisconnectEvent);
        
        Mockito.verify(clientDisconnectEvent).getClient();
        
        clientServiceIndexesManager.onEvent(clientOperationEvent);
        
        Mockito.verify(clientOperationEvent).getService();
        Mockito.verify(clientOperationEvent).getClientId();
    }
    
    @Test
    public void testAddPublisherIndexes()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass = ClientServiceIndexesManager.class;
        Method addPublisherIndexes = clientServiceIndexesManagerClass
                .getDeclaredMethod("addPublisherIndexes", Service.class, String.class);
        addPublisherIndexes.setAccessible(true);
        addPublisherIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsRegisteredService(service);
        
        Assert.assertNotNull(allClientsSubscribeService);
        Assert.assertEquals(allClientsSubscribeService.size(), 2);
    }
    
    @Test
    public void testRemovePublisherIndexes()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass = ClientServiceIndexesManager.class;
        Method removePublisherIndexes = clientServiceIndexesManagerClass
                .getDeclaredMethod("removePublisherIndexes", Service.class, String.class);
        removePublisherIndexes.setAccessible(true);
        removePublisherIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsRegisteredService(service);
        
        Assert.assertNotNull(allClientsSubscribeService);
        Assert.assertEquals(allClientsSubscribeService.size(), 1);
    }
    
    @Test
    public void testAddSubscriberIndexes()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass = ClientServiceIndexesManager.class;
        Method addSubscriberIndexes = clientServiceIndexesManagerClass
                .getDeclaredMethod("addSubscriberIndexes", Service.class, String.class);
        addSubscriberIndexes.setAccessible(true);
        addSubscriberIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsSubscribeService(service);
        
        Assert.assertNotNull(allClientsSubscribeService);
        Assert.assertEquals(allClientsSubscribeService.size(), 2);
    }
    
    @Test
    public void testRemoveSubscriberIndexes()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass = ClientServiceIndexesManager.class;
        Method removeSubscriberIndexes = clientServiceIndexesManagerClass
                .getDeclaredMethod("removeSubscriberIndexes", Service.class, String.class);
        removeSubscriberIndexes.setAccessible(true);
        removeSubscriberIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsSubscribeService(service);
        
        Assert.assertNotNull(allClientsSubscribeService);
        Assert.assertEquals(allClientsSubscribeService.size(), 1);
    }
    
}