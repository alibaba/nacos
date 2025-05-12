package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import io.grpc.netty.shaded.io.netty.channel.Channel;
import io.grpc.stub.StreamObserver;

public interface ConnectionGeneratorService {
    Connection getConnection(ConnectionMeta metaInfo, StreamObserver streamObserver, Channel channel);
    
    String getType();
}
