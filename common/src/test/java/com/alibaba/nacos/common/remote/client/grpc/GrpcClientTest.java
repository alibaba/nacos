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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.SetupAckRequest;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GrpcClientTest {
    
    protected GrpcClient grpcClient;
    
    @Mock
    RpcClientTlsConfig tlsConfig;
    
    protected RpcClient.ServerInfo serverInfo;
    
    protected GrpcClientConfig clientConfig;
    
    @Before
    public void setUp() throws Exception {
        clientConfig = DefaultGrpcClientConfig.newBuilder().setServerCheckTimeOut(100L)
                .setCapabilityNegotiationTimeout(100L).setChannelKeepAliveTimeout((int) TimeUnit.SECONDS.toMillis(3L))
                .setChannelKeepAlive(1000).setName("testClient").build();
        clientConfig.setTlsConfig(tlsConfig);
        grpcClient = spy(new GrpcClient(clientConfig) {
            @Override
            protected AbilityMode abilityMode() {
                return AbilityMode.SDK_CLIENT;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
        });
        serverInfo = new RpcClient.ServerInfo("10.10.10.10", 8848);
    }
    
    @After
    public void tearDown() throws NacosException {
        grpcClient.shutdown();
    }
    
    @Test
    public void testGetConnectionType() {
        assertEquals(ConnectionType.GRPC, grpcClient.getConnectionType());
    }
    
    @Test
    public void testConnectToServerFailed() {
        assertNull(grpcClient.connectToServer(serverInfo));
    }
    
    @Test
    public void testConnectToServerException() {
        doThrow(new RuntimeException("test")).when(grpcClient).createNewChannelStub(any(ManagedChannel.class));
        assertNull(grpcClient.connectToServer(serverInfo));
    }
    
    @Test
    public void testConnectToServerMockSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        RequestGrpc.RequestFutureStub stub = mockStub(new ServerCheckResponse(), null);
        doReturn(stub).when(grpcClient).createNewChannelStub(any(ManagedChannel.class));
        Connection connection = grpcClient.connectToServer(serverInfo);
        assertNotNull(connection);
        assertTrue(connection instanceof GrpcConnection);
        assertEquals(stub, ((GrpcConnection) connection).getGrpcFutureServiceStub());
    }
    
    @Test
    public void testConnectToServerMockSuccessWithAbility()
            throws ExecutionException, InterruptedException, TimeoutException {
        ServerCheckResponse response = new ServerCheckResponse();
        response.setSupportAbilityNegotiation(true);
        RequestGrpc.RequestFutureStub stub = mockStub(response, null);
        doReturn(stub).when(grpcClient).createNewChannelStub(any(ManagedChannel.class));
        Connection connection = grpcClient.connectToServer(serverInfo);
        assertNull(connection);
    }
    
    @Test
    public void testConnectToServerMockHealthCheckFailed()
            throws ExecutionException, InterruptedException, TimeoutException {
        RequestGrpc.RequestFutureStub stub = mockStub(null, new RuntimeException("test"));
        doReturn(stub).when(grpcClient).createNewChannelStub(any(ManagedChannel.class));
        Connection connection = grpcClient.connectToServer(serverInfo);
        assertNull(connection);
    }
    
    private RequestGrpc.RequestFutureStub mockStub(ServerCheckResponse response, Throwable throwable)
            throws InterruptedException, ExecutionException, TimeoutException {
        RequestGrpc.RequestFutureStub stub = mock(RequestGrpc.RequestFutureStub.class);
        ListenableFuture<Payload> listenableFuture = mock(ListenableFuture.class);
        when(stub.request(any(Payload.class))).thenReturn(listenableFuture);
        if (null == throwable) {
            when(listenableFuture.get(100L, TimeUnit.MILLISECONDS)).thenReturn(GrpcUtils.convert(response));
        } else {
            when(listenableFuture.get(100L, TimeUnit.MILLISECONDS)).thenThrow(throwable);
        }
        Channel channel = mock(Channel.class);
        when(stub.getChannel()).thenReturn(channel);
        ClientCall mockCall = mock(ClientCall.class);
        when(channel.newCall(any(), any())).thenReturn(mockCall);
        return stub;
    }
    
    @Test
    public void testBindRequestStreamOnNextSetupAckRequest()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0))
                    .onNext(GrpcUtils.convert(new SetupAckRequest()));
            return null;
        });
        setCurrentConnection(grpcConnection, grpcClient);
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        verify(grpcConnection, never()).sendResponse(any(Response.class));
    }
    
    @Test
    public void testBindRequestStreamOnNextOtherRequest()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0))
                    .onNext(GrpcUtils.convert(new ConnectResetRequest()));
            return null;
        });
        grpcClient.registerServerRequestHandler((request, connection) -> {
            if (request instanceof ConnectResetRequest) {
                return new ConnectResetResponse();
            }
            return null;
        });
        setCurrentConnection(grpcConnection, grpcClient);
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        verify(grpcConnection).sendResponse(any(ConnectResetResponse.class));
    }
    
    @Test
    public void testBindRequestStreamOnNextNoRequest()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0))
                    .onNext(GrpcUtils.convert(new ConnectResetRequest()));
            return null;
        });
        grpcClient.registerServerRequestHandler((request, connection) -> null);
        setCurrentConnection(grpcConnection, grpcClient);
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        verify(grpcConnection, never()).sendResponse(any(Response.class));
    }
    
    @Test
    public void testBindRequestStreamOnNextHandleException()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0))
                    .onNext(GrpcUtils.convert(new ConnectResetRequest()));
            return null;
        });
        grpcClient.registerServerRequestHandler((request, connection) -> {
            throw new RuntimeException("test");
        });
        setCurrentConnection(grpcConnection, grpcClient);
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        verify(grpcConnection).sendResponse(any(ErrorResponse.class));
    }
    
    @Test
    public void testBindRequestStreamOnNextParseException()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0)).onNext(Payload.newBuilder().build());
            return null;
        });
        setCurrentConnection(grpcConnection, grpcClient);
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        verify(grpcConnection, never()).sendResponse(any(ErrorResponse.class));
    }
    
    @Test
    public void testBindRequestStreamOnErrorFromRunning()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0)).onError(new RuntimeException("test"));
            return null;
        });
        setStatus(grpcClient, RpcClientStatus.RUNNING);
        setCurrentConnection(grpcConnection, grpcClient);
        assertTrue(grpcClient.isRunning());
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        assertFalse(grpcClient.isRunning());
    }
    
    @Test
    public void testBindRequestStreamOnErrorFromNotRunning()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0)).onError(new RuntimeException("test"));
            return null;
        });
        setStatus(grpcClient, RpcClientStatus.WAIT_INIT);
        setCurrentConnection(grpcConnection, grpcClient);
        assertFalse(grpcClient.isRunning());
        assertTrue(grpcClient.isWaitInitiated());
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        assertFalse(grpcClient.isRunning());
        assertTrue(grpcClient.isWaitInitiated());
    }
    
    @Test
    public void testBindRequestStreamOnCompletedFromRunning()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0)).onCompleted();
            return null;
        });
        setStatus(grpcClient, RpcClientStatus.RUNNING);
        setCurrentConnection(grpcConnection, grpcClient);
        assertTrue(grpcClient.isRunning());
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        assertFalse(grpcClient.isRunning());
    }
    
    @Test
    public void testBindRequestStreamOnCompletedFromNotRunning()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiRequestStreamGrpc.BiRequestStreamStub stub = mock(BiRequestStreamGrpc.BiRequestStreamStub.class);
        GrpcConnection grpcConnection = mock(GrpcConnection.class);
        when(stub.requestBiStream(any())).thenAnswer((Answer<StreamObserver<Payload>>) invocationOnMock -> {
            ((StreamObserver<Payload>) invocationOnMock.getArgument(0)).onCompleted();
            return null;
        });
        setStatus(grpcClient, RpcClientStatus.WAIT_INIT);
        setCurrentConnection(grpcConnection, grpcClient);
        assertFalse(grpcClient.isRunning());
        assertTrue(grpcClient.isWaitInitiated());
        invokeBindRequestStream(grpcClient, stub, grpcConnection);
        assertFalse(grpcClient.isRunning());
        assertTrue(grpcClient.isWaitInitiated());
    }
    
    private void invokeBindRequestStream(GrpcClient grpcClient, BiRequestStreamGrpc.BiRequestStreamStub stub,
            GrpcConnection grpcConnection)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method bindRequestStreamMethod = GrpcClient.class
                .getDeclaredMethod("bindRequestStream", BiRequestStreamGrpc.BiRequestStreamStub.class,
                        GrpcConnection.class);
        bindRequestStreamMethod.setAccessible(true);
        bindRequestStreamMethod.invoke(grpcClient, stub, grpcConnection);
    }
    
    private void setCurrentConnection(GrpcConnection connection, GrpcClient client)
            throws NoSuchFieldException, IllegalAccessException {
        Field connectionField = RpcClient.class.getDeclaredField("currentConnection");
        connectionField.setAccessible(true);
        connectionField.set(client, connection);
    }
    
    private void setStatus(GrpcClient grpcClient, RpcClientStatus status)
            throws IllegalAccessException, NoSuchFieldException {
        Field statusField = RpcClient.class.getDeclaredField("rpcClientStatus");
        statusField.setAccessible(true);
        statusField.set(grpcClient, new AtomicReference<>(status));
    }
    
    @Test
    public void testAfterReset() throws NoSuchFieldException, IllegalAccessException {
        Field recAbilityContextField = GrpcClient.class.getDeclaredField("recAbilityContext");
        recAbilityContextField.setAccessible(true);
        GrpcClient.RecAbilityContext context = mock(GrpcClient.RecAbilityContext.class);
        recAbilityContextField.set(grpcClient, context);
        grpcClient.afterReset(new ConnectResetRequest());
        verify(context).release(null);
    }
    
    @Test
    public void testAppendRecAbilityContext() {
        GrpcClient.RecAbilityContext context = new GrpcClient.RecAbilityContext(null);
        GrpcConnection connection = mock(GrpcConnection.class);
        context.reset(connection);
        assertTrue(context.isNeedToSync());
        assertFalse(context.check(connection));
        context.release(Collections.emptyMap());
        assertFalse(context.isNeedToSync());
        verify(connection).setAbilityTable(anyMap());
        when(connection.isAbilitiesSet()).thenReturn(true);
        assertTrue(context.check(connection));
    }
    
    @Test
    public void testSendResponseWithException()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        GrpcConnection connection = mock(GrpcConnection.class);
        setCurrentConnection(connection, grpcClient);
        doThrow(new RuntimeException("test")).when(connection).sendResponse(any(Response.class));
        Method sendResponseMethod = GrpcClient.class.getDeclaredMethod("sendResponse", Response.class);
        sendResponseMethod.setAccessible(true);
        sendResponseMethod.invoke(grpcClient, new ConnectResetResponse());
        // don't throw any exception.
    }
    
    @Test
    public void testConstructorWithServerListFactory() {
        ServerListFactory serverListFactory = mock(ServerListFactory.class);
        GrpcClient grpcClient = new GrpcClient(clientConfig, serverListFactory) {
            @Override
            protected AbilityMode abilityMode() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
        };
        assertFalse(grpcClient.isWaitInitiated());
    }
    
    @Test
    public void testConstructorWithoutServerListFactory() {
        GrpcClient grpcClient = new GrpcClient("testNoFactory", 2, 2, Collections.emptyMap()) {
            @Override
            protected AbilityMode abilityMode() {
                return null;
            }
            
            @Override
            public int rpcPortOffset() {
                return 0;
            }
        };
        assertTrue(grpcClient.isWaitInitiated());
    }
}
