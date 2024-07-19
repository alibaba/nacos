/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link CatalogServiceV2Impl} unit tests.
 *
 * @author chenglu
 * @date 2021-08-03 19:56
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceV2ImplTest {
    
    private CatalogServiceV2Impl catalogServiceV2Impl;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @BeforeEach
    void setUp() {
        catalogServiceV2Impl = new CatalogServiceV2Impl(serviceStorage, metadataManager);
        ServiceManager serviceManager = ServiceManager.getInstance();
        Service service = Service.newService("A", "B", "C");
        serviceManager.getSingleton(service);
    }
    
    @AfterEach
    void tearDown() {
        ServiceManager serviceManager = ServiceManager.getInstance();
        Service service = Service.newService("A", "B", "C");
        serviceManager.removeSingleton(service);
        for (Service each : serviceManager.getSingletons("CatalogService")) {
            serviceManager.removeSingleton(each);
        }
    }
    
    @Test
    void testGetServiceDetail() throws NacosException {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(0.75F);
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(serviceMetadata));
        Mockito.when(serviceStorage.getClusters(Mockito.any())).thenReturn(Collections.singleton("C"));
        Object obj = catalogServiceV2Impl.getServiceDetail("A", "B", "C");
        ObjectNode objectNode = (ObjectNode) obj;
        assertEquals("C", objectNode.get(FieldsConstants.SERVICE).get(FieldsConstants.NAME).asText());
        assertEquals("B", objectNode.get(FieldsConstants.SERVICE).get(FieldsConstants.GROUP_NAME).asText());
        assertEquals("none", objectNode.get(FieldsConstants.SERVICE).get(FieldsConstants.SELECTOR).get("type").asText());
        assertEquals(0, objectNode.get(FieldsConstants.SERVICE).get(FieldsConstants.METADATA).size());
        assertEquals(0.75, objectNode.get(FieldsConstants.SERVICE).get(FieldsConstants.PROTECT_THRESHOLD).asDouble(), 0.1);
    }
    
    @Test
    void testGetServiceDetailNonExist() throws NacosException {
        assertThrows(NacosException.class, () -> {
            catalogServiceV2Impl.getServiceDetail("A", "BB", "CC");
        });
    }
    
    @Test
    void testListInstances() throws NacosException {
        Mockito.when(serviceStorage.getClusters(Mockito.any())).thenReturn(Collections.singleton("D"));
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setGroupName("B");
        serviceInfo.setName("C");
        Instance instance = new Instance();
        instance.setClusterName("D");
        instance.setIp("1.1.1.1");
        serviceInfo.setHosts(Collections.singletonList(instance));
        Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        List<? extends Instance> instances = catalogServiceV2Impl.listInstances("A", "B", "C", "D");
        assertEquals(1, instances.size());
    }
    
    @Test
    void testListInstancesNonExistService() throws NacosException {
        assertThrows(NacosException.class, () -> {
            catalogServiceV2Impl.listInstances("A", "BB", "CC", "DD");
        });
    }
    
    @Test
    void testListInstancesNonExistCluster() throws NacosException {
        assertThrows(NacosException.class, () -> {
            catalogServiceV2Impl.listInstances("A", "B", "C", "DD");
        });
    }
    
    @Test
    void testPageListService() throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.singletonList(new Instance()));
        Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setProtectThreshold(0.75F);
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(metadata));
        
        ObjectNode obj = (ObjectNode) catalogServiceV2Impl.pageListService("A", "B", "C", 1, 10, null, false);
        assertEquals(1, obj.get(FieldsConstants.COUNT).asInt());
    }
    
    @Test
    void testPageListServiceNotSpecifiedName() throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.singletonList(new Instance()));
        Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setProtectThreshold(0.75F);
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(metadata));
        
        ObjectNode obj = (ObjectNode) catalogServiceV2Impl.pageListService("A", "", "", 1, 10, null, false);
        assertEquals(1, obj.get(FieldsConstants.COUNT).asInt());
    }
    
    @Test
    void testPageListServiceForIgnoreEmptyService() throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo();
        Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        ObjectNode obj = (ObjectNode) catalogServiceV2Impl.pageListService("A", "B", "C", 1, 10, null, true);
        assertEquals(0, obj.get(FieldsConstants.COUNT).asInt());
    }
    
    @Test
    void testPageListServiceForPage() throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo();
        Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        ServiceManager.getInstance().getSingleton(Service.newService("CatalogService", "CatalogService", "1"));
        ServiceManager.getInstance().getSingleton(Service.newService("CatalogService", "CatalogService", "2"));
        ServiceManager.getInstance().getSingleton(Service.newService("CatalogService", "CatalogService", "3"));
        
        ObjectNode obj = (ObjectNode) catalogServiceV2Impl.pageListService("CatalogService", "", "", 2, 1, null, false);
        assertEquals(3, obj.get(FieldsConstants.COUNT).asInt());
        assertEquals("2", obj.get(FieldsConstants.SERVICE_LIST).get(0).get("name").asText());
    }
    
    @Test
    void testPageListServiceDetail() {
        try {
            ServiceMetadata metadata = new ServiceMetadata();
            Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(metadata));
            
            Instance instance = new Instance();
            instance.setServiceName("C");
            instance.setClusterName("D");
            List<Instance> instances = Collections.singletonList(instance);
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setHosts(instances);
            Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
            
            List<ServiceDetailInfo> result = (List<ServiceDetailInfo>) catalogServiceV2Impl.pageListServiceDetail("A", "B", "C", 1, 10);
            
            assertEquals(1, result.size());
        } catch (NacosException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
