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
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.RequestTypeConstants;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.ConnectionUnregisterResponse;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.api.remote.response.UnKnowResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.remote.RpcAckCallbackSynchronizer;
import com.alibaba.nacos.core.utils.Loggers;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author liuzunfei
 * @version $Id: GrpcCommonRequestAcceptor.java, v 0.1 2020年09月01日 10:52 AM liuzunfei Exp $
 */
@Service
public class GrpcRequestAcceptor extends RequestGrpc.RequestImplBase {
    
    @Autowired
    RequestHandlerRegistry requestHandlerRegistry;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    
    @Override
    public void request(Payload grpcRequest, StreamObserver<Payload> responseObserver) {
        
        Loggers.RPC_DIGEST.debug(" gRpc Server receive request :" + grpcRequest);
        String type = grpcRequest.getType();
        
        if (ServerCheckRequest.class.getName().equals(type)) {
            responseObserver.onNext(convertResponseToPayload(new ServerCheckResponse()));
            responseObserver.onCompleted();
            return;
        }
        
        Object parseObj = GrpcUtils.parse(grpcRequest);
        
        if (parseObj != null) {
            if (parseObj instanceof Response) {
                Response response = (Response) parseObj;
                String connectionId = grpcRequest.getMetadata().getConnectionId();
                RpcAckCallbackSynchronizer.ackNotify(connectionId, response);
                responseObserver.onNext(convertResponseToPayload(new PlainBodyResponse()));
                responseObserver.onCompleted();
                return;
            } else {
                Request request = (Request) parseObj;
                RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
                if (requestHandler != null) {
                    try {
                        RequestMeta requestMeta = convertMeta(grpcRequest.getMetadata());
                        boolean requestValid = connectionManager.checkValid(requestMeta.getConnectionId());
                        if (!requestValid) {
                            responseObserver.onNext(convertResponseToPayload(new ConnectionUnregisterResponse()));
                            responseObserver.onCompleted();
                            return;
                        }
                        connectionManager.refreshActiveTime(requestMeta.getConnectionId());
                        Response response = requestHandler.handle(request, requestMeta);
                        responseObserver.onNext(convertResponseToPayload(response));
                        responseObserver.onCompleted();
                    } catch (Exception e) {
                        Loggers.RPC_DIGEST.error(" gRpc Server handle  request  exception :" + e.getMessage(), e);
                        responseObserver.onNext(convertResponseToPayload(buildFailResponse("Error")));
                        responseObserver.onCompleted();
                    }
                } else {
                    Loggers.RPC_DIGEST.error(" gRpc Server requestHandler Not found ！ ");
                    responseObserver.onNext(convertResponseToPayload(buildFailResponse("RequestHandler Not Found")));
                    responseObserver.onCompleted();
                }
            }
        }
    }
    
    private RequestMeta convertMeta(Metadata metadata) {
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp(metadata.getClientIp());
        requestMeta.setConnectionId(metadata.getConnectionId());
        requestMeta.setClientVersion(metadata.getVersion());
        return requestMeta;
    }
    
    private Payload convertResponseToPayload(Response response) {
        Payload payload = Payload.newBuilder()
                .setBody(Any.newBuilder().setValue(ByteString.copyFrom(JacksonUtils.toJsonBytes(response))))
                .setType(response.getClass().getName()).build();
        return payload;
    }
    
    private Response buildFailResponse(String msg) {
        UnKnowResponse response = new UnKnowResponse();
        response.setErrorCode(ResponseCode.FAIL.getCode());
        response.setMessage(msg);
        return response;
    }
    
}
