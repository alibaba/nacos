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
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.remote.DefaultRequestFuture;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NamingGrpcClientProxyTest {
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Test
    public void testRegisterService() throws NacosException, NoSuchFieldException, IllegalAccessException {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        // inject rpcClient;
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        Response res = new InstanceResponse();
        when(rpc.request(any())).thenReturn(res);
        
        String groupName = "group1";
        
        // when
        client.registerService(serviceName, groupName, instance);
        
        // then
        verify(rpc, times(1)).request(argThat(new ArgumentMatcher<Request>() {
            @Override
            public boolean matches(Request request) {
                if (request instanceof InstanceRequest) {
                    InstanceRequest request1 = (InstanceRequest) request;
                    return request1.getType().equals(NamingRemoteConstants.REGISTER_INSTANCE);
                }
                return false;
            }
        }));
        
    }
    
    @Test
    public void testDeregisterService() throws NacosException, NoSuchFieldException, IllegalAccessException {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        // inject rpcClient;
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        Response res = new InstanceResponse();
        when(rpc.request(any())).thenReturn(res);
        
        String groupName = "group1";
        
        // when
        client.deregisterService(serviceName, groupName, instance);
        
        // then
        verify(rpc, times(1)).request(argThat(new ArgumentMatcher<Request>() {
            @Override
            public boolean matches(Request request) {
                if (request instanceof InstanceRequest) {
                    InstanceRequest request1 = (InstanceRequest) request;
                    return request1.getType().equals(NamingRemoteConstants.DE_REGISTER_INSTANCE);
                }
                return false;
            }
        }));
    }
    
    @Test
    public void testUpdateInstance() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        
        String groupName = "group1";
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        client.updateInstance(serviceName, groupName, instance);
    }
    
    @Test
    public void testQueryInstancesOfService() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        QueryServiceResponse res = new QueryServiceResponse();
        String clusters = "cluster1";
        String groupName = "group1";
        
        ServiceInfo info = new ServiceInfo(groupName + "@@" + serviceName + "@@" + clusters);
        res.setServiceInfo(info);
        when(rpc.request(any())).thenReturn(res);
        
        // when
        ServiceInfo actual = client.queryInstancesOfService(serviceName, groupName, clusters, 0, false);
        
        Assert.assertEquals(info, actual);
    }
    
    @Test
    public void testQueryService() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        // when
        Service service = client.queryService(serviceName, groupName);
        // then
        Assert.assertNull(service);
    }
    
    @Test
    public void testCreateService() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        
        // when
        client.createService(service, selector);
        
    }
    
    @Test
    public void testDeleteService() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        // when
        Assert.assertFalse(client.deleteService(serviceName, groupName));
    }
    
    @Test
    public void testUpdateService() throws NacosException {
        //TODO thrown.expect(UnsupportedOperationException.class);
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        // when
        client.updateService(service, selector);
    }
    
    @Test
    public void testGetServiceList() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        ServiceListResponse res = new ServiceListResponse();
        
        List<String> services = Arrays.asList("service1", "service2");
        res.setServiceNames(services);
        res.setCount(5);
        when(rpc.request(any())).thenReturn(res);
        
        AbstractSelector selector = new NoneSelector();
        String groupName = "group1";
        
        // when
        ListView<String> serviceList = client.getServiceList(1, 10, groupName, selector);
        
        Assert.assertEquals(5, serviceList.getCount());
        Assert.assertEquals(services, serviceList.getData());
    }
    
    @Test
    public void testSubscribe() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        String clusters = "cluster1";
        String groupName = "group1";
        
        ServiceInfo info = new ServiceInfo(groupName + "@@" + serviceName + "@@" + clusters);
        res.setServiceInfo(info);
        when(rpc.request(any())).thenReturn(res);
        
        // when
        ServiceInfo actual = client.subscribe(serviceName, groupName, clusters);
        
        Assert.assertEquals(info, actual);
    }
    
    @Test
    public void testUnsubscribe() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        String serviceName = "service1";
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        String clusters = "cluster1";
        String groupName = "group1";
        
        ServiceInfo info = new ServiceInfo(groupName + "@@" + serviceName + "@@" + clusters);
        res.setServiceInfo(info);
        when(rpc.request(any())).thenReturn(res);
        
        // when
        client.unsubscribe(serviceName, groupName, clusters);
        
        verify(rpc, times(1)).request(argThat(new ArgumentMatcher<Request>() {
            @Override
            public boolean matches(Request request) {
                if (request instanceof SubscribeServiceRequest) {
                    SubscribeServiceRequest request1 = (SubscribeServiceRequest) request;
                    // not subscribe
                    return !request1.isSubscribe();
                }
                return false;
            }
        }));
    }
    
    @Test
    public void testUpdateBeatInfo() throws NacosException {
        //TODO thrown.expect(UnsupportedOperationException.class);
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        // when
        client.updateBeatInfo(new HashSet<>());
    }
    
    @Test
    public void testServerHealthy() throws NacosException, IllegalAccessException, NoSuchFieldException {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        when(rpc.isRunning()).thenReturn(true);
        
        // when
        Assert.assertTrue(client.serverHealthy());
        
        verify(rpc, times(1)).isRunning();
    }
    
    @Test
    public void testShutdown() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        when(rpc.isRunning()).thenReturn(true);
        
        // when
        client.shutdown();
        
        verify(rpc, times(1)).shutdown();
    }
    
    @Test
    public void testIsEnable() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        when(rpc.isRunning()).thenReturn(true);
        
        // when
        Assert.assertTrue(client.isEnable());
        
        verify(rpc, times(1)).isRunning();
    }
    
    @Test
    public void testServerListChanged() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
    
        String originServer = "www.google.com";
        when(factory.getServerList()).thenReturn(Stream.of(originServer, "anotherServer").collect(Collectors.toList()));
        // inject rpcClient;
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        when(factory.genNextServer()).thenReturn(originServer);
    
        RpcClient rpc = new RpcClient("testServerListHasChanged", factory) {
            @Override
            public ConnectionType getConnectionType() {
                return ConnectionType.GRPC;
            }
        
            @Override
            public int rpcPortOffset() {
                return 0;
            }
        
            @Override
            public Connection connectToServer(ServerInfo serverInfo) throws Exception {
                return new Connection(serverInfo) {
                
                    @Override
                    public Response request(Request request, long timeoutMills) throws NacosException {
                        Response response = new Response() {
                        };
                        response.setRequestId(request.getRequestId());
                        return response;
                    }
                
                    @Override
                    public RequestFuture requestFuture(Request request) throws NacosException {
                        return new DefaultRequestFuture("test", request.getRequestId());
                    }
                
                    @Override
                    public void asyncRequest(Request request, RequestCallBack requestCallBack) throws NacosException {
                    
                    }
                
                    @Override
                    public void close() {
                    }
                };
            }
        };
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
    
        rpc.serverListFactory(factory);
        rpc.registerServerRequestHandler(new NamingPushRequestHandler(holder));
        Field listenerField = NamingGrpcClientProxy.class.getDeclaredField("namingGrpcConnectionEventListener");
        listenerField.setAccessible(true);
        NamingGrpcConnectionEventListener listener = (NamingGrpcConnectionEventListener) listenerField.get(client);
        rpc.registerConnectionListener(listener);
        rpc.start();
        int retry = 10;
        while (!rpc.isRunning()) {
            TimeUnit.SECONDS.sleep(1);
            if (--retry < 0) {
                Assert.fail("rpc is not running");
            }
        }
    
        Assert.assertEquals(originServer, rpc.getCurrentServer().getServerIp());
    
        String newServer = "www.aliyun.com";
        when(factory.genNextServer()).thenReturn(newServer);
        when(factory.getServerList()).thenReturn(Stream.of(newServer, "anotherServer").collect(Collectors.toList()));
        NotifyCenter.publishEvent(new ServerListChangedEvent());
    
        retry = 10;
        while (originServer.equals(rpc.getCurrentServer().getServerIp())) {
            TimeUnit.SECONDS.sleep(1);
            if (--retry < 0) {
                Assert.fail("failed to auth switch server");
            }
        }

        Assert.assertEquals(newServer, rpc.getCurrentServer().getServerIp());
    }
}