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
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.SubscriberRedoData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RedoScheduledTaskTest {
    
    private static final String SERVICE = "service";
    
    private static final String GROUP = "group";
    
    private static final String CLUSTER = "cluster";
    
    private static final Instance INSTANCE = new Instance();
    
    @Mock
    private NamingGrpcClientProxy clientProxy;
    
    @Mock
    private NamingGrpcRedoService redoService;
    
    private RedoScheduledTask redoTask;
    
    @Before
    public void setUp() throws Exception {
        redoTask = new RedoScheduledTask(clientProxy, redoService);
        when(clientProxy.isEnable()).thenReturn(true);
        when(redoService.isConnected()).thenReturn(true);
    }
    
    @Test
    public void testRunRedoRegisterInstance() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, false, true);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doRegisterService(SERVICE, GROUP, INSTANCE);
    }
    
    @Test
    public void testRunRedoDeregisterInstance() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(true, true, false);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doDeregisterService(SERVICE, GROUP, INSTANCE);
    }
    
    @Test
    public void testRunRedoRemoveInstanceRedoData() throws NacosException {
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, true, false);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(redoService).removeInstanceForRedo(SERVICE, GROUP);
    }
    
    @Test
    public void testRunRedoRegisterInstanceWithClientDisabled() throws NacosException {
        when(clientProxy.isEnable()).thenReturn(false);
        Set<InstanceRedoData> mockData = generateMockInstanceData(false, false, true);
        when(redoService.findInstanceRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy, never()).doRegisterService(SERVICE, GROUP, INSTANCE);
    }
    
    private Set<InstanceRedoData> generateMockInstanceData(boolean registered, boolean unregistering, boolean expectedRegistered) {
        InstanceRedoData redoData = InstanceRedoData.build(SERVICE, GROUP, INSTANCE);
        redoData.setRegistered(registered);
        redoData.setUnregistering(unregistering);
        redoData.setExpectedRegistered(expectedRegistered);
        Set<InstanceRedoData> result = new HashSet<>();
        result.add(redoData);
        return result;
    }
    
    @Test
    public void testRunRedoRegisterSubscriber() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, false, true);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doSubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    public void testRunRedoDeregisterSubscriber() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(true, true, false);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy).doUnsubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    public void testRunRedoRemoveSubscriberRedoData() throws NacosException {
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, true, false);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(redoService).removeSubscriberForRedo(SERVICE, GROUP, CLUSTER);
    }
    
    @Test
    public void testRunRedoRegisterSubscriberWithClientDisabled() throws NacosException {
        when(clientProxy.isEnable()).thenReturn(false);
        Set<SubscriberRedoData> mockData = generateMockSubscriberData(false, false, true);
        when(redoService.findSubscriberRedoData()).thenReturn(mockData);
        redoTask.run();
        verify(clientProxy, never()).doSubscribe(SERVICE, GROUP, CLUSTER);
    }
    
    private Set<SubscriberRedoData> generateMockSubscriberData(boolean registered, boolean unregistering, boolean expectedRegistered) {
        SubscriberRedoData redoData = SubscriberRedoData.build(SERVICE, GROUP, CLUSTER);
        redoData.setRegistered(registered);
        redoData.setUnregistering(unregistering);
        redoData.setExpectedRegistered(expectedRegistered);
        Set<SubscriberRedoData> result = new HashSet<>();
        result.add(redoData);
        return result;
    }
    
    @Test
    public void testRunRedoWithDisconnection() {
        when(redoService.isConnected()).thenReturn(false);
        redoTask.run();
        verify(redoService, never()).findInstanceRedoData();
        verify(redoService, never()).findSubscriberRedoData();
    }
}
