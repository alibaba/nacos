/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.inner.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.selector.SelectorManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceInnerHandlerTest {
    
    private static final String TEST_NAMESPACE_ID = "testNamespaceId";
    
    private static final String TEST_GROUP_NAME = "testGroupName";
    
    private static final String TEST_SERVICE_NAME = "testServiceName";
    
    @Mock
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private CatalogServiceV2Impl catalogServiceV2;
    
    @Mock
    private ClusterOperatorV2Impl clusterOperatorV2;
    
    ServiceInnerHandler serviceInnerHandler;
    
    @BeforeEach
    void setUp() {
        serviceInnerHandler = new ServiceInnerHandler(serviceOperatorV2, selectorManager, catalogServiceV2,
                clusterOperatorV2);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void createService() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(TEST_NAMESPACE_ID);
        serviceForm.setGroupName(TEST_GROUP_NAME);
        serviceForm.setServiceName(TEST_SERVICE_NAME);
        serviceForm.setEphemeral(true);
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceInnerHandler.createService(serviceForm, serviceMetadata);
        verify(serviceOperatorV2).create(any(Service.class), any(ServiceMetadata.class));
    }
    
    @Test
    void deleteService() throws Exception {
        serviceInnerHandler.deleteService(TEST_NAMESPACE_ID, TEST_SERVICE_NAME, TEST_GROUP_NAME);
        verify(serviceOperatorV2).delete(any(Service.class));
    }
    
    @Test
    void updateServiceNonExist() {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(TEST_NAMESPACE_ID);
        serviceForm.setGroupName(TEST_GROUP_NAME);
        serviceForm.setServiceName(TEST_SERVICE_NAME);
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        assertThrows(NacosApiException.class, () -> serviceInnerHandler.updateService(serviceForm, serviceMetadata));
    }
    
    @Test
    void updateServiceExist() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(TEST_NAMESPACE_ID);
        serviceForm.setGroupName(TEST_GROUP_NAME);
        serviceForm.setServiceName(TEST_SERVICE_NAME);
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        ServiceManager.getInstance()
                .getSingleton(Service.newService(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME));
        serviceInnerHandler.updateService(serviceForm, serviceMetadata);
        serviceOperatorV2.update(any(Service.class), any(ServiceMetadata.class));
    }
    
    @Test
    void getSelectorTypeList() throws NacosException {
        List<String> mock = Collections.singletonList("mock");
        when(selectorManager.getAllSelectorTypes()).thenReturn(mock);
        List<String> actual = serviceInnerHandler.getSelectorTypeList();
        assertEquals(mock, actual);
    }
    
    @Test
    void getSubscribers() throws Exception {
        Page<SubscriberInfo> mockPage = new Page<>();
        when(serviceOperatorV2.getSubscribers(eq(TEST_NAMESPACE_ID), eq(TEST_SERVICE_NAME), eq(TEST_GROUP_NAME),
                eq(true), eq(1), eq(10))).thenReturn(mockPage);
        Page<SubscriberInfo> actual = serviceInnerHandler.getSubscribers(1, 10, TEST_NAMESPACE_ID, TEST_SERVICE_NAME,
                TEST_GROUP_NAME, true);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getServiceListWithInstances() throws NacosException {
        Page<ServiceDetailInfo> mockPage = new Page<>();
        when(catalogServiceV2.pageListServiceDetail(eq(TEST_NAMESPACE_ID), eq(TEST_GROUP_NAME), eq(TEST_SERVICE_NAME),
                eq(1), eq(10))).thenReturn(mockPage);
        Page<ServiceDetailInfo> actual = (Page<ServiceDetailInfo>) serviceInnerHandler.getServiceList(true,
                TEST_NAMESPACE_ID, 1, 10, TEST_SERVICE_NAME, TEST_GROUP_NAME, false);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getServiceListWithoutInstances() throws NacosException {
        Page<ServiceView> mockPage = new Page<>();
        when(catalogServiceV2.listService(eq(TEST_NAMESPACE_ID), eq(TEST_GROUP_NAME), eq(TEST_SERVICE_NAME), eq(1),
                eq(10), eq(false))).thenReturn(mockPage);
        Page<ServiceView> actual = (Page<ServiceView>) serviceInnerHandler.getServiceList(false, TEST_NAMESPACE_ID, 1,
                10, TEST_SERVICE_NAME, TEST_GROUP_NAME, false);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getServiceDetail() throws NacosException {
        ServiceDetailInfo mock = new ServiceDetailInfo();
        when(catalogServiceV2.getServiceDetail(eq(TEST_NAMESPACE_ID), eq(TEST_GROUP_NAME),
                eq(TEST_SERVICE_NAME))).thenReturn(mock);
        ServiceDetailInfo actual = serviceInnerHandler.getServiceDetail(TEST_NAMESPACE_ID, TEST_SERVICE_NAME,
                TEST_GROUP_NAME);
        assertEquals(mock, actual);
    }
    
    @Test
    void updateClusterMetadata() throws Exception {
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        serviceInnerHandler.updateClusterMetadata(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME,
                Constants.DEFAULT_CLUSTER_NAME, clusterMetadata);
        verify(clusterOperatorV2).updateClusterMetadata(eq(TEST_NAMESPACE_ID), eq(TEST_GROUP_NAME),
                eq(TEST_SERVICE_NAME), eq(Constants.DEFAULT_CLUSTER_NAME), eq(clusterMetadata));
    }
}