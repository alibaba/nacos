/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.PayloadRegistry;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Any;
import com.google.protobuf.UnsafeByteOperations;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GrpcConnectionTest {
    
    @Mock
    private Executor executor;
    
    @Mock
    private ManagedChannel channel;
    
    @Mock
    private StreamObserver<Payload> payloadStreamObserver;
    
    @Mock
    private RequestGrpc.RequestFutureStub requestFutureStub;
    
    @Mock
    ListenableFuture<Payload> future;
    
    Payload responsePayload;
    
    Payload errorResponsePayload;
    
    GrpcConnection connection;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        PayloadRegistry.init();
    }
    
    @Before
    public void setUp() throws Exception {
        connection = new GrpcConnection(new RpcClient.ServerInfo(), executor);
        connection.setChannel(channel);
        connection.setPayloadStreamObserver(payloadStreamObserver);
        connection.setGrpcFutureServiceStub(requestFutureStub);
        when(requestFutureStub.request(any(Payload.class))).thenReturn(future);
        responsePayload = GrpcUtils.convert(new HealthCheckResponse());
        errorResponsePayload = GrpcUtils.convert(ErrorResponse.build(500, "test"));
        when(future.get()).thenReturn(responsePayload);
        when(future.get(100L, TimeUnit.MILLISECONDS)).thenReturn(responsePayload);
        when(future.isDone()).thenReturn(true);
    }
    
    @After
    public void tearDown() throws Exception {
        connection.close();
    }
    
    @Test
    public void testGetAll() {
        assertEquals(channel, connection.getChannel());
        assertEquals(payloadStreamObserver, connection.getPayloadStreamObserver());
        assertEquals(requestFutureStub, connection.getGrpcFutureServiceStub());
    }
    
    @Test
    public void testRequestSuccessSync() throws NacosException {
        Response response = connection.request(new HealthCheckRequest(), -1);
        assertTrue(response instanceof HealthCheckResponse);
    }
    
    @Test
    public void testRequestSuccessAsync() throws NacosException {
        Response response = connection.request(new HealthCheckRequest(), 100);
        assertTrue(response instanceof HealthCheckResponse);
    }
    
    @Test(expected = NacosException.class)
    public void testRequestTimeout() throws InterruptedException, ExecutionException, TimeoutException, NacosException {
        when(future.get(100L, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException("test"));
        connection.request(new HealthCheckRequest(), 100);
    }
    
    @Test
    public void testRequestFuture() throws Exception {
        RequestFuture requestFuture = connection.requestFuture(new HealthCheckRequest());
        assertTrue(requestFuture.isDone());
        Response response = requestFuture.get();
        assertTrue(response instanceof HealthCheckResponse);
    }
    
    @Test
    public void testRequestFutureWithTimeout() throws Exception {
        RequestFuture requestFuture = connection.requestFuture(new HealthCheckRequest());
        assertTrue(requestFuture.isDone());
        Response response = requestFuture.get(100L);
        assertTrue(response instanceof HealthCheckResponse);
    }
    
    @Test(expected = NacosException.class)
    public void testRequestFutureFailure() throws Exception {
        when(future.get()).thenReturn(errorResponsePayload);
        RequestFuture requestFuture = connection.requestFuture(new HealthCheckRequest());
        assertTrue(requestFuture.isDone());
        requestFuture.get();
    }
    
    @Test(expected = NacosException.class)
    public void testRequestFutureWithTimeoutFailure() throws Exception {
        when(future.get(100L, TimeUnit.MILLISECONDS)).thenReturn(errorResponsePayload);
        RequestFuture requestFuture = connection.requestFuture(new HealthCheckRequest());
        assertTrue(requestFuture.isDone());
        requestFuture.get(100L);
    }
    
    @Test
    public void testSendResponse() {
        connection.sendResponse(new HealthCheckResponse());
        verify(payloadStreamObserver).onNext(any(Payload.class));
    }
    
    @Test
    public void testSendRequest() {
        connection.sendRequest(new HealthCheckRequest());
        verify(payloadStreamObserver).onNext(any(Payload.class));
    }
    
    @Test
    public void testAsyncRequestSuccess() throws NacosException {
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(future).addListener(any(Runnable.class), eq(executor));
        RequestCallBack requestCallBack = mock(RequestCallBack.class);
        connection.asyncRequest(new HealthCheckRequest(), requestCallBack);
        verify(requestCallBack).onResponse(any(HealthCheckResponse.class));
    }
    
    @Test
    public void testAsyncRequestError() throws NacosException, ExecutionException, InterruptedException {
        when(future.get()).thenReturn(errorResponsePayload);
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(future).addListener(any(Runnable.class), eq(executor));
        RequestCallBack requestCallBack = mock(RequestCallBack.class);
        connection.asyncRequest(new HealthCheckRequest(), requestCallBack);
        verify(requestCallBack).onException(any(NacosException.class));
    }
    
    @Test
    public void testAsyncRequestNullResponse() throws NacosException, ExecutionException, InterruptedException {
        byte[] jsonBytes = JacksonUtils.toJsonBytes(null);
        Metadata.Builder metaBuilder = Metadata.newBuilder().setType(HealthCheckResponse.class.getSimpleName());
        Payload nullResponsePayload = Payload.newBuilder()
                .setBody(Any.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(jsonBytes)))
                .setMetadata(metaBuilder.build()).build();
        when(future.get()).thenReturn(nullResponsePayload);
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(future).addListener(any(Runnable.class), eq(executor));
        RequestCallBack requestCallBack = mock(RequestCallBack.class);
        connection.asyncRequest(new HealthCheckRequest(), requestCallBack);
        verify(requestCallBack).onException(any(NacosException.class));
    }
    
    @Test
    public void testAsyncRequestWithCancelException() throws NacosException, ExecutionException, InterruptedException {
        when(future.get()).thenThrow(new CancellationException("test"));
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(future).addListener(any(Runnable.class), eq(executor));
        RequestCallBack requestCallBack = mock(RequestCallBack.class);
        connection.asyncRequest(new HealthCheckRequest(), requestCallBack);
        verify(requestCallBack).onException(any(TimeoutException.class));
    }
    
    @Test
    public void testAsyncRequestWithOtherException() throws NacosException, ExecutionException, InterruptedException {
        when(future.get()).thenThrow(new RuntimeException("test"));
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(future).addListener(any(Runnable.class), eq(executor));
        RequestCallBack requestCallBack = mock(RequestCallBack.class);
        connection.asyncRequest(new HealthCheckRequest(), requestCallBack);
        verify(requestCallBack).onException(any(RuntimeException.class));
    }
    
    @Test
    public void testCloseWithException() {
        doThrow(new RuntimeException("test")).when(payloadStreamObserver).onCompleted();
        when(channel.shutdownNow()).thenThrow(new RuntimeException("test"));
        connection.close();
        // don't throw any exception
    }
}