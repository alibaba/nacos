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
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
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
    
    private void traceIfNecessary(Payload grpcRequest, boolean receive) {
        String clientIp = grpcRequest.getMetadata().getClientIp();
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        try {
            if (connectionManager.traced(clientIp)) {
                Loggers.REMOTE_DIGEST.info("[{}]Payload {},meta={},body={}", connectionId, receive ? "receive" : "send",
                        grpcRequest.getMetadata().toByteString().toStringUtf8(),
                        grpcRequest.getBody().toByteString().toStringUtf8());
            }
        } catch (Throwable throwable) {
            Loggers.REMOTE_DIGEST.error("[{}]Monitor request error,payload={},error={}", clientIp,
                    grpcRequest.toByteString().toStringUtf8());
        }
        
    }
    
    @Override
    public void request(Payload grpcRequest, StreamObserver<Payload> responseObserver) {
        
        traceIfNecessary(grpcRequest, true);
        String type = grpcRequest.getMetadata().getType();
        
        //server is on starting.
        if (!ApplicationUtils.isStarted()) {
            Payload payloadResponse = GrpcUtils.convert(
                    buildErrorResponse(NacosException.INVALID_SERVER_STATUS, "Server is starting,please try later."));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            
            responseObserver.onCompleted();
            return;
        }
        
        // server check.
        if (ServerCheckRequest.class.getSimpleName().equals(type)) {
            Payload serverCheckResponseP = GrpcUtils.convert(new ServerCheckResponse(CONTEXT_KEY_CONN_ID.get()));
            traceIfNecessary(serverCheckResponseP, false);
            responseObserver.onNext(serverCheckResponseP);
            responseObserver.onCompleted();
            return;
        }
        
        RequestHandler requestHandler = requestHandlerRegistry.getByRequestType(type);
        //no handler found.
        if (requestHandler == null) {
            Loggers.REMOTE_DIGEST.warn(String.format("[%s] No handler for request type : %s :", "grpc", type));
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(NacosException.NO_HANDLER, "RequestHandler Not Found"));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }
        
        //check connection status.
        String connectionId = CONTEXT_KEY_CONN_ID.get();
        boolean requestValid = connectionManager.checkValid(connectionId);
        if (!requestValid) {
            Loggers.REMOTE_DIGEST
                    .warn("[{}] Invalid connection Id ,connection [{}] is un registered ,", "grpc", connectionId);
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(NacosException.UN_REGISTER, "Connection is unregistered."));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }
        
        Object parseObj = null;
        try {
            parseObj = GrpcUtils.parse(grpcRequest);
        } catch (Exception e) {
            Loggers.REMOTE_DIGEST
                    .warn("[{}] Invalid request receive from connection [{}] ,error={}", "grpc", connectionId, e);
            Payload payloadResponse = GrpcUtils.convert(buildErrorResponse(NacosException.BAD_GATEWAY, e.getMessage()));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }
        
        if (parseObj == null) {
            Loggers.REMOTE_DIGEST.warn("[{}] Invalid request receive  ,parse request is null", connectionId);
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(NacosException.BAD_GATEWAY, "Invalid request"));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
        }
        
        if (!(parseObj instanceof Request)) {
            Loggers.REMOTE_DIGEST
                    .warn("[{}] Invalid request receive  ,parsed payload is not a request,parseObj={}", connectionId,
                            parseObj);
            Payload payloadResponse = GrpcUtils
                    .convert(buildErrorResponse(NacosException.BAD_GATEWAY, "Invalid request"));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
            return;
        }
        
        Request request = (Request) parseObj;
        try {
            Connection connection = connectionManager.getConnection(CONTEXT_KEY_CONN_ID.get());
            RequestMeta requestMeta = new RequestMeta();
            requestMeta.setClientIp(connection.getMetaInfo().getClientIp());
            requestMeta.setConnectionId(CONTEXT_KEY_CONN_ID.get());
            requestMeta.setClientVersion(connection.getMetaInfo().getVersion());
            requestMeta.setLabels(connection.getMetaInfo().getLabels());
            connectionManager.refreshActiveTime(requestMeta.getConnectionId());
            Response response = requestHandler.handleRequest(request, requestMeta);
            Payload payloadResponse = GrpcUtils.convert(response);
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
        } catch (Throwable e) {
            Loggers.REMOTE_DIGEST
                    .error("[{}] Fail to handle request from connection [{}] ,error message :{}", "grpc", connectionId,
                            e);
            Payload payloadResponse = GrpcUtils.convert(buildErrorResponse(
                    (e instanceof NacosException) ? ((NacosException) e).getErrCode() : ResponseCode.FAIL.getCode(),
                    e.getMessage()));
            traceIfNecessary(payloadResponse, false);
            responseObserver.onNext(payloadResponse);
            responseObserver.onCompleted();
        }
        
    }
    
    private Response buildErrorResponse(int errorCode, String msg) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorInfo(errorCode, msg);
        return response;
    }
}