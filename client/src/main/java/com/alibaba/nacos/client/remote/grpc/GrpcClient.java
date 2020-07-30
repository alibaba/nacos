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
import com.alibaba.nacos.api.remote.request.PushAckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.ConnectionUnregisterResponse;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseTypeConstants;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientStatus;
import com.alibaba.nacos.client.remote.ServerListFactory;
import com.alibaba.nacos.client.remote.ServerPushResponseHandler;
import com.alibaba.nacos.client.utils.ClientCommonUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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
    
    private static final Logger LOGGER = LogUtils.logger(GrpcClient.class);
    
    protected ManagedChannel channel;
    
    protected RequestStreamGrpc.RequestStreamStub grpcStreamServiceStub;
    
    protected RequestGrpc.RequestFutureStub grpcFutureServiceStub;
    
    private ExecutorService aynsRequestExecutor = Executors.newFixedThreadPool(10);
    
    private ExecutorService eventExecutor = Executors.newFixedThreadPool(1);
    
    LinkedBlockingQueue<ConnectionEvent> eventLinkedBlockingQueue = new LinkedBlockingQueue<ConnectionEvent>();
    
    private ReentrantLock lock = new ReentrantLock();
    
    public GrpcClient() {
        super();
    }
    
    public GrpcClient(ServerListFactory serverListFactory) {
        super(serverListFactory);
    }
    
    /**
     * create a new channel .
     *
     * @param serverIp   serverIp.
     * @param serverPort serverPort.
     * @return if server check success,return stub.
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
    
    private void connectToServer() {
        
        rpcClientStatus.compareAndSet(RpcClientStatus.INITED, RpcClientStatus.STARTING);
        
        GrpcServerInfo serverInfo = nextServer();
        RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.serverIp,
                serverInfo.serverPort);
        if (newChannelStubTemp != null) {
            RequestStreamGrpc.RequestStreamStub requestStreamStubTemp = RequestStreamGrpc
                    .newStub(newChannelStubTemp.getChannel());
            RequestGrpc.RequestFutureStub grpcFutureServiceStubTemp = RequestGrpc
                    .newFutureStub(newChannelStubTemp.getChannel());
            
            bindRequestStream(requestStreamStubTemp);
            //switch current channel and stub
            channel = (ManagedChannel) newChannelStubTemp.getChannel();
            grpcFutureServiceStub = grpcFutureServiceStubTemp;
            grpcStreamServiceStub = requestStreamStubTemp;
            rpcClientStatus.set(RpcClientStatus.RUNNING);
            eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
            notifyConnected();
        } else {
            switchServer(true);
        }
    }
    
    @Override
    public void start() throws NacosException {
        
        if (rpcClientStatus.get() == RpcClientStatus.WAIT_INIT) {
            LOGGER.error("RpcClient has not init yet, please check init ServerListFactory...");
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "RpcClient not init yet");
        }
        if (rpcClientStatus.get() == RpcClientStatus.RUNNING || rpcClientStatus.get() == RpcClientStatus.STARTING) {
            return;
        }
        
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
                        eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
                        switchServer(false);
                    } catch (Exception e) {
                        LOGGER.error("rebuildClient error ", e);
                    }
                }
            }
        });
        
        eventExecutor.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        ConnectionEvent event = eventLinkedBlockingQueue.take();
                        if (event.isConnected()) {
                            notifyConnected();
                        } else if (event.isDisConnected()) {
                            notifyDisConnected();
                        }
                    } catch (Exception e) {
                        LOGGER.error("connection event process fail ", e);
                    }
                }
            }
        });
    }
    
    /**
     * switch a new server.
     */
    private void switchServer(final boolean onStarting) {
        
        //try to get operate lock.
        boolean lockResult = lock.tryLock();
        if (!lockResult) {
            return;
        }
        
        if (onStarting) {
            // access on startup fail
            rpcClientStatus.set(RpcClientStatus.SWITCHING_SERVER);
            
        } else {
            // access from running status, sendbeat fail or receive reset message from server.
            boolean changeStatusSuccess = rpcClientStatus
                    .compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.SWITCHING_SERVER);
            if (!changeStatusSuccess) {
                return;
            }
        }
        
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                
                // loop until start client success.
                while (!isRunning()) {
                    
                    //1.get a new server
                    GrpcServerInfo serverInfo = nextServer();
                    //2.get a new channel to new server
                    RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.serverIp,
                            serverInfo.serverPort);
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
            }
        }, 0L, TimeUnit.MILLISECONDS);
        lock.unlock();
        
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
                    eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
                    switchServer(false);
                }
                return;
            } catch (Exception e) {
                LOGGER.warn("Send heart beat fail,server is not avaliable now,retry ... ");
                maxRetryTimes--;
                LOGGER.error("Send heart beat error, ", e);
            }
        }
        
        eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
        LOGGER.warn("Max retry times for send heart beat fail reached,trying to switch server... ");
        switchServer(false);
    }
    
    private GrpcMetadata buildMeta() {
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .setVersion(ClientCommonUtils.VERSION).build();
        
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
            
            ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
            GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(buildMeta())
                    .setType(serverCheckRequest.getType()).setBody(
                            Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(serverCheckRequest)))
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
                    sendAckResponse(grpcResponse.getAck(), true);
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
                    
                } catch (Exception e) {
                    e.printStackTrace(System.out);
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
            System.out.println("send ack error..ackid:" + ackId + ",success=" + success);
            e.printStackTrace();
            //Ignore
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
        
        LOGGER.warn("Max retry times for request fail reached.");
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
                    callback.onFailure(new NacosException(response.getErrorCode(), response.getMessage()));
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
    
    private GrpcServerInfo nextServer() {
        getServerListFactory().genNextServer();
        String serverAddress = getServerListFactory().getCurrentServer();
        return resolveServerInfo(serverAddress);
    }
    
    private GrpcServerInfo currentServer() {
        String serverAddress = getServerListFactory().getCurrentServer();
        return resolveServerInfo(serverAddress);
    }
    
    private GrpcServerInfo resolveServerInfo(String serverAddress) {
        GrpcServerInfo serverInfo = new GrpcServerInfo();
        serverInfo.serverPort = 1000;
        if (serverAddress.contains("http")) {
            serverInfo.serverIp = serverAddress.split(":")[1].replaceAll("//", "");
            serverInfo.serverPort += Integer.valueOf(serverAddress.split(":")[2].replaceAll("//", ""));
        } else {
            serverInfo.serverIp = serverAddress.split(":")[0];
            serverInfo.serverPort += Integer.valueOf(serverAddress.split(":")[1]);
        }
        return serverInfo;
    }
    
    class GrpcServerInfo {
        
        String serverIp;
        
        int serverPort;
        
    }
    
    class ConnectionEvent {
        
        static final int CONNECTED = 1;
        
        static final int DISCONNECTED = 0;
        
        int eventType;
        
        public ConnectionEvent(int eventType) {
            this.eventType = eventType;
        }
        
        public boolean isConnected() {
            return eventType == CONNECTED;
        }
        
        public boolean isDisConnected() {
            return eventType == DISCONNECTED;
        }
    }
}



