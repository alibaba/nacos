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
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientFactory;
import com.alibaba.nacos.client.remote.ServerListFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.Set;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Naming grpc client proxy.
 *
 * @author xiweng.yy
 */
public class NamingGrpcClientProxy implements NamingClientProxy {
    
    private final String namespaceId;
    
    private final RpcClient rpcClient;
    
    private final NamingGrpcConnectionEventListener namingGrpcConnectionEventListener;
    
    public NamingGrpcClientProxy(String namespaceId, ServerListFactory serverListFactory,
            ServiceInfoHolder serviceInfoHolder) throws NacosException {
        this.namespaceId = namespaceId;
        this.rpcClient = RpcClientFactory.getClient("naming");
        this.namingGrpcConnectionEventListener = new NamingGrpcConnectionEventListener(this);
        start(serverListFactory, serviceInfoHolder);
    }
    
    private void start(ServerListFactory serverListFactory, ServiceInfoHolder serviceInfoHolder) throws NacosException {
        rpcClient.init(serverListFactory);
        rpcClient.start();
        rpcClient.registerServerPushResponseHandler(new NamingPushResponseHandler(serviceInfoHolder));
        rpcClient.registerConnectionListener(namingGrpcConnectionEventListener);
    }
    
    @Override
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[REGISTER-SERVICE] {} registering service {} with instance {}", namespaceId, serviceName,
                instance);
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.REGISTER_INSTANCE, instance);
        requestToServer(request, Response.class);
        namingGrpcConnectionEventListener.cacheInstanceForRedo(serviceName, groupName, instance);
    }
    
    @Override
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER
                .info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}", namespaceId, serviceName,
                        instance);
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.DE_REGISTER_INSTANCE, instance);
        requestToServer(request, Response.class);
        namingGrpcConnectionEventListener.removeInstanceForRedo(serviceName, groupName, instance);
    }
    
    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
    
    }
    
    @Override
    public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort,
            boolean healthyOnly) throws NacosException {
        ServiceQueryRequest request = new ServiceQueryRequest(namespaceId,
                NamingUtils.getGroupedName(serviceName, groupName));
        request.setCluster(clusters);
        request.setHealthyOnly(healthyOnly);
        request.setUdpPort(udpPort);
        QueryServiceResponse response = requestToServer(request, QueryServiceResponse.class);
        return response.getServiceInfo();
    }
    
    @Override
    public Service queryService(String serviceName, String groupName) throws NacosException {
        return null;
    }
    
    @Override
    public void createService(Service service, AbstractSelector selector) throws NacosException {
    
    }
    
    @Override
    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        return false;
    }
    
    @Override
    public void updateService(Service service, AbstractSelector selector) throws NacosException {
    
    }
    
    @Override
    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException {
        ServiceListRequest request = new ServiceListRequest(namespaceId, groupName, pageNo, pageSize);
        if (selector != null) {
            if (SelectorType.valueOf(selector.getType()) == SelectorType.label) {
                request.setSelector(JacksonUtils.toJson(selector));
            }
        }
        ServiceListResponse response = requestToServer(request, ServiceListResponse.class);
        ListView<String> result = new ListView<String>();
        result.setCount(response.getCount());
        result.setData(response.getServiceNames());
        return result;
    }
    
    @Override
    public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
        String serviceNameWithGroup = NamingUtils.getGroupedName(serviceName, groupName);
        SubscribeServiceRequest request = new SubscribeServiceRequest(namespaceId, serviceNameWithGroup, clusters,
                true);
        SubscribeServiceResponse response = requestToServer(request, SubscribeServiceResponse.class);
        namingGrpcConnectionEventListener.cacheSubscriberForRedo(serviceNameWithGroup, clusters);
        return response.getServiceInfo();
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        String serviceNameWithGroup = NamingUtils.getGroupedName(serviceName, groupName);
        SubscribeServiceRequest request = new SubscribeServiceRequest(namespaceId, serviceNameWithGroup, clusters,
                false);
        requestToServer(request, SubscribeServiceResponse.class);
        namingGrpcConnectionEventListener.removeSubscriberForRedo(serviceNameWithGroup, clusters);
    }
    
    @Override
    public void updateBeatInfo(Set<Instance> modifiedInstances) {
    }
    
    @Override
    public boolean serverHealthy() {
        return rpcClient.isRunning();
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
    
    @Override
    public void shutdown() throws NacosException {
        rpcClient.shutdown();
    }
    
    public boolean isEnable() {
        return rpcClient.isRunning();
    }
}
