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

package com.alibaba.nacos.client.naming.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NamingClientProxyDelegateTest {
    
    @Test
    public void testRegisterServiceByGrpc() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        // use http
        instance.setEphemeral(true);
        delegate.registerService(serviceName, groupName, instance);
        verify(mockGrpcClient, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRegisterServiceByHttp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingHttpClientProxy mockHttpClient = Mockito.mock(NamingHttpClientProxy.class);
        Field mockHttpClientField = NamingClientProxyDelegate.class.getDeclaredField("httpClientProxy");
        mockHttpClientField.setAccessible(true);
        mockHttpClientField.set(delegate, mockHttpClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        // use grpc
        instance.setEphemeral(false);
        delegate.registerService(serviceName, groupName, instance);
        verify(mockHttpClient, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testDeregisterServiceGrpc() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        // use grpc
        instance.setEphemeral(true);
        delegate.deregisterService(serviceName, groupName, instance);
        verify(mockGrpcClient, times(1)).deregisterService(serviceName, groupName, instance);
    }
    
    @Test
    public void testDeregisterServiceHttp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingHttpClientProxy mockHttpClient = Mockito.mock(NamingHttpClientProxy.class);
        Field mockHttpClientField = NamingClientProxyDelegate.class.getDeclaredField("httpClientProxy");
        mockHttpClientField.setAccessible(true);
        mockHttpClientField.set(delegate, mockHttpClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        // use http
        instance.setEphemeral(false);
        delegate.deregisterService(serviceName, groupName, instance);
        verify(mockHttpClient, times(1)).deregisterService(serviceName, groupName, instance);
    }
    
    @Test
    public void testUpdateInstance() throws NacosException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        try {
            delegate.updateInstance(serviceName, groupName, instance);
        } catch (Exception e) {
            Assert.fail();
        }
    }
    
    @Test
    public void testQueryInstancesOfService() throws NacosException, IllegalAccessException, NoSuchFieldException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        delegate.queryInstancesOfService(serviceName, groupName, clusters, 0, false);
        verify(mockGrpcClient, times(1)).queryInstancesOfService(serviceName, groupName, clusters, 0, false);
    }
    
    @Test
    public void testQueryService() throws NacosException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        Service service = delegate.queryService("a", "b");
        Assert.assertNull(service);
    }
    
    @Test
    public void testCreateService() throws NacosException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        Service service = new Service();
        try {
            delegate.createService(service, new NoneSelector());
        } catch (Exception e) {
            Assert.fail();
        }
    }
    
    @Test
    public void testDeleteService() throws NacosException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        Assert.assertFalse(delegate.deleteService("service", "group1"));
    }
    
    @Test
    public void testUpdateService() throws NacosException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        Service service = new Service();
        try {
            delegate.updateService(service, new ExpressionSelector());
        } catch (Exception e) {
            Assert.fail();
        }
    }
    
    @Test
    public void testGetServiceList() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        AbstractSelector selector = new ExpressionSelector();
        int pageNo = 1;
        int pageSize = 10;
        String groupName = "group2";
        delegate.getServiceList(pageNo, pageSize, groupName, selector);
        verify(mockGrpcClient, times(1)).getServiceList(pageNo, pageSize, groupName, selector);
        
    }
    
    @Test
    public void testSubscribe() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        ServiceInfo info = new ServiceInfo();
        info.setName(serviceName);
        info.setGroupName(groupName);
        info.setClusters(clusters);
        when(mockGrpcClient.subscribe(serviceName, groupName, clusters)).thenReturn(info);
        
        ServiceInfo actual = delegate.subscribe(serviceName, groupName, clusters);
        Assert.assertEquals(info, actual);
        verify(mockGrpcClient, times(1)).subscribe(serviceName, groupName, clusters);
        verify(holder, times(1)).processServiceInfo(info);
        
    }
    
    @Test
    public void testUnsubscribe() throws NacosException, IllegalAccessException, NoSuchFieldException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        
        delegate.unsubscribe(serviceName, groupName, clusters);
        verify(mockGrpcClient, times(1)).unsubscribe(serviceName, groupName, clusters);
    }
    
    @Test
    public void testUpdateBeatInfo() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingHttpClientProxy mockHttpClient = Mockito.mock(NamingHttpClientProxy.class);
        Field mockHttpClientField = NamingClientProxyDelegate.class.getDeclaredField("httpClientProxy");
        mockHttpClientField.setAccessible(true);
        mockHttpClientField.set(delegate, mockHttpClient);
        
        //HTTP ONLY
        Set<Instance> set = new HashSet<>();
        delegate.updateBeatInfo(set);
        verify(mockHttpClient, times(1)).updateBeatInfo(set);
    }
    
    @Test
    public void testServerHealthy() throws IllegalAccessException, NacosException, NoSuchFieldException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        Mockito.when(mockGrpcClient.serverHealthy()).thenReturn(true);
        Assert.assertTrue(delegate.serverHealthy());
    }
    
    @Test
    public void testShutdown() throws NacosException, IllegalAccessException, NoSuchFieldException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, props, notifier);
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        delegate.shutdown();
        verify(mockGrpcClient, times(1)).shutdown();
    }
}