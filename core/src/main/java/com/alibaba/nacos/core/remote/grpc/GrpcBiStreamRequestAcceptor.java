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

import com.alibaba.nacos.api.grpc.GrpcUtils;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.core.remote.grpc.GrpcServer.CONTEXT_KEY_CHANNEL;
import static com.alibaba.nacos.core.remote.grpc.GrpcServer.CONTEXT_KEY_CONN_ID;

/**
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
                
                Object parseObj = GrpcUtils.parse(payload);
                if (parseObj instanceof ConnectionSetupRequest) {
                    ConnectionSetupRequest setupRequest = (ConnectionSetupRequest) parseObj;
                    Context current = Context.current();
                    Metadata metadata = payload.getMetadata();
                    String clientIp = metadata.getClientIp();
                    String version = metadata.getVersion();
                    ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(connectionId, clientIp,
                            ConnectionType.GRPC.getType(), version, metadata.getLabelsMap());
                    
                    Connection connection = new GrpcConnection(metaInfo, responseObserver, CONTEXT_KEY_CHANNEL.get());
                    if (connectionManager.isOverLimit()) {
                        //Not register to the connection manager if current server is over limit.
                        try {
                            System.out.println("over limit ...");
                            connection.sendRequestNoAck(new ConnectResetRequest());
                            connection.closeGrapcefully();
                        } catch (Exception e) {
                            //Do nothing.
                        }
                    } else {
                        connectionManager.register(connectionId, connection);
                    }
                } else if (parseObj instanceof Response) {
                    Response response = (Response) parseObj;
                    RpcAckCallbackSynchronizer.ackNotify(connectionId, response);
                }
                
            }
            
            @Override
            public void onError(Throwable t) {
            
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
        
        return streamObserver;
    }
}
