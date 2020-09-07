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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.grpc.auto.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.remote.GrpcUtils;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * grpc connection.
 *
 * @author liuzunfei
 * @version $Id: GrpcConnection.java, v 0.1 2020年08月09日 1:36 PM liuzunfei Exp $
 */
public class GrpcConnection extends Connection {
    
    /**
     * executor to execute future request.
     */
    static ScheduledExecutorService aynsRequestExecutor = Executors
            .newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    
    /**
     * grpc channel.
     */
    protected ManagedChannel channel;
    
    /**
     * stub to send stream request.
     */
    protected RequestStreamGrpc.RequestStreamStub grpcStreamServiceStub;
    
    /**
     * stub to send request.
     */
    protected RequestGrpc.RequestFutureStub grpcFutureServiceStub;
    
    protected StreamObserver<Payload> payloadStreamObserver;
    
    public GrpcConnection(RpcClient.ServerInfo serverInfo, StreamObserver<Payload> payloadStreamObserver) {
        super(serverInfo);
        this.payloadStreamObserver = payloadStreamObserver;
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta) throws NacosException {
        return request(request, requestMeta, 3000L);
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta, long timeouts) throws NacosException {
        Payload grpcRequest = GrpcUtils.convert(request, requestMeta);
        
        ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        Payload grpcResponse = null;
        try {
            grpcResponse = requestFuture.get(timeouts, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        
        Response response = (Response) GrpcUtils.parse(grpcResponse).getBody();
        return response;
    }
    
    
    public void sendResponse(Response response) {
        Payload convert = GrpcUtils.convert(response);
        payloadStreamObserver.onNext(convert);
    }
    
    public void sendRequest(Request request, RequestMeta meta) {
        Payload convert = GrpcUtils.convert(request, meta);
        payloadStreamObserver.onNext(convert);
    }
    
    @Override
    public void asyncRequest(Request request, RequestMeta requestMeta, final RequestCallBack requestCallBack)
            throws NacosException {
        Payload grpcRequest = GrpcUtils.convert(request, requestMeta);
        ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
    
        //set callback .
        Futures.addCallback(requestFuture, new FutureCallback<Payload>() {
            @Override
            public void onSuccess(@NullableDecl Payload grpcResponse) {
                Response response = (Response) GrpcUtils.parse(grpcResponse).getBody();
                if (response != null && response.isSuccess()) {
                    requestCallBack.onResponse(response);
                } else {
                    requestCallBack.onException(new NacosException(
                            (response == null) ? ResponseCode.FAIL.getCode() : response.getErrorCode(),
                            (response == null) ? "null" : response.getMessage()));
                }
            }
            
            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof CancellationException) {
                    requestCallBack.onException(
                            new TimeoutException("Timeout after " + requestCallBack.getTimeout() + " millseconds."));
                } else {
                    requestCallBack.onException(throwable);
                }
            }
        }, aynsRequestExecutor);
        // set timeout future.
        ListenableFuture<Payload> payloadListenableFuture = Futures
                .withTimeout(requestFuture, requestCallBack.getTimeout(), TimeUnit.MILLISECONDS, aynsRequestExecutor);
        
    }
    
    @Override
    public void close() {
        if (this.channel != null && !channel.isShutdown()) {
            this.channel.shutdownNow();
        }
    }
    
    /**
     * Getter method for property <tt>channel</tt>.
     *
     * @return property value of channel
     */
    public ManagedChannel getChannel() {
        return channel;
    }
    
    /**
     * Setter method for property <tt>channel</tt>.
     *
     * @param channel value to be assigned to property channel
     */
    public void setChannel(ManagedChannel channel) {
        this.channel = channel;
    }
    
    /**
     * Getter method for property <tt>grpcStreamServiceStub</tt>.
     *
     * @return property value of grpcStreamServiceStub
     */
    public RequestStreamGrpc.RequestStreamStub getGrpcStreamServiceStub() {
        return grpcStreamServiceStub;
    }
    
    /**
     * Setter method for property <tt>grpcStreamServiceStub</tt>.
     *
     * @param grpcStreamServiceStub value to be assigned to property grpcStreamServiceStub
     */
    public void setGrpcStreamServiceStub(RequestStreamGrpc.RequestStreamStub grpcStreamServiceStub) {
        this.grpcStreamServiceStub = grpcStreamServiceStub;
    }
    
    /**
     * Getter method for property <tt>grpcFutureServiceStub</tt>.
     *
     * @return property value of grpcFutureServiceStub
     */
    public RequestGrpc.RequestFutureStub getGrpcFutureServiceStub() {
        return grpcFutureServiceStub;
    }
    
    /**
     * Setter method for property <tt>grpcFutureServiceStub</tt>.
     *
     * @param grpcFutureServiceStub value to be assigned to property grpcFutureServiceStub
     */
    public void setGrpcFutureServiceStub(RequestGrpc.RequestFutureStub grpcFutureServiceStub) {
        this.grpcFutureServiceStub = grpcFutureServiceStub;
    }
}
