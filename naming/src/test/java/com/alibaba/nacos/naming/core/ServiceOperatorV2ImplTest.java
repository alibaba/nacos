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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.naming.constants.FieldsConstants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    
    @Mock
    private SubscribeManager subscribeManager;
    
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
    void testCreateDuplicateServiceThrows() {
        assertThrows(NacosApiException.class,
            () -> serviceOperatorV2.create(Service.newService("A", "B", "C"),
                new ServiceMetadata()));
    }
    
    @Test
    void testUpdate() throws NacosException {
        serviceOperatorV2.update(Service.newService("A", "B", "C"), new ServiceMetadata());
        
        Mockito.verify(metadataOperateService).updateServiceMetadata(Mockito.any(), Mockito.any());
    }
    
    @Test
    void testUpdateMissingServiceThrows() {
        assertThrows(NacosApiException.class,
            () -> serviceOperatorV2.update(Service.newService("A", "B", "missing"),
                new ServiceMetadata()));
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
    void testDeleteMissingServiceThrows() {
        assertThrows(NacosApiException.class,
            () -> serviceOperatorV2.delete(Service.newService("A", "B", "missing")));
    }
    
    @Test
    void testDeleteNotEmptyServiceThrows() {
        ServiceInfo serviceInfo = new ServiceInfo();
        Instance instance = new Instance();
        serviceInfo.setHosts(Collections.singletonList(instance));
        Mockito.when(serviceStorage.getPushData(Mockito.any())).thenReturn(serviceInfo);
        
        assertThrows(NacosApiException.class,
            () -> serviceOperatorV2.delete(Service.newService("A", "B", "C")));
    }
    
    @Test
    void testQueryService() throws NacosException {
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        Map<String, ClusterMetadata> clusterMetadataMap = new HashMap<>(2);
        clusterMetadataMap.put("D", clusterMetadata);
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setClusters(clusterMetadataMap);
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any()))
            .thenReturn(Optional.of(metadata));
        
        Mockito.when(serviceStorage.getClusters(Mockito.any()))
            .thenReturn(Collections.singleton("D"));
        
        ObjectNode objectNode = serviceOperatorV2.queryService("A", "B@@C");
        
        assertEquals("A", objectNode.get(FieldsConstants.NAME_SPACE_ID).asText());
        assertEquals("C", objectNode.get(FieldsConstants.NAME).asText());
        assertEquals(1, objectNode.get(FieldsConstants.CLUSTERS).size());
    }
    
    @Test
    void testQueryServiceUsesDefaultClusterMetadata() throws NacosException {
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any()))
            .thenReturn(Optional.of(new ServiceMetadata()));
        Mockito.when(serviceStorage.getClusters(Mockito.any()))
            .thenReturn(Collections.singleton("D"));
        
        ObjectNode objectNode = serviceOperatorV2.queryService("A", "B@@C");
        
        assertEquals("D", objectNode.get(FieldsConstants.CLUSTERS).get(0)
            .get(FieldsConstants.NAME).asText());
    }
    
    @Test
    void testQueryServiceMissingThrows() {
        assertThrows(NacosApiException.class,
            () -> serviceOperatorV2.queryService("A", "B@@missing"));
    }
    
    @Test
    void testQueryServiceDetail() throws NacosException {
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(8080);
        clusterMetadata.setUseInstancePortForCheck(false);
        clusterMetadata.getExtendData().put("zone", "z1");
        Map<String, ClusterMetadata> clusterMetadataMap = new HashMap<>(2);
        clusterMetadataMap.put("D", clusterMetadata);
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setProtectThreshold(0.5F);
        metadata.getExtendData().put("owner", "naming");
        metadata.setClusters(clusterMetadataMap);
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any()))
            .thenReturn(Optional.of(metadata));
        Mockito.when(serviceStorage.getClusters(Mockito.any()))
            .thenReturn(Collections.singleton("D"));
        
        ServiceDetailInfo serviceDetail =
            serviceOperatorV2.queryService(Service.newService("A", "B", "C"));
        
        assertEquals("A", serviceDetail.getNamespaceId());
        assertEquals("B", serviceDetail.getGroupName());
        assertEquals("C", serviceDetail.getServiceName());
        assertTrue(serviceDetail.isEphemeral());
        assertEquals(0.5F, serviceDetail.getProtectThreshold());
        assertEquals("naming", serviceDetail.getMetadata().get("owner"));
        ClusterInfo clusterInfo = serviceDetail.getClusterMap().get("D");
        assertFalse(clusterInfo.isUseInstancePortForCheck());
        assertEquals(8080, clusterInfo.getHealthyCheckPort());
        assertEquals("z1", clusterInfo.getMetadata().get("zone"));
    }
    
    @Test
    void testQueryServiceDetailUsesDefaultClusterMetadata() throws NacosException {
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any()))
            .thenReturn(Optional.of(new ServiceMetadata()));
        Mockito.when(serviceStorage.getClusters(Mockito.any()))
            .thenReturn(Collections.singleton("D"));
        
        ServiceDetailInfo serviceDetail =
            serviceOperatorV2.queryService(Service.newService("A", "B", "C"));
        
        assertTrue(serviceDetail.getClusterMap().containsKey("D"));
    }
    
    @Test
    void testQueryServiceDetailMissingThrows() {
        assertThrows(NacosApiException.class,
            () -> serviceOperatorV2.queryService(Service.newService("A", "B", "missing")));
    }
    
    @Test
    void testListService() throws NacosException {
        Collection<String> res = serviceOperatorV2.listService("A", "B", null);
        assertEquals(1, res.size());
    }
    
    @Test
    void testListServiceReturnsEmptyWhenNamespaceMissing() throws NacosException {
        Collection<String> res = serviceOperatorV2.listService("missing", "B", null);
        assertTrue(res.isEmpty());
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
    
    @Test
    void testGetSubscribers() throws NacosException {
        Subscriber subscriber =
            new Subscriber("1.1.1.1:8848", "agent", "app", "1.1.1.1", "A", "B@@C", 8848);
        Mockito.when(subscribeManager.getSubscribers(Mockito.any(), Mockito.eq(false)))
            .thenReturn(Collections.singletonList(subscriber));
        
        Page<SubscriberInfo> page = serviceOperatorV2.getSubscribers("A", "C", "B", false, 1, 10);
        
        assertEquals(1, page.getTotalCount());
        assertEquals(1, page.getPageItems().size());
        SubscriberInfo subscriberInfo = page.getPageItems().get(0);
        assertEquals("A", subscriberInfo.getNamespaceId());
        assertEquals("B", subscriberInfo.getGroupName());
        assertEquals("C", subscriberInfo.getServiceName());
        assertEquals("app", subscriberInfo.getAppName());
        assertEquals("agent", subscriberInfo.getAgent());
        assertEquals("1.1.1.1", subscriberInfo.getIp());
        assertEquals(8848, subscriberInfo.getPort());
    }
    
    @Test
    void testGetSubscribersReturnsEmptyWhenQueryFails() throws NacosException {
        Mockito.when(subscribeManager.getSubscribers(Mockito.any(), Mockito.eq(true)))
            .thenThrow(new RuntimeException("failed"));
        
        Page<SubscriberInfo> page = serviceOperatorV2.getSubscribers("A", "C", "B", true, 1, 10);
        
        assertTrue(page.getPageItems().isEmpty());
        assertEquals(0, page.getTotalCount());
    }
}
