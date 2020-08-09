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
import com.alibaba.nacos.api.grpc.GrpcMetadata;
import com.alibaba.nacos.api.grpc.GrpcRequest;
import com.alibaba.nacos.api.grpc.GrpcResponse;
import com.alibaba.nacos.api.grpc.GrpcUtils;
import com.alibaba.nacos.api.grpc.RequestGrpc;
import com.alibaba.nacos.api.grpc.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    static ExecutorService aynsRequestExecutor = Executors.newScheduledThreadPool(10);
    
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
    
    public GrpcConnection(String connetionId, RpcClient.ServerInfo serverInfo) {
        super(connetionId, serverInfo);
    }
    
    @Override
    public Response request(Request request) throws NacosException {
        GrpcRequest grpcRequest = GrpcUtils.convertToGrpcRequest(request, buildMeta());
        
        ListenableFuture<GrpcResponse> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        GrpcResponse grpcResponse = null;
        try {
            grpcResponse = requestFuture.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        Response response = GrpcUtils.parseResponsefromGrpcResponse(grpcResponse);
        return response;
    }
    
    private GrpcMetadata buildMeta() {
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .setVersion(VersionUtils.getFullClientVersion()).build();
        return meta;
    }
    
    @Override
    public void asyncRequest(Request request, final FutureCallback<Response> callback) throws NacosException {
        GrpcRequest grpcRequest = GrpcUtils.convertToGrpcRequest(request, buildMeta());
        ListenableFuture<GrpcResponse> requestFuture = grpcFutureServiceStub.request(grpcRequest);
        Futures.addCallback(requestFuture, new FutureCallback<GrpcResponse>() {
            @Override
            public void onSuccess(@NullableDecl GrpcResponse grpcResponse) {
                Response response = GrpcUtils.parseResponsefromGrpcResponse(grpcResponse);
                if (response != null && response.isSuccess()) {
                    callback.onSuccess(response);
                } else {
                    callback.onFailure(new NacosException(
                            (response == null) ? ResponseCode.FAIL.getCode() : response.getErrorCode(),
                            (response == null) ? "null" : response.getMessage()));
                }
            }
            
            @Override
            public void onFailure(Throwable throwable) {
                callback.onFailure(throwable);
            }
        }, aynsRequestExecutor);
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
