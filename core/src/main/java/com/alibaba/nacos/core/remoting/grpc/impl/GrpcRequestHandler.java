package com.alibaba.nacos.core.remoting.grpc.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;

public interface GrpcRequestHandler {

    GrpcResponse handle(GrpcRequest request) throws NacosException;
}
