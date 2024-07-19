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

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ClientDetectionResponse;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.grpc.DefaultGrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RpcClientTest {
    
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
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
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
    
    @AfterEach
    void tearDown() throws IllegalAccessException, NacosException {
        rpcClientConfig.labels().clear();
        rpcClient.rpcClientStatus.set(RpcClientStatus.WAIT_INIT);
        serverListFactoryField.set(rpcClient, null);
        ((Queue<?>) reconnectionSignalField.get(rpcClient)).clear();
        rpcClient.currentConnection = null;
        System.clearProperty("nacos.server.port");
        rpcClient.eventLinkedBlockingQueue.clear();
        rpcClient.shutdown();
    }
    
    @Test
    void testInitServerListFactory() {
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
    void testLabels() {
        when(rpcClientConfig.labels()).thenReturn(Collections.singletonMap("labelKey1", "labelValue1"));
        Map.Entry<String, String> element = rpcClient.getLabels().entrySet().iterator().next();
        assertEquals("labelKey1", element.getKey());
        assertEquals("labelValue1", element.getValue());
        
        // accumulate labels
        Map<String, String> map = new HashMap<>();
        map.put("labelKey2", "labelValue2");
        when(rpcClientConfig.labels()).thenReturn(map);
        assertEquals(1, rpcClient.getLabels().size());
        assertEquals("test", rpcClient.getName());
    }
    
    @Test
    void testOnServerListChangeWhenCurrentConnectionIsNullThenDoNothing() throws IllegalAccessException {
        int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize, afterSize);
    }
    
    @Test
    void testOnServerListChangeWhenServiceInfoIsNullThenDoNothing() throws IllegalAccessException {
        int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.currentConnection = mock(Connection.class);
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize, afterSize);
    }
    
    @Test
    void testOnServerListChangeWhenCurrentConnectionIsNotInServerListThenSwitchServerAsync() throws IllegalAccessException {
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
    void testOnServerListChangeWhenCurrentConnectionIsInServerListThenDoNothing() throws IllegalAccessException {
        final int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.currentConnection = new GrpcConnection(new RpcClient.ServerInfo("10.10.10.10", 8848), null);
        doReturn(Collections.singletonList("http://10.10.10.10:8848")).when(serverListFactory).getServerList();
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        assertEquals(beforeSize, afterSize);
    }
    
    @Test
    void testResolveServerInfo1() throws InvocationTargetException, IllegalAccessException {
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "10.10.10.10::8848")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "10.10.10.10:8848")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10:8848")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10::8848")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10")).getAddress());
        assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "https://10.10.10.10::8848")).getAddress());
    }
    
    @Test
    void testResolveServerInfo2() throws InvocationTargetException, IllegalAccessException {
        System.setProperty("nacos.server.port", "4424");
        assertEquals("10.10.10.10:4424",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10")).getAddress());
    }
    
    @Test
    void testRequestSuccess() throws NacosException, NoSuchFieldException, IllegalAccessException {
        rpcClient.currentConnection = connection;
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        when(connection.request(any(), anyLong())).thenReturn(new HealthCheckResponse());
        Field lastActiveTimeStampField = RpcClient.class.getDeclaredField("lastActiveTimeStamp");
        lastActiveTimeStampField.setAccessible(true);
        final long lastActiveTimeStamp = (long) lastActiveTimeStampField.get(rpcClient);
        Response response = rpcClient.request(new HealthCheckRequest());
        assertTrue(response instanceof HealthCheckResponse);
        assertTrue(lastActiveTimeStamp <= (long) lastActiveTimeStampField.get(rpcClient));
    }
    
    @Test
    void testRequestWithoutAnyTry() throws NacosException {
        assertThrows(NacosException.class, () -> {
            when(rpcClientConfig.retryTimes()).thenReturn(-1);
            rpcClient.request(null);
        });
    }
    
    @Test
    void testRequestWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        assertThrows(NacosException.class, () -> {
            rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
            rpcClient.currentConnection = connection;
            rpcClient.request(null);
        });
    }
    
    @Test
    void testRequestWhenTimeoutThenThrowException() throws NacosException {
        assertThrows(NacosException.class, () -> {
            rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
            rpcClient.currentConnection = connection;
            doReturn(null).when(connection).request(any(), anyLong());
            rpcClient.request(null, 10000);
        });
    }
    
    @Test
    void testRequestWhenResponseErrorThenThrowException() throws NacosException {
        assertThrows(NacosException.class, () -> {
            rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
            rpcClient.currentConnection = connection;
            doReturn(new ErrorResponse()).when(connection).request(any(), anyLong());
            rpcClient.request(null, 10000);
        });
    }
    
    @Test
    void testRequestWhenResponseUnregisterThenSwitchServer() throws NacosException {
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
        assertNotNull(exception);
    }
    
    @Test
    void testAsyncRequestSuccess() throws NacosException {
        rpcClient.currentConnection = connection;
        rpcClient.rpcClientStatus.set(RpcClientStatus.RUNNING);
        RequestCallBack<?> requestCallBack = mock(RequestCallBack.class);
        when(requestCallBack.getTimeout()).thenReturn(1000L);
        rpcClient.asyncRequest(null, requestCallBack);
        verify(connection).asyncRequest(any(), any());
    }
    
    @Test
    void testAsyncRequestWithoutAnyTry() throws NacosException {
        assertThrows(NacosException.class, () -> {
            when(rpcClientConfig.retryTimes()).thenReturn(-1);
            rpcClient.asyncRequest(null, null);
        });
    }
    
    @Test
    void testAsyncRequestWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        assertThrows(NacosException.class, () -> {
            rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
            rpcClient.currentConnection = connection;
            RequestCallBack<?> requestCallBack = mock(RequestCallBack.class);
            doReturn(10000L).when(requestCallBack).getTimeout();
            rpcClient.asyncRequest(null, requestCallBack);
        });
    }
    
    @Test
    void testAsyncRequestWhenSendRequestFailedMannyTimesThenSwitchServer() throws NacosException {
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
        assertNotNull(exception);
        assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
    }
    
    @Test
    void testRequestFutureWithoutAnyTry() throws NacosException {
        assertThrows(NacosException.class, () -> {
            when(rpcClientConfig.retryTimes()).thenReturn(-1);
            rpcClient.requestFuture(null);
        });
    }
    
    @Test
    void testRequestFutureWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        assertThrows(NacosException.class, () -> {
            rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
            rpcClient.currentConnection = connection;
            rpcClient.requestFuture(null);
        });
    }
    
    @Test
    void testRequestFutureWhenRetryReachMaxRetryTimesThenSwitchServer() throws NacosException {
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
        
        verify(connection, times(4)).requestFuture(any());
        verify(rpcClient).switchServerAsyncOnRequestFail();
        assertNotNull(exception);
        assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
    }
    
    @Test
    void testRpcClientShutdownWhenClientDidntStart() throws NacosException {
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
            public Connection connectToServer(ServerInfo serverInfo) {
                return null;
            }
        };
        
        rpcClient.shutdown();
        assertTrue(rpcClient.isShutdown());
    }
    
    @Test
    void testHealthCheck() throws IllegalAccessException, NacosException {
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
    void testNextRpcServerForIpv4WithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("127.0.0.1:7777", actual.getAddress());
        assertEquals("127.0.0.1", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForIpv4WithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("127.0.0.1:8848", actual.getAddress());
        assertEquals("127.0.0.1", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForIpv6WithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("[fe80::35ba:6827:c5ff:d161%11]:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]:7777", actual.getAddress());
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForIpv6WithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("[fe80::35ba:6827:c5ff:d161%11]");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]:8848", actual.getAddress());
        assertEquals("[fe80::35ba:6827:c5ff:d161%11]", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForDomainWithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("nacos.io:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("nacos.io:7777", actual.getAddress());
        assertEquals("nacos.io", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForDomainWithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("nacos.io");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("nacos.io:8848", actual.getAddress());
        assertEquals("nacos.io", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForLocalhostWithPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("localhost:7777");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("localhost:7777", actual.getAddress());
        assertEquals("localhost", actual.getServerIp());
        assertEquals(7777, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForLocalhostWithoutPort() {
        RpcClient rpcClient = buildTestNextRpcServerClient();
        rpcClient.serverListFactory(serverListFactory);
        when(serverListFactory.genNextServer()).thenReturn("localhost");
        RpcClient.ServerInfo actual = rpcClient.nextRpcServer();
        assertEquals("localhost:8848", actual.getAddress());
        assertEquals("localhost", actual.getServerIp());
        assertEquals(8848, actual.getServerPort());
    }
    
    @Test
    void testNextRpcServerForEmpty() {
        assertThrows(IllegalArgumentException.class, () -> {
            RpcClient rpcClient = buildTestNextRpcServerClient();
            rpcClient.serverListFactory(serverListFactory);
            when(serverListFactory.genNextServer()).thenReturn("");
            rpcClient.nextRpcServer();
        });
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
            public Connection connectToServer(ServerInfo serverInfo) {
                return null;
            }
            
            @Override
            public ServerInfo nextRpcServer() {
                return super.nextRpcServer();
            }
        };
    }
    
    @Test
    void testHandleServerRequestWhenExceptionThenThrowException() throws RuntimeException {
        assertThrows(RuntimeException.class, () -> {
            RpcClient rpcClient = buildTestNextRpcServerClient();
            Request request = new Request() {
                @Override
                public String getModule() {
                    return null;
                }
            };
            rpcClient.serverRequestHandlers.add((req, conn) -> {
                throw new RuntimeException();
            });
            rpcClient.handleServerRequest(request);
        });
    }
    
    @Test
    void testNotifyDisConnectedForEmpty() {
        rpcClient.notifyDisConnected(null);
        verify(rpcClientConfig, never()).name();
    }
    
    @Test
    void testNotifyDisConnected() {
        ConnectionEventListener listener = mock(ConnectionEventListener.class);
        rpcClient.registerConnectionListener(listener);
        rpcClient.notifyDisConnected(null);
        verify(listener).onDisConnect(null);
        verify(rpcClientConfig, times(2)).name();
    }
    
    @Test
    void testNotifyDisConnectedException() {
        ConnectionEventListener listener = mock(ConnectionEventListener.class);
        rpcClient.registerConnectionListener(listener);
        doThrow(new RuntimeException("test")).when(listener).onDisConnect(null);
        rpcClient.notifyDisConnected(null);
        verify(rpcClientConfig, times(3)).name();
    }
    
    @Test
    void testNotifyConnectedForEmpty() {
        rpcClient.notifyConnected(null);
        verify(rpcClientConfig, never()).name();
    }
    
    @Test
    void testNotifyConnected() {
        ConnectionEventListener listener = mock(ConnectionEventListener.class);
        rpcClient.registerConnectionListener(listener);
        rpcClient.notifyConnected(null);
        verify(listener).onConnected(null);
        verify(rpcClientConfig, times(2)).name();
    }
    
    @Test
    void testNotifyConnectedException() {
        ConnectionEventListener listener = mock(ConnectionEventListener.class);
        rpcClient.registerConnectionListener(listener);
        doThrow(new RuntimeException("test")).when(listener).onConnected(null);
        rpcClient.notifyConnected(null);
        verify(rpcClientConfig, times(3)).name();
    }
    
    @Test
    void testStartClient() throws NacosException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(new Function<RpcClient.ServerInfo, Connection>() {
            
            private int count;
            
            @Override
            public Connection apply(RpcClient.ServerInfo serverInfo) {
                if (count == 0) {
                    count++;
                    throw new RuntimeException("test");
                }
                return connection;
            }
        });
        try {
            rpcClient.start();
            assertTrue(rpcClient.isRunning());
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testStartClientWithFailed() throws NacosException, InterruptedException {
        RpcClient rpcClient = buildTestStartClient(serverInfo -> null);
        try {
            rpcClient.start();
            TimeUnit.MILLISECONDS.sleep(1000);
            assertFalse(rpcClient.isRunning());
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testStartClientAfterShutdown() throws NacosException {
        RpcClient rpcClient = buildTestStartClient(serverInfo -> null);
        rpcClient.shutdown();
        rpcClient.start();
        assertTrue(rpcClient.isShutdown());
    }
    
    @Test
    void testDisConnectionEventAfterStart() throws NacosException, InterruptedException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> connection);
        ConnectionEventListener listener = mock(ConnectionEventListener.class);
        rpcClient.registerConnectionListener(listener);
        try {
            rpcClient.start();
            TimeUnit.MILLISECONDS.sleep(100);
            rpcClient.eventLinkedBlockingQueue.put(new RpcClient.ConnectionEvent(0, connection));
            TimeUnit.MILLISECONDS.sleep(100);
            verify(listener).onDisConnect(connection);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectContextAfterStartWithNullConnection() throws NacosException, InterruptedException {
        RpcClient rpcClient = buildTestStartClient(serverInfo -> null);
        try {
            when(rpcClientConfig.connectionKeepAlive()).thenReturn(-1L);
            rpcClient.start();
            TimeUnit.MILLISECONDS.sleep(100);
            verify(rpcClientConfig, never()).healthCheckRetryTimes();
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectContextAfterStartWithConnectionHealthCheckFail() throws NacosException, InterruptedException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(new Function<RpcClient.ServerInfo, Connection>() {
            
            private int count;
            
            @Override
            public Connection apply(RpcClient.ServerInfo serverInfo) {
                if (count == 0) {
                    count++;
                    return connection;
                }
                return null;
            }
        });
        try {
            when(rpcClientConfig.connectionKeepAlive()).thenReturn(10L);
            rpcClient.start();
            TimeUnit.MILLISECONDS.sleep(500);
            assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectContextAfterStartWithConnectionHealthCheckSuccess()
            throws NacosException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> connection);
        when(connection.request(any(Request.class), anyLong())).thenReturn(new HealthCheckResponse());
        try {
            Field lastActiveTimeStampField = RpcClient.class.getDeclaredField("lastActiveTimeStamp");
            lastActiveTimeStampField.setAccessible(true);
            final long lastActiveTimeStamp = (long) lastActiveTimeStampField.get(rpcClient);
            when(rpcClientConfig.connectionKeepAlive()).thenReturn(10L);
            rpcClient.start();
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(RpcClientStatus.RUNNING, rpcClient.rpcClientStatus.get());
            long newLastActiveTimeStamp = (long) lastActiveTimeStampField.get(rpcClient);
            assertTrue(newLastActiveTimeStamp > lastActiveTimeStamp);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectContextAfterStartWithActiveTimeIsNew()
            throws NacosException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> connection);
        try {
            Field lastActiveTimeStampField = RpcClient.class.getDeclaredField("lastActiveTimeStamp");
            lastActiveTimeStampField.setAccessible(true);
            long setTime = System.currentTimeMillis() + 10000;
            lastActiveTimeStampField.set(rpcClient, setTime);
            when(rpcClientConfig.connectionKeepAlive()).thenReturn(10L);
            rpcClient.start();
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(RpcClientStatus.RUNNING, rpcClient.rpcClientStatus.get());
            long newLastActiveTimeStamp = (long) lastActiveTimeStampField.get(rpcClient);
            assertEquals(setTime, newLastActiveTimeStamp);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectContextAfterStartWithOldServiceInfo() throws NacosException, InterruptedException, IllegalAccessException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        when(serverListFactory.getServerList()).thenReturn(Collections.singletonList("127.0.0.1:8848"));
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> connection);
        try {
            rpcClient.start();
            RpcClient.ReconnectContext reconnectContext = new RpcClient.ReconnectContext(new RpcClient.ServerInfo("127.0.0.1", 0),
                    false);
            ((BlockingQueue<RpcClient.ReconnectContext>) reconnectionSignalField.get(rpcClient)).put(reconnectContext);
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(RpcClientStatus.RUNNING, rpcClient.rpcClientStatus.get());
            assertEquals(8848, reconnectContext.serverInfo.serverPort);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectContextAfterStartWithNewServiceInfo() throws NacosException, InterruptedException, IllegalAccessException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        when(serverListFactory.getServerList()).thenReturn(Collections.singletonList("1.1.1.1:8848"));
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> connection);
        try {
            rpcClient.start();
            RpcClient.ReconnectContext reconnectContext = new RpcClient.ReconnectContext(new RpcClient.ServerInfo("127.0.0.1", 0),
                    false);
            ((BlockingQueue<RpcClient.ReconnectContext>) reconnectionSignalField.get(rpcClient)).put(reconnectContext);
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(RpcClientStatus.RUNNING, rpcClient.rpcClientStatus.get());
            assertNull(reconnectContext.serverInfo);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testHandleConnectionResetRequestWithoutServer() throws NacosException, InterruptedException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848", "1.1.1.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> {
            connection.serverInfo = serverInfo;
            return connection;
        });
        try {
            rpcClient.start();
            Response response = rpcClient.handleServerRequest(new ConnectResetRequest());
            assertTrue(response instanceof ConnectResetResponse);
            TimeUnit.MILLISECONDS.sleep(500);
            assertEquals("1.1.1.1", connection.serverInfo.getServerIp());
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testHandleConnectionResetRequestWithServer() throws NacosException, InterruptedException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848", "1.1.1.1:8848");
        List<String> serverList = new LinkedList<>();
        serverList.add("127.0.0.1:8848");
        serverList.add("1.1.1.1:8848");
        serverList.add("2.2.2.2:8848");
        when(serverListFactory.getServerList()).thenReturn(serverList);
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> {
            connection.serverInfo = serverInfo;
            return connection;
        });
        try {
            rpcClient.start();
            ConnectResetRequest connectResetRequest = new ConnectResetRequest();
            connectResetRequest.setServerIp("2.2.2.2");
            connectResetRequest.setServerPort("8848");
            Response response = rpcClient.handleServerRequest(connectResetRequest);
            assertTrue(response instanceof ConnectResetResponse);
            TimeUnit.MILLISECONDS.sleep(500);
            assertEquals("2.2.2.2", connection.serverInfo.getServerIp());
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testHandleConnectionResetRequestWithException() throws NacosException, InterruptedException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848", "1.1.1.1:8848");
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> {
            connection.serverInfo = serverInfo;
            return connection;
        });
        try {
            rpcClient.start();
            System.setProperty("nacos.server.port", "2.2.2.2");
            ConnectResetRequest connectResetRequest = new ConnectResetRequest();
            connectResetRequest.setServerIp("2.2.2.2");
            Response response = rpcClient.handleServerRequest(connectResetRequest);
            assertTrue(response instanceof ConnectResetResponse);
            TimeUnit.MILLISECONDS.sleep(500);
            assertEquals("127.0.0.1", connection.serverInfo.getServerIp());
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testHandleClientDetectionRequest() throws NacosException {
        RpcClient rpcClient = buildTestStartClient(serverInfo -> null);
        try {
            rpcClient.start();
            Response response = rpcClient.handleServerRequest(new ClientDetectionRequest());
            assertTrue(response instanceof ClientDetectionResponse);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testHandleOtherRequest() throws NacosException {
        RpcClient rpcClient = buildTestStartClient(serverInfo -> null);
        try {
            rpcClient.start();
            Response response = rpcClient.handleServerRequest(new HealthCheckRequest());
            assertNull(response);
        } finally {
            rpcClient.shutdown();
        }
    }
    
    @Test
    void testReconnectForRequestFailButHealthCheckOK() throws NacosException {
        RpcClient rpcClient = buildTestStartClient(serverInfo -> null);
        rpcClient.currentConnection = connection;
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        when(connection.request(any(Request.class), anyLong())).thenReturn(new HealthCheckResponse());
        rpcClient.reconnect(null, true);
        assertTrue(rpcClient.isRunning());
    }
    
    @Test
    void testReconnectFailTimes() throws NacosException {
        when(serverListFactory.genNextServer()).thenReturn("127.0.0.1:8848");
        when(serverListFactory.getServerList()).thenReturn(Collections.singletonList("127.0.0.1:8848"));
        final AtomicInteger count = new AtomicInteger(0);
        RpcClient rpcClient = buildTestStartClient(serverInfo -> {
            int actual = count.incrementAndGet();
            return actual > 3 ? connection : null;
        });
        rpcClient.currentConnection = connection;
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        long start = System.currentTimeMillis();
        rpcClient.reconnect(null, false);
        assertTrue(rpcClient.isRunning());
        assertTrue(System.currentTimeMillis() - start > 400);
    }
    
    @Test
    void testGetCurrentServer() {
        assertNull(rpcClient.getCurrentServer());
        rpcClient.currentConnection = connection;
        rpcClient.serverListFactory(serverListFactory);
        connection.serverInfo = new RpcClient.ServerInfo("127.0.0.1", 8848);
        assertNotNull(rpcClient.getCurrentServer());
    }
    
    @Test
    void testCurrentRpcServer() throws IllegalAccessException {
        when(serverListFactory.getCurrentServer()).thenReturn("127.0.0.1:8848");
        serverListFactoryField.set(rpcClient, serverListFactory);
        RpcClient.ServerInfo serverInfo = rpcClient.currentRpcServer();
        assertEquals("127.0.0.1", serverInfo.getServerIp());
        assertEquals(8848, serverInfo.getServerPort());
        assertEquals("127.0.0.1:8848", serverInfo.getAddress());
    }
    
    private RpcClient buildTestStartClient(Function<RpcClient.ServerInfo, Connection> function) {
        return new RpcClient(rpcClientConfig, serverListFactory) {
            
            @Override
            public ConnectionType getConnectionType() {
                return ConnectionType.GRPC;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
            
            @Override
            public Connection connectToServer(ServerInfo serverInfo) {
                return function.apply(serverInfo);
            }
        };
    }
    
    @Test
    void testServerInfoSet() {
        RpcClient.ServerInfo serverInfo = new RpcClient.ServerInfo();
        String ip = "127.0.0.1";
        int port = 80;
        serverInfo.setServerIp(ip);
        serverInfo.setServerPort(port);
        assertEquals("127.0.0.1:80", serverInfo.getAddress());
        assertEquals(port, serverInfo.getServerPort());
        assertEquals(ip, serverInfo.getServerIp());
        String expected = "{serverIp = '127.0.0.1', server main port = 80}";
        assertEquals(expected, serverInfo.toString());
    }
    
    @Test
    void testSetTenant() {
        String tenant = "testTenant";
        assertNull(rpcClient.getTenant());
        rpcClient.setTenant(tenant);
        assertEquals(tenant, rpcClient.getTenant());
    }
    
    @Test
    void testGetConnectionAbilityWithNullConnection() {
        AbilityStatus abilityStatus = rpcClient.getConnectionAbility(AbilityKey.SERVER_TEST_1);
        assertNull(abilityStatus);
    }
    
    @Test
    void testGetConnectionAbilityWithReadyConnection() {
        when(connection.getConnectionAbility(AbilityKey.SERVER_TEST_1)).thenReturn(AbilityStatus.SUPPORTED);
        rpcClient.currentConnection = connection;
        AbilityStatus abilityStatus = rpcClient.getConnectionAbility(AbilityKey.SERVER_TEST_1);
        assertNotNull(abilityStatus);
        assertEquals(AbilityStatus.SUPPORTED, abilityStatus);
    }
}
