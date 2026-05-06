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

package com.alibaba.nacos.console.handler.impl.remote.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    private static final String TEST_NAMESPACE_ID = "testNamespaceId";
    
    private static final String TEST_GROUP_NAME = "testGroupName";
    
    private static final String TEST_SERVICE_NAME = "testServiceName";
    
    ServiceRemoteHandler serviceRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        serviceRemoteHandler = new ServiceRemoteHandler(clientHolder);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void createService() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName(TEST_SERVICE_NAME);
        serviceForm.setGroupName(TEST_GROUP_NAME);
        serviceForm.setNamespaceId(TEST_NAMESPACE_ID);
        serviceForm.validate();
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceRemoteHandler.createService(serviceForm, serviceMetadata);
        verify(namingMaintainerService).createService(any(Service.class));
    }
    
    @Test
    void deleteService() throws Exception {
        serviceRemoteHandler.deleteService(TEST_NAMESPACE_ID, TEST_SERVICE_NAME, TEST_GROUP_NAME);
        verify(namingMaintainerService).removeService(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME);
    }
    
    @Test
    void updateService() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName(TEST_SERVICE_NAME);
        serviceForm.setGroupName(TEST_GROUP_NAME);
        serviceForm.setNamespaceId(TEST_NAMESPACE_ID);
        serviceForm.validate();
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceRemoteHandler.updateService(serviceForm, serviceMetadata);
        verify(namingMaintainerService).updateService(any(Service.class));
    }
    
    @Test
    void getSelectorTypeList() throws NacosException {
        List<String> selectorTypeList = Collections.singletonList("Mock");
        when(namingMaintainerService.listSelectorTypes()).thenReturn(selectorTypeList);
        List<String> actual = serviceRemoteHandler.getSelectorTypeList();
        assertEquals(selectorTypeList, actual);
    }
    
    @Test
    void getSubscribers() throws Exception {
        Page<SubscriberInfo> mockPage = new Page<>();
        mockPage.setTotalCount(1);
        mockPage.setPagesAvailable(1);
        mockPage.setPageNumber(1);
        mockPage.setPageItems(Collections.singletonList(new SubscriberInfo()));
        when(namingMaintainerService.getSubscribers(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, 1, 1,
                true)).thenReturn(mockPage);
        Page<SubscriberInfo> actual = serviceRemoteHandler.getSubscribers(1, 1, TEST_NAMESPACE_ID, TEST_SERVICE_NAME,
                TEST_GROUP_NAME, true);
        assertEquals(mockPage.getPageNumber(), actual.getPageNumber());
        assertEquals(mockPage.getPagesAvailable(), actual.getPagesAvailable());
        assertEquals(mockPage.getTotalCount(), actual.getTotalCount());
        assertEquals(mockPage.getPageItems().size(), actual.getPageItems().size());
    }
    
    @Test
    void getServiceListWithInstances() throws NacosException {
        Page<ServiceDetailInfo> mockPage = new Page<>();
        when(namingMaintainerService.listServicesWithDetail(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, 1,
                1)).thenReturn(mockPage);
        Page<ServiceDetailInfo> actual = (Page<ServiceDetailInfo>) serviceRemoteHandler.getServiceList(true,
                TEST_NAMESPACE_ID, 1, 1, TEST_SERVICE_NAME, TEST_GROUP_NAME, false);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getServiceListWithoutInstance() throws NacosException {
        Page<ServiceView> mockPage = new Page<>();
        when(namingMaintainerService.listServices(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME, false, 1,
                1)).thenReturn(mockPage);
        Page<ServiceDetailInfo> actual = (Page<ServiceDetailInfo>) serviceRemoteHandler.getServiceList(false,
                TEST_NAMESPACE_ID, 1, 1, TEST_SERVICE_NAME, TEST_GROUP_NAME, false);
        assertEquals(mockPage, actual);
    }
    
    @Test
    void getServiceDetail() throws NacosException {
        ServiceDetailInfo mockServiceDetailInfo = new ServiceDetailInfo();
        when(namingMaintainerService.getServiceDetail(TEST_NAMESPACE_ID, TEST_GROUP_NAME,
                TEST_SERVICE_NAME)).thenReturn(mockServiceDetailInfo);
        ServiceDetailInfo actual = serviceRemoteHandler.getServiceDetail(TEST_NAMESPACE_ID, TEST_SERVICE_NAME,
                TEST_GROUP_NAME);
        assertEquals(mockServiceDetailInfo, actual);
    }
    
    @Test
    void updateClusterMetadata() throws Exception {
        serviceRemoteHandler.updateClusterMetadata(TEST_NAMESPACE_ID, TEST_GROUP_NAME, TEST_SERVICE_NAME,
                Constants.DEFAULT_CLUSTER_NAME, new ClusterMetadata());
        verify(namingMaintainerService).updateCluster(any(Service.class), any(ClusterInfo.class));
    }
}