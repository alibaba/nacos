package com.alibaba.nacos.core.remote;

public interface SslContextChangeAware {
    
    void init(BaseRpcServer baseRpcServer);
    
    void onSslContextChange();
    
    void shutdown();
}
