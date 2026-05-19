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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ClientServiceIndexesManagerTest {
    
    private static final String NACOS = "nacos";
    
    @Mock
    private Service service;
    
    @Mock
    private ClientOperationEvent.ClientReleaseEvent clientReleaseEvent;
    
    @Mock
    private ClientOperationEvent clientOperationEvent;
    
    @Mock
    private Client client;
    
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        clientServiceIndexesManager = new ClientServiceIndexesManager();
        
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass =
            ClientServiceIndexesManager.class;
        Field publisherIndexesField =
            clientServiceIndexesManagerClass.getDeclaredField("publisherIndexes");
        publisherIndexesField.setAccessible(true);
        ConcurrentMap<Service, Set<String>> publisherIndexes =
            (ConcurrentMap<Service, Set<String>>) publisherIndexesField.get(
                clientServiceIndexesManager);
        publisherIndexes.put(service, new HashSet<>(Collections.singletonList(NACOS)));
        
        Field subscriberIndexesField =
            clientServiceIndexesManagerClass.getDeclaredField("subscriberIndexes");
        subscriberIndexesField.setAccessible(true);
        ConcurrentMap<Service, Set<String>> subscriberIndexes =
            (ConcurrentMap<Service, Set<String>>) subscriberIndexesField.get(
                clientServiceIndexesManager);
        subscriberIndexes.put(service, new HashSet<>(Collections.singletonList(NACOS)));
    }
    
    @Test
    void testGetAllClientsRegisteredService() {
        Collection<String> allClientsRegisteredService =
            clientServiceIndexesManager.getAllClientsRegisteredService(service);
        
        assertNotNull(allClientsRegisteredService);
        assertEquals(1, allClientsRegisteredService.size());
    }
    
    @Test
    void testGetAllClientsSubscribeService() {
        
        Collection<String> allClientsSubscribeService =
            clientServiceIndexesManager.getAllClientsSubscribeService(service);
        
        assertNotNull(allClientsSubscribeService);
        assertEquals(1, allClientsSubscribeService.size());
    }
    
    @Test
    void testGetSubscribedService() {
        Collection<Service> subscribedService = clientServiceIndexesManager.getSubscribedService();
        
        assertNotNull(subscribedService);
        assertEquals(1, subscribedService.size());
    }
    
    @Test
    void testRemovePublisherIndexesByEmptyService()
        throws NoSuchFieldException, IllegalAccessException {
        clientServiceIndexesManager.removePublisherIndexesByEmptyService(service);
        
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass =
            ClientServiceIndexesManager.class;
        Field publisherIndexesField =
            clientServiceIndexesManagerClass.getDeclaredField("publisherIndexes");
        publisherIndexesField.setAccessible(true);
        ConcurrentMap<Service, Set<String>> publisherIndexes =
            (ConcurrentMap<Service, Set<String>>) publisherIndexesField.get(
                clientServiceIndexesManager);
        
        assertEquals(1, publisherIndexes.size());
    }
    
    @Test
    void testSubscribeTypes() {
        List<Class<? extends Event>> classes = clientServiceIndexesManager.subscribeTypes();
        
        assertNotNull(classes);
        assertEquals(5, classes.size());
    }
    
    @Test
    void testOnEvent() {
        Mockito.when(clientReleaseEvent.getClient()).thenReturn(client);
        clientServiceIndexesManager.onEvent(clientReleaseEvent);
        
        Mockito.verify(clientReleaseEvent).getClient();
        
        clientServiceIndexesManager.onEvent(clientOperationEvent);
        
        Mockito.verify(clientOperationEvent).getService();
        Mockito.verify(clientOperationEvent).getClientId();
    }
    
    @Test
    void testAddPublisherIndexes()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass =
            ClientServiceIndexesManager.class;
        Method addPublisherIndexes = clientServiceIndexesManagerClass
            .getDeclaredMethod("addPublisherIndexes", Service.class, String.class);
        addPublisherIndexes.setAccessible(true);
        addPublisherIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService =
            clientServiceIndexesManager.getAllClientsRegisteredService(service);
        
        assertNotNull(allClientsSubscribeService);
        assertEquals(2, allClientsSubscribeService.size());
    }
    
    @Test
    void testAddPublisherIndexesEmitsAddServiceOnFirstAndInstanceChangedOnRepeat()
        throws Exception {
        // The shared setUp() pre-seeds publisherIndexes for `service`, so use a fresh
        // manager + fresh service to exercise the "first-time registration" path.
        ClientServiceIndexesManager freshManager = new ClientServiceIndexesManager();
        Service freshService = Mockito.mock(Service.class);
        Method addPublisherIndexes = ClientServiceIndexesManager.class.getDeclaredMethod(
            "addPublisherIndexes", Service.class, String.class);
        addPublisherIndexes.setAccessible(true);
        
        AtomicInteger addServiceEventCount = new AtomicInteger();
        AtomicInteger instanceChangedEventCount = new AtomicInteger();
        
        try (MockedStatic<NotifyCenter> mocked = Mockito.mockStatic(NotifyCenter.class)) {
            mocked.when(() -> NotifyCenter.publishEvent(any())).thenAnswer(invocation -> {
                Object event = invocation.getArgument(0);
                if (event instanceof ServiceEvent.ServiceChangedEvent) {
                    String type = ((ServiceEvent.ServiceChangedEvent) event).getChangedType();
                    if (Constants.ServiceChangedType.ADD_SERVICE.equals(type)) {
                        addServiceEventCount.incrementAndGet();
                    } else if (Constants.ServiceChangedType.INSTANCE_CHANGED.equals(type)) {
                        instanceChangedEventCount.incrementAndGet();
                    }
                }
                return true;
            });
            
            // First registration of the service: must emit ADD_SERVICE exactly once and the
            // index entry must already exist by the time the event is published (the new
            // implementation orders the computeIfAbsent before the publishEvent for that reason).
            addPublisherIndexes.invoke(freshManager, freshService, "client-1");
            assertEquals(1, addServiceEventCount.get(),
                "First-time registration must emit ADD_SERVICE once");
            assertEquals(0, instanceChangedEventCount.get(),
                "First-time registration must not emit INSTANCE_CHANGED");
            assertEquals(1, freshManager.getAllClientsRegisteredService(freshService).size(),
                "Index entry must be present by the time the event is published");
            
            // Subsequent registrations of the same service must emit INSTANCE_CHANGED, not
            // another ADD_SERVICE.
            addPublisherIndexes.invoke(freshManager, freshService, "client-2");
            addPublisherIndexes.invoke(freshManager, freshService, "client-3");
            assertEquals(1, addServiceEventCount.get(),
                "Re-registration must not re-emit ADD_SERVICE");
            assertEquals(2, instanceChangedEventCount.get(),
                "Each re-registration must emit one INSTANCE_CHANGED");
            assertEquals(3, freshManager.getAllClientsRegisteredService(freshService).size(),
                "All three clients must be present in the index");
        }
    }
    
    @Test
    void testRemovePublisherIndexes()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass =
            ClientServiceIndexesManager.class;
        Method removePublisherIndexes = clientServiceIndexesManagerClass.getDeclaredMethod(
            "removePublisherIndexes", Service.class,
            String.class);
        removePublisherIndexes.setAccessible(true);
        removePublisherIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService =
            clientServiceIndexesManager.getAllClientsRegisteredService(service);
        
        assertNotNull(allClientsSubscribeService);
        assertEquals(1, allClientsSubscribeService.size());
    }
    
    @Test
    void testAddSubscriberIndexes()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass =
            ClientServiceIndexesManager.class;
        Method addSubscriberIndexes = clientServiceIndexesManagerClass.getDeclaredMethod(
            "addSubscriberIndexes", Service.class,
            String.class);
        addSubscriberIndexes.setAccessible(true);
        addSubscriberIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService =
            clientServiceIndexesManager.getAllClientsSubscribeService(service);
        
        assertNotNull(allClientsSubscribeService);
        assertEquals(2, allClientsSubscribeService.size());
    }
    
    @Test
    void testRemoveSubscriberIndexes()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String clientId = "clientId";
        Class<ClientServiceIndexesManager> clientServiceIndexesManagerClass =
            ClientServiceIndexesManager.class;
        Method removeSubscriberIndexes = clientServiceIndexesManagerClass.getDeclaredMethod(
            "removeSubscriberIndexes", Service.class,
            String.class);
        removeSubscriberIndexes.setAccessible(true);
        removeSubscriberIndexes.invoke(clientServiceIndexesManager, service, clientId);
        
        Collection<String> allClientsSubscribeService =
            clientServiceIndexesManager.getAllClientsSubscribeService(service);
        
        assertNotNull(allClientsSubscribeService);
        assertEquals(1, allClientsSubscribeService.size());
    }
    
}
