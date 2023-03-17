package com.alibaba.nacos.core.remote;

public interface RpcServerSslContextRefresher {
    
    SslContextChangeAware refresh(BaseRpcServer baseRpcServer);
    
    String getName();
}
