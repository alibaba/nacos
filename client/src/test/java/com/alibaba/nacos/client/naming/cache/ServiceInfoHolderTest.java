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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServiceInfoHolderTest {
    
    NacosClientProperties nacosClientProperties;
    
    ServiceInfoHolder holder;
    
    @Before
    public void setUp() throws Exception {
        nacosClientProperties = NacosClientProperties.PROTOTYPE.derive();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
    }
    
    @After
    public void tearDown() throws Exception {
    
    }
    
    @Test
    public void testGetServiceInfoMap() throws NoSuchFieldException, IllegalAccessException {
        Assert.assertEquals(0, holder.getServiceInfoMap().size());
        Field fieldNotifierEventScope = ServiceInfoHolder.class.getDeclaredField("notifierEventScope");
        fieldNotifierEventScope.setAccessible(true);
        Assert.assertEquals("scope-001", fieldNotifierEventScope.get(holder));
    }
    
    @Test
    public void testProcessServiceInfo() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        
        ServiceInfo actual1 = holder.processServiceInfo(info);
        Assert.assertEquals(info, actual1);
        
        Instance newInstance1 = createInstance("1.1.1.1", 1);
        newInstance1.setWeight(2.0);
        Instance instance3 = createInstance("1.1.1.3", 3);
        List<Instance> hosts2 = new ArrayList<>();
        hosts2.add(newInstance1);
        hosts2.add(instance3);
        ServiceInfo info2 = new ServiceInfo("a@@b@@c");
        info2.setHosts(hosts2);
        
        ServiceInfo actual2 = holder.processServiceInfo(info2);
        Assert.assertEquals(info2, actual2);
    }
    
    private Instance createInstance(String ip, int port) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        return instance;
    }
    
    @Test
    public void testProcessServiceInfo2() {
        String json = "{\"groupName\":\"a\",\"name\":\"b\",\"clusters\":\"c\"}";
        
        ServiceInfo actual = holder.processServiceInfo(json);
        ServiceInfo expect = new ServiceInfo("a@@b@@c");
        expect.setJsonFromServer(json);
        Assert.assertEquals(expect.getKey(), actual.getKey());
    }
    
    @Test
    public void testProcessServiceInfoWithPushEmpty() throws NacosException {
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
        
        Assert.assertEquals(oldInfo.getKey(), actual.getKey());
        Assert.assertEquals(2, actual.getHosts().size());
    }
    
    @Test
    public void testProcessNullServiceInfo() {
        Assert.assertNull(holder.processServiceInfo(new ServiceInfo()));
    }
    
    @Test
    public void testProcessServiceInfoForOlder() {
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
        Assert.assertEquals(olderInfo, actual);
    }
    
    @Test
    public void testGetServiceInfo() {
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
        Assert.assertEquals(expect.getKey(), actual.getKey());
        Assert.assertEquals(expect.getHosts().size(), actual.getHosts().size());
        Assert.assertEquals(expect.getHosts().get(0), actual.getHosts().get(0));
    }
    
    @Test
    public void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Field field = ServiceInfoHolder.class.getDeclaredField("failoverReactor");
        field.setAccessible(true);
        FailoverReactor reactor = (FailoverReactor) field.get(holder);
        Field executorService = FailoverReactor.class.getDeclaredField("executorService");
        executorService.setAccessible(true);
        ScheduledExecutorService pool = (ScheduledExecutorService) executorService.get(reactor);
        Assert.assertFalse(pool.isShutdown());
        holder.shutdown();
        Assert.assertTrue(pool.isShutdown());
    }
    
    @Test
    public void testConstructWithCacheLoad() throws NacosException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "true");
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR, "non-exist");
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        Assert.assertEquals(System.getProperty("user.home") + "/nacos/non-exist/naming/aa", holder.getCacheDir());
        Assert.assertTrue(holder.getServiceInfoMap().isEmpty());
    }
    
    @Test
    public void testIsFailoverSwitch() throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        when(mock.isFailoverSwitch()).thenReturn(true);
        Assert.assertTrue(holder.isFailoverSwitch());
    }
    
    @Test
    public void testGetFailoverServiceInfo() throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b@@c");
        when(mock.getService("a@@b@@c")).thenReturn(serviceInfo);
        Assert.assertEquals(serviceInfo, holder.getFailoverServiceInfo("b", "a", "c"));
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