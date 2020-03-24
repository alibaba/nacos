package com.alibaba.nacos.client.connection.grpc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.common.grpc.GrpcServiceGrpc;
import com.alibaba.nacos.common.grpc.GrpcStreamServiceGrpc;
import com.alibaba.nacos.common.utils.UuidUtils;
import io.grpc.ManagedChannel;

public class BaseGrpcClient {

    protected ManagedChannel channel;

    protected String connectionId;

    protected GrpcStreamServiceGrpc.GrpcStreamServiceStub grpcStreamServiceStub;

    protected GrpcServiceGrpc.GrpcServiceBlockingStub grpcServiceBlockingStub;

    protected BaseGrpcClient(String connectionId) {
        this.connectionId = connectionId;
    }

    public JSONObject sendBeat() {
        GrpcResponse response = grpcServiceBlockingStub.request(buildBeat(connectionId));
        return JSON.parseObject(response.getMessage().getValue().toStringUtf8());
    }

    protected String buildRequestId(String connectionId) {
        // TODO use better Id generator:
        return connectionId + ":" + UuidUtils.generateUuid();
    }

    private GrpcRequest buildBeat(String connectionId) {
        return GrpcRequest.newBuilder()
            .setRequestId(buildRequestId(connectionId))
            .setClientId(connectionId)
            .setAction("sendBeat")
            .setSource(NetUtils.localIP())
            .build();
    }
}
