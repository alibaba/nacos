/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.service.impl;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PersistentClientOperationServiceImplTest {
    
    private PersistentClientOperationServiceImpl persistentClientOperationServiceImpl;
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private PersistentIpPortClientManager clientManager;
    
    @Mock
    private Service service;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private Instance instance;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    private final String clientId = "1.1.1.1:80#false";
    
    @Mock
    private Serializer serializer;
    
    @Before
    public void setUp() throws Exception {
        when(applicationContext.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(serializer.serialize(any(PersistentClientOperationServiceImpl.InstanceStoreRequest.class)))
                .thenReturn(new byte[1]);
        ApplicationUtils.injectContext(applicationContext);
        Field serializerField = PersistentClientOperationServiceImpl.class.getDeclaredField("serializer");
        serializerField.setAccessible(true);
        clientManager = new PersistentIpPortClientManager();
        persistentClientOperationServiceImpl = new PersistentClientOperationServiceImpl(clientManager);
        serializerField.set(persistentClientOperationServiceImpl, serializer);
    }
    
    @Test
    public void testRegisterAndDeregisterInstance() throws Exception {
        Field clientManagerField = PersistentClientOperationServiceImpl.class.getDeclaredField("clientManager");
        clientManagerField.setAccessible(true);
        // Test register instance
        persistentClientOperationServiceImpl.registerInstance(service, instance, clientId);
        verify(cpProtocol).write(any(WriteRequest.class));
        // Test deregister instance
        persistentClientOperationServiceImpl.deregisterInstance(service, instance, clientId);
        verify(cpProtocol, times(2)).write(any(WriteRequest.class));
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testSubscribeService() {
        persistentClientOperationServiceImpl.subscribeService(service, subscriber, clientId);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testUnsubscribeService() {
        persistentClientOperationServiceImpl.unsubscribeService(service, subscriber, clientId);
    }
}