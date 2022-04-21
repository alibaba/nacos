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
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.BatchInstanceResponse;
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
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NamingGrpcClientProxyTest {
    
    private static final String NAMESPACE_ID = "ns1";
    
    private static final String SERVICE_NAME = "service1";
    
    private static final String GROUP_NAME = "group1";
    
    private static final String CLUSTERS = "cluster1";
    
    private static final String ORIGIN_SERVER = "127.0.0.1";
    
    @Mock
    private SecurityProxy proxy;
    
    @Mock
    private ServerListFactory factory;
    
    @Mock
    private ServiceInfoHolder holder;
    
    @Mock
    private RpcClient rpcClient;
    
    private Properties prop;
    
    private NamingGrpcClientProxy client;
    
    private Response response;
    
    private Instance instance;
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Before
    public void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        List<String> serverList = Stream.of(ORIGIN_SERVER, "anotherServer").collect(Collectors.toList());
        when(factory.getServerList()).thenReturn(serverList);
        when(factory.genNextServer()).thenReturn(ORIGIN_SERVER);
        
        prop = new Properties();
        client = new NamingGrpcClientProxy(NAMESPACE_ID, proxy, factory, prop, holder);
        Field rpcClientField = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClientField.setAccessible(true);
        rpcClientField.set(client, this.rpcClient);
        response = new InstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        instance = new Instance();
        instance.setServiceName(SERVICE_NAME);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        instance.setHealthy(true);
    }
    
    @Test
    public void testRegisterService() throws NacosException {
        client.registerService(SERVICE_NAME, GROUP_NAME, instance);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof InstanceRequest) {
                InstanceRequest request1 = (InstanceRequest) request;
                return request1.getType().equals(NamingRemoteConstants.REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    public void testBatchRegisterService() throws NacosException {
        List<Instance> instanceList = new ArrayList<>();
        instance.setHealthy(true);
        instanceList.add(instance);
        response = new BatchInstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        client.batchRegisterInstance(SERVICE_NAME, GROUP_NAME, instanceList);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof BatchInstanceRequest) {
                BatchInstanceRequest request1 = (BatchInstanceRequest) request;
                request1.setRequestId("1");
                return request1.getType().equals(NamingRemoteConstants.BATCH_REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    public void testDeregisterService() throws NacosException {
        client.deregisterService(SERVICE_NAME, GROUP_NAME, instance);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof InstanceRequest) {
                InstanceRequest request1 = (InstanceRequest) request;
                return request1.getType().equals(NamingRemoteConstants.DE_REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    public void testUpdateInstance() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        client.updateInstance(SERVICE_NAME, GROUP_NAME, instance);
    }
    
    @Test
    public void testQueryInstancesOfService() throws Exception {
        QueryServiceResponse res = new QueryServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        ServiceInfo actual = client.queryInstancesOfService(SERVICE_NAME, GROUP_NAME, CLUSTERS, 0, false);
        Assert.assertEquals(info, actual);
    }
    
    @Test
    public void testQueryService() throws Exception {
        Service service = client.queryService(SERVICE_NAME, GROUP_NAME);
        Assert.assertNull(service);
    }
    
    @Test
    public void testCreateService() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        client.createService(service, selector);
    }
    
    @Test
    public void testDeleteService() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        Assert.assertFalse(client.deleteService(SERVICE_NAME, GROUP_NAME));
    }
    
    @Test
    public void testUpdateService() throws NacosException {
        //TODO thrown.expect(UnsupportedOperationException.class);
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        client.updateService(service, selector);
    }
    
    @Test
    public void testGetServiceList() throws Exception {
        ServiceListResponse res = new ServiceListResponse();
        List<String> services = Arrays.asList("service1", "service2");
        res.setServiceNames(services);
        res.setCount(5);
        when(this.rpcClient.request(any())).thenReturn(res);
        AbstractSelector selector = new NoneSelector();
        ListView<String> serviceList = client.getServiceList(1, 10, GROUP_NAME, selector);
        Assert.assertEquals(5, serviceList.getCount());
        Assert.assertEquals(services, serviceList.getData());
    }
    
    @Test
    public void testSubscribe() throws Exception {
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        ServiceInfo actual = client.subscribe(SERVICE_NAME, GROUP_NAME, CLUSTERS);
        Assert.assertEquals(info, actual);
    }
    
    @Test
    public void testUnsubscribe() throws Exception {
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        client.unsubscribe(SERVICE_NAME, GROUP_NAME, CLUSTERS);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof SubscribeServiceRequest) {
                SubscribeServiceRequest request1 = (SubscribeServiceRequest) request;
                // not subscribe
                return !request1.isSubscribe();
            }
            return false;
        }));
    }
    
    @Test
    public void testUpdateBeatInfo() {
        //TODO thrown.expect(UnsupportedOperationException.class);
        client.updateBeatInfo(new HashSet<>());
    }
    
    @Test
    public void testServerHealthy() {
        when(this.rpcClient.isRunning()).thenReturn(true);
        Assert.assertTrue(client.serverHealthy());
        verify(this.rpcClient, times(1)).isRunning();
    }
    
    @Test
    public void testShutdown() throws Exception {
        client.shutdown();
        verify(this.rpcClient, times(1)).shutdown();
    }
    
    @Test
    public void testIsEnable() {
        when(this.rpcClient.isRunning()).thenReturn(true);
        Assert.assertTrue(client.isEnable());
        verify(this.rpcClient, times(1)).isRunning();
    }
    
    @Test
    public void testServerListChanged() throws Exception {
        
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
        Field listenerField = NamingGrpcClientProxy.class.getDeclaredField("redoService");
        listenerField.setAccessible(true);
        NamingGrpcRedoService listener = (NamingGrpcRedoService) listenerField.get(client);
        rpc.registerConnectionListener(listener);
        rpc.start();
        int retry = 10;
        while (!rpc.isRunning()) {
            TimeUnit.SECONDS.sleep(1);
            if (--retry < 0) {
                Assert.fail("rpc is not running");
            }
        }
        
        Assert.assertEquals(ORIGIN_SERVER, rpc.getCurrentServer().getServerIp());
        
        String newServer = "www.aliyun.com";
        when(factory.genNextServer()).thenReturn(newServer);
        when(factory.getServerList()).thenReturn(Stream.of(newServer, "anotherServer").collect(Collectors.toList()));
        NotifyCenter.publishEvent(new ServerListChangedEvent());
        
        retry = 10;
        while (ORIGIN_SERVER.equals(rpc.getCurrentServer().getServerIp())) {
            TimeUnit.SECONDS.sleep(1);
            if (--retry < 0) {
                Assert.fail("failed to auth switch server");
            }
        }
        
        Assert.assertEquals(newServer, rpc.getCurrentServer().getServerIp());
    }
    
}
