/**
 * Alipay.com Inc. Copyright (c) 2004-2020 All Rights Reserved.
 */

package com.alibaba.nacos.client.config.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigChangeListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientFactory;
import com.alibaba.nacos.client.remote.ServerListFactory;

/**
 * config grpc client proxy.
 * @author liuzunfei
 * @version $Id: ConfigGrpcClientProxy.java, v 0.1 2020年07月14日 3:37 PM liuzunfei Exp $
 */

public class ConfigGrpcClientProxy {
    
    private RpcClient rpcClient;
    
    public ConfigGrpcClientProxy() {
        rpcClient = RpcClientFactory.getClient("config");
    }
    
    public void start() throws NacosException {
        rpcClient.start();
    }
    
    public void switchServer() {
        rpcClient.switchServer();
    }
    
    public Response request(Request request) {
        return rpcClient.request(request);
    }
    
    public RpcClient getRpcClient() {
        return this.rpcClient;
    }
    
    public void initAndStart(ServerListFactory serverListFactory) throws NacosException {
        rpcClient.init(serverListFactory);
        rpcClient.start();
    }
    
    /**
     * send congif change listen request.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenat  tenat
     * @throws NacosException throws when listen fail.
     */
    public void listenConfigChange(String dataId, String group, String tenat) throws NacosException {
        ConfigChangeListenRequest configChangeListenRequest = ConfigChangeListenRequest
                .buildListenRequest(dataId, group, tenat);
        Response response = rpcClient.request(configChangeListenRequest);
        if (!response.isSuccess()) {
            throw new NacosException(NacosException.SERVER_ERROR, "Fail to Listen Config Change");
        }
    }
    
    /**
     * sned cancel listen congif change request .
     * @param dataId dataId
     * @param group group
     * @param tenat tenat
     */
    public void unListenConfigChange(String dataId, String group, String tenat) throws NacosException {
        ConfigChangeListenRequest configChangeListenRequest = ConfigChangeListenRequest
                .buildUnListenRequest(dataId, group, tenat);
        Response response = rpcClient.request(configChangeListenRequest);
        if (!response.isSuccess()) {
            throw new NacosException(NacosException.SERVER_ERROR, "Fail to UnListen Config Change");
        }
    }
    
    /**
     * query config content by grpc channel .
     *
     * @param dataId dataId
     * @param group  group
     * @param tenat  tenat
     * @return ConfigQueryResponse.
     * @throws NacosException throw where query fail .
     */
    public ConfigQueryResponse queryConfig(String dataId, String group, String tenat) throws NacosException {
        ConfigQueryRequest request = ConfigQueryRequest.build(dataId, group, tenat);
        Response response = rpcClient.request(request);
        return (ConfigQueryResponse) response;
        
    }
}
