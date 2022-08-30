package com.alibaba.nacos.client.naming.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;

public class TestConnection extends Connection {
    
    public TestConnection(RpcClient.ServerInfo serverInfo) {
        super(serverInfo);
    }
    
    @Override
    public Response request(Request request, long timeoutMills) throws NacosException {
        return null;
    }
    
    @Override
    public RequestFuture requestFuture(Request request) throws NacosException {
        return null;
    }
    
    @Override
    public void asyncRequest(Request request, RequestCallBack requestCallBack) throws NacosException {
    
    }
    
    @Override
    public void close() {
    
    }
}
