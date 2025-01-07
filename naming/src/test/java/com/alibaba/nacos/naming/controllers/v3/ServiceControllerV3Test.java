/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.SubscribeManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceNameView;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ServiceControllerV3Test.
 *
 * @author Nacos
 */

@ExtendWith(MockitoExtension.class)
class ServiceControllerV3Test {
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private SubscribeManager subscribeManager;
    
    @Mock
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    private ServiceControllerV3 serviceControllerV3;
    
    private SmartSubscriber subscriber;
    
    private volatile Class<? extends Event> eventReceivedClass;
    
    @BeforeEach
    void setUp() throws Exception {
        serviceControllerV3 = new ServiceControllerV3(serviceOperatorV2, selectorManager, subscribeManager);
        subscriber = new SmartSubscriber() {
            @Override
            public List<Class<? extends Event>> subscribeTypes() {
                List<Class<? extends Event>> result = new LinkedList<>();
                result.add(UpdateServiceTraceEvent.class);
                return result;
            }
            
            @Override
            public void onEvent(Event event) {
                eventReceivedClass = event.getClass();
            }
        };
        NotifyCenter.registerSubscriber(subscriber);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        NotifyCenter.deregisterSubscriber(subscriber);
        NotifyCenter.deregisterPublisher(UpdateServiceTraceEvent.class);
        eventReceivedClass = null;
    }
    
    @Test
    void testCreate() throws Exception {
        
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        serviceForm.setServiceName("service");
        serviceForm.setGroupName(Constants.DEFAULT_GROUP);
        serviceForm.setEphemeral(true);
        serviceForm.setProtectThreshold(0.0F);
        serviceForm.setMetadata("");
        serviceForm.setSelector("");
        
        Result<String> actual = serviceControllerV3.create(serviceForm);
        verify(serviceOperatorV2).create(eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testRemove() throws Exception {
        Result<String> actual = serviceControllerV3.remove(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP);
        verify(serviceOperatorV2).delete(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"));
        assertEquals("ok", actual.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
    }
    
    @Test
    void testDetail() throws Exception {
        ServiceDetailInfo expected = new ServiceDetailInfo();
        when(serviceOperatorV2.queryService(
                Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"))).thenReturn(expected);
        Result<ServiceDetailInfo> actual = serviceControllerV3.detail(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(expected, actual.getData());
    }
    
    @Test
    void testList() throws Exception {
        
        when(serviceOperatorV2.listService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "")).thenReturn(
                Collections.singletonList("serviceName"));
        Result<ServiceNameView> actual = serviceControllerV3.list(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "", 1, 10);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(1, actual.getData().getCount());
        assertEquals(1, actual.getData().getServices().size());
        assertEquals("serviceName", actual.getData().getServices().iterator().next());
    }
    
    @Test
    void testUpdate() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        serviceForm.setGroupName(Constants.DEFAULT_GROUP);
        serviceForm.setServiceName("service");
        serviceForm.setProtectThreshold(0.0f);
        serviceForm.setMetadata("");
        serviceForm.setSelector("");
        Result<String> actual = serviceControllerV3.update(serviceForm);
        verify(serviceOperatorV2).update(eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
        TimeUnit.SECONDS.sleep(1);
        assertEquals(UpdateServiceTraceEvent.class, eventReceivedClass);
    }
    
    @Test
    void testSearchService() {
        try {
            Mockito.when(serviceOperatorV2.searchServiceName(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Collections.singletonList("result"));
            
            ObjectNode objectNode = serviceControllerV3.searchService("test-namespace", "").getData();
            assertEquals(1, objectNode.get("count").asInt());
        } catch (NacosException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        
        try {
            Mockito.when(serviceOperatorV2.searchServiceName(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(Arrays.asList("re1", "re2"));
            Mockito.when(serviceOperatorV2.listAllNamespace()).thenReturn(Arrays.asList("re1", "re2"));
            
            ObjectNode objectNode = serviceControllerV3.searchService(null, "").getData();
            assertEquals(4, objectNode.get("count").asInt());
        } catch (NacosException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testSubscribers() {
        Mockito.when(subscribeManager.getSubscribers(Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(Collections.singletonList(Mockito.mock(Subscriber.class)));
        
        ObjectNode objectNode = serviceControllerV3.subscribers("nameSpaceId", "serviceName", true, 1, 10).getData();
        assertEquals(1, objectNode.get("count").asInt());
    }
    
    @Test
    void testListSelectorTypes() {
        Mockito.when(selectorManager.getAllSelectorTypes()).thenReturn(Arrays.asList("re1", "re2"));
        Result<List<String>> result = serviceControllerV3.listSelectorTypes();
        assertEquals(Arrays.asList("re1", "re2"), result.getData());
    }
}
