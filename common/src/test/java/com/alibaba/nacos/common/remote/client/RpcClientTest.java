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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class RpcClientTest {
    
    RpcClient rpcClient;
    
    Field keepAliveTimeField;
    
    Field serverListFactoryField;
    
    Field reconnectionSignalField;
    
    Field retryTimesField;
    
    Field timeoutMillsField;
    
    Method resolveServerInfoMethod;
    
    Answer<?> runAsSync;
    
    Answer<?> notInvoke;
    
    @Mock
    ServerListFactory serverListFactory;
    
    @Mock
    Connection connection;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        rpcClient = spy(new RpcClient("testClient") {
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
        
        keepAliveTimeField = RpcClient.class.getDeclaredField("keepAliveTime");
        keepAliveTimeField.setAccessible(true);
        
        serverListFactoryField = RpcClient.class.getDeclaredField("serverListFactory");
        serverListFactoryField.setAccessible(true);
        
        reconnectionSignalField = RpcClient.class.getDeclaredField("reconnectionSignal");
        reconnectionSignalField.setAccessible(true);
        Field modifiersField1 = Field.class.getDeclaredField("modifiers");
        modifiersField1.setAccessible(true);
        modifiersField1.setInt(reconnectionSignalField, reconnectionSignalField.getModifiers() & ~Modifier.FINAL);
        
        retryTimesField = RpcClient.class.getDeclaredField("RETRY_TIMES");
        retryTimesField.setAccessible(true);
        Field modifiersField3 = Field.class.getDeclaredField("modifiers");
        modifiersField3.setAccessible(true);
        modifiersField3.setInt(retryTimesField, retryTimesField.getModifiers() & ~Modifier.FINAL);
        
        timeoutMillsField = RpcClient.class.getDeclaredField("DEFAULT_TIMEOUT_MILLS");
        timeoutMillsField.setAccessible(true);
        Field modifiersField4 = Field.class.getDeclaredField("modifiers");
        modifiersField4.setAccessible(true);
        modifiersField4.setInt(timeoutMillsField, timeoutMillsField.getModifiers() & ~Modifier.FINAL);
        
        resolveServerInfoMethod = RpcClient.class.getDeclaredMethod("resolveServerInfo", String.class);
        resolveServerInfoMethod.setAccessible(true);
        
        runAsSync = invocationOnMock -> {
            Runnable runnable = (Runnable) invocationOnMock.getArguments()[0];
            runnable.run();
            return null;
        };
        
        notInvoke = invocationOnMock -> null;
    }
    
    @After
    public void tearDown() throws IllegalAccessException {
        rpcClient.labels.clear();
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
        Assert.assertEquals(RpcClientStatus.INITIALIZED, rpcClient.rpcClientStatus.get());
        
        rpcClient.rpcClientStatus.set(RpcClientStatus.INITIALIZED);
        rpcClient.serverListFactory(serverListFactory);
        Assert.assertEquals(RpcClientStatus.INITIALIZED, rpcClient.rpcClientStatus.get());
        
        RpcClient client1 = new RpcClient("test", serverListFactory) {
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
        Assert.assertEquals(RpcClientStatus.INITIALIZED, client1.rpcClientStatus.get());
        
        RpcClient client2 = new RpcClient(serverListFactory) {
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
        Assert.assertEquals(RpcClientStatus.INITIALIZED, client2.rpcClientStatus.get());
    }
    
    @Test
    public void testLabels() {
        rpcClient.labels(Collections.singletonMap("labelKey1", "labelValue1"));
        Map.Entry<String, String> element = rpcClient.getLabels().entrySet().iterator().next();
        Assert.assertEquals("labelKey1", element.getKey());
        Assert.assertEquals("labelValue1", element.getValue());
        
        // accumulate labels
        rpcClient.labels(Collections.singletonMap("labelKey2", "labelValue2"));
        Assert.assertEquals(2, rpcClient.getLabels().size());
    }
    
    @Test
    public void testKeepAlive() throws IllegalAccessException {
        rpcClient.keepAlive(1, TimeUnit.SECONDS);
        long keepAliveTime = (long) keepAliveTimeField.get(rpcClient);
        Assert.assertEquals(1000L, keepAliveTime);
        
        rpcClient.keepAlive(1, TimeUnit.MINUTES);
        keepAliveTime = (long) keepAliveTimeField.get(rpcClient);
        Assert.assertEquals(60000L, keepAliveTime);
    }
    
    @Test
    public void testOnServerListChangeWhenCurrentConnectionIsNullThenDoNothing() throws IllegalAccessException {
        int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        Assert.assertEquals(beforeSize, afterSize);
    }
    
    @Test
    public void testOnServerListChangeWhenServiceInfoIsNullThenDoNothing() throws IllegalAccessException {
        int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.currentConnection = mock(Connection.class);
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        Assert.assertEquals(beforeSize, afterSize);
    }
    
    @Test
    public void testOnServerListChangeWhenCurrentConnectionIsNotInServerListThenSwitchServerAsync()
            throws IllegalAccessException {
        final int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.currentConnection = new GrpcConnection(new RpcClient.ServerInfo("10.10.10.10", 8848), null);
        doReturn(Collections.singletonList("")).when(serverListFactory).getServerList();
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        Assert.assertEquals(beforeSize + 1, afterSize);
    }
    
    @Test
    public void testOnServerListChangeWhenCurrentConnectionIsInServerListThenDoNothing() throws IllegalAccessException {
        final int beforeSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.currentConnection = new GrpcConnection(new RpcClient.ServerInfo("10.10.10.10", 8848), null);
        doReturn(Collections.singletonList("http://10.10.10.10:8848")).when(serverListFactory).getServerList();
        
        rpcClient.onServerListChange();
        
        int afterSize = ((Queue<?>) reconnectionSignalField.get(rpcClient)).size();
        Assert.assertEquals(beforeSize, afterSize);
    }
    
    @Test
    public void testResolveServerInfo1() throws InvocationTargetException, IllegalAccessException {
        Assert.assertEquals(":8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "")).getAddress());
        Assert.assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "10.10.10.10::8848")).getAddress());
        Assert.assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "10.10.10.10:8848")).getAddress());
        Assert.assertEquals("10.10.10.10:8848", ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient,
                "http://10.10.10.10:8848")).getAddress());
        Assert.assertEquals("10.10.10.10:8848", ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient,
                "http://10.10.10.10::8848")).getAddress());
        Assert.assertEquals("10.10.10.10:8848",
                ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient, "http://10.10.10.10")).getAddress());
        Assert.assertEquals("10.10.10.10:8848", ((RpcClient.ServerInfo) resolveServerInfoMethod.invoke(rpcClient,
                "https://10.10.10.10::8848")).getAddress());
    }
    
    @Test
    public void testResolveServerInfo2() throws InvocationTargetException, IllegalAccessException {
        System.setProperty("nacos.server.port", "4424");
        Assert.assertEquals("10.10.10.10:4424",
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
        
        Assert.assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
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
        Assert.assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
    }
    
    @Test(expected = NacosException.class)
    public void testRequestFutureWhenClientAlreadyShutDownThenThrowException() throws NacosException {
        rpcClient.rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
        rpcClient.currentConnection = connection;
        rpcClient.requestFuture(null);
    }
    
    @Test
    public void testRequestFutureWhenRetryReachMaxRetryTimesThenSwitchServer() throws NacosException, IllegalAccessException {
        timeoutMillsField.set(rpcClient, 5000L);
        retryTimesField.set(rpcClient, 3);
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
        Assert.assertEquals(RpcClientStatus.UNHEALTHY, rpcClient.rpcClientStatus.get());
    }
    
    @Test
    public void testRpcClientShutdownWhenClientDidntStart() throws NacosException {
        RpcClient rpcClient = new RpcClient("test-client") {
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
}