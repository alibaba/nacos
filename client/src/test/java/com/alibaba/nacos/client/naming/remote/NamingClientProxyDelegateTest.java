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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingClientProxyDelegateTest {
    
    private static final String TEST_NAMESPACE = "ns1";
    
    @Mock
    ServiceInfoHolder holder;
    
    @Mock
    NamingGrpcClientProxy mockGrpcClient;
    
    NamingClientProxyDelegate delegate;
    
    InstancesChangeNotifier notifier;
    
    NacosClientProperties nacosClientProperties;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties props = new Properties();
        props.setProperty("serverAddr", "localhost");
        nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        notifier = new InstancesChangeNotifier();
        delegate = new NamingClientProxyDelegate(TEST_NAMESPACE, holder, nacosClientProperties, notifier);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        delegate.shutdown();
    }
    
    @Test
    void testRegisterEphemeralServiceByGrpc() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        delegate.registerService(serviceName, groupName, instance);
        verify(mockGrpcClient, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    void testBatchRegisterServiceByGrpc() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        List<Instance> instanceList = new ArrayList<>();
        delegate.batchRegisterService(serviceName, groupName, instanceList);
        verify(mockGrpcClient, times(1)).batchRegisterService(serviceName, groupName, instanceList);
    }
    
    @Test
    void testBatchDeregisterServiceByGrpc() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        List<Instance> instanceList = new ArrayList<>();
        delegate.batchDeregisterService(serviceName, groupName, instanceList);
        verify(mockGrpcClient, times(1)).batchDeregisterService(serviceName, groupName, instanceList);
        reset(mockGrpcClient);
        instanceList.add(new Instance());
        delegate.batchDeregisterService(serviceName, groupName, instanceList);
        verify(mockGrpcClient, times(1)).batchDeregisterService(serviceName, groupName, instanceList);
    }
    
    @Test
    void testRegisterPersistentServiceByGrpc() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        // persistent instance
        instance.setEphemeral(false);
        // when server support register persistent instance by grpc, will use grpc to register
        when(mockGrpcClient.isAbilitySupportedByServer(
                AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC)).thenReturn(true);
        delegate.registerService(serviceName, groupName, instance);
        verify(mockGrpcClient, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    void testRegisterPersistentServiceByHttp() throws NacosException, NoSuchFieldException, IllegalAccessException {
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
        // persistent instance
        instance.setEphemeral(false);
        // when server do not support register persistent instance by grpc, will use http to register
        delegate.registerService(serviceName, groupName, instance);
        verify(mockHttpClient, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    void testDeregisterEphemeralServiceGrpc() throws NacosException {
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
    void testDeregisterPersistentServiceGrpc() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        // persistent instance
        instance.setEphemeral(false);
        // when server support deregister persistent instance by grpc, will use grpc to deregister
        when(mockGrpcClient.isAbilitySupportedByServer(
                AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC)).thenReturn(true);
        delegate.deregisterService(serviceName, groupName, instance);
        verify(mockGrpcClient, times(1)).deregisterService(serviceName, groupName, instance);
    }
    
    @Test
    void testDeregisterPersistentServiceHttp() throws NacosException, NoSuchFieldException, IllegalAccessException {
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
    void testUpdateInstance() {
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        Assertions.assertDoesNotThrow(() -> {
            delegate.updateInstance(serviceName, groupName, instance);
        });
    }
    
    @Test
    void testQueryInstancesOfService() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        delegate.queryInstancesOfService(serviceName, groupName, clusters, false);
        verify(mockGrpcClient, times(1)).queryInstancesOfService(serviceName, groupName, clusters, false);
    }
    
    @Test
    void testQueryService() throws NacosException {
        Service service = delegate.queryService("a", "b");
        assertNull(service);
    }
    
    @Test
    void testCreateService() {
        Service service = new Service();
        Assertions.assertDoesNotThrow(() -> {
            delegate.createService(service, new NoneSelector());
        });
    }
    
    @Test
    void testDeleteService() throws NacosException {
        assertFalse(delegate.deleteService("service", "group1"));
    }
    
    @Test
    void testUpdateService() {
        Service service = new Service();
        Assertions.assertDoesNotThrow(() -> {
            delegate.updateService(service, new ExpressionSelector());
        });
    }
    
    @Test
    void testGetServiceList() throws NacosException {
        AbstractSelector selector = new ExpressionSelector();
        int pageNo = 1;
        int pageSize = 10;
        String groupName = "group2";
        delegate.getServiceList(pageNo, pageSize, groupName, selector);
        verify(mockGrpcClient, times(1)).getServiceList(pageNo, pageSize, groupName, selector);
        
    }
    
    @Test
    void testSubscribe() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        ServiceInfo info = new ServiceInfo();
        info.setName(serviceName);
        info.setGroupName(groupName);
        info.setClusters(clusters);
        when(mockGrpcClient.subscribe(serviceName, groupName, clusters)).thenReturn(info);
        
        ServiceInfo actual = delegate.subscribe(serviceName, groupName, clusters);
        assertEquals(info, actual);
        verify(mockGrpcClient, times(1)).subscribe(serviceName, groupName, clusters);
        verify(holder, times(1)).processServiceInfo(info);
        
    }
    
    @Test
    void testUnsubscribe() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        delegate.unsubscribe(serviceName, groupName, clusters);
        verify(mockGrpcClient, times(1)).unsubscribe(serviceName, groupName, clusters);
    }
    
    @Test
    void testServerHealthy() {
        Mockito.when(mockGrpcClient.serverHealthy()).thenReturn(true);
        assertTrue(delegate.serverHealthy());
    }
    
    @Test
    void testIsSubscribed() throws NacosException {
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        assertFalse(delegate.isSubscribed(serviceName, groupName, clusters));
        when(mockGrpcClient.isSubscribed(serviceName, groupName, clusters)).thenReturn(true);
        assertTrue(delegate.isSubscribed(serviceName, groupName, clusters));
    }
    
    @Test
    void testShutdown() throws NacosException {
        delegate.shutdown();
        verify(mockGrpcClient, times(1)).shutdown();
    }
}
