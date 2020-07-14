/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.remote.grpc.GrpcClient;

import sun.management.resources.agent;

/**
 * @author liuzunfei
 * @version $Id: RpcClientFactory.java, v 0.1 2020年07月14日 3:41 PM liuzunfei Exp $
 */
public class RpcClientFactory {

    private RpcClient sharedClient;
    Map<String ,RpcClient> clientMap=new HashMap<String ,RpcClient>();

    public RpcClient getClient(String module){
        String useIndependentClient = System.getProperty("rpc.client.independent");
        if ("Y".equalsIgnoreCase(useIndependentClient)){
            if(clientMap.get(module)==null){
                RpcClient moduleClient=new GrpcClient();
                return clientMap.putIfAbsent(module,moduleClient);
            }else{
                return clientMap.get(module);
            }
        }else{
            if (sharedClient!=null){
                return sharedClient;
            }else{
                sharedClient=new GrpcClient();
                return sharedClient;
            }

        }
    }
}
