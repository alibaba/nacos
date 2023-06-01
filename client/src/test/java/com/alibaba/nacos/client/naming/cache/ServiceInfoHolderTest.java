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
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

public class ServiceInfoHolderTest {
    
    @Test
    public void testGetServiceInfoMap() throws NoSuchFieldException, IllegalAccessException {
        Properties prop = new Properties();
    
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
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
        
        Properties prop = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        
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
        Properties prop = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        String json = "{\"groupName\":\"a\",\"name\":\"b\",\"clusters\":\"c\"}";
        
        ServiceInfo actual = holder.processServiceInfo(json);
        ServiceInfo expect = new ServiceInfo("a@@b@@c");
        expect.setJsonFromServer(json);
        Assert.assertEquals(expect.getKey(), actual.getKey());
    }
    
    @Test
    public void testProcessServiceInfoWithPushEmpty() {
        ServiceInfo oldInfo = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        oldInfo.setHosts(hosts);
        
        Properties prop = new Properties();
        prop.setProperty(PropertyKeyConst.NAMING_PUSH_EMPTY_PROTECTION, "true");
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        holder.processServiceInfo(oldInfo);
        
        ServiceInfo newInfo = new ServiceInfo("a@@b@@c");
        
        final ServiceInfo actual = holder.processServiceInfo(newInfo);
        
        Assert.assertEquals(oldInfo.getKey(), actual.getKey());
        Assert.assertEquals(2, actual.getHosts().size());
    }
    
    @Test
    public void testGetServiceInfo() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        info.setHosts(hosts);
        
        Properties prop = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        
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
        Properties prop = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
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
    public void testMakeChangeEvent() {
        Properties prop = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        ServiceInfo oldService = new ServiceInfo();
        oldService.setName("test-service-1");
        oldService.setGroupName("test-group");
        oldService.setClusters("test-cluster");
        List<Instance> list = new ArrayList<>();
        Instance instance1 = new Instance();
        instance1.setIp("12345.23.1");
        instance1.setPort(120);
        list.add(instance1);
        Instance instance2 = new Instance();
        instance2.setIp("1230.90.1");
        instance2.setPort(230);
        list.add(instance2);
        Instance instance3 = new Instance();
        instance3.setIp("3467.45.0");
        instance3.setPort(546);
        list.add(instance3);
        oldService.setHosts(list);
        ServiceInfo newService = new ServiceInfo();
        newService.setName("test-service-1");
        newService.setGroupName("test-group");
        newService.setClusters("test-cluster");
        List<Instance> list2 = new ArrayList<>();
        Instance instance4 = new Instance();
        instance4.setIp("12345.23.1");
        instance4.setPort(120);
        list2.add(instance4);
        Instance instance6 = new Instance();
        instance6.setIp("3467.45.0");
        instance6.setPort(546);
        instance6.setEnabled(false);
        list2.add(instance6);
        Instance instance7 = new Instance();
        instance7.setIp("862.23.0");
        instance7.setPort(1235);
        list2.add(instance7);
        newService.setHosts(list2);
        InstancesChangeEvent changeEvent = holder.makeChangeEvent(oldService, newService);
        Assert.assertEquals(1, changeEvent.getModHosts().size());
        Assert.assertEquals(1, changeEvent.getNewHosts().size());
        Assert.assertEquals(1, changeEvent.getRemvHosts().size());
        Assert.assertEquals(instance6, changeEvent.getModHosts().stream().findFirst().orElse(null));
        Assert.assertEquals(instance7, changeEvent.getNewHosts().stream().findFirst().orElse(null));
        Assert.assertEquals(instance2, changeEvent.getRemvHosts().stream().findFirst().orElse(null));
        Assert.assertTrue(changeEvent.changed());
        Assert.assertEquals("test-service-1", changeEvent.getServiceName());
        Assert.assertEquals("test-group", changeEvent.getGroupName());
        Assert.assertEquals("test-cluster", changeEvent.getClusters());
        Assert.assertEquals("scope-001", changeEvent.scope());
        Assert.assertEquals(list2, changeEvent.getHosts());
    }
}