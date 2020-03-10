package com.alibaba.nacos.core.remoting.grpc.impl;


import com.alibaba.nacos.common.grpc.GrpcMetadata;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.common.grpc.GrpcStreamServiceGrpc;
import com.alibaba.nacos.core.remoting.*;
import com.alibaba.nacos.core.utils.Loggers;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class StreamServiceGrpcImpl extends GrpcStreamServiceGrpc.GrpcStreamServiceImplBase implements PushService {

    @Autowired
    private ConnectionManager connectionManager;

    private Map<String, StreamObserver<GrpcResponse>> grpcClients = new ConcurrentHashMap<>();

    public void streamRequest(GrpcRequest request, StreamObserver<GrpcResponse> responseObserver) {

        Loggers.GRPC.info("new connection {}", request.getClientId());
        grpcClients.put(request.getClientId(), responseObserver);
        connectionManager.putIfAbsent(new Connection(request.getClientId(), ConnectionType.GRPC));
        connectionManager.listen(request.getClientId(), new GrpcConnectionEventListener());
    }

    @Override
    public void push(String clientId, String dataId, byte[] data) {

        Loggers.GRPC.info("[PUSH] push now client:{}, dataId:{}", clientId, dataId);

        if (!grpcClients.containsKey(clientId)) {
            Loggers.GRPC.warn("[PUSH] grpc client not found: {}", clientId);
            return;
        }

        GrpcMetadata metadata = GrpcMetadata.newBuilder().putLabels("dataId", dataId).putLabels("clientId", clientId).build();
        GrpcResponse response = GrpcResponse.newBuilder().
            setMetadata(metadata).
            setMessage(Any.newBuilder().setValue(ByteString.copyFrom(data))).
            build();

        grpcClients.get(clientId).onNext(response);
    }

    private class GrpcConnectionEventListener implements ConnectionEventListener {

        @Override
        public void onEvent(ConnectionEvent event) {

        }
    }
}
