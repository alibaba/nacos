package com.alibaba.nacos.core.remote.tls;

import com.alibaba.nacos.core.remote.BaseRpcServer;

public  class ClusterRpcServerSslContextRefresherTest implements RpcServerSslContextRefresher {

    @Override
    public SslContextChangeAware refresh(BaseRpcServer baseRpcServer) {
        return new SslContextChangeAware() {
            @Override
            public void init(BaseRpcServer baseRpcServer) {

            }

            @Override
            public void onSslContextChange() {

            }

            @Override
            public void shutdown() {

            }
        };
    }

    @Override
    public String getName() {
        return "cluster-refresher-test";
    }
}
