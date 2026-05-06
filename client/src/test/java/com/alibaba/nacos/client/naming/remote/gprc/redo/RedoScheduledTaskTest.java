/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.remote.gprc.redo;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.BatchInstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.SubscriberRedoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo  remove strictness lenient
@MockitoSettings(strictness = Strictness.LENIENT)
class RedoScheduledTaskTest {
    
    private static final String SERVICE = "service";
    
    private static final String GROUP = "group";
    
    private static final String CLUSTER = "cluster";
    
    private static final Instance INSTANCE = new Instance();
    
    @Mock
    private NamingGrpcClientProxy clientProxy;
    
    @Mock
    private NamingGrpcRedoService redoService;
    
    private RedoScheduledTask redoTask;
    
    @BeforeEach
    void setUp() throws Exception {
        redoTask = new RedoScheduledTask(clientProxy, redoService);
        when(clientProxy.isEnable()).thenReturn(true);
        when(redoService.isConnected()).thenReturn(true);
    }
    
    @Test
    void testRunRedoRegisterInstance() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, false, true);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doRegisterService(SERVICE, GROUP, INSTANCE);
    }
    
    @Test
    void testRunRedoRegisterBatchInstance() throws NacosException {
        BatchInstanceRedoData redoData = BatchInstanceRedoData.build(SERVICE, GROUP,
                Collections.singletonList(INSTANCE));
        redoData.setRegistered(false);
        redoData.setUnregistering(false);
        redoData.setExpectedRegistered(true);
        Set<InstanceRedoData> mockData = new HashSet<>();
        mockData.add(redoData);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doBatchRegisterService(SERVICE, GROUP, redoData.getInstances());
    }
    
    @Test
    void testRunRedoDeregisterInstance() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(true, true, false);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doDeregisterService(SERVICE, GROUP, INSTANCE);
    }
    
    @Test
    void testRunRedoRemoveInstanceRedoData() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, true, false);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(redoService).removeInstanceForRedo(SERVICE, GROUP);
    }
    
    @Test
    void testRunRedoRegisterInstanceWithClientDisabled() throws NacosException {
        when(clientProxy.isEnable()).thenReturn(false);
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, false, true);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy, never()).doRegisterService(SERVICE, GROUP, INSTANCE);
    }
    
    @Test
    void testRunRedoDeregisterInstanceWithClientDisabled() throws NacosException {
        when(clientProxy.isEnable()).thenReturn(false);
        Set<InstanceRedoData> mockData = generateMockInstanceData(true, true, false);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy, never()).doRegisterService(SERVICE, GROUP, INSTANCE);
    }
    
    @Test
    void testRunRedoRegisterInstanceWithNacosException() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, false, true);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        doThrow(new NacosException()).when(clientProxy).doRegisterService(SERVICE, GROUP, INSTANCE);
        redoTask.run();
        // Not any exception thrown
    }
    
    @Test
    void testRunRedoRegisterInstanceWithOtherException() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, false, true);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        doThrow(new RuntimeException("test")).when(clientProxy).doRegisterService(SERVICE, GROUP, INSTANCE);
        redoTask.run();
        // Not any exception thrown
    }
    
    private Set<InstanceRedoData> generateMockInstanceData(boolean registered, boolean unregistering,
            boolean expectedRegistered) {
        InstanceRedoData redoData = InstanceRedoData.build(SERVICE, GROUP, INSTANCE);
        redoData.setRegistered(registered);
        redoData.setUnregistering(unregistering);
        redoData.setExpectedRegistered(expectedRegistered);
        Set<InstanceRedoData> result = new HashSet<>();
        result.add(redoData);
        return result;
    }
    
    @Test
    void testRunRedoRegisterSubscriber() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, false, true);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doSubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    void testRunRedoDeregisterSubscriber() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(true, true, false);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doUnsubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    void testRunRedoRemoveSubscriberRedoData() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, true, false);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(redoService).removeSubscriberForRedo(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    void testRunRedoRegisterSubscriberWithClientDisabled() throws NacosException {
        when(clientProxy.isEnable()).thenReturn(false);
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, false, true);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy, never()).doSubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    void testRunRedoDeRegisterSubscriberWithClientDisabled() throws NacosException {
        when(clientProxy.isEnable()).thenReturn(false);
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(true, true, false);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy, never()).doUnsubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    void testRunRedoRegisterSubscriberWithNacosException() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, false, true);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        doThrow(new NacosException()).when(clientProxy).doSubscribe(SERVICE, GROUP, CLUSTER);
        redoTask.run();
        // Not any exception thrown
    }
    
    private Set<SubscriberRedoData> generateMockSubscriberData(boolean registered, boolean unregistering,
            boolean expectedRegistered) {
        SubscriberRedoData redoData = SubscriberRedoData.build(SERVICE, GROUP, CLUSTER);
        redoData.setRegistered(registered);
        redoData.setUnregistering(unregistering);
        redoData.setExpectedRegistered(expectedRegistered);
        Set<SubscriberRedoData> result = new HashSet<>();
        result.add(redoData);
        return result;
    }
    
    @Test
    void testRunRedoWithDisconnection() {
        when(redoService.isConnected()).thenReturn(false);
        redoTask.run();
        verify(redoService, never()).findInstanceRedoData();
        verify(redoService, never()).findSubscriberRedoData();
    }
}
