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

package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NamingGrpcConnectionEventListenerTest {
    
    @Test
    public void testCacheInstanceForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        //when
        listener.onConnected();
        //then
        verify(proxy, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRemoveInstanceForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        listener.removeInstanceForRedo(serviceName, groupName, instance);
        //when
        listener.onConnected();
        //then
        verify(proxy, times(0)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testCacheSubscriberForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        //when
        listener.onConnected();
        //then
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(1)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
    
    @Test
    public void testRemoveSubscriberForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        listener.removeSubscriberForRedo(fullServiceName, cluster);
        //when
        listener.onConnected();
        //then
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(0)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
    
    @Test
    public void testRedoRegisterEachService1()
            throws NacosException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        Set<String> failedServices = new ConcurrentHashSet<>();
        failedServices.add(NamingUtils.getGroupedName(serviceName, groupName));
        //when
        Field connected = NamingGrpcConnectionEventListener.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.setBoolean(listener, true);
        Method method = NamingGrpcConnectionEventListener.class.getDeclaredMethod("redoRegisterEachService", Set.class);
        method.setAccessible(true);
        method.invoke(listener, failedServices);
        //then
        verify(proxy, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRedoRegisterEachService2()
            throws NacosException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        Set<String> failedServices = new ConcurrentHashSet<>();
        failedServices.add(NamingUtils.getGroupedName(serviceName, groupName));
        //when
        Field connected = NamingGrpcConnectionEventListener.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.setBoolean(listener, false);
        Method method = NamingGrpcConnectionEventListener.class.getDeclaredMethod("redoRegisterEachService", Set.class);
        method.setAccessible(true);
        method.invoke(listener, failedServices);
        //then
        verify(proxy, times(0)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRedoRegisterEachService3()
            throws NacosException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException, InterruptedException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        Set<String> failedServices = new ConcurrentHashSet<>();
        failedServices.add(NamingUtils.getGroupedName(serviceName, groupName));
        //when
        Field connected = NamingGrpcConnectionEventListener.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.setBoolean(listener, true);
        Field executorFiled = NamingGrpcConnectionEventListener.class.getDeclaredField("redoExecutorService");
        executorFiled.setAccessible(true);
        ScheduledExecutorService executorService = (ScheduledExecutorService) executorFiled.get(listener);
        Method method = NamingGrpcConnectionEventListener.class.getDeclaredMethod("redoRegisterEachService", Set.class);
        method.setAccessible(true);
        executorService.schedule(() -> method.invoke(listener, failedServices), 0, TimeUnit.MILLISECONDS);
        //then
        Thread.sleep(100);
        verify(proxy, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRedoSubscribe1()
            throws NacosException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        Set<String> failedSubscribes = new ConcurrentHashSet<>();
        failedSubscribes.add(ServiceInfo.getKey(fullServiceName, cluster));
        //when
        Field connected = NamingGrpcConnectionEventListener.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.setBoolean(listener, true);
        Method method = NamingGrpcConnectionEventListener.class.getDeclaredMethod("redoSubscribe", Set.class);
        method.setAccessible(true);
        method.invoke(listener, failedSubscribes);
        //then
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(1)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
    
    @Test
    public void testRedoSubscribe2()
            throws NacosException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        Set<String> failedSubscribes = new ConcurrentHashSet<>();
        failedSubscribes.add(ServiceInfo.getKey(fullServiceName, cluster));
        //when
        Field connected = NamingGrpcConnectionEventListener.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.setBoolean(listener, false);
        Method method = NamingGrpcConnectionEventListener.class.getDeclaredMethod("redoSubscribe", Set.class);
        method.setAccessible(true);
        method.invoke(listener, failedSubscribes);
        //then
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(0)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
    
    @Test
    public void testRedoSubscribe3()
            throws NacosException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException, InterruptedException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        Set<String> failedSubscribes = new ConcurrentHashSet<>();
        failedSubscribes.add(ServiceInfo.getKey(fullServiceName, cluster));
        //when
        Field connected = NamingGrpcConnectionEventListener.class.getDeclaredField("connected");
        connected.setAccessible(true);
        connected.setBoolean(listener, true);
        Field executorFiled = NamingGrpcConnectionEventListener.class.getDeclaredField("redoExecutorService");
        executorFiled.setAccessible(true);
        ScheduledExecutorService executorService = (ScheduledExecutorService) executorFiled.get(listener);
        Method method = NamingGrpcConnectionEventListener.class.getDeclaredMethod("redoSubscribe", Set.class);
        method.setAccessible(true);
        executorService.schedule(() -> method.invoke(listener, failedSubscribes), 0, TimeUnit.MILLISECONDS);
        //then
        Thread.sleep(100);
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(1)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
}