/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.naming.net;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientFactory;
import com.alibaba.nacos.client.remote.ServerListFactory;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Naming grpc client proxy.
 *
 * @author xiweng.yy
 */
public class NamingGrpcClientProxy {
    
    private final String namespaceId;
    
    private RpcClient rpcClient;
    
    public NamingGrpcClientProxy(String namespaceId) {
        this.namespaceId = namespaceId;
        rpcClient = RpcClientFactory.getClient("naming");
    }
    
    public void start() throws NacosException {
        rpcClient.init(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "localhost:8848";
            }
            
            @Override
            public String getCurrentServer() {
                return "localhost:8848";
            }
        });
        rpcClient.start();
    }
    
    /**
     * register a instance to service with specified instance properties.
     *
     * @param serviceName name of service
     * @param groupName   group of service
     * @param instance    instance to register
     * @throws NacosException nacos exception
     */
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[REGISTER-SERVICE] {} registering service {} with instance {}", namespaceId, serviceName,
                instance);
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.REGISTER_INSTANCE, instance);
        Response response = rpcClient.request(request);
        if (200 != response.getResultCode()) {
            throw new NacosException(response.getErrorCode(), response.getMessage());
        }
    }
    
    /**
     * deregister instance from a service.
     *
     * @param serviceName name of service
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    public void deregisterService(String serviceName, Instance instance) throws NacosException {
        NAMING_LOGGER
                .info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}", namespaceId, serviceName,
                        instance);
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName,
                NamingRemoteConstants.DE_REGISTER_INSTANCE, instance);
        Response response = rpcClient.request(request);
        if (200 != response.getResultCode()) {
            throw new NacosException(response.getErrorCode(), response.getMessage());
        }
    }
}
