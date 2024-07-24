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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceNameView;
import com.alibaba.nacos.naming.selector.SelectorManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceControllerV2Test {
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    private ServiceControllerV2 serviceController;
    
    private SmartSubscriber subscriber;
    
    private volatile Class<? extends Event> eventReceivedClass;
    
    @BeforeEach
    void setUp() throws Exception {
        serviceController = new ServiceControllerV2(serviceOperatorV2, selectorManager);
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
        
        Result<String> actual = serviceController.create(serviceForm);
        verify(serviceOperatorV2).create(eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    void testRemove() throws Exception {
        Result<String> actual = serviceController.remove(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP);
        verify(serviceOperatorV2).delete(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"));
        assertEquals("ok", actual.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
    }
    
    @Test
    void testDetail() throws Exception {
        ServiceDetailInfo expected = new ServiceDetailInfo();
        when(serviceOperatorV2.queryService(
                Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"))).thenReturn(expected);
        Result<ServiceDetailInfo> actual = serviceController.detail(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(expected, actual.getData());
    }
    
    @Test
    void testList() throws Exception {
        
        when(serviceOperatorV2.listService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "")).thenReturn(
                Collections.singletonList("serviceName"));
        Result<ServiceNameView> actual = serviceController.list(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "", 1, 10);
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
        Result<String> actual = serviceController.update(serviceForm);
        verify(serviceOperatorV2).update(eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                any(ServiceMetadata.class));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals("ok", actual.getData());
        TimeUnit.SECONDS.sleep(1);
        assertEquals(UpdateServiceTraceEvent.class, eventReceivedClass);
    }
}
