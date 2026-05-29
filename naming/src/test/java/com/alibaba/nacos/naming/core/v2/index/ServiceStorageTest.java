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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ServiceStorageTest {
    
    private static final Service SERVICE =
        Service.newService("namespaceId", "groupName", "serviceName");
    
    private static final String NACOS = "nacos";
    
    @Mock
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    @Mock
    private ClientManagerDelegate clientManagerDelegate;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private NamingMetadataManager namingMetadataManager;
    
    @Mock
    private ServiceInfo serviceInfo;
    
    @Mock
    private InstancePublishInfo instancePublishInfo;
    
    private ServiceStorage serviceStorage;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        serviceStorage = new ServiceStorage(clientServiceIndexesManager, clientManagerDelegate,
            switchDomain, namingMetadataManager);
        
        Field serviceClusterIndex = ServiceStorage.class.getDeclaredField("serviceClusterIndex");
        serviceClusterIndex.setAccessible(true);
        ConcurrentMap<Service, Set<String>> serviceSetConcurrentMap =
            (ConcurrentMap<Service, Set<String>>) serviceClusterIndex.get(
                serviceStorage);
        serviceSetConcurrentMap.put(SERVICE, new HashSet<>(Collections.singletonList(NACOS)));
        
        Field serviceDataIndexes = ServiceStorage.class.getDeclaredField("serviceDataIndexes");
        serviceDataIndexes.setAccessible(true);
        ConcurrentMap<Service, ServiceInfo> infoConcurrentMap =
            (ConcurrentMap<Service, ServiceInfo>) serviceDataIndexes.get(
                serviceStorage);
        infoConcurrentMap.put(SERVICE, serviceInfo);
    }
    
    @AfterEach
    void tearDown() {
        ServiceManager.getInstance().removeSingleton(SERVICE);
    }
    
    @Test
    void testGetClusters() {
        Set<String> clusters = serviceStorage.getClusters(SERVICE);
        
        assertNotNull(clusters);
        for (String cluster : clusters) {
            assertEquals(NACOS, cluster);
        }
    }
    
    @Test
    void testGetData() {
        ServiceInfo serviceInfo = serviceStorage.getData(SERVICE);
        
        assertNotNull(serviceInfo);
    }
    
    @Test
    void testGetPushData() {
        ServiceInfo pushData = serviceStorage.getPushData(SERVICE);
        
        Mockito.verify(switchDomain).getDefaultPushCacheMillis();
        assertNotNull(pushData);
    }
    
    @Test
    void testGetPushDataBuildsInstancesFromIndex() {
        ServiceManager.getInstance().getSingleton(SERVICE);
        InstancePublishInfo normalInstance = instance("1.1.1.1", 8848, "clusterA");
        InstancePublishInfo batchInstance = instance("2.2.2.2", 8848, "clusterB");
        BatchInstancePublishInfo batchInstancePublishInfo = new BatchInstancePublishInfo();
        batchInstancePublishInfo.setInstancePublishInfos(Collections.singletonList(batchInstance));
        Client clientA = client(normalInstance);
        Client clientB = client(batchInstancePublishInfo);
        Mockito.when(clientServiceIndexesManager.getAllClientsRegisteredService(Mockito.any()))
            .thenReturn(new HashSet<>(Arrays.asList("clientA", "clientB", "missing")));
        Mockito.when(clientManagerDelegate.getClient("clientA")).thenReturn(clientA);
        Mockito.when(clientManagerDelegate.getClient("clientB")).thenReturn(clientB);
        Mockito.when(clientManagerDelegate.getClient("missing")).thenReturn(null);
        InstanceMetadata instanceMetadata = new InstanceMetadata();
        instanceMetadata.setWeight(2.0D);
        instanceMetadata.setEnabled(false);
        Mockito.when(namingMetadataManager.getInstanceMetadata(Mockito.any(), Mockito.anyString()))
            .thenReturn(Optional.empty());
        Mockito.when(namingMetadataManager.getInstanceMetadata(Mockito.any(),
            Mockito.eq(normalInstance.getMetadataId()))).thenReturn(Optional.of(instanceMetadata));
        
        ServiceInfo pushData = serviceStorage.getPushData(SERVICE);
        
        assertEquals(2, pushData.getHosts().size());
        assertTrue(serviceStorage.getClusters(SERVICE).contains("clusterA"));
        assertTrue(serviceStorage.getClusters(SERVICE).contains("clusterB"));
        assertTrue(pushData.getHosts().stream()
            .anyMatch(instance -> "1.1.1.1".equals(instance.getIp()) && !instance.isEnabled()
                && instance.getWeight() == 2.0D));
    }
    
    @Test
    void testRemoveData() throws NoSuchFieldException, IllegalAccessException {
        serviceStorage.removeData(SERVICE);
        
        Field serviceClusterIndex = ServiceStorage.class.getDeclaredField("serviceClusterIndex");
        serviceClusterIndex.setAccessible(true);
        ConcurrentMap<Service, Set<String>> serviceSetConcurrentMap =
            (ConcurrentMap<Service, Set<String>>) serviceClusterIndex.get(
                serviceStorage);
        
        Field serviceDataIndexes = ServiceStorage.class.getDeclaredField("serviceDataIndexes");
        serviceDataIndexes.setAccessible(true);
        ConcurrentMap<Service, ServiceInfo> infoConcurrentMap =
            (ConcurrentMap<Service, ServiceInfo>) serviceDataIndexes.get(
                serviceStorage);
        
        assertEquals(0, serviceSetConcurrentMap.size());
        assertEquals(0, infoConcurrentMap.size());
    }
    
    @Test
    void testGetAllInstancesFromIndex()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<ServiceStorage> serviceStorageClass = ServiceStorage.class;
        Method getAllInstancesFromIndex =
            serviceStorageClass.getDeclaredMethod("getAllInstancesFromIndex", Service.class);
        getAllInstancesFromIndex.setAccessible(true);
        List<Instance> list =
            (List<Instance>) getAllInstancesFromIndex.invoke(serviceStorage, SERVICE);
        
        assertNotNull(list);
    }
    
    @Test
    void testGetInstanceInfo()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<ServiceStorage> serviceStorageClass = ServiceStorage.class;
        Method getInstanceInfo =
            serviceStorageClass.getDeclaredMethod("getInstanceInfo", String.class, Service.class);
        getInstanceInfo.setAccessible(true);
        Optional<InstancePublishInfo> optionalInstancePublishInfo =
            (Optional<InstancePublishInfo>) getInstanceInfo.invoke(serviceStorage,
                NACOS, SERVICE);
        
        assertFalse(optionalInstancePublishInfo.isPresent());
    }
    
    @Test
    void testParseInstance()
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<ServiceStorage> serviceStorageClass = ServiceStorage.class;
        Method parseInstance = serviceStorageClass.getDeclaredMethod("parseInstance", Service.class,
            InstancePublishInfo.class);
        parseInstance.setAccessible(true);
        Instance instance =
            (Instance) parseInstance.invoke(serviceStorage, SERVICE, instancePublishInfo);
        
        Mockito.verify(namingMetadataManager).getInstanceMetadata(SERVICE,
            instancePublishInfo.getMetadataId());
        assertNotNull(instance);
    }
    
    private Client client(InstancePublishInfo instancePublishInfo) {
        Client result = Mockito.mock(Client.class);
        Mockito.when(result.getInstancePublishInfo(Mockito.any())).thenReturn(instancePublishInfo);
        return result;
    }
    
    private InstancePublishInfo instance(String ip, int port, String cluster) {
        InstancePublishInfo result = new InstancePublishInfo(ip, port);
        result.setCluster(cluster);
        return result;
    }
    
}
