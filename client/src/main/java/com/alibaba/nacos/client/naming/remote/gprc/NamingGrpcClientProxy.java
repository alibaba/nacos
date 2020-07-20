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

package com.alibaba.nacos.client.naming.remote.gprc;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.core.HostReactor;
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
    
    private HostReactor hostReactor;
    
    private RpcClient rpcClient;
    
    public NamingGrpcClientProxy(String namespaceId, HostReactor hostReactor) {
        this.namespaceId = namespaceId;
        this.hostReactor = hostReactor;
        this.rpcClient = RpcClientFactory.getClient("naming");
    }
    
    /**
     * Start Grpc client proxy.
     *
     * @param serverListFactory server list factory
     * @throws NacosException nacos exception
     */
    public void start(ServerListFactory serverListFactory) throws NacosException {
        rpcClient.init(serverListFactory);
        rpcClient.start();
        rpcClient.registerServerPushResponseHandler(new NamingPushResponseHandler(hostReactor));
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
        requestToServer(request, Response.class);
    }
    
    /**
     * deregister instance from a service.
     *
     * @param serviceName name of service
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER
                .info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}", namespaceId, serviceName,
                        instance);
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.DE_REGISTER_INSTANCE, instance);
        requestToServer(request, Response.class);
    }
    
    /**
     * Query instance list.
     *
     * @param serviceName service name
     * @param clusters    clusters
     * @param udpPort     udp port
     * @param healthyOnly healthy only
     * @return service info
     * @throws NacosException nacos exception
     */
    public ServiceInfo queryInstancesOfService(String serviceName, String clusters, int udpPort, boolean healthyOnly)
            throws NacosException {
        ServiceQueryRequest request = new ServiceQueryRequest(namespaceId, serviceName);
        request.setCluster(clusters);
        request.setHealthyOnly(healthyOnly);
        request.setUdpPort(udpPort);
        QueryServiceResponse response = requestToServer(request, QueryServiceResponse.class);
        return response.getServiceInfo();
    }
    
    /**
     * Subscribe service.
     *
     * @param serviceName full service name with group
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @return current service info of subscribe service
     * @throws NacosException nacos exception
     */
    public ServiceInfo subscribe(String serviceName, String clusters) throws NacosException {
        ServiceInfo serviceInfo = new ServiceInfo(serviceName, clusters);
        if (hostReactor.getServiceInfoMap().containsKey(serviceInfo.getKey())) {
            return hostReactor.getServiceInfoMap().get(serviceInfo.getKey());
        }
        hostReactor.updatingService(serviceName);
        SubscribeServiceRequest request = new SubscribeServiceRequest(namespaceId, serviceName, clusters, true);
        SubscribeServiceResponse response = requestToServer(request, SubscribeServiceResponse.class);
        ServiceInfo result = response.getServiceInfo();
        hostReactor.getServiceInfoMap().put(result.getKey(), result);
        hostReactor.finishUpdating(serviceName);
        return result;
    }
    
    /**
     * Unsubscribe service.
     *
     * @param serviceName full service name with group
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @throws NacosException nacos exception
     */
    public void unsubscribe(String serviceName, String clusters) throws NacosException {
        SubscribeServiceRequest request = new SubscribeServiceRequest(namespaceId, serviceName, clusters, false);
        requestToServer(request, SubscribeServiceResponse.class);
    }
    
    private <T extends Response> T requestToServer(Request request, Class<T> responseClass) throws NacosException {
        try {
            Response response = rpcClient.request(request);
            if (200 != response.getResultCode()) {
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            if (responseClass.isAssignableFrom(response.getClass())) {
                return (T) response;
            }
            NAMING_LOGGER.error("Server return unexpected response '{}', expected response should be '{}'",
                    response.getClass().getName(), responseClass.getName());
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, "Request nacos server failed: ", e);
        }
        throw new NacosException(NacosException.SERVER_ERROR, "Server return invalid response");
    }
}
