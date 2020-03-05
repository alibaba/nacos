package com.alibaba.nacos.core.remoting.grpc.impl;


import com.alibaba.nacos.core.remoting.ConnectionManager;
import com.alibaba.nacos.core.remoting.grpc.*;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class ConnectionServiceGrpcImpl extends ConnectionServiceGrpc.ConnectionServiceImplBase {

    @Autowired
    private ConnectionManager connectionManager;

    public void streamConnection(GrpcConnection request, StreamObserver<GrpcResource> responseObserver) {

    }


    public void sendBeat(GrpcBeat request, StreamObserver<GrpcResponse> responseObserver) {
        connectionManager.refreshConnection(request.getConnectionId());
        GrpcResponse response = GrpcResponse.newBuilder().setCode(200).setMessage("ok").build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
