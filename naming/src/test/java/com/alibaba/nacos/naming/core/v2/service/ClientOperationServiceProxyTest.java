/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOperationServiceProxyTest {
    
    private final String ephemeralIpPortId = System.currentTimeMillis() + "127.0.0.1:80#true";
    
    private final String persistentIpPortId = System.currentTimeMillis() + "127.0.0.1:80#false";
    
    @Mock
    private EphemeralClientOperationServiceImpl ephemeralClientOperationServiceImpl;
    
    @Mock
    private PersistentClientOperationServiceImpl persistentClientOperationServiceImpl;
    
    private ClientOperationServiceProxy clientOperationServiceProxy;
    
    @Mock
    private Service service;
    
    @Mock
    private Instance ephemeralInstance;
    
    @Mock
    private Instance persistentInstance;
    
    @Mock
    private Subscriber subscriber;
    
    @BeforeEach
    void setUp() throws Exception {
        clientOperationServiceProxy = new ClientOperationServiceProxy(ephemeralClientOperationServiceImpl,
                persistentClientOperationServiceImpl);
        when(ephemeralInstance.isEphemeral()).thenReturn(true);
        when(persistentInstance.isEphemeral()).thenReturn(false);
        when(service.getNamespace()).thenReturn("public");
    }
    
    @Test
    void testChooseEphemeralClientOperationService() throws NacosException {
        // Test register.
        clientOperationServiceProxy.registerInstance(service, ephemeralInstance, ephemeralIpPortId);
        verify(ephemeralClientOperationServiceImpl).registerInstance(service, ephemeralInstance, ephemeralIpPortId);
        verify(persistentClientOperationServiceImpl, never()).registerInstance(service, ephemeralInstance, ephemeralIpPortId);
        // Before service is registered.
        clientOperationServiceProxy.deregisterInstance(service, ephemeralInstance, ephemeralIpPortId);
        verify(ephemeralClientOperationServiceImpl, never()).deregisterInstance(service, ephemeralInstance, ephemeralIpPortId);
        verify(persistentClientOperationServiceImpl, never()).deregisterInstance(service, ephemeralInstance, ephemeralIpPortId);
        
        ServiceManager.getInstance().getSingleton(service);
        // Test deregister.
        clientOperationServiceProxy.deregisterInstance(service, ephemeralInstance, ephemeralIpPortId);
        verify(ephemeralClientOperationServiceImpl).deregisterInstance(service, ephemeralInstance, ephemeralIpPortId);
        verify(persistentClientOperationServiceImpl, never()).deregisterInstance(service, ephemeralInstance, ephemeralIpPortId);
    }
    
    @Test
    void testChoosePersistentClientOperationService() throws NacosException {
        clientOperationServiceProxy.registerInstance(service, persistentInstance, persistentIpPortId);
        verify(persistentClientOperationServiceImpl).registerInstance(service, persistentInstance, persistentIpPortId);
        verify(ephemeralClientOperationServiceImpl, never()).registerInstance(service, persistentInstance, persistentIpPortId);
        ServiceManager.getInstance().getSingleton(service);
        // Test deregister.
        clientOperationServiceProxy.deregisterInstance(service, persistentInstance, persistentIpPortId);
        verify(persistentClientOperationServiceImpl).deregisterInstance(service, persistentInstance, persistentIpPortId);
        verify(ephemeralClientOperationServiceImpl, never()).deregisterInstance(service, persistentInstance, persistentIpPortId);
    }
    
    @Test
    void testSubscribeService() {
        clientOperationServiceProxy.subscribeService(service, subscriber, ephemeralIpPortId);
        verify(ephemeralClientOperationServiceImpl).subscribeService(service, subscriber, ephemeralIpPortId);
        verify(persistentClientOperationServiceImpl, never()).subscribeService(service, subscriber, ephemeralIpPortId);
    }
    
    @Test
    void testUnsubscribeService() {
        clientOperationServiceProxy.unsubscribeService(service, subscriber, ephemeralIpPortId);
        verify(ephemeralClientOperationServiceImpl).unsubscribeService(service, subscriber, ephemeralIpPortId);
        verify(persistentClientOperationServiceImpl, never()).unsubscribeService(service, subscriber, ephemeralIpPortId);
    }
}