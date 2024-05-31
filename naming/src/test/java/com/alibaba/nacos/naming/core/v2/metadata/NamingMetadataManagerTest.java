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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.metadata.MetadataEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class NamingMetadataManagerTest {
    
    private static final String METADATA_ID = "METADATA_ID";
    
    @Mock
    private Service service;
    
    @Mock
    private ServiceMetadata serviceMetadata;
    
    @Mock
    private InstanceMetadata instanceMetadata;
    
    @Mock
    private MetadataEvent.InstanceMetadataEvent instanceMetadataEvent;
    
    @Mock
    private MetadataEvent.ServiceMetadataEvent serviceMetadataEvent;
    
    @Mock
    private ClientEvent.ClientDisconnectEvent clientDisconnectEvent;
    
    @Mock
    private Client client;
    
    private NamingMetadataManager namingMetadataManager;
    
    @BeforeEach
    void setUp() throws Exception {
        namingMetadataManager = new NamingMetadataManager();
        
        Class<NamingMetadataManager> namingMetadataManagerClass = NamingMetadataManager.class;
        Field serviceMetadataMapField = namingMetadataManagerClass.getDeclaredField("serviceMetadataMap");
        serviceMetadataMapField.setAccessible(true);
        ConcurrentMap<Service, ServiceMetadata> serviceMetadataMap = (ConcurrentMap<Service, ServiceMetadata>) serviceMetadataMapField.get(
                namingMetadataManager);
        serviceMetadataMap.put(service, serviceMetadata);
        
        Field instanceMetadataMapField = namingMetadataManagerClass.getDeclaredField("instanceMetadataMap");
        instanceMetadataMapField.setAccessible(true);
        
        ConcurrentMap<String, InstanceMetadata> concurrentMap = new ConcurrentHashMap<>();
        concurrentMap.put(METADATA_ID, instanceMetadata);
        ((ConcurrentMap<Service, ConcurrentMap<String, InstanceMetadata>>) instanceMetadataMapField.get(namingMetadataManager)).put(service,
                concurrentMap);
    }
    
    @Test
    void testContainServiceMetadata() {
        boolean result = namingMetadataManager.containServiceMetadata(service);
        
        assertTrue(result);
    }
    
    @Test
    void testContainInstanceMetadata() {
        boolean result = namingMetadataManager.containInstanceMetadata(service, METADATA_ID);
        
        assertTrue(result);
    }
    
    @Test
    void testGetServiceMetadata() {
        Optional<ServiceMetadata> serviceMetadata = namingMetadataManager.getServiceMetadata(service);
        
        assertTrue(serviceMetadata.isPresent());
        assertNotNull(serviceMetadata.get());
    }
    
    @Test
    void testGetInstanceMetadata() {
        Optional<InstanceMetadata> instanceMetadata = namingMetadataManager.getInstanceMetadata(service, METADATA_ID);
        
        assertTrue(instanceMetadata.isPresent());
        assertNotNull(instanceMetadata.get());
    }
    
    @Test
    void testUpdateServiceMetadata() throws NoSuchFieldException, IllegalAccessException {
        
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        Class<ServiceMetadata> serviceMetadataClass = ServiceMetadata.class;
        Field ephemeral = serviceMetadataClass.getDeclaredField("ephemeral");
        ephemeral.setAccessible(true);
        ephemeral.set(serviceMetadata, false);
        
        namingMetadataManager.updateServiceMetadata(service, serviceMetadata);
        
        Optional<ServiceMetadata> optional = namingMetadataManager.getServiceMetadata(service);
        assertTrue(optional.isPresent());
        assertNotNull(optional.get());
        assertFalse(optional.get().isEphemeral());
    }
    
    @Test
    void testUpdateInstanceMetadata() throws NoSuchFieldException, IllegalAccessException {
        InstanceMetadata instanceMetadata = new InstanceMetadata();
        Class<InstanceMetadata> instanceMetadataClass = InstanceMetadata.class;
        Field enabled = instanceMetadataClass.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(instanceMetadata, false);
        
        namingMetadataManager.updateInstanceMetadata(service, METADATA_ID, instanceMetadata);
        
        Optional<InstanceMetadata> optional = namingMetadataManager.getInstanceMetadata(service, METADATA_ID);
        assertTrue(optional.isPresent());
        assertNotNull(optional.get());
        assertFalse(optional.get().isEnabled());
    }
    
    @Test
    void testRemoveServiceMetadata() {
        
        namingMetadataManager.removeServiceMetadata(service);
        
        Optional<ServiceMetadata> serviceMetadata = namingMetadataManager.getServiceMetadata(service);
        
        assertFalse(serviceMetadata.isPresent());
    }
    
    @Test
    void testRemoveInstanceMetadata() {
        
        namingMetadataManager.removeInstanceMetadata(service, METADATA_ID);
        
        Optional<InstanceMetadata> instanceMetadata = namingMetadataManager.getInstanceMetadata(service, METADATA_ID);
        
        assertFalse(instanceMetadata.isPresent());
    }
    
    @Test
    void testGetServiceMetadataSnapshot() {
        Map<Service, ServiceMetadata> serviceMetadataSnapshot = namingMetadataManager.getServiceMetadataSnapshot();
        
        assertEquals(1, serviceMetadataSnapshot.size());
    }
    
    @Test
    void testGetInstanceMetadataSnapshot() {
        Map<Service, ConcurrentMap<String, InstanceMetadata>> instanceMetadataSnapshot = namingMetadataManager.getInstanceMetadataSnapshot();
        
        assertEquals(1, instanceMetadataSnapshot.size());
    }
    
    @Test
    void testLoadServiceMetadataSnapshot() {
        namingMetadataManager.loadServiceMetadataSnapshot(new ConcurrentHashMap<>());
        Map<Service, ServiceMetadata> serviceMetadataSnapshot = namingMetadataManager.getServiceMetadataSnapshot();
        
        assertEquals(0, serviceMetadataSnapshot.size());
    }
    
    @Test
    void testLoadInstanceMetadataSnapshot() {
        namingMetadataManager.loadInstanceMetadataSnapshot(new ConcurrentHashMap<>());
        Map<Service, ConcurrentMap<String, InstanceMetadata>> instanceMetadataSnapshot = namingMetadataManager.getInstanceMetadataSnapshot();
        
        assertEquals(0, instanceMetadataSnapshot.size());
    }
    
    @Test
    void testGetExpiredMetadataInfos() {
        Set<ExpiredMetadataInfo> expiredMetadataInfos = namingMetadataManager.getExpiredMetadataInfos();
        
        assertNotNull(expiredMetadataInfos);
    }
    
    @Test
    void testSubscribeTypes() {
        List<Class<? extends Event>> classes = namingMetadataManager.subscribeTypes();
        
        assertEquals(3, classes.size());
    }
    
    @Test
    void testOnEvent() {
        Mockito.when(instanceMetadataEvent.getService()).thenReturn(service);
        Mockito.when(instanceMetadataEvent.getMetadataId()).thenReturn(METADATA_ID);
        Mockito.when(serviceMetadataEvent.getService()).thenReturn(service);
        Mockito.when(clientDisconnectEvent.getClient()).thenReturn(client);
        
        namingMetadataManager.onEvent(instanceMetadataEvent);
        Mockito.verify(instanceMetadataEvent, Mockito.times(2)).getMetadataId();
        Mockito.verify(instanceMetadataEvent, Mockito.times(2)).getService();
        
        namingMetadataManager.onEvent(serviceMetadataEvent);
        Mockito.verify(serviceMetadataEvent).getService();
        
        namingMetadataManager.onEvent(clientDisconnectEvent);
        Mockito.verify(clientDisconnectEvent).getClient();
    }
}