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

package com.alibaba.nacos.client.remote.grpc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.GrpcMetadata;
import com.alibaba.nacos.api.grpc.GrpcRequest;
import com.alibaba.nacos.api.grpc.GrpcResponse;
import com.alibaba.nacos.api.grpc.RequestGrpc;
import com.alibaba.nacos.api.grpc.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.ResponseRegistry;
import com.alibaba.nacos.api.remote.request.HeartBeatRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.remote.ChangeListenResponseHandler;
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientStatus;
import com.alibaba.nacos.client.remote.ServerListFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * gRPC Client.
 *
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
public class GrpcClient extends RpcClient {
    
    protected ManagedChannel channel;
    
    protected RequestStreamGrpc.RequestStreamStub grpcStreamServiceStub;
    
    protected RequestGrpc.RequestBlockingStub grpcServiceStub;
    
    public GrpcClient() {
        super();
    }
    
    public GrpcClient(ServerListFactory serverListFactory) {
        super(serverListFactory);
        try {
            start();
        } catch (Exception e) {
            System.out.println("GrpcClient  start fail .....");
            e.printStackTrace();
        }
    }
    
    @Override
    public void start() throws NacosException {
        
        if (rpcClientStatus != RpcClientStatus.INITED) {
            return;
        }
        
        rpcClientStatus = RpcClientStatus.STARTING;
        
        buildClient();
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.config.grpc.worker");
                t.setDaemon(true);
                return t;
            }
        });
        
        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sendBeat();
            }
        }, 5000, 10000, TimeUnit.MILLISECONDS);
        
        rpcClientStatus = RpcClientStatus.RUNNING;
    
        super.registerChangeListenHandler(new ChangeListenResponseHandler() {
            @Override
            public void responseReply(Response response) {
                if (response instanceof ConnectResetResponse) {
                    try {
                        buildClient();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                
                }
            }
        });
        
    }
    
    /**
     * Send Heart Beat Request.
     */
    public void sendBeat() {
    
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .build();
        HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
        GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(meta).setType(heartBeatRequest.getType())
                .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(heartBeatRequest)))
                        .build()).build();
        GrpcResponse response = grpcServiceStub.request(streamRequest);
    }
    
    private void buildClient() throws NacosException {
    
        String serverAddress = getServerListFactory().genNextServer();
    
        String serverIp = "";
        int serverPort = 0;
        
        if (serverAddress.contains("http")) {
            serverIp = serverAddress.split(":")[1].replaceAll("//", "");
            serverPort = Integer.valueOf(serverAddress.split(":")[2].replaceAll("//", ""));
        } else {
            serverIp = serverAddress.split(":")[0];
            serverPort = Integer.valueOf(serverAddress.split(":")[1]);
        }
    
        this.channel = ManagedChannelBuilder.forAddress(serverIp, serverPort + 1000).usePlaintext(true).build();
        
        grpcStreamServiceStub = RequestStreamGrpc.newStub(channel);
    
        grpcServiceStub = RequestGrpc.newBlockingStub(channel);
    
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .build();
        GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(meta).build();
    
        grpcStreamServiceStub.requestStream(streamRequest, new StreamObserver<GrpcResponse>() {
            @Override
            public void onNext(GrpcResponse grpcResponse) {
                String message = grpcResponse.getBody().getValue().toStringUtf8();
            
                String type = grpcResponse.getType();
                String bodyString = grpcResponse.getBody().getValue().toStringUtf8();
                Class classByType = ResponseRegistry.getClassByType(type);
                final Response response;
                if (classByType != null) {
                    response = (Response) JacksonUtils.toObj(bodyString, classByType);
                } else {
                    PlainBodyResponse myresponse = JacksonUtils.toObj(bodyString, PlainBodyResponse.class);
                    myresponse.setBodyString(bodyString);
                    response = myresponse;
                }
            
                changeListenReplyListeners.forEach(new Consumer<ChangeListenResponseHandler>() {
                    @Override
                    public void accept(ChangeListenResponseHandler changeListenResponseHandler) {
                        changeListenResponseHandler.responseReply(response);
                    }
                });
            }
        
            @Override
            public void onError(Throwable throwable) {
            
            }
        
            @Override
            public void onCompleted() {
            
            }
        });
        
    }
    
    private class NacosStreamObserver implements StreamObserver<GrpcResponse> {
        
        @Override
        public void onNext(GrpcResponse response) {
        
        }
        
        @Override
        public void onError(Throwable t) {
            //LOGGER.error("[GRPC] config error", t);
            //rebuildClient();
        }
        
        @Override
        public void onCompleted() {
            //LOGGER.info("[GRPC] config connection closed.");
            //rebuildClient();
        }
    }
    
    @Override
    public void switchServer() {
    
    }
    
    @Override
    public Response request(Request request) {
        
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .build();
        GrpcRequest grpcrequest = GrpcRequest.newBuilder().setMetadata(meta).setType(request.getType())
                .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(request)))).build();
        GrpcResponse response = grpcServiceStub.request(grpcrequest);
        String type = response.getType();
        String bodyString = response.getBody().getValue().toStringUtf8();
        // transfrom grpcResponse to response model
        Class classByType = ResponseRegistry.getClassByType(type);
        if (classByType != null) {
            Object object = JacksonUtils.toObj(bodyString, classByType);
            return (Response) object;
        } else {
            PlainBodyResponse myresponse = JacksonUtils.toObj(bodyString, PlainBodyResponse.class);
            myresponse.setBodyString(bodyString);
            return (PlainBodyResponse) myresponse;
        }
    }
    
}
