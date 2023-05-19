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
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@RunWith(MockitoJUnitRunner.class)
public class ServiceStorageTest {
    
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
    
    private static final Service SERVICE = Service.newService("namespaceId", "groupName", "serviceName");
    
    private static final String NACOS = "nacos";
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        serviceStorage = new ServiceStorage(clientServiceIndexesManager, clientManagerDelegate, switchDomain,
                namingMetadataManager);
        
        Field serviceClusterIndex = ServiceStorage.class.getDeclaredField("serviceClusterIndex");
        serviceClusterIndex.setAccessible(true);
        ConcurrentMap<Service, Set<String>> serviceSetConcurrentMap = (ConcurrentMap<Service, Set<String>>) serviceClusterIndex
                .get(serviceStorage);
        serviceSetConcurrentMap.put(SERVICE, new HashSet<>(Collections.singletonList(NACOS)));
        
        Field serviceDataIndexes = ServiceStorage.class.getDeclaredField("serviceDataIndexes");
        serviceDataIndexes.setAccessible(true);
        ConcurrentMap<Service, ServiceInfo> infoConcurrentMap = (ConcurrentMap<Service, ServiceInfo>) serviceDataIndexes
                .get(serviceStorage);
        infoConcurrentMap.put(SERVICE, serviceInfo);
    }
    
    @Test
    public void testGetClusters() {
        Set<String> clusters = serviceStorage.getClusters(SERVICE);
        
        Assert.assertNotNull(clusters);
        for (String cluster : clusters) {
            Assert.assertEquals(cluster, NACOS);
        }
    }
    
    @Test
    public void testGetData() {
        ServiceInfo serviceInfo = serviceStorage.getData(SERVICE);
        
        Assert.assertNotNull(serviceInfo);
    }
    
    @Test()
    public void testGetPushData() {
        ServiceInfo pushData = serviceStorage.getPushData(SERVICE);
        
        Mockito.verify(switchDomain).getDefaultPushCacheMillis();
        Assert.assertNotNull(pushData);
    }
    
    @Test
    public void testRemoveData() throws NoSuchFieldException, IllegalAccessException {
        serviceStorage.removeData(SERVICE);
        
        Field serviceClusterIndex = ServiceStorage.class.getDeclaredField("serviceClusterIndex");
        serviceClusterIndex.setAccessible(true);
        ConcurrentMap<Service, Set<String>> serviceSetConcurrentMap = (ConcurrentMap<Service, Set<String>>) serviceClusterIndex
                .get(serviceStorage);
        
        Field serviceDataIndexes = ServiceStorage.class.getDeclaredField("serviceDataIndexes");
        serviceDataIndexes.setAccessible(true);
        ConcurrentMap<Service, ServiceInfo> infoConcurrentMap = (ConcurrentMap<Service, ServiceInfo>) serviceDataIndexes
                .get(serviceStorage);
        
        Assert.assertEquals(serviceSetConcurrentMap.size(), 0);
        Assert.assertEquals(infoConcurrentMap.size(), 0);
    }
    
    @Test
    public void testGetAllInstancesFromIndex()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<ServiceStorage> serviceStorageClass = ServiceStorage.class;
        Method getAllInstancesFromIndex = serviceStorageClass
                .getDeclaredMethod("getAllInstancesFromIndex", Service.class);
        getAllInstancesFromIndex.setAccessible(true);
        List<Instance> list = (List<Instance>) getAllInstancesFromIndex.invoke(serviceStorage, SERVICE);
        
        Assert.assertNotNull(list);
    }
    
    @Test
    public void testGetInstanceInfo() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<ServiceStorage> serviceStorageClass = ServiceStorage.class;
        Method getInstanceInfo = serviceStorageClass.getDeclaredMethod("getInstanceInfo", String.class, Service.class);
        getInstanceInfo.setAccessible(true);
        Optional<InstancePublishInfo> optionalInstancePublishInfo = (Optional<InstancePublishInfo>) getInstanceInfo
                .invoke(serviceStorage, NACOS, SERVICE);
        
        Assert.assertFalse(optionalInstancePublishInfo.isPresent());
    }
    
    @Test
    public void testParseInstance() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<ServiceStorage> serviceStorageClass = ServiceStorage.class;
        Method parseInstance = serviceStorageClass
                .getDeclaredMethod("parseInstance", Service.class, InstancePublishInfo.class);
        parseInstance.setAccessible(true);
        Instance instance = (Instance) parseInstance.invoke(serviceStorage, SERVICE, instancePublishInfo);
        
        Mockito.verify(namingMetadataManager).getInstanceMetadata(SERVICE, instancePublishInfo.getMetadataId());
        Assert.assertNotNull(instance);
    }
    
}