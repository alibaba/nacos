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
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
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
    
    static final Logger LOGGER = LoggerFactory.getLogger(GrpcClient.class);
    
    private ThreadPoolExecutor grpcExecutor = null;
    
    private static final long DEFAULT_MAX_INBOUND_MESSAGE_SIZE = 10 * 1024 * 1024L;
    
    private static final long DEFAULT_KEEP_ALIVE_TIME = 6 * 60 * 1000;
    
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
        if (grpcExecutor != null) {
            LOGGER.info("Shutdown grpc executor " + grpcExecutor);
            grpcExecutor.shutdown();
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
        
        ManagedChannelBuilder<?> o = ManagedChannelBuilder.forAddress(serverIp, serverPort).executor(grpcExecutor)
                .compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .maxInboundMessageSize(getInboundMessageSize())
                .keepAliveTime(keepAliveTimeMillis(), TimeUnit.MILLISECONDS).usePlaintext();
        
        ManagedChannel managedChannelTemp = o.build();
        
        return RequestGrpc.newFutureStub(managedChannelTemp);
        
    }
    
    private int getInboundMessageSize() {
        String messageSize = System.getProperty("nacos.remote.client.grpc.maxinbound.message.size",
                String.valueOf(DEFAULT_MAX_INBOUND_MESSAGE_SIZE));
        return Integer.parseInt(messageSize);
    }
    
    private int keepAliveTimeMillis() {
        String keepAliveTimeMillis = System
                .getProperty("nacos.remote.grpc.keep.alive.millis", String.valueOf(DEFAULT_KEEP_ALIVE_TIME));
        return Integer.parseInt(keepAliveTimeMillis);
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
    private Response serverCheck(String ip, int port, RequestGrpc.RequestFutureStub requestBlockingStub) {
        try {
            if (requestBlockingStub == null) {
                return null;
            }
            ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
            Payload grpcRequest = GrpcUtils.convert(serverCheckRequest);
            ListenableFuture<Payload> responseFuture = requestBlockingStub.request(grpcRequest);
            Payload response = responseFuture.get(3000L, TimeUnit.MILLISECONDS);
            //receive connection unregister response here,not check response is success.
            return (Response) GrpcUtils.parse(response);
        } catch (Exception e) {
            LoggerUtils.printIfErrorEnabled(LOGGER,
                    "Server check fail, please check server {} ,port {} is available , error ={}", ip, port, e);
            return null;
        }
    }
    
    private StreamObserver<Payload> bindRequestStream(final BiRequestStreamGrpc.BiRequestStreamStub streamStub,
            final GrpcConnection grpcConn) {
        
        return streamStub.requestBiStream(new StreamObserver<Payload>() {
            
            @Override
            public void onNext(Payload payload) {
                
                LoggerUtils.printIfDebugEnabled(LOGGER, "[{}]Stream server request receive, original info: {}",
                        grpcConn.getConnectionId(), payload.toString());
                try {
                    Object parseBody = GrpcUtils.parse(payload);
                    final Request request = (Request) parseBody;
                    if (request != null) {
                        
                        try {
                            Response response = handleServerRequest(request);
                            if (response != null) {
                                response.setRequestId(request.getRequestId());
                                sendResponse(response);
                            } else {
                                LOGGER.warn("[{}]Fail to process server request, ackId->{}", grpcConn.getConnectionId(),
                                        request.getRequestId());
                            }
                            
                        } catch (Exception e) {
                            LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Handle server request exception: {}",
                                    grpcConn.getConnectionId(), payload.toString(), e.getMessage());
                            sendResponse(request.getRequestId(), false);
                        }
                        
                    }
                    
                } catch (Exception e) {
                    
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Error to process server push response: {}",
                            grpcConn.getConnectionId(), payload.getBody().getValue().toStringUtf8());
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream error, switch server,error={}",
                            grpcConn.getConnectionId(), throwable);
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                    
                } else {
                    LoggerUtils.printIfWarnEnabled(LOGGER, "[{}]Ignore error event,isRunning:{},isAbandon={}",
                            grpcConn.getConnectionId(), isRunning, isAbandon);
                }
                
            }
            
            @Override
            public void onCompleted() {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream onCompleted, switch server",
                            grpcConn.getConnectionId());
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                    
                } else {
                    LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]Ignore complete event,isRunning:{},isAbandon={}",
                            grpcConn.getConnectionId(), isRunning, isAbandon);
                }
                
            }
        });
    }
    
    private void sendResponse(String ackId, boolean success) {
        try {
            PushAckRequest request = PushAckRequest.build(ackId, success);
            this.currentConnection.request(request, 3000L);
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", this.currentConnection.getConnectionId(), ackId);
        }
    }
    
    private void sendResponse(Response response) {
        try {
            ((GrpcConnection) this.currentConnection).sendResponse(response);
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", this.currentConnection.getConnectionId(),
                    response.getRequestId());
        }
    }
    
    @Override
    public Connection connectToServer(ServerInfo serverInfo) {
        try {
            if (grpcExecutor == null) {
                int threadNumber = ThreadUtils.getSuitableThreadCount(8);
                grpcExecutor = new ThreadPoolExecutor(threadNumber, threadNumber, 10L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(10000),
                        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("nacos-grpc-client-executor-%d")
                                .build());
                grpcExecutor.allowCoreThreadTimeOut(true);
                
            }
            int port = serverInfo.getServerPort() + rpcPortOffset();
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.getServerIp(), port);
            if (newChannelStubTemp != null) {
                
                Response response = serverCheck(serverInfo.getServerIp(), port, newChannelStubTemp);
                if (response == null || !(response instanceof ServerCheckResponse)) {
                    shuntDownChannel((ManagedChannel) newChannelStubTemp.getChannel());
                    return null;
                }
                
                BiRequestStreamGrpc.BiRequestStreamStub biRequestStreamStub = BiRequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                GrpcConnection grpcConn = new GrpcConnection(serverInfo, grpcExecutor);
                grpcConn.setConnectionId(((ServerCheckResponse) response).getConnectionId());
                
                //create stream request and bind connection event to this connection.
                StreamObserver<Payload> payloadStreamObserver = bindRequestStream(biRequestStreamStub, grpcConn);
                
                // stream observer to send response to server
                grpcConn.setPayloadStreamObserver(payloadStreamObserver);
                grpcConn.setGrpcFutureServiceStub(newChannelStubTemp);
                grpcConn.setChannel((ManagedChannel) newChannelStubTemp.getChannel());
                //send a  setup request.
                ConnectionSetupRequest conSetupRequest = new ConnectionSetupRequest();
                conSetupRequest.setClientVersion(VersionUtils.getFullClientVersion());
                conSetupRequest.setLabels(super.getLabels());
                conSetupRequest.setAbilities(super.clientAbilities);
                conSetupRequest.setTenant(super.getTenant());
                grpcConn.sendRequest(conSetupRequest);
                //wait to register connection setup
                Thread.sleep(100L);
                return grpcConn;
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("[{}]Fail to connect to server!,error={}", GrpcClient.this.getName(), e);
        }
        return null;
    }
    
}



