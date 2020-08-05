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
import com.alibaba.nacos.api.grpc.RequestGrpc;
import com.alibaba.nacos.api.grpc.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.request.HeartBeatRequest;
import com.alibaba.nacos.api.remote.request.PushAckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.ConnectionUnregisterResponse;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.ResponseTypeConstants;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.client.ResponseRegistry;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerPushResponseHandler;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * gRPC Client.
 *
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
public class GrpcClient extends RpcClient {
    
    static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
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
    
    /**
     * executor to execute future request.
     */
    private ExecutorService aynsRequestExecutor;
    
    /**
     * Empty constructor.
     */
    public GrpcClient() {
        super();
    }
    
    /**
     * constructor with a server liset factory.
     *
     * @param serverListFactory serverListFactory.
     */
    public GrpcClient(ServerListFactory serverListFactory) {
        super(serverListFactory);
    }
    
    /**
     * create a new channel with specfic server address.
     *
     * @param serverIp   serverIp.
     * @param serverPort serverPort.
     * @return if server check success,return a non-null stub.
     */
    private RequestGrpc.RequestFutureStub createNewChannelStub(String serverIp, int serverPort) {
        
        ManagedChannel managedChannelTemp = ManagedChannelBuilder.forAddress(serverIp, serverPort).usePlaintext()
                .build();
    
        RequestGrpc.RequestFutureStub grpcServiceStubTemp = RequestGrpc.newFutureStub(managedChannelTemp);
        
        boolean checkSucess = serverCheck(grpcServiceStubTemp);
    
        if (checkSucess) {
            return grpcServiceStubTemp;
        } else {
            shuntDownChannel(managedChannelTemp);
            return null;
        }
    
    }
    
    /**
     * shutdown a  channel.
     *
     * @param managedChannel channel to be shutdown.
     */
    private void shuntDownChannel(ManagedChannel managedChannel) {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            managedChannel.shutdownNow();
        }
    }
    
    /**
     * try to connect to server ,if fail at first time ,it will retry asynchrous.
     */
    private void connectToServer() {
        LOGGER.info("starting to connect to server .  ");
        
        rpcClientStatus.compareAndSet(RpcClientStatus.INITED, RpcClientStatus.STARTING);
        try {
            GrpcServerInfo serverInfo = nextServer();
            LOGGER.info("trying  to connect to server, " + serverInfo);
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.getServerIp(),
                    serverInfo.getServerPort());
            if (newChannelStubTemp != null) {
                
                LOGGER.info("connect to server success !");
                
                RequestStreamGrpc.RequestStreamStub requestStreamStubTemp = RequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                
                bindRequestStream(requestStreamStubTemp);
                //switch current channel and stub
                channel = (ManagedChannel) newChannelStubTemp.getChannel();
                grpcStreamServiceStub = requestStreamStubTemp;
                RequestGrpc.RequestFutureStub grpcFutureServiceStubTemp = RequestGrpc
                        .newFutureStub(newChannelStubTemp.getChannel());
                grpcFutureServiceStub = grpcFutureServiceStubTemp;
                rpcClientStatus.set(RpcClientStatus.RUNNING);
                eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
                return;
            }
        } catch (Exception e) {
            LOGGER.error("fail to connect to server  ! ", e);
        }
        switchServer();
        
    }
    
    @Override
    public void innerStart() throws NacosException {
        
        if (rpcClientStatus.get() == RpcClientStatus.WAIT_INIT) {
            LOGGER.error("RpcClient has not init yet, please check init ServerListFactory...");
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "rpc client not init yet");
        }
        if (rpcClientStatus.get() != RpcClientStatus.INITED) {
            return;
        }
    
        aynsRequestExecutor = Executors.newFixedThreadPool(10);
        
        connectToServer();
    
        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sendBeat();
            }
        }, 0, 3000, TimeUnit.MILLISECONDS);
    
        super.registerServerPushResponseHandler(new ServerPushResponseHandler() {
            @Override
            public void responseReply(Response response) {
                if (response instanceof ConnectResetResponse) {
                    try {
    
                        if (!isRunning()) {
                            return;
                        }
                        switchServer();
                    } catch (Exception e) {
                        LOGGER.error("switch server  error ", e);
                    }
                }
            }
        });
    
    }
    
    @Override
    public int rpcPortOffset() {
        return 1000;
    }
    
    private final ReentrantLock switchingLock = new ReentrantLock();
    
    /**
     * switch a new server.
     */
    private void switchServer() {
        
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
    
                try {
                    //only one thread can execute switching meantime.
                    boolean innerLock = switchingLock.tryLock();
                    if (!innerLock) {
                        return;
                    }
        
                    if (rpcClientStatus.get() == RpcClientStatus.RUNNING) {
                        eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
                    }
                    rpcClientStatus.set(RpcClientStatus.SWITCHING_SERVER);
                    // loop until start client success.
                    while (!isRunning()) {
    
                        //1.get a new server
                        GrpcServerInfo serverInfo = nextServer();
                        //2.create a new channel to new server
                        RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(
                                serverInfo.getServerIp(), serverInfo.getServerPort());
                        if (newChannelStubTemp != null) {
                            RequestStreamGrpc.RequestStreamStub requestStreamStubTemp = RequestStreamGrpc
                                    .newStub(newChannelStubTemp.getChannel());
    
                            bindRequestStream(requestStreamStubTemp);
                            final ManagedChannel depratedChannel = channel;
                            //switch current channel and stub
                            channel = (ManagedChannel) newChannelStubTemp.getChannel();
                            grpcStreamServiceStub = requestStreamStubTemp;
                            grpcFutureServiceStub = newChannelStubTemp;
                            rpcClientStatus.getAndSet(RpcClientStatus.RUNNING);
                            eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
                            shuntDownChannel(depratedChannel);
                            continue;
                        }
                        //
                        try {
                            //sleep 3 second to switch next server.
                            Thread.sleep(3000L);
                        } catch (InterruptedException e) {
                            // Do  nothing.
                        }
                    }
                } finally {
                    switchingLock.unlock();
                }
            }
        }, 0L, TimeUnit.MILLISECONDS);
        
    }
    
    /**
     * Send Heart Beat Request.
     */
    public void sendBeat() {
    
        int maxRetryTimes = 3;
        while (maxRetryTimes > 0) {
    
            try {
                if (!isRunning()) {
                    return;
                }
                HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
                GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(buildMeta())
                        .setType(heartBeatRequest.getType()).setBody(Any.newBuilder()
                                .setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(heartBeatRequest))).build())
                        .build();
                ListenableFuture<GrpcResponse> requestFuture = grpcFutureServiceStub.request(streamRequest);
                GrpcResponse response = requestFuture.get();
                if (ResponseTypeConstants.CONNECION_UNREGISTER.equals(response.getType())) {
                    LOGGER.warn(" connection is not register to current server ,trying to switch server ");
                    switchServer();
                }
                return;
            } catch (Exception e) {
                LOGGER.warn("Send heart beat fail,server is not avaliable now,retry ... ");
                maxRetryTimes--;
                LOGGER.error("Send heart beat error, ", e);
            }
        }
    
        LOGGER.warn("max retry times for send heart beat fail reached,trying to switch server... ");
        switchServer();
    }
    
    private GrpcMetadata buildMeta() {
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .setVersion(VersionUtils.getFullClientVersion()).build();
        return meta;
    }
    
    /**
     * chenck server if ok.
     *
     * @param requestBlockingStub requestBlockingStub used to check server.
     * @return
     */
    private boolean serverCheck(RequestGrpc.RequestFutureStub requestBlockingStub) {
        try {
            if (requestBlockingStub == null) {
                return false;
            }
            ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
            GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(buildMeta())
                    .setType(serverCheckRequest.getType())
                    .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(serverCheckRequest)))
                                    .build()).build();
            ListenableFuture<GrpcResponse> responseFuture = requestBlockingStub.request(streamRequest);
            GrpcResponse response = responseFuture.get();
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * bind request stream observer (send a connection).
     *
     * @param streamStub streamStub to bind.
     */
    private void bindRequestStream(RequestStreamGrpc.RequestStreamStub streamStub) {
        GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(buildMeta()).build();
        LOGGER.info("GrpcClient send stream request  grpc server,streamRequest:{}", streamRequest);
        streamStub.requestStream(streamRequest, new StreamObserver<GrpcResponse>() {
            @Override
            public void onNext(GrpcResponse grpcResponse) {
    
                LOGGER.debug(" stream response receive  ,original reponse :{}", grpcResponse);
                try {
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
    
                    serverPushResponseListeners.forEach(new Consumer<ServerPushResponseHandler>() {
                        @Override
                        public void accept(ServerPushResponseHandler serverPushResponseHandler) {
                            serverPushResponseHandler.responseReply(response);
                        }
                    });
                    sendAckResponse(grpcResponse.getAck(), true);
                } catch (Exception e) {
                    sendAckResponse(grpcResponse.getAck(), false);
    
                    LOGGER.error("error tp process server push response  :{}", grpcResponse);
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
            }
            
            @Override
            public void onCompleted() {
            }
        });
    }
    
    private void sendAckResponse(String ackId, boolean success) {
        try {
            PushAckRequest request = PushAckRequest.build(ackId, success);
            GrpcRequest grpcrequest = GrpcRequest.newBuilder().setMetadata(buildMeta()).setType(request.getType())
                    .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(request)))).build();
            ListenableFuture<GrpcResponse> requestFuture = grpcFutureServiceStub.request(grpcrequest);
        } catch (Exception e) {
            LOGGER.error("error to send ack  response,ackId->:{}", ackId);
        }
    }
    
    @Override
    public Response request(Request request) throws NacosException {
    
        int maxRetryTimes = 3;
        while (maxRetryTimes > 0) {
            try {
    
                GrpcRequest grpcrequest = GrpcRequest.newBuilder().setMetadata(buildMeta()).setType(request.getType())
                        .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(request))))
                        .build();
                ListenableFuture<GrpcResponse> requestFuture = grpcFutureServiceStub.request(grpcrequest);
                GrpcResponse grpcResponse = requestFuture.get();
                Response response = convertResponse(grpcResponse);
                if (response != null) {
                    return response;
                }
            } catch (Exception e) {
                maxRetryTimes--;
                LOGGER.error("grpc client request error, retry...", e.getMessage(), e);
            }
        }
    
        LOGGER.warn("Max retry times for request fail reached !");
        throw new NacosException(NacosException.SERVER_ERROR, "Fail to request.");
    
    }
    
    private Response convertResponse(GrpcResponse grpcResponse) {
        String type = grpcResponse.getType();
        String bodyString = grpcResponse.getBody().getValue().toStringUtf8();
        
        // transfrom grpcResponse to response model
        Class classByType = ResponseRegistry.getClassByType(type);
        if (classByType != null) {
            Object object = JacksonUtils.toObj(bodyString, classByType);
            if (object instanceof ConnectionUnregisterResponse) {
                LOGGER.warn("grpc client request error, connection is unregister ");
                return null;
            }
            return (Response) object;
        } else {
            PlainBodyResponse myresponse = JacksonUtils.toObj(bodyString, PlainBodyResponse.class);
            myresponse.setBodyString(bodyString);
            return (PlainBodyResponse) myresponse;
        }
    }
    
    @Override
    public void asyncRequest(Request request, final FutureCallback<Response> callback) throws NacosException {
        GrpcRequest grpcrequest = GrpcRequest.newBuilder().setMetadata(buildMeta()).setType(request.getType())
                .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(request)))).build();
        ListenableFuture<GrpcResponse> requestFuture = grpcFutureServiceStub.request(grpcrequest);
        Futures.addCallback(requestFuture, new FutureCallback<GrpcResponse>() {
            @Override
            public void onSuccess(@NullableDecl GrpcResponse grpcResponse) {
                Response response = convertResponse(grpcResponse);
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
    public void shutdown() throws NacosException {
        if (this.channel != null && !this.channel.isShutdown()) {
            this.channel.shutdownNow();
        }
    }
    
}



