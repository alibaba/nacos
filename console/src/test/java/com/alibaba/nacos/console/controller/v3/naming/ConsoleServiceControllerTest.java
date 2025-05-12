/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.naming;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.console.proxy.naming.ServiceProxy;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.model.form.ServiceListForm;
import com.alibaba.nacos.naming.model.form.UpdateClusterForm;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConsoleServiceControllerTest.
 *
 * @author zhangyukun on:2024/9/5
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleServiceControllerTest {
    
    @Mock
    private ServiceProxy serviceProxy;
    
    @Mock
    private SelectorManager selectorManager;
    
    @InjectMocks
    private ConsoleServiceController consoleServiceController;
    
    @BeforeEach
    void setUp() {
        consoleServiceController = new ConsoleServiceController(serviceProxy, selectorManager);
    }
    
    @Test
    void testCreateService() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        serviceForm.setProtectThreshold(0.8f);
        serviceForm.setEphemeral(true);
        serviceForm.setMetadata("{\"key\":\"value\"}");
        serviceForm.setSelector("{\"type\":\"label\",\"expression\":\"role=admin\"}");
        
        when(selectorManager.parseSelector(any(String.class), any(String.class))).thenReturn(new LabelSelector());
        
        Result<String> actual = consoleServiceController.createService(serviceForm);
        
        verify(serviceProxy).createService(eq(serviceForm), any(ServiceMetadata.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testCreateServiceWithoutSelector() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        serviceForm.setProtectThreshold(0.8f);
        serviceForm.setEphemeral(true);
        serviceForm.setMetadata("{\"key\":\"value\"}");
        
        Result<String> actual = consoleServiceController.createService(serviceForm);
        verify(serviceProxy).createService(eq(serviceForm), any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testCreateServiceWithoutSelectorType() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        serviceForm.setProtectThreshold(0.8f);
        serviceForm.setEphemeral(true);
        serviceForm.setMetadata("{\"key\":\"value\"}");
        serviceForm.setSelector("{\"expression\":\"role=admin\"}");
        assertThrows(NacosApiException.class, () -> consoleServiceController.createService(serviceForm));
    }
    
    @Test
    void testCreateServiceNotFoundSelector() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        serviceForm.setProtectThreshold(0.8f);
        serviceForm.setEphemeral(true);
        serviceForm.setMetadata("{\"key\":\"value\"}");
        serviceForm.setSelector("{\"type\":\"non-exist\"}");
        assertThrows(NacosApiException.class, () -> consoleServiceController.createService(serviceForm));
    }
    
    @Test
    void testDeleteService() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setServiceName("testService");
        serviceForm.setGroupName("testGroup");
        Result<String> actual = consoleServiceController.deleteService(serviceForm);
        
        verify(serviceProxy).deleteService(eq("testNamespace"), eq("testService"), eq("testGroup"));
        
        assertEquals("ok", actual.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
    }
    
    @Test
    void testUpdateService() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        serviceForm.setProtectThreshold(0.8f);
        serviceForm.setEphemeral(true);
        serviceForm.setMetadata("{\"key\":\"value\"}");
        serviceForm.setSelector("{\"type\":\"label\",\"expression\":\"role=admin\"}");
        
        when(selectorManager.parseSelector(any(String.class), any(String.class))).thenReturn(new LabelSelector());
        
        Result<String> actual = consoleServiceController.updateService(serviceForm);
        
        verify(serviceProxy).updateService(eq(serviceForm), any(ServiceMetadata.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testGetServiceDetail() throws Exception {
        ServiceDetailInfo serviceDetail = new ServiceDetailInfo();
        serviceDetail.setNamespaceId("testNamespace");
        serviceDetail.setServiceName("testService");
        serviceDetail.setGroupName("testGroup");
        serviceDetail.setClusterMap(Collections.emptyMap());
        when(serviceProxy.getServiceDetail(any(String.class), any(String.class), any(String.class))).thenReturn(
                serviceDetail);
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        Result<ServiceDetailInfo> actual = consoleServiceController.getServiceDetail(serviceForm);
        
        verify(serviceProxy).getServiceDetail(any(String.class), any(String.class), any(String.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(serviceDetail, actual.getData());
    }
    
    @Test
    void testGetSelectorTypeList() throws Exception {
        when(serviceProxy.getSelectorTypeList()).thenReturn(Collections.singletonList("label"));
        
        Result<List<String>> actual = consoleServiceController.getSelectorTypeList();
        
        verify(serviceProxy).getSelectorTypeList();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(1, actual.getData().size());
        assertEquals("label", actual.getData().get(0));
    }
    
    @Test
    void testGetSubscribers() throws Exception {
        Page<SubscriberInfo> subscribers = new Page<>();
        subscribers.setTotalCount(1);
        subscribers.setPagesAvailable(1);
        subscribers.setPageItems(Collections.singletonList(new SubscriberInfo()));
        subscribers.setPageNumber(1);
        subscribers.getPageItems().get(0).setNamespaceId("testNamespace");
        subscribers.getPageItems().get(0).setServiceName("testService");
        subscribers.getPageItems().get(0).setGroupName("testGroup");
        when(serviceProxy.getSubscribers(anyInt(), anyInt(), anyString(), anyString(), anyString(),
                anyBoolean())).thenReturn(subscribers);
        
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setServiceName("testService");
        serviceForm.setGroupName("testGroup");
        AggregationForm aggregationForm = new AggregationForm();
        
        Result<Page<SubscriberInfo>> actual = consoleServiceController.subscribers(serviceForm, pageForm,
                aggregationForm);
        
        verify(serviceProxy).getSubscribers(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyBoolean());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(1, actual.getData().getTotalCount());
        assertEquals(1, actual.getData().getPageItems().size());
        assertEquals("testGroup", actual.getData().getPageItems().get(0).getGroupName());
        assertEquals("testService", actual.getData().getPageItems().get(0).getServiceName());
        assertEquals("testNamespace", actual.getData().getPageItems().get(0).getNamespaceId());
    }
    
    @Test
    void testGetServiceList() throws Exception {
        Page<ServiceView> expected = new Page<>();
        expected.setTotalCount(1);
        expected.getPageItems().add(new ServiceView());
        when(serviceProxy.getServiceList(anyBoolean(), anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyBoolean())).thenReturn(expected);
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        ServiceListForm serviceForm = new ServiceListForm();
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setServiceNameParam("testService");
        serviceForm.setGroupNameParam("testGroup");
        Result<Object> actual = consoleServiceController.getServiceList(serviceForm, pageForm);
        
        verify(serviceProxy).getServiceList(anyBoolean(), anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyBoolean());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertInstanceOf(Page.class, actual.getData());
        assertEquals(1, ((Page<?>) actual.getData()).getPageItems().size());
    }
    
    @Test
    void testUpdateCluster() throws Exception {
        UpdateClusterForm updateClusterForm = getUpdateClusterForm();
        
        Result<String> actual = consoleServiceController.updateCluster(updateClusterForm);
        
        verify(serviceProxy).updateClusterMetadata(anyString(), anyString(), anyString(), anyString(),
                any(ClusterMetadata.class));
        
        assertEquals("ok", actual.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
    }
    
    private static UpdateClusterForm getUpdateClusterForm() {
        UpdateClusterForm updateClusterForm = new UpdateClusterForm();
        updateClusterForm.setNamespaceId("testNamespace");
        updateClusterForm.setGroupName("testGroup");
        updateClusterForm.setClusterName("testCluster");
        updateClusterForm.setServiceName("testService");
        updateClusterForm.setCheckPort(8080);
        updateClusterForm.setUseInstancePort4Check(true);
        updateClusterForm.setHealthChecker("{\"type\":\"TCP\"}");
        updateClusterForm.setMetadata("{\"key\":\"value\"}");
        return updateClusterForm;
    }
}
