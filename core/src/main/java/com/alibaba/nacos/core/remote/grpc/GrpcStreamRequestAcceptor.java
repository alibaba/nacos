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

import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.grpc.auto.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.core.remote.grpc.GrpcServer.CONTEXT_KEY_CHANNEL;

/**
 * grpc stream handler,to accepted client stream request to push message to client.
 *
 * @author liuzunfei
 * @version $Id: GrpcStreamRequestHanderImpl.java, v 0.1 2020年07月13日 7:30 PM liuzunfei Exp $
 */
public class GrpcStreamRequestAcceptor extends RequestStreamGrpc.RequestStreamImplBase {
    
    @Autowired
    ConnectionManager connectionManager;
    
    @Override
    public void requestStream(Payload request, StreamObserver<Payload> responseObserver) {
    
        Context current = Context.current();
        Metadata metadata = request.getMetadata();
        String clientIp = metadata.getClientIp();
        String connectionId = metadata.getConnectionId();
        String version = metadata.getClientVersion();
        ConnectionMetaInfo metaInfo = new ConnectionMetaInfo(connectionId, clientIp, ConnectionType.GRPC.getType(),
                version, metadata.getLabelsMap());
    
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
    }
    
}
