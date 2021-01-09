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
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.PushAckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Client.
 *
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class GrpcClient extends RpcClient {
    
    static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
    private ThreadPoolExecutor executor = null;
    
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
    
    @Override
    public void shutdown() throws NacosException {
        super.shutdown();
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    /**
     * create a new channel with specific server address.
     *
     * @param serverIp   serverIp.
     * @param serverPort serverPort.
     * @return if server check success,return a non-null stub.
     */
    private RequestGrpc.RequestFutureStub createNewChannelStub(String serverIp, int serverPort) {
        
        ManagedChannelBuilder<?> o = ManagedChannelBuilder.forAddress(serverIp, serverPort).executor(executor)
                .keepAliveTime(30, TimeUnit.SECONDS).usePlaintext();
        
        ManagedChannel managedChannelTemp = o.build();
        
        RequestGrpc.RequestFutureStub grpcServiceStubTemp = RequestGrpc.newFutureStub(managedChannelTemp);
        
        boolean checkSuccess = serverCheck(grpcServiceStubTemp);
        
        if (checkSuccess) {
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
     * check server if success.
     *
     * @param requestBlockingStub requestBlockingStub used to check server.
     * @return success or not
     */
    private boolean serverCheck(RequestGrpc.RequestFutureStub requestBlockingStub) {
        try {
            if (requestBlockingStub == null) {
                return false;
            }
            ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
            Payload grpcRequest = GrpcUtils.convert(serverCheckRequest, buildMeta());
            ListenableFuture<Payload> responseFuture = requestBlockingStub.request(grpcRequest);
            Payload response = responseFuture.get();
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    private StreamObserver<Payload> bindRequestStream(final BiRequestStreamGrpc.BiRequestStreamStub streamStub,
            final GrpcConnection grpcConn) {
        
        return streamStub.requestBiStream(new StreamObserver<Payload>() {
            
            @Override
            public void onNext(Payload payload) {
                
                LoggerUtils.printIfDebugEnabled(LOGGER, "[{}]Stream server request receive, original info: {}",
                        GrpcClient.this.getName(), payload.toString());
                try {
                    GrpcUtils.PlainRequest parse = GrpcUtils.parse(payload);
                    final Request request = (Request) parse.getBody();
                    if (request != null) {
                        
                        try {
                            Response response = handleServerRequest(request, parse.metadata);
                            if (response != null) {
                                response.setRequestId(request.getRequestId());
                                sendResponse(response);
                            } else {
                                LOGGER.warn("[{}]Fail to process server request, ackId->{}", GrpcClient.this.getName(),
                                        request.getRequestId());
                            }
                            
                        } catch (Exception e) {
                            LoggerUtils.printIfErrorEnabled(LOGGER, e.getMessage(),
                                    "[{}]Handle server request exception: {}", GrpcClient.this.getName(),
                                    payload.toString());
                            sendResponse(request.getRequestId(), false);
                        }
                        
                    }
                    
                } catch (Exception e) {
                    
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Error tp process server push response: {}",
                            GrpcClient.this.getName(), payload.getBody().getValue().toStringUtf8());
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream error, switch server,error={}",
                            GrpcClient.this.getName(), throwable);
                    switchServerAsync();
                } else {
                    LoggerUtils.printIfWarnEnabled(LOGGER, "[{}]ignore error event,isRunning:{},isAbandon={}",
                            GrpcClient.this.getName(), isRunning, isAbandon);
                }
                
            }
            
            @Override
            public void onCompleted() {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream onCompleted, switch server",
                            GrpcClient.this.getName());
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                } else {
                    LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]ignore complete event,isRunning:{},isAbandon={}",
                            GrpcClient.this.getName(), isRunning, isAbandon);
                }
                
            }
        });
    }
    
    private void sendResponse(String ackId, boolean success) {
        try {
            PushAckRequest request = PushAckRequest.build(ackId, success);
            this.currentConnection.request(request, buildMeta());
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", GrpcClient.this.getName(), ackId);
        }
    }
    
    private void sendResponse(Response response) {
        try {
            ((GrpcConnection) this.currentConnection).sendResponse(response);
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", GrpcClient.this.getName(),
                    response.getRequestId());
        }
    }
    
    @Override
    public Connection connectToServer(ServerInfo serverInfo) {
        try {
            if (executor == null) {
                executor = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors() * 8, 10L,
                        TimeUnit.SECONDS, new SynchronousQueue(),
                        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("nacos-grpc-client-executor-%d")
                                .build());
            }
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.getServerIp(),
                    serverInfo.getServerPort());
            if (newChannelStubTemp != null) {
                
                BiRequestStreamGrpc.BiRequestStreamStub biRequestStreamStub = BiRequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                GrpcConnection grpcConn = new GrpcConnection(serverInfo, super.executor);
                
                //create stream request and bind connection event to this connection.
                StreamObserver<Payload> payloadStreamObserver = bindRequestStream(biRequestStreamStub, grpcConn);
                
                // stream observer to send response to server
                grpcConn.setPayloadStreamObserver(payloadStreamObserver);
                grpcConn.setGrpcFutureServiceStub(newChannelStubTemp);
                grpcConn.setChannel((ManagedChannel) newChannelStubTemp.getChannel());
                
                //send a connection setup request.
                ConnectionSetupRequest conSetupRequest = new ConnectionSetupRequest();
                grpcConn.sendRequest(conSetupRequest, buildMeta());
                return grpcConn;
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("[{}]Fail to connect to server!,error={}", GrpcClient.this.getName(), e);
        }
        return null;
    }
    
}



