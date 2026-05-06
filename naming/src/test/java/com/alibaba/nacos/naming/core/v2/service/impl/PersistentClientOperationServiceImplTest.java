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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.PersistentIpPortClientManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class PersistentClientOperationServiceImplTest {
    
    private final String clientId = "1.1.1.1:80#false";
    
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
    
    @Mock
    private Serializer serializer;
    
    @Mock
    private IpPortBasedClient ipPortBasedClient;
    
    @BeforeEach
    void setUp() throws Exception {
        when(service.getNamespace()).thenReturn("n");
        when(applicationContext.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(serializer.serialize(any(PersistentClientOperationServiceImpl.InstanceStoreRequest.class))).thenReturn(new byte[1]);
        ApplicationUtils.injectContext(applicationContext);
        Field serializerField = PersistentClientOperationServiceImpl.class.getDeclaredField("serializer");
        serializerField.setAccessible(true);
        persistentClientOperationServiceImpl = new PersistentClientOperationServiceImpl(clientManager);
        serializerField.set(persistentClientOperationServiceImpl, serializer);
        
    }
    
    @Test
    void testRegisterPersistentInstance() {
        assertThrows(NacosRuntimeException.class, () -> {
            when(service.isEphemeral()).thenReturn(true);
            persistentClientOperationServiceImpl.registerInstance(service, instance, clientId);
        });
    }
    
    @Test
    void testRegisterAndDeregisterInstance() throws Exception {
        Field clientManagerField = PersistentClientOperationServiceImpl.class.getDeclaredField("clientManager");
        clientManagerField.setAccessible(true);
        // Test register instance
        persistentClientOperationServiceImpl.registerInstance(service, instance, clientId);
        verify(cpProtocol).write(any(WriteRequest.class));
        // Test deregister instance
        persistentClientOperationServiceImpl.deregisterInstance(service, instance, clientId);
        verify(cpProtocol, times(2)).write(any(WriteRequest.class));
    }
    
    @Test
    void updateInstance() throws Exception {
        Field clientManagerField = PersistentClientOperationServiceImpl.class.getDeclaredField("clientManager");
        clientManagerField.setAccessible(true);
        // Test register instance
        persistentClientOperationServiceImpl.updateInstance(service, instance, clientId);
        verify(cpProtocol).write(any(WriteRequest.class));
    }
    
    @Test
    void testSubscribeService() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentClientOperationServiceImpl.subscribeService(service, subscriber, clientId);
        });
    }
    
    @Test
    void testUnsubscribeService() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentClientOperationServiceImpl.unsubscribeService(service, subscriber, clientId);
        });
    }
    
    @Test
    void testOnRequest() {
        assertThrows(UnsupportedOperationException.class, () -> {
            persistentClientOperationServiceImpl.onRequest(ReadRequest.newBuilder().build());
        });
    }
    
    @Test
    void testOnApply() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request = new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId("xxxx");
        request.setInstance(new Instance());
        
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        
        Mockito.when(clientManager.contains(Mockito.anyString())).thenReturn(true);
        
        IpPortBasedClient ipPortBasedClient = Mockito.mock(IpPortBasedClient.class);
        Mockito.when(clientManager.getClient(Mockito.anyString())).thenReturn(ipPortBasedClient);
        
        WriteRequest writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build();
        Response response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        assertTrue(ServiceManager.getInstance().containSingleton(service1));
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.DELETE.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        ServiceManager.getInstance().removeSingleton(service1);
        
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.VERIFY.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertFalse(response.getSuccess());
        
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.CHANGE.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        assertFalse(ServiceManager.getInstance().containSingleton(service1));
    }
    
    @Test
    void testOnApplyChange() {
        PersistentClientOperationServiceImpl.InstanceStoreRequest request = new PersistentClientOperationServiceImpl.InstanceStoreRequest();
        Service service1 = Service.newService("A", "B", "C");
        request.setService(service1);
        request.setClientId("xxxx");
        request.setInstance(new Instance());
        Mockito.when(serializer.deserialize(Mockito.any())).thenReturn(request);
        Mockito.when(clientManager.contains(Mockito.anyString())).thenReturn(true);
        when(clientManager.getClient(Mockito.anyString())).thenReturn(ipPortBasedClient);
        when(ipPortBasedClient.getAllPublishedService()).thenReturn(Collections.singletonList(service1));
        WriteRequest writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build();
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.ADD.name()).build();
        Response response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        writeRequest = WriteRequest.newBuilder().setOperation(DataOperation.CHANGE.name()).build();
        response = persistentClientOperationServiceImpl.onApply(writeRequest);
        assertTrue(response.getSuccess());
        assertTrue(ServiceManager.getInstance().containSingleton(service1));
    }
}
