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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_CLIENT_IP;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_CLIENT_PORT;
import static com.alibaba.nacos.core.remote.grpc.BaseGrpcServer.CONTEXT_KEY_CONN_ID;

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
        
        // server check.
        if (ServerCheckRequest.class.getName().equals(type)) {
            responseObserver.onNext(GrpcUtils.convert(new ServerCheckResponse(CONTEXT_KEY_CONN_ID.get())));
            responseObserver.onCompleted();
            return;
        }
        
        RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
        //no handler found.
        if (requestHandler == null) {
            Loggers.REMOTE_DIGEST.warn(String.format("[%s] No handler for request type : %s :", "grpc", type));
            responseObserver.onNext(GrpcUtils
                    .convert(buildErrorResponse(NacosException.NO_HANDLER, "RequestHandler Not Found")));
            responseObserver.onCompleted();
            return;
        }
        
        //server is on starting.
        if (!ApplicationUtils.isStarted()) {
            responseObserver.onNext(GrpcUtils.convert(
                    buildErrorResponse(NacosException.INVALID_SERVER_STATUS, "Server is starting,please try later.")));
            responseObserver.onCompleted();
            return;
        }
        
        //check connection status.
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        boolean requestValid = connectionManager.checkValid(connectionId);
        if (!requestValid) {
            Loggers.REMOTE_DIGEST
                    .warn("[{}] Invalid connection Id ,connection [{}] is un registered ,", "grpc", connectionId);
            responseObserver.onNext(GrpcUtils
                    .convert(buildErrorResponse(NacosException.UN_REGISTER, "Connection is unregistered.")));
            responseObserver.onCompleted();
            return;
        }
        
        Object parseObj = null;
        try {
            parseObj = GrpcUtils.parse(grpcRequest);
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST
                    .warn("[{}] Invalid request receive from connection [{}] ,error={}", "grpc", connectionId, e);
            responseObserver.onNext(GrpcUtils.convert(buildErrorResponse(NacosException.BAD_GATEWAY, e.getMessage())));
            responseObserver.onCompleted();
            return;
        }
        
        if (parseObj == null) {
            Loggers.REMOTE_DIGEST.warn("[{}] Invalid request receive  ,parse request is null", connectionId);
            responseObserver
                    .onNext(GrpcUtils.convert(buildErrorResponse(NacosException.BAD_GATEWAY, "Invalid request")));
            responseObserver.onCompleted();
            return;
        }
        
        if (!(parseObj instanceof Request)) {
            Loggers.REMOTE_DIGEST
                    .warn("[{}] Invalid request receive  ,parsed payload is not a request,parseObj={}", connectionId,
                            parseObj);
            responseObserver
                    .onNext(GrpcUtils.convert(buildErrorResponse(NacosException.BAD_GATEWAY, "Invalid request")));
            responseObserver.onCompleted();
            return;
        }
        
        Request request = (Request) parseObj;
        try {
            Connection connection = connectionManager.getConnection(CONTEXT_KEY_CONN_ID.get());
            RequestMeta requestMeta = new RequestMeta();
            requestMeta.setClientIp(CONTEXT_KEY_CONN_CLIENT_IP.get());
            requestMeta.setConnectionId(CONTEXT_KEY_CONN_ID.get());
            requestMeta.setClientPort(CONTEXT_KEY_CONN_CLIENT_PORT.get());
            requestMeta.setClientVersion(connection.getMetaInfo().getVersion());
            requestMeta.setLabels(connection.getMetaInfo().getLabels());
            connectionManager.refreshActiveTime(requestMeta.getConnectionId());
            Response response = requestHandler.handleRequest(request, requestMeta);
            responseObserver.onNext(GrpcUtils.convert(response));
            responseObserver.onCompleted();
        } catch (Throwable e) {
            Loggers.REMOTE_DIGEST
                    .error("[{}] Fail to handle request from connection [{}] ,error message :{}", "grpc", connectionId,
                            e);
            responseObserver.onNext(GrpcUtils.convert(buildErrorResponse(ResponseCode.FAIL.getCode(), e.getMessage())));
            responseObserver.onCompleted();
            return;
        }
        
    }
    
    private Response buildErrorResponse(int errorCode, String msg) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorInfo(errorCode, msg);
        return response;
    }
}
