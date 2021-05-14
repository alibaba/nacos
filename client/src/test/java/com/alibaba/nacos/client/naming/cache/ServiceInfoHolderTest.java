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
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

public class ServiceInfoHolderTest {
    
    @Test
    public void testGetServiceInfoMap() {
        Properties prop = new Properties();
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", prop);
        Assert.assertEquals(0, holder.getServiceInfoMap().size());
        
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
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", prop);
        
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
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", prop);
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
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", prop);
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
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", prop);
        
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
        ServiceInfoHolder holder = new ServiceInfoHolder("aa", prop);
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
}