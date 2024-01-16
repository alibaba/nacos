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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServiceInfoUpdateServiceTest {
    
    String serviceName = "aa";
    
    String group = "bb";
    
    String clusters = "cc";
    
    @Mock
    ServiceInfoHolder holder;
    
    @Mock
    NamingClientProxy proxy;
    
    @Mock
    InstancesChangeNotifier notifier;
    
    NacosClientProperties nacosClientProperties;
    
    ServiceInfo info;
    
    ServiceInfoUpdateService serviceInfoUpdateService;
    
    @Before
    public void setUp() throws Exception {
        nacosClientProperties = NacosClientProperties.PROTOTYPE.derive();
        info = new ServiceInfo();
        info.setName(serviceName);
        info.setGroupName(group);
        info.setClusters(clusters);
        info.setLastRefTime(System.currentTimeMillis());
        when(proxy.queryInstancesOfService(serviceName, group, clusters, false)).thenReturn(info);
    }
    
    @After
    public void tearDown() throws Exception {
        if (null != serviceInfoUpdateService) {
            serviceInfoUpdateService.shutdown();
        }
    }
    
    @Test
    public void testScheduleUpdateWithoutOpen() throws InterruptedException, NacosException {
        serviceInfoUpdateService = new ServiceInfoUpdateService(null, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        TimeUnit.MILLISECONDS.sleep(1500);
        Mockito.verify(proxy, Mockito.never()).queryInstancesOfService(serviceName, group, clusters, false);
    }
    
    @Test
    public void testScheduleUpdateIfAbsent() throws InterruptedException, NacosException {
        info.setCacheMillis(10000L);
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        TimeUnit.MILLISECONDS.sleep(1500);
        Mockito.verify(proxy).queryInstancesOfService(serviceName, group, clusters, false);
    }
    
    @Test
    public void testScheduleUpdateIfAbsentDuplicate() throws InterruptedException, NacosException {
        info.setCacheMillis(10000L);
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        TimeUnit.MILLISECONDS.sleep(1500);
        // Only once called
        Mockito.verify(proxy).queryInstancesOfService(serviceName, group, clusters, false);
    }
    
    @Test
    public void testScheduleUpdateIfAbsentUpdateOlder() throws InterruptedException, NacosException {
        info.setCacheMillis(10000L);
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        Map<String, ServiceInfo> map = new HashMap<>();
        map.put(ServiceInfo.getKey(group + "@@" + serviceName, clusters), info);
        when(holder.getServiceInfoMap()).thenReturn(map);
        TimeUnit.MILLISECONDS.sleep(1500);
        Mockito.verify(proxy).queryInstancesOfService(serviceName, group, clusters, false);
    }
    
    @Test
    public void testScheduleUpdateIfAbsentUpdateOlderWithInstance() throws InterruptedException, NacosException {
        info.setCacheMillis(10000L);
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        Map<String, ServiceInfo> map = new HashMap<>();
        map.put(ServiceInfo.getKey(group + "@@" + serviceName, clusters), info);
        when(holder.getServiceInfoMap()).thenReturn(map);
        info.setHosts(Collections.singletonList(new Instance()));
        TimeUnit.MILLISECONDS.sleep(1500);
        Mockito.verify(proxy).queryInstancesOfService(serviceName, group, clusters, false);
    }
    
    @Test
    public void testScheduleUpdateIfAbsentWith403Exception()
            throws InterruptedException, NacosException, NoSuchFieldException, IllegalAccessException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        when(proxy.queryInstancesOfService(serviceName, group, clusters, false))
                .thenThrow(new NacosException(403, "test"));
        TimeUnit.MILLISECONDS.sleep(1500);
        assertTrue(getScheduleFuture().getDelay(TimeUnit.MILLISECONDS) > 1000);
    }
    
    @Test
    public void testScheduleUpdateIfAbsentWith500Exception()
            throws InterruptedException, NacosException, NoSuchFieldException, IllegalAccessException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        when(proxy.queryInstancesOfService(serviceName, group, clusters, false))
                .thenThrow(new NacosException(500, "test"));
        TimeUnit.MILLISECONDS.sleep(1500);
        assertTrue(getScheduleFuture().getDelay(TimeUnit.MILLISECONDS) > 2000);
    }
    
    @Test
    public void testScheduleUpdateIfAbsentWithOtherException()
            throws InterruptedException, NacosException, NoSuchFieldException, IllegalAccessException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        when(proxy.queryInstancesOfService(serviceName, group, clusters, false))
                .thenThrow(new RuntimeException("test"));
        TimeUnit.MILLISECONDS.sleep(1500);
        assertTrue(getScheduleFuture().getDelay(TimeUnit.MILLISECONDS) > 1000);
    }
    
    @Test
    public void testStopScheduleUpdateIfAbsent() throws InterruptedException, NacosException {
        info.setCacheMillis(10000L);
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_ASYNC_QUERY_SUBSCRIBE_SERVICE, "true");
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        serviceInfoUpdateService.stopUpdateIfContain(serviceName, group, clusters);
        TimeUnit.MILLISECONDS.sleep(1500);
        Mockito.verify(proxy, Mockito.never()).queryInstancesOfService(serviceName, group, clusters, false);
    }
    
    @Test
    public void testStopUpdateIfContainWithoutOpen() throws NacosException, InterruptedException {
        serviceInfoUpdateService = new ServiceInfoUpdateService(nacosClientProperties, holder, proxy, notifier);
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, group, clusters);
        TimeUnit.MILLISECONDS.sleep(1500);
        Mockito.verify(proxy, Mockito.never()).queryInstancesOfService(serviceName, group, clusters, false);
        serviceInfoUpdateService.stopUpdateIfContain(serviceName, group, clusters);
        serviceInfoUpdateService.shutdown();
    }
    
    private ScheduledFuture getScheduleFuture() throws NoSuchFieldException, IllegalAccessException {
        Field field = serviceInfoUpdateService.getClass().getDeclaredField("executor");
        field.setAccessible(true);
        ScheduledThreadPoolExecutor executorService = (ScheduledThreadPoolExecutor) field.get(serviceInfoUpdateService);
        return (ScheduledFuture) executorService.getQueue().peek();
    }
}
