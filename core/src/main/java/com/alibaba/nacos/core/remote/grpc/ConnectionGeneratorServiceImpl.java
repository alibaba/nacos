package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.StreamObserver;

public class ConnectionGeneratorServiceImpl implements ConnectionGeneratorService {
    
    @Override
    public Connection getConnection(ConnectionMeta metaInfo, StreamObserver streamObserver, Channel channel) {
        return new GrpcConnection(metaInfo, streamObserver, channel);
    }
    
    @Override
    public String getType() {
        return "nacos";
    }
}
