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

import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.ConnectionUnregisterResponse;
import com.alibaba.nacos.api.remote.response.PlainBodyResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.api.remote.response.UnKnowResponse;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * rpc request accetor of grpc.
 *
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
    
        String type = grpcRequest.getMetadata().getType();
    
        if (!ApplicationUtils.isStarted()) {
            responseObserver.onNext(GrpcUtils.convert(new PlainBodyResponse("server is starting.")));
            responseObserver.onCompleted();
            return;
        }
        
        if (ServerCheckRequest.class.getName().equals(type)) {
    
            Loggers.REMOTE_DIGEST.debug(String.format("[%s]  server check request receive ,clientIp : %s ", "grpc",
                    grpcRequest.getMetadata().getClientIp()));
            responseObserver.onNext(GrpcUtils.convert(new ServerCheckResponse()));
            responseObserver.onCompleted();
            return;
        }
    
        GrpcUtils.PlainRequest parseObj = GrpcUtils.parse(grpcRequest);
        
        if (parseObj != null) {
            Request request = (Request) parseObj.getBody();
            RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
            if (requestHandler != null) {
                try {
                    boolean requestValid = connectionManager.checkValid(parseObj.getMetadata().getConnectionId());
                    if (!requestValid) {
                        responseObserver.onNext(GrpcUtils.convert(new ConnectionUnregisterResponse()));
                        responseObserver.onCompleted();
                        return;
                    }
                    connectionManager.refreshActiveTime(parseObj.getMetadata().getConnectionId());
                    Response response = requestHandler.handle(request, parseObj.getMetadata());
                    responseObserver.onNext(GrpcUtils.convert(response));
                    responseObserver.onCompleted();
                    return;
                } catch (Exception e) {
    
                    Loggers.REMOTE_DIGEST.error(String
                            .format("[%s] fail to handle request ,error message :%s", "grpc", e.getMessage(), e));
                    responseObserver.onNext(GrpcUtils.convert(buildFailResponse("Error")));
                    responseObserver.onCompleted();
                    return;
                }
            } else {
                Loggers.REMOTE_DIGEST.debug(String.format("[%s] no handler for request type : %s :", "grpc", type));
                responseObserver.onNext(GrpcUtils.convert(buildFailResponse("RequestHandler Not Found")));
                responseObserver.onCompleted();
                return;
            }
            
        }
    }
    
    private Response buildFailResponse(String msg) {
        UnKnowResponse response = new UnKnowResponse();
        response.setErrorInfo(ResponseCode.FAIL.getCode(), msg);
        return response;
    }
    
}
