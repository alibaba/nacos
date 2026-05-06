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

package com.alibaba.nacos.console.proxy.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.console.handler.naming.ServiceHandler;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ServiceProxyTest {
    
    private static final String NAMESPACE_ID = "namespaceId";
    
    private static final String SERVICE_NAME = "serviceName";
    
    private static final String GROUP_NAME = "groupName";
    
    @Mock
    private ServiceHandler serviceHandler;
    
    private ServiceProxy serviceProxy;
    
    private ServiceForm serviceForm;
    
    private ServiceMetadata serviceMetadata;
    
    @BeforeEach
    public void setUp() {
        serviceProxy = new ServiceProxy(serviceHandler);
        serviceForm = new ServiceForm();
        serviceMetadata = new ServiceMetadata();
    }
    
    @Test
    public void createService() throws Exception {
        assertDoesNotThrow(() -> serviceProxy.createService(serviceForm, serviceMetadata));
        verify(serviceHandler).createService(serviceForm, serviceMetadata);
    }
    
    @Test
    public void updateService() throws Exception {
        doNothing().when(serviceHandler).updateService(serviceForm, serviceMetadata);
        serviceProxy.updateService(serviceForm, serviceMetadata);
    }
    
    @Test
    public void deleteService() throws Exception {
        doNothing().when(serviceHandler).deleteService(NAMESPACE_ID, SERVICE_NAME, GROUP_NAME);
        serviceProxy.deleteService(NAMESPACE_ID, SERVICE_NAME, GROUP_NAME);
    }
    
    @Test
    public void getSelectorTypeList() throws NacosException {
        List<String> expectedSelectorTypes = Arrays.asList("type1", "type2");
        when(serviceHandler.getSelectorTypeList()).thenReturn(expectedSelectorTypes);
        List<String> actualSelectorTypes = serviceProxy.getSelectorTypeList();
        assertEquals(expectedSelectorTypes, actualSelectorTypes,
                "The selector type list should match the expected list.");
    }
    
    @Test
    public void getServiceList() throws NacosException {
        Object expectedServiceList = new Object();
        when(serviceHandler.getServiceList(anyBoolean(), anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyBoolean())).thenReturn(expectedServiceList);
        Object actualServiceList = serviceProxy.getServiceList(true, "namespaceId", 1, 10, "serviceName", "groupName",
                true);
        assertEquals(expectedServiceList, actualServiceList);
    }
    
    @Test
    public void getSubscribers() throws Exception {
        Page<SubscriberInfo> expectedPage = new Page<>();
        when(serviceHandler.getSubscribers(anyInt(), anyInt(), anyString(), anyString(), anyString(),
                anyBoolean())).thenReturn(expectedPage);
        
        Page<SubscriberInfo> result = serviceProxy.getSubscribers(1, 10, "namespaceId", "serviceName", "groupName",
                true);
        
        assertNotNull(result);
        assertEquals(expectedPage, result);
        verify(serviceHandler, times(1)).getSubscribers(1, 10, "namespaceId", "serviceName", "groupName", true);
    }
    
    @Test
    public void getServiceDetail() throws NacosException {
        ServiceDetailInfo expectedInfo = new ServiceDetailInfo();
        when(serviceHandler.getServiceDetail(NAMESPACE_ID, SERVICE_NAME, GROUP_NAME)).thenReturn(expectedInfo);
        
        ServiceDetailInfo actualInfo = serviceProxy.getServiceDetail(NAMESPACE_ID, SERVICE_NAME, GROUP_NAME);
        
        assertEquals(expectedInfo, actualInfo);
        verify(serviceHandler, times(1)).getServiceDetail(NAMESPACE_ID, SERVICE_NAME, GROUP_NAME);
    }
    
    @Test
    public void updateClusterMetadata() throws Exception {
        String namespaceId = "testNamespace";
        String groupName = "testGroup";
        String serviceName = "testService";
        String clusterName = "testCluster";
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        doNothing().when(serviceHandler)
                .updateClusterMetadata(namespaceId, groupName, serviceName, clusterName, clusterMetadata);
        serviceProxy.updateClusterMetadata(namespaceId, groupName, serviceName, clusterName, clusterMetadata);
    }
    
}
