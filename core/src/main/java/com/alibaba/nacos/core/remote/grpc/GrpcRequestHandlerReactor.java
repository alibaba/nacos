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

import com.alibaba.nacos.api.grpc.GrpcMetadata;
import com.alibaba.nacos.api.grpc.GrpcRequest;
import com.alibaba.nacos.api.grpc.GrpcResponse;
import com.alibaba.nacos.api.grpc.RequestGrpc;
import com.alibaba.nacos.api.remote.request.PushAckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.RequestTypeConstants;
import com.alibaba.nacos.api.remote.response.ConnectionUnregisterResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.utils.Loggers;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * grpc request handler reactor, to receive grpc request and transfer to request handler.
 *
 * @author liuzunfei
 * @version $Id: GrpcRequestHandlerReactor.java, v 0.1 2020年07月13日 4:25 PM liuzunfei Exp $
 */
@Service
public class GrpcRequestHandlerReactor extends RequestGrpc.RequestImplBase {
    
    @Autowired
    RequestHandlerRegistry requestHandlerRegistry;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Override
    public void request(GrpcRequest grpcRequest, StreamObserver<GrpcResponse> responseObserver) {
        Loggers.RPC_DIGEST.debug(" gRpc Server receive request :" + grpcRequest);
        String type = grpcRequest.getType();
        if (RequestTypeConstants.SERVER_CHECK.equals(type)) {
            responseObserver.onNext(GrpcUtils.convert(new ServerCheckResponse()));
            responseObserver.onCompleted();
            return;
        } else if (RequestTypeConstants.PUSH_ACK.equals(type)) {
            // server push ack response.
            String connectionId = grpcRequest.getMetadata().getConnectionId();
            PushAckRequest request = JacksonUtils
                    .toObj(grpcRequest.getBody().getValue().toStringUtf8(), PushAckRequest.class);
            RpcAckCallbackSynchronizer
                    .ackNotify(connectionId, request.getRequestId(), request.isSuccess(), request.getException());
            responseObserver.onNext(GrpcUtils.convert(new ServerCheckResponse()));
            responseObserver.onCompleted();
            return;
        }
        
        RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
        if (requestHandler != null) {
            String bodyString = grpcRequest.getBody().getValue().toStringUtf8();
            Request request = requestHandler.parseBodyString(bodyString);
    
            try {
                RequestMeta requestMeta = convertMeta(grpcRequest.getMetadata());
                boolean requestValid = connectionManager.checkValid(requestMeta.getConnectionId());
                if (!requestValid) {
                    responseObserver.onNext(GrpcUtils.convert(new ConnectionUnregisterResponse()));
                    responseObserver.onCompleted();
                    return;
                }
                connectionManager.refreshActiveTime(requestMeta.getConnectionId());
                Response response = requestHandler.handle(request, requestMeta);
                responseObserver.onNext(GrpcUtils.convert(response));
                responseObserver.onCompleted();
            } catch (Exception e) {
                Loggers.RPC_DIGEST.error(" gRpc Server handle  request  exception :" + e.getMessage(), e);
                responseObserver.onNext(GrpcUtils.buildFailResponse("Error"));
                responseObserver.onCompleted();
            }
        } else {
            Loggers.RPC_DIGEST.error(" gRpc Server requestHandler Not found ！ ");
            responseObserver.onNext(GrpcUtils.buildFailResponse("RequestHandler Not Found"));
            responseObserver.onCompleted();
        }
    }
    
    private RequestMeta convertMeta(GrpcMetadata metadata) {
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp(metadata.getClientIp());
        requestMeta.setConnectionId(metadata.getConnectionId());
        requestMeta.setClientVersion(metadata.getVersion());
        return requestMeta;
    }
    
}
