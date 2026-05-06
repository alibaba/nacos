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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ServiceOperatorV2Impl} unit tests.
 *
 * @author chenglu
 * @date 2021-08-04 00:06
 */
@ExtendWith(MockitoExtension.class)
class ServiceOperatorV2ImplTest {
    
    @InjectMocks
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    @Mock
    private NamingMetadataOperateService metadataOperateService;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @BeforeEach
    void setUp() throws IllegalAccessException {
        cleanNamespace();
        Service service = Service.newService("A", "B", "C");
        ServiceManager.getInstance().getSingleton(service);
    }
    
    @AfterEach
    void tearDown() throws IllegalAccessException {
        Service service = Service.newService("A", "B", "C");
        ServiceManager.getInstance().removeSingleton(service);
        cleanNamespace();
    }
    
    private void cleanNamespace() throws IllegalAccessException {
        Field field = ReflectionUtils.findField(ServiceManager.class, "namespaceSingletonMaps");
        field.setAccessible(true);
        Map map = (Map) field.get(ServiceManager.getInstance());
        map.clear();
    }
    
    @Test
    void testCreate() throws NacosException {
        serviceOperatorV2.create("A", "B", new ServiceMetadata());
        
        Mockito.verify(metadataOperateService).updateServiceMetadata(Mockito.any(), Mockito.any());
    }
    
    @Test
    void testUpdate() throws NacosException {
        serviceOperatorV2.update(Service.newService("A", "B", "C"), new ServiceMetadata());
        
        Mockito.verify(metadataOperateService).updateServiceMetadata(Mockito.any(), Mockito.any());
    }
    
    @Test
    void testDelete() throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.emptyList());
        Mockito.when(serviceStorage.getPushData(Mockito.any())).thenReturn(serviceInfo);
        
        serviceOperatorV2.delete("A", "B@@C");
        
        Mockito.verify(metadataOperateService).deleteServiceMetadata(Mockito.any());
    }
    
    @Test
    void testQueryService() throws NacosException {
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        Map<String, ClusterMetadata> clusterMetadataMap = new HashMap<>(2);
        clusterMetadataMap.put("D", clusterMetadata);
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setClusters(clusterMetadataMap);
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(metadata));
        
        Mockito.when(serviceStorage.getClusters(Mockito.any())).thenReturn(Collections.singleton("D"));
        
        ObjectNode objectNode = serviceOperatorV2.queryService("A", "B@@C");
        
        assertEquals("A", objectNode.get(FieldsConstants.NAME_SPACE_ID).asText());
        assertEquals("C", objectNode.get(FieldsConstants.NAME).asText());
        assertEquals(1, objectNode.get(FieldsConstants.CLUSTERS).size());
    }
    
    @Test
    void testListService() throws NacosException {
        Collection<String> res = serviceOperatorV2.listService("A", "B", null);
        assertEquals(1, res.size());
    }
    
    @Test
    void testListAllNamespace() {
        assertEquals(1, serviceOperatorV2.listAllNamespace().size());
    }
    
    @Test
    void testSearchServiceName() throws NacosException {
        Collection<String> res = serviceOperatorV2.searchServiceName("A", "");
        assertEquals(1, res.size());
    }
}
