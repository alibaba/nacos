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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC Client.
 *
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class GrpcClient extends RpcClient {
    
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
        
        final StreamObserver<Payload> payloadStreamObserver = streamStub.requestBiStream(new StreamObserver<Payload>() {
    
            @Override
            public void onNext(Payload payload) {
    
                LoggerUtils.printIfDebugEnabled(LOGGER, " stream server reuqust receive  ,original info :{}",
                        payload.toString());
                try {
                    GrpcUtils.PlainRequest parse = GrpcUtils.parse(payload);
                    final Request request = (Request) parse.getBody();
                    if (request != null) {
                        try {
                            Response response = handleServerRequest(request, parse.metadata);
                            response.setRequestId(request.getRequestId());
                            sendResponse(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                            sendResponse(request.getRequestId(), false);
                        }
                    }
                    
                } catch (Exception e) {
    
                    LoggerUtils.printIfErrorEnabled(LOGGER, "error tp process server push response  :{}",
                            payload.getBody().getValue().toStringUtf8());
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                if (isRunning() && !grpcConn.isAbandon()) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, " Request Stream Error ,switch server ", throwable);
                    if (throwable instanceof StatusRuntimeException) {
                        Status.Code code = ((StatusRuntimeException) throwable).getStatus().getCode();
                        if (Status.UNAVAILABLE.getCode().equals(code) || Status.CANCELLED.getCode().equals(code)) {
                            if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                                switchServerAsync();
                            }
                        }
                    }
                } else {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "client is not running status ,ignore error event");
                }
                
            }
            
            @Override
            public void onCompleted() {
                if (isRunning() && !grpcConn.isAbandon()) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, " Request Stream onCompleted ,switch server ");
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                } else {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "client is not running status ,ignore complete  event ");
                }
                
            }
        });
        
        return payloadStreamObserver;
    }
    
    private void sendResponse(String ackId, boolean success) {
        try {
            PushAckRequest request = PushAckRequest.build(ackId, success);
            this.currentConnetion.request(request, buildMeta());
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
    public Connection connectToServer(ServerInfo serverInfo) {
        try {
            
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(serverInfo.getServerIp(),
                    serverInfo.getServerPort());
            if (newChannelStubTemp != null) {
                
                BiRequestStreamGrpc.BiRequestStreamStub biRequestStreamStub = BiRequestStreamGrpc
                        .newStub(newChannelStubTemp.getChannel());
                GrpcConnection grpcConn = new GrpcConnection(serverInfo);
    
                //create stream request and bind connection event to this connection.
                StreamObserver<Payload> payloadStreamObserver = bindRequestStream(biRequestStreamStub, grpcConn);
    
                // stream observer to send response to server
                grpcConn.setPayloadStreamObserver(payloadStreamObserver);
                grpcConn.setGrpcFutureServiceStub(newChannelStubTemp);
                grpcConn.setChannel((ManagedChannel) newChannelStubTemp.getChannel());
    
                //send a connection setup request.
                ConnectionSetupRequest conconSetupRequest = new ConnectionSetupRequest();
                grpcConn.sendRequest(conconSetupRequest, buildMeta());
                return grpcConn;
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("fail to connect to server  ! ", e);
        }
        return null;
    }
    
}



