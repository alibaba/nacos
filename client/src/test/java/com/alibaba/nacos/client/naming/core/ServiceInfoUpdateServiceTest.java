/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ServiceInfoUpdateServiceTest {
    
    @Test
    public void testScheduleUpdateIfAbsent() throws InterruptedException, NacosException {
        String serviceName = "aa";
        String group = "bb";
        String clusters = "cc";
        ServiceInfo info = new ServiceInfo();
        info.setName(serviceName);
        info.setGroupName(group);
        info.setClusters(clusters);
        info.setLastRefTime(System.currentTimeMillis());
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        NamingClientProxy proxy = Mockito.mock(NamingClientProxy.class);
        Mockito.when(proxy.queryInstancesOfService(serviceName, group, clusters, 0, false)).thenReturn(info);
        
        InstancesChangeNotifier notifyer = Mockito.mock(InstancesChangeNotifier.class);
        Properties prop = new Properties();
        final ServiceInfoUpdateService serviceInfoUpdateService = new ServiceInfoUpdateService(prop, holder, proxy,
                notifyer);
        
        serviceInfoUpdateService.scheduleUpdateIfAbsent("aa", "bb", "cc");
        TimeUnit.SECONDS.sleep(2);
        Mockito.verify(proxy).queryInstancesOfService(serviceName, group, clusters, 0, false);
    }
    
    @Test
    public void testStopUpdateIfContain() throws NacosException {
        String serviceName = "aa";
        String group = "bb";
        String clusters = "cc";
        ServiceInfo info = new ServiceInfo();
        info.setName(serviceName);
        info.setGroupName(group);
        info.setClusters(clusters);
        info.setLastRefTime(System.currentTimeMillis());
        NamingClientProxy proxy = Mockito.mock(NamingClientProxy.class);
        Mockito.when(proxy.queryInstancesOfService(serviceName, group, clusters, 0, false)).thenReturn(info);
        
        InstancesChangeNotifier notifyer = Mockito.mock(InstancesChangeNotifier.class);
        Properties prop = new Properties();
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
    
        final ServiceInfoUpdateService serviceInfoUpdateService = new ServiceInfoUpdateService(prop, holder, proxy,
                notifyer);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
    
        serviceInfoUpdateService.stopUpdateIfContain(serviceName, group, clusters);
        serviceInfoUpdateService.shutdown();
    }
}