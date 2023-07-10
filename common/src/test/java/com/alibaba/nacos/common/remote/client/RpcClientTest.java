/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.grpc.DefaultGrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RpcClientTest {
    
    RpcClient rpcClient;
    
    Field serverListFactoryField;
    
    Field reconnectionSignalField;
    
    Method resolveServerInfoMethod;
    
    Method healthCheck;
    
    Answer<?> runAsSync;
    
    Answer<?> notInvoke;
    
    @Mock
    ServerListFactory serverListFactory;
    
    @Mock
    Connection connection;
    
    RpcClientConfig rpcClientConfig;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        rpcClientConfig = spy(new RpcClientConfig() {
            @Override
            public String name() {
                return "test";
            }
            
            @Override
            public int retryTimes() {
                return 1;
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
        });
        rpcClient = spy(new RpcClient(rpcClientConfig) {
            @Override
            public ConnectionType getConnectionType() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) {
                return null;
            }
        });
        
        serverListFactoryField = RpcClient.class.getDeclaredField("serverListFactory");
        serverListFactoryField.setAccessible(true);
        
        reconnectionSignalField = RpcClient.class.getDeclaredField("reconnectionSignal");
        reconnectionSignalField.setAccessible(true);
        Field modifiersField1 = Field.class.getDeclaredField("modifiers");
        modifiersField1.setAccessible(true);
        modifiersField1.setInt(reconnectionSignalField, reconnectionSignalField.getModifiers() & ~Modifier.FINAL);
        
        resolveServerInfoMethod = RpcClient.class.getDeclaredMethod("resolveServerInfo", String.class);
        resolveServerInfoMethod.setAccessible(true);
        
        healthCheck = RpcClient.class.getDeclaredMethod("healthCheck");
        healthCheck.setAccessible(true);
        
        runAsSync = invocationOnMock -> {
            Runnable runnable = (Runnable) invocationOnMock.getArguments()[0];
            runnable.run();
            return null;
        };
        
        notInvoke = invocationOnMock -> null;
    }
    
    @After
    public void tearDown() throws IllegalAccessException {
        rpcClientConfig.labels().clear();
        rpcClient.rpcClientStatus.set(RpcClientStatus.WAIT_INIT);
        serverListFactoryField.set(rpcClient, null);
        ((Queue<?>) reconnectionSignalField.get(rpcClient)).clear();
        rpcClient.currentConnection = null;
        System.clearProperty("nacos.server.port");
        rpcClient.eventLinkedBlockingQueue.clear();
    }
    
    @Test
    public void testInitServerListFactory() {
        rpcClient.rpcClientStatus.set(RpcClientStatus.WAIT_INIT);
        rpcClient.serverListFactory(serverListFactory);
        assertEquals(RpcClientStatus.INITIALIZED, rpcClient.rpcClientStatus.get());
        
        rpcClient.rpcClientStatus.set(RpcClientStatus.INITIALIZED);
        rpcClient.serverListFactory(serverListFactory);
        assertEquals(RpcClientStatus.INITIALIZED, rpcClient.rpcClientStatus.get());
        
        RpcClient client1 = new RpcClient(new RpcClientConfig() {
            @Override
            public String name() {
                return "test";
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
        }, serverListFactory) {
            @Override
            public ConnectionType getConnectionType() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) {
                return null;
            }
        };
        assertEquals(RpcClientStatus.INITIALIZED, client1.rpcClientStatus.get());
        
        RpcClient client2 = new RpcClient(rpcClientConfig, serverListFactory) {
            @Override
            public ConnectionType getConnectionType() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) {
                return null;
            }
        };
        assertEquals(RpcClientStatus.INITIALIZED, client2.rpcClientStatus.get());
    }
    
    @Test
    public void testLabels() {
        when(rpcClientConfig.labels()).thenReturn(Collections.singletonMap("labelKey1", "labelValue1"));
        Map.Entry<String, String> element = rpcClient.getLabels().entrySet().iterator().next();
        assertEquals("labelKey1", element.getKey());
        assertEquals("labelValue1", element.getValue());
        
        // accumulate labels
        Map<String, String> map = new HashMap<>();
        map.put("labelKey2", "labelValue2");
        when(rpcClientConfig.labels()).thenReturn(map);
        assertEquals(1, rpcClient.getLabels().size());
    }
    
    @Test
    public void testOnServerListChangeWhenCurrentConnectionIsNullThenDoNothing() throws IllegalAccessException {
        int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize, afterSize);
    }
    
    @Test
    public void testOnServerListChangeWhenServiceInfoIsNullThenDoNothing() throws IllegalAccessException {
        int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.currentConnection = mock(Connection.class);
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize, afterSize);
    }
    
    @Test
    public void testOnServerListChangeWhenCurrentConnectionIsNotInServerListThenSwitchServerAsync()
            throws IllegalAccessException {
        final int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.currentConnection = new GrpcConnection(new RpcClient.ServerInfo("10.10.10.10", 8848), null);
        doReturn(Collections.singletonList("")).when(serverListFactory).getServerList();
        when(serverListFactory.getServerList()).thenReturn(Collections.singletonList("127.0.0.1"));
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize + 1, afterSize);
    }
    
    @Test
    public void testOnServerListChangeWhenCurrentConnectionIsInServerListThenDoNothing() throws IllegalAccessException {
        final int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.currentConnection = new GrpcConnection(new RpcClient.ServerInfo("10.10.10.10", 8848), null);
        doReturn(Collections.singletonList("http://10.10.10.10:8848")).when(serverListFactory).getServerList();
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize, afterSize);
    }
    
    @Test
    public void testResolveServerInfo1() throws InvocationTargetException, IllegalAccessException {
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "10.10.10.10::8848")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "10.10.10.10:8848")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10:8848"))
                        .getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10::8848"))
                        .getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "https://10.10.10.10::8848"))
                        .getAddress());
    }
    
    @Test
    public void testResolveServerInfo2() throws InvocationTargetException, IllegalAccessException {
        System.setProperty("nacos.server.port", "4424");
        assertEquals("10.10.10.10:4424",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10")).getAddress());
    }
    
    @Test(expected = NacosException.class)
    public void testRequestWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
        rpcClient.currentConnection = connection;
        rpcClient.request(null, 10000);
    }
    
    @Test(expected = NacosException.class)
    public void testRequestWhenTimeoutThenThrowException() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        rpcClient.currentConnection = connection;
        doReturn(null).when(connection).request(any(), anyLong());
        
        rpcClient.request(null, 10000);
    }
    
    @Test(expected = NacosException.class)
    public void testRequestWhenResponseErrorThenThrowException() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        rpcClient.currentConnection = connection;
        doReturn(new ErrorResponse()).when(connection).request(any(), anyLong());
        
        rpcClient.request(null, 10000);
    }
    
    @Test
    public void testRequestWhenResponseUnregisterThenSwitchServer() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        rpcClient.currentConnection = connection;
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(NacosException.UN_REGISTER);
        doReturn(errorResponse).when(connection).request(any(), anyLong());
        Exception exception = null;
        
        try {
            rpcClient.request(mock(Request.class), 10000);
        } catch (Exception e) {
            exception = e;
        }
        
        assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
        verify(rpcClient).switchServerAsync();
        Assert.assertNotNull(exception);
    }
    
    @Test(expected = NacosException.class)
    public void testAsyncRequestWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
        rpcClient.currentConnection = connection;
        RequestCallBack<?> requestCallBack = mock(RequestCallBack.class);
        doReturn(10000L).when(requestCallBack).getTimeout();
        rpcClient.asyncRequest(null, requestCallBack);
    }
    
    @Test
    public void testAsyncRequestWhenSendRequestFailedMannyTimesThenSwitchServer() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        rpcClient.currentConnection = connection;
        doThrow(new NacosException()).when(connection).asyncRequest(any(), any());
        RequestCallBack<?> requestCallBack = mock(RequestCallBack.class);
        doReturn(10000L).when(requestCallBack).getTimeout();
        Exception exception = null;
        
        try {
            rpcClient.asyncRequest(null, requestCallBack);
        } catch (NacosException e) {
            exception = e;
        }
        
        verify(connection, atLeastOnce()).asyncRequest(any(), any());
        verify(rpcClient).switchServerAsyncOnRequestFail();
        Assert.assertNotNull(exception);
        assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
    }
    
    @Test(expected = NacosException.class)
    public void testRequestFutureWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
        rpcClient.currentConnection = connection;
        rpcClient.requestFuture(null);
    }
    
    @Test
    public void testRequestFutureWhenRetryReachMaxRetryTimesThenSwitchServer()
            throws NacosException, IllegalAccessException {
        when(rpcClientConfig.timeOutMills()).thenReturn(5000L);
        when(rpcClientConfig.retryTimes()).thenReturn(3);
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        rpcClient.currentConnection = connection;
        doThrow(NacosException.class).when(connection).requestFuture(any());
        Exception exception = null;
        
        try {
            rpcClient.requestFuture(null);
        } catch (NacosException e) {
            exception = e;
        }
        
        verify(connection, times(3)).requestFuture(any());
        verify(rpcClient).switchServerAsyncOnRequestFail();
        Assert.assertNotNull(exception);
        assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
    }
    
    @Test
    public void testRpcClientShutdownWhenClientDidntStart() throws NacosException {
        RpcClient rpcClient = new RpcClient(new RpcClientConfig() {
            @Override
            public String name() {
                return "test-client";
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
        }) {
            @Override
            public ConnectionType getConnectionType() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) throws Exception {
                return null;
            }
        };
        
        rpcClient.shutdown();
    }
    
    @Test
    public void testHealthCheck() throws IllegalAccessException, NacosException {
        Random random = new Random();
        int retry = random.nextInt(10);
        when(rpcClientConfig.healthCheckRetryTimes()).thenReturn(retry);
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        rpcClient.currentConnection = connection;
        doThrow(new NacosException()).when(connection).request(any(), anyLong());
        try {
            healthCheck.invoke(rpcClient);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        verify(connection, times(retry + 1)).request(any(), anyLong());
    }
    
    @Test
    public void testNextRpcServerForIpv4WithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("127.0.0.1:7777", actual.getAddress());
        assertEquals("127.0.0.1", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForIpv4WithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("127.0.0.1:8848", actual.getAddress());
        assertEquals("127.0.0.1", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForIpv6WithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("[fe80::35ba:6827:c5ff:d161%11]:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]:7777", actual.getAddress());
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForIpv6WithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("[fe80::35ba:6827:c5ff:d161%11]");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]:8848", actual.getAddress());
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForDomainWithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("nacos.io:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("nacos.io:7777", actual.getAddress());
        assertEquals("nacos.io", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForDomainWithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("nacos.io");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("nacos.io:8848", actual.getAddress());
        assertEquals("nacos.io", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForLocalhostWithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("localhost:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("localhost:7777", actual.getAddress());
        assertEquals("localhost", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    public void testNextRpcServerForLocalhostWithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("localhost");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("localhost:8848", actual.getAddress());
        assertEquals("localhost", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testNextRpcServerForEmpty() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("");
        rpcClient.nextRpcServer();
    }
    
    private RpcClient buildTestNextRpcServerClient() {
        return new RpcClient(DefaultGrpcClientConfig.newBuilder().build()) {
            @Override
            public ConnectionType getConnectionType() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) throws Exception {
                return null;
            }
            
            @Override
            public ServerInfo nextRpcServer() {
                return super.nextRpcServer();
            }
        };
    }
}
