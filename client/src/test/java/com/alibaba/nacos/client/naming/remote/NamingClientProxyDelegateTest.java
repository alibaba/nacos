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
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    
    public void testDeregisterService() {
    }
    
    public void testUpdateInstance() {
    }
    
    public void testQueryInstancesOfService() {
    }
    
    public void testQueryService() {
    }
    
    public void testCreateService() {
    }
    
    public void testDeleteService() {
    }
    
    public void testUpdateService() {
    }
    
    public void testGetServiceList() {
    }
    
    public void testSubscribe() {
    }
    
    public void testUnsubscribe() {
    }
    
    public void testUpdateBeatInfo() {
    }
    
    public void testServerHealthy() {
    }
    
    public void testShutdown() {
    }
}