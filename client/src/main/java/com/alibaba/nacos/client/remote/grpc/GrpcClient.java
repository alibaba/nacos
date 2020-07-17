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
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientStatus;
import com.alibaba.nacos.client.remote.ServerListFactory;
import com.alibaba.nacos.client.remote.ServerPushResponseHandler;
import com.alibaba.nacos.client.utils.ClientCommonUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    
    protected RequestGrpc.RequestBlockingStub grpcServiceStub;
    
    private ReentrantLock startClientLock = new ReentrantLock();
    
    ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(5, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.config.grpc.worker");
            t.setDaemon(true);
            return t;
        }
    });
    
    /**
     * Reconnect to current server before switch a new server
     */
    private static final int MAX_RECONNECT_TIMES = 5;
    
    private AtomicInteger reConnectTimesLeft = new AtomicInteger(MAX_RECONNECT_TIMES);
    
    public GrpcClient() {
        super();
    }
    
    public GrpcClient(ServerListFactory serverListFactory) {
        super(serverListFactory);
    }
    
    /**
     * tryConnectServer. 1.if in start stage, this method will return true after success to connect to th server or
     * return false after timeout . 2.if in running stage ,this method will start a thread to reconnect the server
     * asynchronous,and will return true directly.
     *
     * @return
     */
    private boolean tryConnectServer() {
        
        LOGGER.error("tryConnectServer.....clientStarus={},currentServer={}", rpcClientStatus.get(),
                getServerListFactory().getCurrentServer());
        //当前状态未运行中，说明是运行期异常，并且没有其他线程启动重联
        if (rpcClientStatus.get() == RpcClientStatus.RUNNING) {
            boolean updateSucess = rpcClientStatus
                    .compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.RE_CONNECTING);
            if (updateSucess) {
                executorService.schedule(new Runnable() {
                    @Override
                    public void run() {
                        while (rpcClientStatus.get() != RpcClientStatus.RUNNING) {
                            
                            boolean sucess = serverCheck();
                            if (sucess) {
                                System.out.println("Service check success ....");
                                notifyReConnected();
                                rpcClientStatus.compareAndSet(RpcClientStatus.RE_CONNECTING, RpcClientStatus.RUNNING);
                                reConnectTimesLeft.set(MAX_RECONNECT_TIMES);
                                
                            } else {
                                
                                int leftRetryTimes = reConnectTimesLeft.decrementAndGet();
                                if (leftRetryTimes <= 0) {
                                    getServerListFactory().genNextServer();
                                    reConnectTimesLeft.set(MAX_RECONNECT_TIMES);
                                    try {
                                        reBuildClient();
                                    } catch (NacosException e) {
                                        LOGGER.error("Fail to build client. ", e);
                                    }
                                }
                            }
                            
                            try {
                                Thread.sleep(200L);
                            } catch (InterruptedException e) {
                                //do nothing
                            }
                        }
                        
                    }
                }, 0L, TimeUnit.MILLISECONDS);
            }
            
            return true;
            
        } else if (rpcClientStatus.get() == RpcClientStatus.RE_CONNECTING
                || rpcClientStatus.get() == RpcClientStatus.STARTING) {
            // Direct return if current client is in reconnting status...
            return true;
        } else if (rpcClientStatus.get() == RpcClientStatus.INITED) {
            //First time to start ....
            
            boolean updateStatusSucess = rpcClientStatus
                    .compareAndSet(RpcClientStatus.INITED, RpcClientStatus.STARTING);
            if (!updateStatusSucess) {
                return true;
            }
            
            try {
                buildClient();
            } catch (NacosException e) {
                LOGGER.error("Fail to build client  firt time in start. ", e);
            }
            ScheduledFuture<Boolean> future = executorService.schedule(new Callable<Boolean>() {
                
                @Override
                public Boolean call() throws Exception {
                    
                    while (rpcClientStatus.get() != RpcClientStatus.RUNNING) {
                        boolean sucess = serverCheck();
                        if (sucess) {
                            rpcClientStatus.compareAndSet(RpcClientStatus.RE_CONNECTING, RpcClientStatus.RUNNING);
                            reConnectTimesLeft.set(MAX_RECONNECT_TIMES);
                            return true;
                        } else {
                            
                            int leftRetryTimes = reConnectTimesLeft.decrementAndGet();
                            if (leftRetryTimes <= 0) {
                                getServerListFactory().genNextServer();
                                reConnectTimesLeft.set(MAX_RECONNECT_TIMES);
                                try {
                                    reBuildClient();
                                } catch (NacosException e) {
                                    LOGGER.error("Fail to build client in start. ", e);
                                }
                            }
                        }
                        
                        try {
                            Thread.sleep(200L);
                        } catch (InterruptedException e) {
                            //do nothing
                        }
                    }
                    return true;
                }
            }, 0L, TimeUnit.MILLISECONDS);
            
            try {
                Boolean aBoolean = future.get(10000L, TimeUnit.MILLISECONDS);
                return aBoolean.booleanValue();
            } catch (Exception e) {
                LOGGER.error("Fail to start RpcCLient . ", e);
                return false;
            }
            
        }
        return true;
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
    
        tryConnectServer();
        
        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sendBeat();
            }
        }, 5000, 10000, TimeUnit.MILLISECONDS);
    
        rpcClientStatus.compareAndSet(RpcClientStatus.STARTING, RpcClientStatus.RUNNING);
        
        super.registerServerPushResponseHandler(new ServerPushResponseHandler() {
            @Override
            public void responseReply(Response response) {
                if (response instanceof ConnectResetResponse) {
                    try {
                        buildClient();
                    } catch (Exception e) {
                        System.out.println(e);
                        e.printStackTrace();
                        LOGGER.error("rebuildClient error ", e);
                    }
    
                }
            }
        });
        
    }
    
    /**
     * Send Heart Beat Request.
     */
    public void sendBeat() {
        try {
        
            if (this.rpcClientStatus.get() == RpcClientStatus.RE_CONNECTING) {
                return;
            }
        
            GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                    .build();
            HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
            GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(meta).setType(heartBeatRequest.getType())
                    .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(heartBeatRequest)))
                            .build()).build();
            GrpcResponse response = grpcServiceStub.request(streamRequest);
        } catch (Exception e) {
        
            System.out.println(e);
            e.printStackTrace(System.out);
            // 心跳失败
            LOGGER.error("Send heart beat error ", e);
        
            tryConnectServer();
        }
    }
    
    private boolean serverCheck() {
        try {
            GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                    .build();
            HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
            GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(meta).setType(heartBeatRequest.getType())
                    .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(heartBeatRequest)))
                            .build()).build();
            GrpcResponse response = grpcServiceStub.request(streamRequest);
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void reBuildClient() throws NacosException {
        if (this.channel != null && !this.channel.isShutdown()) {
            System.out.println("Shutdown curent channel...");
            this.channel.shutdown();
        }
        buildClient();
    }
    
    private void buildClient() throws NacosException {
    
        String serverAddress = getServerListFactory().getCurrentServer();
        
        String serverIp = "";
        int serverPort = 1000;
        
        if (serverAddress.contains("http")) {
            serverIp = serverAddress.split(":")[1].replaceAll("//", "");
            serverPort += Integer.valueOf(serverAddress.split(":")[2].replaceAll("//", ""));
        } else {
            serverIp = serverAddress.split(":")[0];
            serverPort += Integer.valueOf(serverAddress.split(":")[1]);
        }
    
        System.out.println("Build client... " + getServerListFactory().getCurrentServer());
        
        LOGGER.info("GrpcClient start to connect to rpc server, serverIp={},port={}", serverIp, serverPort);
        
        this.channel = ManagedChannelBuilder.forAddress(serverIp, serverPort).usePlaintext(true).build();
        
        grpcStreamServiceStub = RequestStreamGrpc.newStub(channel);
        
        grpcServiceStub = RequestGrpc.newBlockingStub(channel);
        
        GrpcMetadata meta = GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(NetUtils.localIP())
                .setVersion(ClientCommonUtils.VERSION).build();
        GrpcRequest streamRequest = GrpcRequest.newBuilder().setMetadata(meta).build();
        
        LOGGER.info("GrpcClient send stream request  grpc server,streamRequest:{}", streamRequest);
        
        grpcStreamServiceStub.requestStream(streamRequest, new StreamObserver<GrpcResponse>() {
            @Override
            public void onNext(GrpcResponse grpcResponse) {
    
                LOGGER.info(" stream response receive  ,original reponse :{}", grpcResponse);
                
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
            }
    
            @Override
            public void onError(Throwable throwable) {
            
            }
    
            @Override
            public void onCompleted() {
            
            }
        });
        
    }
    
    @Override
    public Response request(Request request) throws NacosException {
    
        try {
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
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
    
}
