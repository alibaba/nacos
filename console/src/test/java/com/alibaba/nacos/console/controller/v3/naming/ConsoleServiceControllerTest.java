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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.proxy.naming.ServiceProxy;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.model.form.ServiceListForm;
import com.alibaba.nacos.naming.model.form.UpdateClusterForm;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omg.CORBA.ServiceDetail;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        
        verify(serviceProxy).updateService(eq(serviceForm),
                eq(Service.newService("testNamespace", "testGroup", "testService")), any(ServiceMetadata.class),
                any(Map.class));
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testGetServiceDetail() throws Exception {
        ServiceDetail serviceDetail = new ServiceDetail();
        
        when(serviceProxy.getServiceDetail(any(String.class), any(String.class), any(String.class))).thenReturn(
                serviceDetail);
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setServiceName("testService");
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setGroupName("testGroup");
        Result<ServiceDetail> actual = (Result<ServiceDetail>) consoleServiceController.getServiceDetail(serviceForm);
        
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
        ObjectNode subscribers = new ObjectMapper().createObjectNode();
        subscribers.put("subscriber", "testSubscriber");
        
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
        
        Result<ObjectNode> actual = consoleServiceController.subscribers(serviceForm, pageForm, aggregationForm);
        
        verify(serviceProxy).getSubscribers(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyBoolean());
        
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(subscribers, actual.getData());
    }
    
    @Test
    void testGetServiceList() throws Exception {
        when(serviceProxy.getServiceList(anyBoolean(), anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyBoolean())).thenReturn(Collections.singletonList(new ServiceDetail()));
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
        assertInstanceOf(List.class, actual.getData());
        assertEquals(1, ((List<?>) actual.getData()).size());
    }
    
    @Test
    void testUpdateCluster() throws Exception {
        UpdateClusterForm updateClusterForm = getUpdateClusterForm();
        
        Result<String> actual = consoleServiceController.updateCluster(updateClusterForm);
        
        verify(serviceProxy).updateClusterMetadata(anyString(), anyString(), anyString(), any(ClusterMetadata.class));
        
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
