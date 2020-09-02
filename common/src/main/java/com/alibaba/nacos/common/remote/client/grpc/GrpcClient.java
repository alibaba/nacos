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
import com.alibaba.nacos.api.grpc.GrpcUtils;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.grpc.auto.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.HeartBeatRequest;
import com.alibaba.nacos.api.remote.request.PushAckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * gRPC Client.
 *
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
public class GrpcClient extends RpcClient {
    
    static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.GRPC;
    }
    
    /**
     * Empty constructor.
     */
    public GrpcClient(String name) {
        super(name);
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
    
    @Override
    public int rpcPortOffset() {
        return 1000;
    }
    
    /**
     * Send Heart Beat Request.
     */
    public void sendBeat() {
    
        int maxRetryTimes = 3;
        while (maxRetryTimes > 0) {
    
            try {
                if (!isRunning() && !overActiveTime()) {
                    return;
                }
                HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
                Response heartBeatResponse = this.currentConnetion.request(heartBeatRequest);
                if (heartBeatResponse != null && heartBeatResponse instanceof ConnectResetResponse) {
                    LOGGER.warn(" connection is not register to current server ,trying to switch server ");
                    switchServerAsync();
                }
                return;
            } catch (Exception e) {
                LOGGER.warn("Send heart beat fail,server is not avaliable now,retry ... ");
                maxRetryTimes--;
                LOGGER.error("Send heart beat error, ", e);
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                //No nothing.
            }
        }
    
        LOGGER.warn("max retry times for send heart beat fail reached,trying to switch server... ");
        switchServerAsync();
    }
    
    private Metadata buildMeta(String connectionIdInner) {
        Metadata meta = Metadata.newBuilder().setClientIp(NetUtils.localIP())
                .setVersion(VersionUtils.getFullClientVersion()).putAllLabels(labels).build();
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
            Payload grpcRequest = GrpcUtils.convert(serverCheckRequest, buildMeta(""));
            ListenableFuture<Payload> responseFuture = requestBlockingStub.request(grpcRequest);
            Payload response = responseFuture.get();
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
    private void bindRequestStream(final RequestStreamGrpc.RequestStreamStub streamStub) {
        Payload streamRequest = Payload.newBuilder().setMetadata(buildMeta("")).build();
        LOGGER.info("GrpcClient send stream request  grpc server,streamRequest:{}", streamRequest);
        streamStub.requestStream(streamRequest, new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
    
                LOGGER.debug(" stream server reuqust receive  ,original info :{}", payload.toString());
                try {
                    final Request request = (Request) GrpcUtils.parse(payload);
                    
                    if (request != null) {
                        try {
                            Response response = handleServerRequest(request);
                            response.setRequestId(request.getRequestId());
                            sendResponse(response);
                        } catch (Exception e) {
                            sendResponse(request.getRequestId(), false);
                        }
                    }
    
                } catch (Exception e) {
                    LOGGER.error("error tp process server push response  :{}",
                            payload.getBody().getValue().toStringUtf8());
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                System.out.println("on error1 ,switch server ");
                switchServerAsync();
            }
            
            @Override
            public void onCompleted() {
                System.out.println("onCompleted 1,switch server " + this);
                switchServerAsync();
            }
        });
    }
    
    private StreamObserver<Payload> bindRequestStream(final BiRequestStreamGrpc.BiRequestStreamStub streamStub) {
        
        final StreamObserver<Payload> payloadStreamObserver = streamStub.requestBiStream(new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                LOGGER.debug(" stream server reuqust receive  ,original info :{}", payload.toString());
                try {
                    final Request request = (Request) GrpcUtils.parse(payload);
                    
                    if (request != null) {
                        try {
                            Response response = handleServerRequest(request);
                            response.setRequestId(request.getRequestId());
                            sendResponse(response);
                        } catch (Exception e) {
                            sendResponse(request.getRequestId(), false);
                        }
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("error tp process server push response  :{}",
                            payload.getBody().getValue().toStringUtf8());
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
                System.out.println("on error ,switch server ");
                switchServerAsync();
            }
            
            @Override
            public void onCompleted() {
                System.out.println("onCompleted ,switch server " + this);
                switchServerAsync();
            }
        });
    
        return payloadStreamObserver;
    }
    
    private void sendResponse(String ackId, boolean success) {
        try {
            PushAckRequest request = PushAckRequest.build(ackId, success);
            this.currentConnetion.request(request);
        } catch (Exception e) {
            LOGGER.error("error to send ack  response,ackId->:{}", ackId);
        }
    }
    
    private void sendResponse(Response response) {
        try {
            ((GrpcConnection) this.currentConnetion).sendResponse(response);
        } catch (Exception e) {
            LOGGER.error("error to send ack  response,ackId->:{}", response.getRequestId());
        }
    }
    
    @Override
    public void start() throws NacosException {
        super.start();
        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sendBeat();
            }
        }, 0, ACTIVE_INTERNAL, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public Connection connectToServer(ServerInfo serverInfo) {
        try {
            LOGGER.info("trying  to connect to server, " + serverInfo);
            
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.getServerIp(),
                    serverInfo.getServerPort());
            if (newChannelStubTemp != null) {
                
                LOGGER.info("success to create a connection to a server.");
                RequestStreamGrpc.RequestStreamStub requestStreamStubTemp = RequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                BiRequestStreamGrpc.BiRequestStreamStub biRequestStreamStub = BiRequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                StreamObserver<Payload> payloadStreamObserver = bindRequestStream(biRequestStreamStub);
                GrpcConnection grpcConn = new GrpcConnection(serverInfo, payloadStreamObserver);
                ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest(NetUtils.localIP(),
                        VersionUtils.getFullClientVersion(), labels);
                grpcConn.sendRequest(conconSetupRequest);
                
                //switch current channel and stub
                RequestGrpc.RequestFutureStub grpcFutureServiceStubTemp = RequestGrpc
                        .newFutureStub(newChannelStubTemp.getChannel());
                grpcConn.setChannel((ManagedChannel) newChannelStubTemp.getChannel());
                
                grpcConn.setGrpcFutureServiceStub(grpcFutureServiceStubTemp);
                grpcConn.setGrpcStreamServiceStub(requestStreamStubTemp);
                return grpcConn;
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("fail to connect to server  ! ", e);
        }
        return null;
    }
    
    @Override
    public void shutdown() throws NacosException {
    
    }
}



