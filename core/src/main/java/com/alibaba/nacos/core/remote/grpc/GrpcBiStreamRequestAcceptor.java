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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CHANNEL;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_ID;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_LOCAL_PORT;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_REMOTE_IP;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_REMOTE_PORT;

/**
 * grpc bi stream request .
 *
 * @author liuzunfei
 * @version $Id: GrpcBiStreamRequest.java, v 0.1 2020年09月01日 10:41 PM liuzunfei Exp $
 */
@Service
public class GrpcBiStreamRequestAcceptor extends BiRequestStreamGrpc.BiRequestStreamImplBase {
    
    @Autowired
    ConnectionManager connectionManager;
    
    private void traceDetailIfNecessary(Payload grpcRequest) {
        String clientIp = grpcRequest.getMetadata().getClientIp();
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        try {
            if (connectionManager.traced(clientIp)) {
                Loggers.REMOTE_DIGEST.info("[{}]Bi stream request receive, meta={},body={}", connectionId,
                        grpcRequest.getMetadata().toByteString().toStringUtf8(),
                        grpcRequest.getBody().toByteString().toStringUtf8());
            }
        } catch (Throwable throwable) {
            Loggers.REMOTE_DIGEST.error("[{}]Bi stream request error,payload={},error={}", connectionId,
                    grpcRequest.toByteString().toStringUtf8(), throwable);
        }
        
    }
    
    @Override
    public StreamObserver<Payload> requestBiStream(StreamObserver<Payload> responseObserver) {
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            
            final String connectionId = CONTEXT_KEY_CONN_ID.get();
            
            final Integer localPort = CONTEXT_KEY_CONN_LOCAL_PORT.get();
            
            final int remotePort = CONTEXT_KEY_CONN_REMOTE_PORT.get();
            
            String remoteIp = CONTEXT_KEY_CONN_REMOTE_IP.get();
            
            String clientIp = "";
            
            @Override
            public void onNext(Payload payload) {
                
                clientIp = payload.getMetadata().getClientIp();
                traceDetailIfNecessary(payload);
                
                Object parseObj;
                try {
                    parseObj = GrpcUtils.parse(payload);
                } catch (Throwable throwable) {
                    Loggers.REMOTE_DIGEST
                            .warn("[{}]Grpc request bi stream,payload parse error={}", connectionId, throwable);
                    return;
                }
                
                if (parseObj == null) {
                    Loggers.REMOTE_DIGEST
                            .warn("[{}]Grpc request bi stream,payload parse null ,body={},meta={}", connectionId,
                                    payload.getBody().getValue().toStringUtf8(), payload.getMetadata());
                    return;
                }
                if (parseObj instanceof ConnectionSetupRequest) {
                    ConnectionSetupRequest setUpRequest = (ConnectionSetupRequest) parseObj;
                    Map<String, String> labels = setUpRequest.getLabels();
                    String appName = "-";
                    if (labels != null && labels.containsKey(Constants.APPNAME)) {
                        appName = labels.get(Constants.APPNAME);
                    }
                    
                    ConnectionMeta metaInfo = new ConnectionMeta(connectionId, payload.getMetadata().getClientIp(),
                            remoteIp, remotePort, localPort, ConnectionType.GRPC.getType(),
                            setUpRequest.getClientVersion(), appName, setUpRequest.getLabels());
                    metaInfo.setTenant(setUpRequest.getTenant());
                    Connection connection = new GrpcConnection(metaInfo, responseObserver, CONTEXT_KEY_CHANNEL.get());
                    connection.setAbilities(setUpRequest.getAbilities());
                    boolean rejectSdkOnStarting = metaInfo.isSdkSource() && !ApplicationUtils.isStarted();
                    
                    if (rejectSdkOnStarting || !connectionManager.register(connectionId, connection)) {
                        //Not register to the connection manager if current server is over limit or server is starting.
                        try {
                            Loggers.REMOTE_DIGEST.warn("[{}]Connection register fail,reason:{}", connectionId,
                                    rejectSdkOnStarting ? " server is not started" : " server is over limited.");
                            connection.request(new ConnectResetRequest(), 3000L);
                            connection.close();
                        } catch (Exception e) {
                            //Do nothing.
                            if (connectionManager.traced(clientIp)) {
                                Loggers.REMOTE_DIGEST
                                        .warn("[{}]Send connect reset request error,error={}", connectionId, e);
                            }
                        }
                    }
                    
                } else if (parseObj instanceof Response) {
                    Response response = (Response) parseObj;
                    if (connectionManager.traced(clientIp)) {
                        Loggers.REMOTE_DIGEST
                                .warn("[{}]Receive response of server request  ,response={}", connectionId, response);
                    }
                    RpcAckCallbackSynchronizer.ackNotify(connectionId, response);
                    connectionManager.refreshActiveTime(connectionId);
                } else {
                    Loggers.REMOTE_DIGEST
                            .warn("[{}]Grpc request bi stream,unknown payload receive ,parseObj={}", connectionId,
                                    parseObj);
                }
                
            }
            
            @Override
            public void onError(Throwable t) {
                if (connectionManager.traced(clientIp)) {
                    Loggers.REMOTE_DIGEST.warn("[{}]Bi stream on error,error={}", connectionId, t);
                }
                
                if (responseObserver instanceof ServerCallStreamObserver) {
                    ServerCallStreamObserver serverCallStreamObserver = ((ServerCallStreamObserver) responseObserver);
                    if (serverCallStreamObserver.isCancelled()) {
                        //client close the stream.
                    } else {
                        try {
                            serverCallStreamObserver.onCompleted();
                        } catch (Throwable throwable) {
                            //ignore
                        }
                    }
                }
                
            }
            
            @Override
            public void onCompleted() {
                if (connectionManager.traced(clientIp)) {
                    Loggers.REMOTE_DIGEST.warn("[{}]Bi stream on completed", connectionId);
                }
                if (responseObserver instanceof ServerCallStreamObserver) {
                    ServerCallStreamObserver serverCallStreamObserver = ((ServerCallStreamObserver) responseObserver);
                    if (serverCallStreamObserver.isCancelled()) {
                        //client close the stream.
                    } else {
                        try {
                            serverCallStreamObserver.onCompleted();
                        } catch (Throwable throwable) {
                            //ignore
                        }
                        
                    }
                }
            }
        };
        
        return streamObserver;
    }
    
}
