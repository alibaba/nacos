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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.BatchInstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.SubscriberRedoData;
import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class NamingGrpcRedoServiceTest {
    
    private static final String SERVICE = "service";
    
    private static final String GROUP = "group";
    
    private static final String CLUSTER = "cluster";
    
    @Mock
    private NamingGrpcClientProxy clientProxy;
    
    private NamingGrpcRedoService redoService;
    
    @Before
    public void setUp() throws Exception {
        redoService = new NamingGrpcRedoService(clientProxy);
        ScheduledExecutorService redoExecutor = (ScheduledExecutorService) ReflectUtils
                .getFieldValue(redoService, "redoExecutor");
        redoExecutor.shutdownNow();
    }
    
    @After
    public void tearDown() throws Exception {
        redoService.shutdown();
    }
    
    @Test
    public void testOnConnected() {
        assertFalse(redoService.isConnected());
        redoService.onConnected();
        assertTrue(redoService.isConnected());
    }
    
    @Test
    public void testOnDisConnect() {
        redoService.onConnected();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceRegistered(SERVICE, GROUP);
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        assertTrue(redoService.isConnected());
        assertTrue(redoService.findInstanceRedoData().isEmpty());
        assertTrue(redoService.findSubscriberRedoData().isEmpty());
        redoService.onDisConnect();
        assertFalse(redoService.isConnected());
        assertFalse(redoService.findInstanceRedoData().isEmpty());
        assertFalse(redoService.findSubscriberRedoData().isEmpty());
    }
    
    @Test
    public void testCacheInstanceForRedo() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        assertTrue(registeredInstances.isEmpty());
        Instance instance = new Instance();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, instance);
        assertFalse(registeredInstances.isEmpty());
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertEquals(SERVICE, actual.getServiceName());
        assertEquals(GROUP, actual.getGroupName());
        assertEquals(instance, actual.get());
        assertFalse(actual.isRegistered());
        assertFalse(actual.isUnregistering());
        assertTrue(actual.isExpectedRegistered());
    }
    
    @Test
    public void testCacheInstanceForRedoByBatchInstanceRedoData() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        assertTrue(registeredInstances.isEmpty());
        Instance instance = new Instance();
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        redoService.cacheInstanceForRedo(SERVICE, GROUP, instanceList);
        assertFalse(registeredInstances.isEmpty());
        BatchInstanceRedoData actual = (BatchInstanceRedoData) registeredInstances.entrySet().iterator().next().getValue();
        assertEquals(SERVICE, actual.getServiceName());
        assertEquals(GROUP, actual.getGroupName());
        assertEquals(instanceList, actual.getInstances());
        assertFalse(actual.isRegistered());
        assertFalse(actual.isUnregistering());
    }
    
    @Test
    public void testInstanceRegistered() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceRegistered(SERVICE, GROUP);
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertTrue(actual.isRegistered());
    }
    
    @Test
    public void testInstanceDeregister() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceDeregister(SERVICE, GROUP);
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertTrue(actual.isUnregistering());
        assertFalse(actual.isExpectedRegistered());
    }
    
    @Test
    public void testInstanceDeregistered() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceDeregistered(SERVICE, GROUP);
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertFalse(actual.isRegistered());
        assertTrue(actual.isUnregistering());
    }
    
    @Test
    public void testRemoveInstanceForRedo() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        assertTrue(registeredInstances.isEmpty());
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        assertFalse(registeredInstances.isEmpty());
        redoService.instanceDeregister(SERVICE, GROUP);
        redoService.removeInstanceForRedo(SERVICE, GROUP);
        assertTrue(registeredInstances.isEmpty());
    }
    
    @Test
    public void testFindInstanceRedoData() {
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        assertFalse(redoService.findInstanceRedoData().isEmpty());
        redoService.instanceRegistered(SERVICE, GROUP);
        assertTrue(redoService.findInstanceRedoData().isEmpty());
        redoService.instanceDeregister(SERVICE, GROUP);
        assertFalse(redoService.findInstanceRedoData().isEmpty());
    }
    
    @SuppressWarnings("all")
    private ConcurrentMap<String, InstanceRedoData> getInstanceRedoDataMap() {
        return (ConcurrentMap<String, InstanceRedoData>) ReflectUtils.getFieldValue(redoService, "registeredInstances");
    }
    
    @Test
    public void testCacheSubscriberForRedo() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        assertTrue(subscribes.isEmpty());
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        assertFalse(subscribes.isEmpty());
        SubscriberRedoData actual = subscribes.entrySet().iterator().next().getValue();
        assertEquals(SERVICE, actual.getServiceName());
        assertEquals(GROUP, actual.getGroupName());
        assertEquals(CLUSTER, actual.get());
        assertFalse(actual.isRegistered());
        assertFalse(actual.isUnregistering());
    }
    
    @Test
    public void testSubscriberRegistered() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        SubscriberRedoData actual = subscribes.entrySet().iterator().next().getValue();
        assertTrue(actual.isRegistered());
    }
    
    @Test
    public void testSubscriberDeregister() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberDeregister(SERVICE, GROUP, CLUSTER);
        SubscriberRedoData actual = subscribes.entrySet().iterator().next().getValue();
        assertTrue(actual.isUnregistering());
    }
    
    @Test
    public void testIsSubscriberRegistered() {
        assertFalse(redoService.isSubscriberRegistered(SERVICE, GROUP, CLUSTER));
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        assertTrue(redoService.isSubscriberRegistered(SERVICE, GROUP, CLUSTER));
    }
    
    @Test
    public void testRemoveSubscriberForRedo() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        assertTrue(subscribes.isEmpty());
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        assertFalse(subscribes.isEmpty());
        redoService.subscriberDeregister(SERVICE, GROUP, CLUSTER);
        redoService.removeSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        assertTrue(subscribes.isEmpty());
    }
    
    @Test
    public void testFindSubscriberRedoData() {
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        assertFalse(redoService.findSubscriberRedoData().isEmpty());
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        assertTrue(redoService.findSubscriberRedoData().isEmpty());
        redoService.subscriberDeregister(SERVICE, GROUP, CLUSTER);
        assertFalse(redoService.findSubscriberRedoData().isEmpty());
    }
    
    @SuppressWarnings("all")
    private ConcurrentMap<String, SubscriberRedoData> getSubscriberRedoDataMap() {
        return (ConcurrentMap<String, SubscriberRedoData>) ReflectUtils.getFieldValue(redoService, "subscribes");
    }
}
