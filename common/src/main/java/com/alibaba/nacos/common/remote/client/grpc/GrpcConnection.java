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
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.RpcScheduledExecutor;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gRPC connection.
 *
 * @author liuzunfei
 * @version $Id: GrpcConnection.java, v 0.1 2020年08月09日 1:36 PM liuzunfei Exp $
 */
public class GrpcConnection extends Connection {
    
    /**
     * grpc channel.
     */
    protected ManagedChannel channel;
    
    Executor executor;
    
    /**
     * stub to send request.
     */
    protected RequestGrpc.RequestFutureStub grpcFutureServiceStub;
    
    protected StreamObserver<Payload> payloadStreamObserver;
    
    public GrpcConnection(RpcClient.ServerInfo serverInfo, Executor executor) {
        super(serverInfo);
        this.executor = executor;
    }
    
    @Override
    public Response request(Request request, long timeouts) throws NacosException {
        Payload grpcRequest = GrpcUtils.convert(request);
        ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        Payload grpcResponse;
        try {
            grpcResponse = requestFuture.get(timeouts, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    
        return (Response) GrpcUtils.parse(grpcResponse);
    }
    
    @Override
    public RequestFuture requestFuture(Request request) throws NacosException {
        Payload grpcRequest = GrpcUtils.convert(request);
        
        final ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        return new RequestFuture() {
            
            @Override
            public boolean isDone() {
                return requestFuture.isDone();
            }
            
            @Override
            public Response get() throws Exception {
                Payload grpcResponse = requestFuture.get();
                Response response = (Response) GrpcUtils.parse(grpcResponse);
                if (response instanceof ErrorResponse) {
                    throw new NacosException(response.getErrorCode(), response.getMessage());
                }
                return response;
            }
            
            @Override
            public Response get(long timeout) throws Exception {
                Payload grpcResponse = requestFuture.get(timeout, TimeUnit.MILLISECONDS);
                Response response = (Response) GrpcUtils.parse(grpcResponse);
                if (response instanceof ErrorResponse) {
                    throw new NacosException(response.getErrorCode(), response.getMessage());
                }
                return response;
            }
        };
    }
    
    public void sendResponse(Response response) {
        Payload convert = GrpcUtils.convert(response);
        payloadStreamObserver.onNext(convert);
    }
    
    public void sendRequest(Request request) {
        Payload convert = GrpcUtils.convert(request);
        payloadStreamObserver.onNext(convert);
    }
    
    @Override
    public void asyncRequest(Request request, final RequestCallBack requestCallBack) throws NacosException {
        Payload grpcRequest = GrpcUtils.convert(request);
        ListenableFuture<Payload> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        
        //set callback .
        Futures.addCallback(requestFuture, new FutureCallback<Payload>() {
            @Override
            public void onSuccess(@Nullable Payload grpcResponse) {
                Response response = (Response) GrpcUtils.parse(grpcResponse);
                
                if (response != null) {
                    if (response instanceof ErrorResponse) {
                        requestCallBack.onException(new NacosException(response.getErrorCode(), response.getMessage()));
                    } else {
                        requestCallBack.onResponse(response);
                    }
                } else {
                    requestCallBack.onException(new NacosException(ResponseCode.FAIL.getCode(), "response is null"));
                }
            }
            
            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof CancellationException) {
                    requestCallBack.onException(
                            new TimeoutException("Timeout after " + requestCallBack.getTimeout() + " milliseconds."));
                } else {
                    requestCallBack.onException(throwable);
                }
            }
        }, requestCallBack.getExecutor() != null ? requestCallBack.getExecutor() : this.executor);
        // set timeout future.
        ListenableFuture<Payload> payloadListenableFuture = Futures
                .withTimeout(requestFuture, requestCallBack.getTimeout(), TimeUnit.MILLISECONDS,
                        RpcScheduledExecutor.TIMEOUT_SCHEDULER);
        
    }
    
    @Override
    public void close() {
        if (this.payloadStreamObserver != null) {
            try {
                payloadStreamObserver.onCompleted();
            } catch (Throwable throwable) {
                //ignore.
            }
        }
        
        if (this.channel != null && !channel.isShutdown()) {
            try {
                this.channel.shutdownNow();
            } catch (Throwable throwable) {
                //ignore.
            }
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
    
    /**
     * Getter method for property <tt>payloadStreamObserver</tt>.
     *
     * @return property value of payloadStreamObserver
     */
    public StreamObserver<Payload> getPayloadStreamObserver() {
        return payloadStreamObserver;
    }
    
    /**
     * Setter method for property <tt>payloadStreamObserver</tt>.
     *
     * @param payloadStreamObserver value to be assigned to property payloadStreamObserver
     */
    public void setPayloadStreamObserver(StreamObserver<Payload> payloadStreamObserver) {
        this.payloadStreamObserver = payloadStreamObserver;
    }
}
