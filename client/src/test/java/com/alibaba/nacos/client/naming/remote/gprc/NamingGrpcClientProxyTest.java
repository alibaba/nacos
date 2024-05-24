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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.PersistentInstanceRequest;
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
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientConfig;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo  remove strictness lenient
@MockitoSettings(strictness = Strictness.LENIENT)
class NamingGrpcClientProxyTest {
    
    private static final String NAMESPACE_ID = "ns1";
    
    private static final String SERVICE_NAME = "service1";
    
    private static final String GROUP_NAME = "group1";
    
    private static final String CLUSTERS = "cluster1";
    
    private static final String ORIGIN_SERVER = "www.google.com";
    
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
    
    private Instance persistentInstance;
    
    private String uuid;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        System.setProperty(GrpcConstants.GRPC_RETRY_TIMES, "1");
        System.setProperty(GrpcConstants.GRPC_SERVER_CHECK_TIMEOUT, "100");
        List<String> serverList = Stream.of(ORIGIN_SERVER, "anotherServer").collect(Collectors.toList());
        when(factory.getServerList()).thenReturn(serverList);
        when(factory.genNextServer()).thenReturn(ORIGIN_SERVER);
        prop = new Properties();
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        client = new NamingGrpcClientProxy(NAMESPACE_ID, proxy, factory, nacosClientProperties, holder);
        
        Field uuidField = NamingGrpcClientProxy.class.getDeclaredField("uuid");
        uuidField.setAccessible(true);
        uuid = (String) uuidField.get(client);
        
        assertNotNull(RpcClientFactory.getClient(uuid));
        Field rpcClientField = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClientField.setAccessible(true);
        ((RpcClient) rpcClientField.get(client)).shutdown();
        rpcClientField.set(client, this.rpcClient);
        
        response = new InstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        instance = new Instance();
        instance.setServiceName(SERVICE_NAME);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        
        persistentInstance = new Instance();
        persistentInstance.setServiceName(SERVICE_NAME);
        persistentInstance.setIp("1.1.1.1");
        persistentInstance.setPort(1111);
        persistentInstance.setEphemeral(false);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        System.clearProperty(GrpcConstants.GRPC_RETRY_TIMES);
        System.clearProperty(GrpcConstants.GRPC_SERVER_CHECK_TIMEOUT);
        client.shutdown();
    }
    
    @Test
    void testRegisterService() throws NacosException {
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
    void testRegisterPersistentService() throws NacosException {
        client.registerService(SERVICE_NAME, GROUP_NAME, persistentInstance);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof PersistentInstanceRequest) {
                PersistentInstanceRequest request1 = (PersistentInstanceRequest) request;
                return request1.getType().equals(NamingRemoteConstants.REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    void testRegisterServiceThrowsNacosException() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            when(this.rpcClient.request(Mockito.any())).thenReturn(ErrorResponse.build(400, "err args"));
            
            try {
                client.registerService(SERVICE_NAME, GROUP_NAME, instance);
            } catch (NacosException ex) {
                assertNull(ex.getCause());
                
                throw ex;
            }
        });
        assertTrue(exception.getMessage().contains("err args"));
    }
    
    @Test
    void testRegisterServiceThrowsException() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            when(this.rpcClient.request(Mockito.any())).thenReturn(null);
            
            try {
                client.registerService(SERVICE_NAME, GROUP_NAME, instance);
            } catch (NacosException ex) {
                assertEquals(NullPointerException.class, ex.getCause().getClass());
                
                throw ex;
            }
        });
        assertTrue(exception.getMessage().contains("Request nacos server failed: "));
    }
    
    @Test
    void testDeregisterService() throws NacosException {
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
    void testDeregisterPersistentService() throws NacosException {
        client.deregisterService(SERVICE_NAME, GROUP_NAME, persistentInstance);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof PersistentInstanceRequest) {
                PersistentInstanceRequest request1 = (PersistentInstanceRequest) request;
                return request1.getType().equals(NamingRemoteConstants.DE_REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    void testDeregisterServiceForBatchRegistered() throws NacosException {
        try {
            List<Instance> instanceList = new ArrayList<>();
            instance.setHealthy(true);
            instanceList.add(instance);
            instanceList.add(new Instance());
            client.batchRegisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        } catch (Exception ignored) {
        }
        response = new BatchInstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        List<Instance> instanceList = new ArrayList<>();
        instance.setHealthy(true);
        instanceList.add(instance);
        client.deregisterService(SERVICE_NAME, GROUP_NAME, instance);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof BatchInstanceRequest) {
                BatchInstanceRequest request1 = (BatchInstanceRequest) request;
                request1.setRequestId("1");
                return request1.getInstances().size() == 1 && request1.getType()
                        .equals(NamingRemoteConstants.BATCH_REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    void testBatchRegisterService() throws NacosException {
        List<Instance> instanceList = new ArrayList<>();
        instance.setHealthy(true);
        instanceList.add(instance);
        response = new BatchInstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        client.batchRegisterService(SERVICE_NAME, GROUP_NAME, instanceList);
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
    void testBatchDeregisterServiceWithEmptyInstances() throws NacosException {
        assertThrows(NacosException.class, () -> {
            client.batchDeregisterService(SERVICE_NAME, GROUP_NAME, Collections.EMPTY_LIST);
        });
    }
    
    @Test
    void testBatchDeregisterServiceWithoutCacheData() throws NacosException {
        assertThrows(NacosException.class, () -> {
            List<Instance> instanceList = new ArrayList<>();
            instance.setHealthy(true);
            instanceList.add(instance);
            client.batchDeregisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        });
    }
    
    @Test
    void testBatchDeregisterServiceNotBatchData() throws NacosException {
        assertThrows(NacosException.class, () -> {
            client.registerService(SERVICE_NAME, GROUP_NAME, instance);
            List<Instance> instanceList = new ArrayList<>();
            instance.setHealthy(true);
            instanceList.add(instance);
            client.batchDeregisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        });
    }
    
    @Test
    void testBatchDeregisterServiceWithEmptyBatchData() throws NacosException {
        assertThrows(NacosException.class, () -> {
            try {
                client.batchRegisterService(SERVICE_NAME, GROUP_NAME, Collections.EMPTY_LIST);
            } catch (Exception ignored) {
            }
            List<Instance> instanceList = new ArrayList<>();
            instance.setHealthy(true);
            instanceList.add(instance);
            client.batchDeregisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        });
    }
    
    @Test
    void testBatchDeregisterService() throws NacosException {
        try {
            List<Instance> instanceList = new ArrayList<>();
            instance.setHealthy(true);
            instanceList.add(instance);
            instanceList.add(new Instance());
            client.batchRegisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        } catch (Exception ignored) {
        }
        response = new BatchInstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        List<Instance> instanceList = new ArrayList<>();
        instance.setHealthy(true);
        instanceList.add(instance);
        client.batchDeregisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof BatchInstanceRequest) {
                BatchInstanceRequest request1 = (BatchInstanceRequest) request;
                request1.setRequestId("1");
                return request1.getInstances().size() == 1 && request1.getType()
                        .equals(NamingRemoteConstants.BATCH_REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    void testBatchDeregisterServiceWithOtherPortInstance() throws NacosException {
        try {
            List<Instance> instanceList = new ArrayList<>();
            instance.setHealthy(true);
            instanceList.add(instance);
            instanceList.add(new Instance());
            client.batchRegisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        } catch (Exception ignored) {
        }
        response = new BatchInstanceResponse();
        when(this.rpcClient.request(any())).thenReturn(response);
        Instance otherPortInstance = new Instance();
        otherPortInstance.setServiceName(SERVICE_NAME);
        otherPortInstance.setIp("1.1.1.1");
        otherPortInstance.setPort(2222);
        List<Instance> instanceList = new ArrayList<>();
        instanceList.add(otherPortInstance);
        client.batchDeregisterService(SERVICE_NAME, GROUP_NAME, instanceList);
        verify(this.rpcClient, times(2)).request(argThat(request -> {
            if (request instanceof BatchInstanceRequest) {
                BatchInstanceRequest request1 = (BatchInstanceRequest) request;
                request1.setRequestId("1");
                return request1.getInstances().size() == 2 && request1.getType()
                        .equals(NamingRemoteConstants.BATCH_REGISTER_INSTANCE);
            }
            return false;
        }));
    }
    
    @Test
    void testUpdateInstance() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        client.updateInstance(SERVICE_NAME, GROUP_NAME, instance);
    }
    
    @Test
    void testQueryInstancesOfService() throws Exception {
        QueryServiceResponse res = new QueryServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        ServiceInfo actual = client.queryInstancesOfService(SERVICE_NAME, GROUP_NAME, CLUSTERS, false);
        assertEquals(info, actual);
    }
    
    @Test
    void testQueryService() throws Exception {
        Service service = client.queryService(SERVICE_NAME, GROUP_NAME);
        assertNull(service);
    }
    
    @Test
    void testCreateService() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        client.createService(service, selector);
    }
    
    @Test
    void testDeleteService() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        assertFalse(client.deleteService(SERVICE_NAME, GROUP_NAME));
    }
    
    @Test
    void testUpdateService() throws NacosException {
        //TODO thrown.expect(UnsupportedOperationException.class);
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        client.updateService(service, selector);
    }
    
    @Test
    void testGetServiceList() throws Exception {
        ServiceListResponse res = new ServiceListResponse();
        List<String> services = Arrays.asList("service1", "service2");
        res.setServiceNames(services);
        res.setCount(5);
        when(this.rpcClient.request(any())).thenReturn(res);
        AbstractSelector selector = new NoneSelector();
        ListView<String> serviceList = client.getServiceList(1, 10, GROUP_NAME, selector);
        assertEquals(5, serviceList.getCount());
        assertEquals(services, serviceList.getData());
    }
    
    @Test
    void testGetServiceListForLabelSelector() throws Exception {
        ServiceListResponse res = new ServiceListResponse();
        List<String> services = Arrays.asList("service1", "service2");
        res.setServiceNames(services);
        res.setCount(5);
        when(this.rpcClient.request(any())).thenReturn(res);
        AbstractSelector selector = new ExpressionSelector();
        ListView<String> serviceList = client.getServiceList(1, 10, GROUP_NAME, selector);
        assertEquals(5, serviceList.getCount());
        assertEquals(services, serviceList.getData());
    }
    
    @Test
    void testSubscribe() throws Exception {
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        ServiceInfo actual = client.subscribe(SERVICE_NAME, GROUP_NAME, CLUSTERS);
        assertEquals(info, actual);
    }
    
    @Test
    void testUnsubscribe() throws Exception {
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        client.unsubscribe(SERVICE_NAME, GROUP_NAME, CLUSTERS);
        verify(this.rpcClient, times(1)).request(argThat(request -> {
            if (request instanceof SubscribeServiceRequest) {
                SubscribeServiceRequest request1 = (SubscribeServiceRequest) request;
                
                // verify request fields
                return !request1.isSubscribe() && SERVICE_NAME.equals(request1.getServiceName()) && GROUP_NAME.equals(
                        request1.getGroupName()) && CLUSTERS.equals(request1.getClusters()) && NAMESPACE_ID.equals(
                        request1.getNamespace());
            }
            return false;
        }));
    }
    
    @Test
    void testIsSubscribed() throws NacosException {
        SubscribeServiceResponse res = new SubscribeServiceResponse();
        ServiceInfo info = new ServiceInfo(GROUP_NAME + "@@" + SERVICE_NAME + "@@" + CLUSTERS);
        res.setServiceInfo(info);
        when(this.rpcClient.request(any())).thenReturn(res);
        assertFalse(client.isSubscribed(SERVICE_NAME, GROUP_NAME, CLUSTERS));
        client.subscribe(SERVICE_NAME, GROUP_NAME, CLUSTERS);
        assertTrue(client.isSubscribed(SERVICE_NAME, GROUP_NAME, CLUSTERS));
    }
    
    @Test
    void testServerHealthy() {
        when(this.rpcClient.isRunning()).thenReturn(true);
        assertTrue(client.serverHealthy());
        verify(this.rpcClient, times(1)).isRunning();
    }
    
    @Test
    void testIsAbilitySupportedByServer1() {
        when(this.rpcClient.getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC)).thenReturn(
                AbilityStatus.SUPPORTED);
        assertTrue(client.isAbilitySupportedByServer(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
        verify(this.rpcClient, times(1)).getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC);
    }
    
    @Test
    void testIsAbilitySupportedByServer2() {
        when(this.rpcClient.getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC)).thenReturn(
                AbilityStatus.NOT_SUPPORTED);
        assertFalse(client.isAbilitySupportedByServer(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
        verify(this.rpcClient, times(1)).getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC);
    }
    
    @Test
    void testIsAbilitySupportedByServer3() {
        when(this.rpcClient.getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC)).thenReturn(
                AbilityStatus.UNKNOWN);
        assertFalse(client.isAbilitySupportedByServer(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
        verify(this.rpcClient, times(1)).getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC);
    }
    
    @Test
    void testIsAbilitySupportedByServer4() {
        when(this.rpcClient.getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC)).thenReturn(
                null);
        assertFalse(client.isAbilitySupportedByServer(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
        verify(this.rpcClient, times(1)).getConnectionAbility(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC);
    }
    
    @Test
    void testShutdown() throws Exception {
        client.shutdown();
        assertNull(RpcClientFactory.getClient(uuid));
        //verify(this.rpcClient, times(1)).shutdown();
    }
    
    @Test
    void testShutdownWithException() throws NoSuchFieldException, IllegalAccessException, NacosException {
        Field field = RpcClientFactory.class.getDeclaredField("CLIENT_MAP");
        field.setAccessible(true);
        Map<String, RpcClient> map = (Map<String, RpcClient>) field.get(RpcClientFactory.class);
        RpcClient oldClient = map.get(uuid);
        try {
            oldClient.shutdown();
        } catch (NacosException ignored) {
        }
        map.put(uuid, rpcClient);
        doThrow(new NacosException()).when(rpcClient).shutdown();
    }
    
    @Test
    void testIsEnable() {
        when(this.rpcClient.isRunning()).thenReturn(true);
        assertTrue(client.isEnable());
        verify(this.rpcClient, times(1)).isRunning();
    }
    
    @Test
    void testServerListChanged() throws Exception {
        
        RpcClient rpc = new RpcClient(new RpcClientConfig() {
            @Override
            public String name() {
                return "testServerListHasChanged";
            }
            
            @Override
            public int retryTimes() {
                return 3;
            }
            
            @Override
            public long timeOutMills() {
                return 3000L;
            }
            
            @Override
            public long connectionKeepAlive() {
                return 5000L;
            }
            
            @Override
            public int healthCheckRetryTimes() {
                return 1;
            }
            
            @Override
            public long healthCheckTimeOut() {
                return 3000L;
            }
            
            @Override
            public Map<String, String> labels() {
                return new HashMap<>();
            }
        }, factory) {
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
            TimeUnit.MILLISECONDS.sleep(200);
            if (--retry < 0) {
                fail("rpc is not running");
            }
        }
        
        assertEquals(ORIGIN_SERVER, rpc.getCurrentServer().getServerIp());
        
        String newServer = "www.aliyun.com";
        when(factory.genNextServer()).thenReturn(newServer);
        when(factory.getServerList()).thenReturn(Stream.of(newServer, "anotherServer").collect(Collectors.toList()));
        NotifyCenter.publishEvent(new ServerListChangedEvent());
        
        retry = 10;
        while (ORIGIN_SERVER.equals(rpc.getCurrentServer().getServerIp())) {
            TimeUnit.MILLISECONDS.sleep(200);
            if (--retry < 0) {
                fail("failed to auth switch server");
            }
        }
        
        assertEquals(newServer, rpc.getCurrentServer().getServerIp());
    }
    
    @Test
    void testConfigAppNameLabels() throws Exception {
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        client = new NamingGrpcClientProxy(NAMESPACE_ID, proxy, factory, nacosClientProperties, holder);
        Field rpcClientField = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClientField.setAccessible(true);
        RpcClient rpcClient = (RpcClient) rpcClientField.get(client);
        Field clientConfig = GrpcClient.class.getDeclaredField("clientConfig");
        clientConfig.setAccessible(true);
        GrpcClientConfig config = (GrpcClientConfig) clientConfig.get(rpcClient);
        String appName = config.labels().get(Constants.APPNAME);
        assertNotNull(appName);
    }
}
