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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SubscribeServiceRequestHandler} unit tests.
 *
 * @author chenglu
 * @date 2021-09-18 18:25
 */
@ExtendWith(MockitoExtension.class)
class SubscribeServiceRequestHandlerTest {
    
    @InjectMocks
    private SubscribeServiceRequestHandler subscribeServiceRequestHandler;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    @Mock
    private NamingMetadataManager metadataManager;
    
    @Mock
    private EphemeralClientOperationServiceImpl clientOperationService;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private SelectorManager selectorManager;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils applicationUtils = new ApplicationUtils();
        applicationUtils.initialize(applicationContext);
        Mockito.when(applicationContext.getBean(SelectorManager.class)).thenReturn(selectorManager);
    }
    
    @Test
    void testHandle() throws NacosException {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        List<Instance> instances = Arrays.asList(instance);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setGroupName("A");
        serviceInfo.setGroupName("B");
        serviceInfo.setName("C");
        serviceInfo.setHosts(instances);
        Mockito.when(serviceStorage.getData(Mockito.any())).thenReturn(serviceInfo);
        
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        Mockito.when(metadataManager.getServiceMetadata(Mockito.any())).thenReturn(Optional.of(serviceMetadata));
        
        SubscribeServiceRequest subscribeServiceRequest = new SubscribeServiceRequest();
        subscribeServiceRequest.setNamespace("A");
        subscribeServiceRequest.setGroupName("B");
        subscribeServiceRequest.setServiceName("C");
        subscribeServiceRequest.setSubscribe(true);
        SubscribeServiceResponse subscribeServiceResponse = subscribeServiceRequestHandler.handle(subscribeServiceRequest,
                new RequestMeta());
        assertEquals("C", subscribeServiceResponse.getServiceInfo().getName());
        Mockito.verify(clientOperationService).subscribeService(Mockito.any(), Mockito.any(), Mockito.anyString());
        
        subscribeServiceRequest.setSubscribe(false);
        subscribeServiceResponse = subscribeServiceRequestHandler.handle(subscribeServiceRequest, new RequestMeta());
        assertEquals("C", subscribeServiceResponse.getServiceInfo().getName());
        Mockito.verify(clientOperationService).subscribeService(Mockito.any(), Mockito.any(), Mockito.anyString());
    }
}
