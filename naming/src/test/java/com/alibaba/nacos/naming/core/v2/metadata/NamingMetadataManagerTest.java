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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RunWith(MockitoJUnitRunner.class)
public class NamingMetadataManagerTest {
    
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
    
    private static final String METADATA_ID = "METADATA_ID";
    
    @Before
    public void setUp() throws Exception {
        namingMetadataManager = new NamingMetadataManager();
        
        Class<NamingMetadataManager> namingMetadataManagerClass = NamingMetadataManager.class;
        Field serviceMetadataMapField = namingMetadataManagerClass.getDeclaredField("serviceMetadataMap");
        serviceMetadataMapField.setAccessible(true);
        ConcurrentMap<Service, ServiceMetadata> serviceMetadataMap = (ConcurrentMap<Service, ServiceMetadata>) serviceMetadataMapField
                .get(namingMetadataManager);
        serviceMetadataMap.put(service, serviceMetadata);
        
        Field instanceMetadataMapField = namingMetadataManagerClass.getDeclaredField("instanceMetadataMap");
        instanceMetadataMapField.setAccessible(true);
        ConcurrentMap<Service, ConcurrentMap<String, InstanceMetadata>> instanceMetadataMap =
                (ConcurrentMap<Service, ConcurrentMap<String, InstanceMetadata>>) instanceMetadataMapField.get(namingMetadataManager);
        
        ConcurrentMap<String, InstanceMetadata> concurrentMap = new ConcurrentHashMap<>();
        concurrentMap.put(METADATA_ID, instanceMetadata);
        instanceMetadataMap.put(service, concurrentMap);
    }
    
    @Test
    public void testContainServiceMetadata() {
        boolean result = namingMetadataManager.containServiceMetadata(service);
        
        Assert.assertTrue(result);
    }
    
    @Test
    public void testContainInstanceMetadata() {
        boolean result = namingMetadataManager.containInstanceMetadata(service, METADATA_ID);
        
        Assert.assertTrue(result);
    }
    
    @Test
    public void testGetServiceMetadata() {
        Optional<ServiceMetadata> serviceMetadata = namingMetadataManager.getServiceMetadata(service);
        
        Assert.assertTrue(serviceMetadata.isPresent());
        Assert.assertNotNull(serviceMetadata.get());
    }
    
    @Test
    public void testGetInstanceMetadata() {
        Optional<InstanceMetadata> instanceMetadata = namingMetadataManager.getInstanceMetadata(service, METADATA_ID);
        
        Assert.assertTrue(instanceMetadata.isPresent());
        Assert.assertNotNull(instanceMetadata.get());
    }
    
    @Test
    public void testUpdateServiceMetadata() throws NoSuchFieldException, IllegalAccessException {
        
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        Class<ServiceMetadata> serviceMetadataClass = ServiceMetadata.class;
        Field ephemeral = serviceMetadataClass.getDeclaredField("ephemeral");
        ephemeral.setAccessible(true);
        ephemeral.set(serviceMetadata, false);
        
        namingMetadataManager.updateServiceMetadata(service, serviceMetadata);
        
        Optional<ServiceMetadata> optional = namingMetadataManager.getServiceMetadata(service);
        Assert.assertTrue(optional.isPresent());
        Assert.assertNotNull(optional.get());
        Assert.assertFalse(optional.get().isEphemeral());
    }
    
    @Test
    public void testUpdateInstanceMetadata() throws NoSuchFieldException, IllegalAccessException {
        InstanceMetadata instanceMetadata = new InstanceMetadata();
        Class<InstanceMetadata> instanceMetadataClass = InstanceMetadata.class;
        Field enabled = instanceMetadataClass.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(instanceMetadata, false);
        
        namingMetadataManager.updateInstanceMetadata(service, METADATA_ID, instanceMetadata);
        
        Optional<InstanceMetadata> optional = namingMetadataManager.getInstanceMetadata(service, METADATA_ID);
        Assert.assertTrue(optional.isPresent());
        Assert.assertNotNull(optional.get());
        Assert.assertFalse(optional.get().isEnabled());
    }
    
    @Test
    public void testRemoveServiceMetadata() {
        
        namingMetadataManager.removeServiceMetadata(service);
        
        Optional<ServiceMetadata> serviceMetadata = namingMetadataManager.getServiceMetadata(service);
        
        Assert.assertFalse(serviceMetadata.isPresent());
    }
    
    @Test
    public void testRemoveInstanceMetadata() {
        
        namingMetadataManager.removeInstanceMetadata(service, METADATA_ID);
        
        Optional<InstanceMetadata> instanceMetadata = namingMetadataManager.getInstanceMetadata(service, METADATA_ID);
        
        Assert.assertFalse(instanceMetadata.isPresent());
    }
    
    @Test
    public void testGetServiceMetadataSnapshot() {
        Map<Service, ServiceMetadata> serviceMetadataSnapshot = namingMetadataManager.getServiceMetadataSnapshot();
        
        Assert.assertEquals(serviceMetadataSnapshot.size(), 1);
    }
    
    @Test
    public void testGetInstanceMetadataSnapshot() {
        Map<Service, ConcurrentMap<String, InstanceMetadata>> instanceMetadataSnapshot = namingMetadataManager
                .getInstanceMetadataSnapshot();
        
        Assert.assertEquals(instanceMetadataSnapshot.size(), 1);
    }
    
    @Test
    public void testLoadServiceMetadataSnapshot() {
        namingMetadataManager.loadServiceMetadataSnapshot(new ConcurrentHashMap<>());
        Map<Service, ServiceMetadata> serviceMetadataSnapshot = namingMetadataManager.getServiceMetadataSnapshot();
        
        Assert.assertEquals(serviceMetadataSnapshot.size(), 0);
    }
    
    @Test
    public void testLoadInstanceMetadataSnapshot() {
        namingMetadataManager.loadInstanceMetadataSnapshot(new ConcurrentHashMap<>());
        Map<Service, ConcurrentMap<String, InstanceMetadata>> instanceMetadataSnapshot = namingMetadataManager
                .getInstanceMetadataSnapshot();
        
        Assert.assertEquals(instanceMetadataSnapshot.size(), 0);
    }
    
    @Test
    public void testGetExpiredMetadataInfos() {
        Set<ExpiredMetadataInfo> expiredMetadataInfos = namingMetadataManager.getExpiredMetadataInfos();
        
        Assert.assertNotNull(expiredMetadataInfos);
    }
    
    @Test
    public void testSubscribeTypes() {
        List<Class<? extends Event>> classes = namingMetadataManager.subscribeTypes();
        
        Assert.assertEquals(classes.size(), 3);
    }
    
    @Test
    public void testOnEvent() {
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