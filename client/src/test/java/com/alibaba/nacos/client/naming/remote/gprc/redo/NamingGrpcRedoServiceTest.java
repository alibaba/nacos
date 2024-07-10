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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.remote.TestConnection;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.BatchInstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.SubscriberRedoData;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class NamingGrpcRedoServiceTest {
    
    private static final String SERVICE = "service";
    
    private static final String GROUP = "group";
    
    private static final String CLUSTER = "cluster";
    
    @Mock
    private NamingGrpcClientProxy clientProxy;
    
    private NamingGrpcRedoService redoService;
    
    @BeforeEach
    void setUp() throws Exception {
        Properties prop = new Properties();
        NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        redoService = new NamingGrpcRedoService(clientProxy, nacosClientProperties);
        ScheduledExecutorService redoExecutor = (ScheduledExecutorService) ReflectUtils.getFieldValue(redoService,
                "redoExecutor");
        redoExecutor.shutdownNow();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        redoService.shutdown();
    }
    
    @Test
    void testDefaultProperties() throws Exception {
        Field redoThreadCountField = NamingGrpcRedoService.class.getDeclaredField("redoThreadCount");
        redoThreadCountField.setAccessible(true);
        
        Field redoDelayTimeField = NamingGrpcRedoService.class.getDeclaredField("redoDelayTime");
        redoDelayTimeField.setAccessible(true);
        
        Long redoDelayTimeValue = (Long) redoDelayTimeField.get(redoService);
        Integer redoThreadCountValue = (Integer) redoThreadCountField.get(redoService);
        
        assertEquals(Long.valueOf(3000L), redoDelayTimeValue);
        assertEquals(Integer.valueOf(1), redoThreadCountValue);
    }
    
    @Test
    void testCustomProperties() throws Exception {
        Properties prop = new Properties();
        prop.setProperty(PropertyKeyConst.REDO_DELAY_TIME, "4000");
        prop.setProperty(PropertyKeyConst.REDO_DELAY_THREAD_COUNT, "2");
        NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        
        NamingGrpcRedoService redoService = new NamingGrpcRedoService(clientProxy, nacosClientProperties);
        
        Field redoThreadCountField = NamingGrpcRedoService.class.getDeclaredField("redoThreadCount");
        redoThreadCountField.setAccessible(true);
        
        Field redoDelayTimeField = NamingGrpcRedoService.class.getDeclaredField("redoDelayTime");
        redoDelayTimeField.setAccessible(true);
        
        Long redoDelayTimeValue = (Long) redoDelayTimeField.get(redoService);
        Integer redoThreadCountValue = (Integer) redoThreadCountField.get(redoService);
        assertEquals(Long.valueOf(4000L), redoDelayTimeValue);
        assertEquals(Integer.valueOf(2), redoThreadCountValue);
    }
    
    @Test
    void testOnConnected() {
        assertFalse(redoService.isConnected());
        redoService.onConnected(new TestConnection(new RpcClient.ServerInfo()));
        assertTrue(redoService.isConnected());
    }
    
    @Test
    void testOnDisConnect() {
        redoService.onConnected(new TestConnection(new RpcClient.ServerInfo()));
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceRegistered(SERVICE, GROUP);
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        assertTrue(redoService.isConnected());
        assertTrue(redoService.findInstanceRedoData().isEmpty());
        assertTrue(redoService.findSubscriberRedoData().isEmpty());
        redoService.onDisConnect(new TestConnection(new RpcClient.ServerInfo()));
        assertFalse(redoService.isConnected());
        assertFalse(redoService.findInstanceRedoData().isEmpty());
        assertFalse(redoService.findSubscriberRedoData().isEmpty());
    }
    
    @Test
    void testCacheInstanceForRedo() {
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
    void testCacheInstanceForRedoByBatchInstanceRedoData() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        assertTrue(registeredInstances.isEmpty());
        Instance instance = new Instance();
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(instance);
        redoService.cacheInstanceForRedo(SERVICE, GROUP, instanceList);
        assertFalse(registeredInstances.isEmpty());
        BatchInstanceRedoData actual = (BatchInstanceRedoData) registeredInstances.entrySet().iterator().next()
                .getValue();
        assertEquals(SERVICE, actual.getServiceName());
        assertEquals(GROUP, actual.getGroupName());
        assertEquals(instanceList, actual.getInstances());
        assertFalse(actual.isRegistered());
        assertFalse(actual.isUnregistering());
    }
    
    @Test
    void testInstanceRegistered() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceRegistered(SERVICE, GROUP);
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertTrue(actual.isRegistered());
    }
    
    @Test
    void testInstanceDeregister() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceDeregister(SERVICE, GROUP);
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertTrue(actual.isUnregistering());
        assertFalse(actual.isExpectedRegistered());
    }
    
    @Test
    void testInstanceDeregistered() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        redoService.instanceDeregistered(SERVICE, GROUP);
        InstanceRedoData actual = registeredInstances.entrySet().iterator().next().getValue();
        assertFalse(actual.isRegistered());
        assertTrue(actual.isUnregistering());
    }
    
    @Test
    void testRemoveInstanceForRedo() {
        ConcurrentMap<String, InstanceRedoData> registeredInstances = getInstanceRedoDataMap();
        assertTrue(registeredInstances.isEmpty());
        redoService.cacheInstanceForRedo(SERVICE, GROUP, new Instance());
        assertFalse(registeredInstances.isEmpty());
        redoService.instanceDeregister(SERVICE, GROUP);
        redoService.removeInstanceForRedo(SERVICE, GROUP);
        assertTrue(registeredInstances.isEmpty());
    }
    
    @Test
    void testFindInstanceRedoData() {
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
    void testCacheSubscriberForRedo() {
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
    void testSubscriberRegistered() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        SubscriberRedoData actual = subscribes.entrySet().iterator().next().getValue();
        assertTrue(actual.isRegistered());
    }
    
    @Test
    void testSubscriberDeregister() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberDeregister(SERVICE, GROUP, CLUSTER);
        SubscriberRedoData actual = subscribes.entrySet().iterator().next().getValue();
        assertTrue(actual.isUnregistering());
    }
    
    @Test
    void testIsSubscriberRegistered() {
        assertFalse(redoService.isSubscriberRegistered(SERVICE, GROUP, CLUSTER));
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        redoService.subscriberRegistered(SERVICE, GROUP, CLUSTER);
        assertTrue(redoService.isSubscriberRegistered(SERVICE, GROUP, CLUSTER));
    }
    
    @Test
    void testRemoveSubscriberForRedo() {
        ConcurrentMap<String, SubscriberRedoData> subscribes = getSubscriberRedoDataMap();
        assertTrue(subscribes.isEmpty());
        redoService.cacheSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        assertFalse(subscribes.isEmpty());
        redoService.subscriberDeregister(SERVICE, GROUP, CLUSTER);
        redoService.removeSubscriberForRedo(SERVICE, GROUP, CLUSTER);
        assertTrue(subscribes.isEmpty());
    }
    
    @Test
    void testFindSubscriberRedoData() {
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
