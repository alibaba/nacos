/*
 *
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
 *
 */

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceInfoHolderTest {
    
    NacosClientProperties nacosClientProperties;
    
    ServiceInfoHolder holder;
    
    @BeforeEach
    void setUp() throws Exception {
        nacosClientProperties = NacosClientProperties.PROTOTYPE.derive();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
    }
    
    @AfterEach
    void tearDown() throws Exception {
    
    }
    
    @Test
    void testGetServiceInfoMap() throws NoSuchFieldException, IllegalAccessException {
        assertEquals(0, holder.getServiceInfoMap().size());
        Field fieldNotifierEventScope = ServiceInfoHolder.class.getDeclaredField("notifierEventScope");
        fieldNotifierEventScope.setAccessible(true);
        assertEquals("scope-001", fieldNotifierEventScope.get(holder));
    }
    
    @Test
    void testProcessServiceInfo() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        
        ServiceInfo actual1 = holder.processServiceInfo(info);
        assertEquals(info, actual1);
        
        Instance newInstance1 = createInstance("1.1.1.1", 1);
        newInstance1.setWeight(2.0);
        Instance instance3 = createInstance("1.1.1.3", 3);
        List<Instance> hosts2 = new ArrayList<>();
        hosts2.add(newInstance1);
        hosts2.add(instance3);
        ServiceInfo info2 = new ServiceInfo("a@@b@@c");
        info2.setHosts(hosts2);
        
        ServiceInfo actual2 = holder.processServiceInfo(info2);
        assertEquals(info2, actual2);
    }
    
    private Instance createInstance(String ip, int port) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        return instance;
    }
    
    @Test
    void testProcessServiceInfo2() {
        String json = "{\"groupName\":\"a\",\"name\":\"b\",\"clusters\":\"c\"}";
        
        ServiceInfo actual = holder.processServiceInfo(json);
        ServiceInfo expect = new ServiceInfo("a@@b@@c");
        expect.setJsonFromServer(json);
        assertEquals(expect.getKey(), actual.getKey());
    }
    
    @Test
    void testProcessServiceInfoWithPushEmpty() throws NacosException {
        ServiceInfo oldInfo = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        oldInfo.setHosts(hosts);
        
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_PUSH_EMPTY_PROTECTION, "true");
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        holder.processServiceInfo(oldInfo);
        
        ServiceInfo newInfo = new ServiceInfo("a@@b@@c");
        
        final ServiceInfo actual = holder.processServiceInfo(newInfo);
        
        assertEquals(oldInfo.getKey(), actual.getKey());
        assertEquals(2, actual.getHosts().size());
    }
    
    @Test
    void testProcessNullServiceInfo() {
        assertNull(holder.processServiceInfo(new ServiceInfo()));
    }
    
    @Test
    void testProcessServiceInfoForOlder() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        info.setLastRefTime(System.currentTimeMillis());
        holder.processServiceInfo(info);
        ServiceInfo olderInfo = new ServiceInfo("a@@b@@c");
        olderInfo.setLastRefTime(0L);
        final ServiceInfo actual = holder.processServiceInfo(olderInfo);
        assertEquals(olderInfo, actual);
    }
    
    @Test
    void testGetServiceInfo() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        info.setHosts(hosts);
        
        ServiceInfo expect = holder.processServiceInfo(info);
        String serviceName = "b";
        String groupName = "a";
        String clusters = "c";
        ServiceInfo actual = holder.getServiceInfo(serviceName, groupName, clusters);
        assertEquals(expect.getKey(), actual.getKey());
        assertEquals(expect.getHosts().size(), actual.getHosts().size());
        assertEquals(expect.getHosts().get(0), actual.getHosts().get(0));
    }
    
    @Test
    void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Field field = ServiceInfoHolder.class.getDeclaredField("failoverReactor");
        field.setAccessible(true);
        FailoverReactor reactor = (FailoverReactor) field.get(holder);
        Field executorService = FailoverReactor.class.getDeclaredField("executorService");
        executorService.setAccessible(true);
        ScheduledExecutorService pool = (ScheduledExecutorService) executorService.get(reactor);
        assertFalse(pool.isShutdown());
        holder.shutdown();
        assertTrue(pool.isShutdown());
    }
    
    @Test
    void testConstructWithCacheLoad() throws NacosException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "true");
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR, "non-exist");
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        assertEquals(System.getProperty("user.home") + "/nacos/non-exist/naming/aa", holder.getCacheDir());
        assertTrue(holder.getServiceInfoMap().isEmpty());
    }
    
    @Test
    void testIsFailoverSwitch() throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        when(mock.isFailoverSwitch()).thenReturn(true);
        assertTrue(holder.isFailoverSwitch());
    }
    
    @Test
    void testGetFailoverServiceInfo() throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b@@c");
        when(mock.getService("a@@b@@c")).thenReturn(serviceInfo);
        assertEquals(serviceInfo, holder.getFailoverServiceInfo("b", "a", "c"));
    }
    
    private FailoverReactor injectMockFailoverReactor()
            throws NoSuchFieldException, IllegalAccessException, NacosException {
        Field field = ServiceInfoHolder.class.getDeclaredField("failoverReactor");
        field.setAccessible(true);
        FailoverReactor old = (FailoverReactor) field.get(holder);
        old.shutdown();
        FailoverReactor mock = mock(FailoverReactor.class);
        field.set(holder, mock);
        return mock;
    }
}