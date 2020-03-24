package com.alibaba.nacos.core.remoting.grpc.impl;

import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.common.grpc.GrpcServiceGrpc;
import com.alibaba.nacos.core.remoting.ConnectionManager;
import com.alibaba.nacos.core.utils.Loggers;
import com.google.common.base.Charsets;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RequestServiceGrpcImpl extends GrpcServiceGrpc.GrpcServiceImplBase {

    private Map<String, GrpcRequestHandler> requestHandlers = new HashMap<>(4);

    @Autowired
    private ConnectionManager connectionManager;

    public void registerHandler(String name, GrpcRequestHandler handler) {
        requestHandlers.putIfAbsent(name, handler);
    }

    public void request(GrpcRequest request,
                        StreamObserver<GrpcResponse> responseObserver) {
        try {

            if (Loggers.GRPC.isDebugEnabled()) {
                Loggers.GRPC.debug("[REQUEST] receive request: {}", request);
            }

            if ("sendBeat".equals(request.getAction())) {
                connectionManager.refreshConnection(request.getClientId());
                GrpcResponse response = GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }

            if (!requestHandlers.containsKey(request.getModule())) {
                Loggers.GRPC.error("[REQUEST-SERVICE] no handler for {}", request.getModule());
                throw new NacosException(NacosException.INVALID_PARAM, "no handler for " + request.getModule());
            }

            GrpcResponse response = requestHandlers.get(request.getModule()).handle(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NacosException ne) {
            GrpcResponse errorResponse = GrpcResponse.newBuilder()
                .setCode(ne.getErrCode())
                .setMessage(Any.newBuilder().setValue(ByteString.copyFrom(ne.getErrMsg(), Charsets.UTF_8)))
                .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }
}
