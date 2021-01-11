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
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CHANNEL;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_CLIENT_IP;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_CLIENT_PORT;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_ID;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_LOCAL_PORT;

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
    
    @Override
    public StreamObserver<Payload> requestBiStream(StreamObserver<Payload> responseObserver) {
        
        StreamObserver<Payload> streamObserver = new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload payload) {
                
                String connectionId = CONTEXT_KEY_CONN_ID.get();
                Integer localPort = CONTEXT_KEY_CONN_LOCAL_PORT.get();
                int clientPort = CONTEXT_KEY_CONN_CLIENT_PORT.get();
                String clientIp = CONTEXT_KEY_CONN_CLIENT_IP.get();
                GrpcUtils.PlainRequest plainRequest = GrpcUtils.parse(payload);
                plainRequest.getMetadata().setClientPort(clientPort);
                plainRequest.getMetadata().setConnectionId(connectionId);
                if (plainRequest.getBody() instanceof ConnectionSetupRequest) {
                    RequestMeta metadata = plainRequest.getMetadata();
                    Map<String, String> labels = metadata.getLabels();
                    String appName = "-";
                    if (labels != null && labels.containsKey(Constants.APPNAME)) {
                        appName = labels.get(Constants.APPNAME);
                    }
                    ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(metadata.getConnectionId(), clientIp,
                            metadata.getClientPort(), localPort, ConnectionType.GRPC.getType(),
                            metadata.getClientVersion(), appName, metadata.getLabels());
                    
                    Connection connection = new GrpcConnection(metaInfo, responseObserver, CONTEXT_KEY_CHANNEL.get());
                    
                    if (!ApplicationUtils.isStarted() || !connectionManager.register(connectionId, connection)) {
                        //Not register to the connection manager if current server is over limit or server is starting.
                        try {
                            connection.request(new ConnectResetRequest(), buildMeta());
                            connection.close();
                        } catch (Exception e) {
                            //Do nothing.
                        }
                        return;
                    }
                    
                } else if (plainRequest.getBody() instanceof Response) {
                    Response response = (Response) plainRequest.getBody();
                    RpcAckCallbackSynchronizer.ackNotify(connectionId, response);
                    connectionManager.refreshActiveTime(plainRequest.getMetadata().getConnectionId());
                    
                }
                
            }
            
            @Override
            public void onError(Throwable t) {
                if (responseObserver instanceof ServerCallStreamObserver) {
                    ServerCallStreamObserver serverCallStreamObserver = ((ServerCallStreamObserver) responseObserver);
                    if (serverCallStreamObserver.isCancelled()) {
                        //client close the stream.
                        return;
                    } else {
                        serverCallStreamObserver.onCompleted();
                    }
                }
                
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
        
        return streamObserver;
    }
    
    private RequestMeta buildMeta() {
        RequestMeta meta = new RequestMeta();
        meta.setClientVersion(VersionUtils.getFullClientVersion());
        meta.setClientIp(NetUtils.localIP());
        return meta;
    }
    
}
