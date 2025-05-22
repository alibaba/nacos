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
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.model.form.ServiceListForm;
import com.alibaba.nacos.naming.selector.SelectorManager;
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
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    @Mock
    private CatalogServiceV2Impl catalogServiceV2;
    
    private ServiceControllerV3 serviceControllerV3;
    
    private SmartSubscriber subscriber;
    
    private volatile Class<? extends Event> eventReceivedClass;
    
    @BeforeEach
    void setUp() throws Exception {
        serviceControllerV3 = new ServiceControllerV3(serviceOperatorV2, selectorManager, catalogServiceV2);
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
        verify(serviceOperatorV2).create(
                eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testRemove() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        serviceForm.setGroupName(Constants.DEFAULT_GROUP);
        serviceForm.setServiceName("service");
        Result<String> actual = serviceControllerV3.remove(serviceForm);
        verify(serviceOperatorV2).delete(
                Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"));
        assertEquals("ok", actual.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
    }
    
    @Test
    void testDetail() throws Exception {
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        serviceForm.setGroupName(Constants.DEFAULT_GROUP);
        serviceForm.setServiceName("service");
        ServiceDetailInfo expected = new ServiceDetailInfo();
        when(serviceOperatorV2.queryService(
                Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"))).thenReturn(
                expected);
        Result<ServiceDetailInfo> actual = serviceControllerV3.detail(serviceForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(expected, actual.getData());
    }
    
    @Test
    void testList() throws Exception {
        Page<ServiceView> result = new Page<>();
        result.getPageItems().add(new ServiceView());
        when(catalogServiceV2.listService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "serviceName", 1, 10,
                false)).thenReturn(result);
        ServiceListForm serviceListForm = new ServiceListForm();
        serviceListForm.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        serviceListForm.setGroupNameParam(Constants.DEFAULT_GROUP);
        serviceListForm.setServiceNameParam("serviceName");
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        
        Result<Object> actual = serviceControllerV3.list(serviceListForm, pageForm);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
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
        verify(serviceOperatorV2).update(
                eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
        TimeUnit.SECONDS.sleep(3);
        assertEquals(UpdateServiceTraceEvent.class, eventReceivedClass);
    }
    
    @Test
    void testSubscribers() throws Exception {
        Page<SubscriberInfo> subscribers = new Page<>();
        subscribers.setTotalCount(1);
        subscribers.setPagesAvailable(1);
        subscribers.setPageItems(Collections.singletonList(new SubscriberInfo()));
        subscribers.setPageNumber(1);
        subscribers.getPageItems().get(0).setNamespaceId("testNamespace");
        subscribers.getPageItems().get(0).setServiceName("testService");
        subscribers.getPageItems().get(0).setGroupName("testGroup");
        Mockito.when(serviceOperatorV2.getSubscribers("testNamespace", "testService", "testGroup", true, 1, 10))
                .thenReturn(subscribers);
        
        ServiceForm serviceForm = new ServiceForm();
        serviceForm.setNamespaceId("testNamespace");
        serviceForm.setServiceName("testService");
        serviceForm.setGroupName("testGroup");
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        AggregationForm aggregationForm = new AggregationForm();
        aggregationForm.setAggregation(true);
        Page<SubscriberInfo> actual = serviceControllerV3.subscribers(serviceForm, pageForm, aggregationForm).getData();
        assertEquals(subscribers, actual);
    }
    
    @Test
    void testListSelectorTypes() {
        Mockito.when(selectorManager.getAllSelectorTypes()).thenReturn(Arrays.asList("re1", "re2"));
        Result<List<String>> result = serviceControllerV3.listSelectorTypes();
        assertEquals(Arrays.asList("re1", "re2"), result.getData());
    }
}
