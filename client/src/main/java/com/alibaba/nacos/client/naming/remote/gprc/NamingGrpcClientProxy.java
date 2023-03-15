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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest;
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.request.ServiceQueryRequest;
import com.alibaba.nacos.api.naming.remote.request.SubscribeServiceRequest;
import com.alibaba.nacos.api.naming.remote.response.BatchInstanceResponse;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.naming.remote.response.SubscribeServiceResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.remote.AbstractNamingClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.BatchInstanceRedoData;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Naming grpc client proxy.
 *
 * @author xiweng.yy
 */
public class NamingGrpcClientProxy extends AbstractNamingClientProxy {
    
    private final String namespaceId;
    
    private final String uuid;
    
    private final Long requestTimeout;
    
    private final RpcClient rpcClient;
    
    private final NamingGrpcRedoService redoService;
    
    public NamingGrpcClientProxy(String namespaceId, SecurityProxy securityProxy, ServerListFactory serverListFactory,
            NacosClientProperties properties, ServiceInfoHolder serviceInfoHolder) throws NacosException {
        super(securityProxy);
        this.namespaceId = namespaceId;
        this.uuid = UUID.randomUUID().toString();
        this.requestTimeout = Long.parseLong(properties.getProperty(CommonParams.NAMING_REQUEST_TIMEOUT, "-1"));
        Map<String, String> labels = new HashMap<>();
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_NAMING);
        this.rpcClient = RpcClientFactory.createClient(uuid, ConnectionType.GRPC, labels, RpcClientTlsConfig.properties(properties.asProperties()));
        this.redoService = new NamingGrpcRedoService(this);
        start(serverListFactory, serviceInfoHolder);
    }
    
    private void start(ServerListFactory serverListFactory, ServiceInfoHolder serviceInfoHolder) throws NacosException {
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.registerConnectionListener(redoService);
        rpcClient.registerServerRequestHandler(new NamingPushRequestHandler(serviceInfoHolder));
        rpcClient.start();
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public void onEvent(ServerListChangedEvent event) {
        rpcClient.onServerListChange();
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerListChangedEvent.class;
    }
    
    @Override
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[REGISTER-SERVICE] {} registering service {} with instance {}", namespaceId, serviceName,
                instance);
        redoService.cacheInstanceForRedo(serviceName, groupName, instance);
        doRegisterService(serviceName, groupName, instance);
    }
    
    @Override
    public void batchRegisterService(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        redoService.cacheInstanceForRedo(serviceName, groupName, instances);
        doBatchRegisterService(serviceName, groupName, instances);
    }
    
    @Override
    public void batchDeregisterService(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        List<Instance> retainInstance = getRetainInstance(serviceName, groupName, instances);
        batchRegisterService(serviceName, groupName, retainInstance);
    }
    
    /**
     * Get instance list that need to be Retained.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   instance list
     * @return instance list that need to be deregistered.
     */
    private List<Instance> getRetainInstance(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        if (CollectionUtils.isEmpty(instances)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    String.format("[Batch deRegistration] need deRegister instance is empty, instances: %s,",
                            instances));
        }
        String combinedServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        InstanceRedoData instanceRedoData = redoService.getRegisteredInstancesByKey(combinedServiceName);
        if (!(instanceRedoData instanceof BatchInstanceRedoData)) {
            throw new NacosException(NacosException.INVALID_PARAM, String.format(
                    "[Batch deRegistration] batch deRegister is not BatchInstanceRedoData type , instances: %s,",
                    instances));
        }
        
        BatchInstanceRedoData batchInstanceRedoData = (BatchInstanceRedoData) instanceRedoData;
        List<Instance> allInstance = batchInstanceRedoData.getInstances();
        if (CollectionUtils.isEmpty(allInstance)) {
            throw new NacosException(NacosException.INVALID_PARAM, String.format(
                    "[Batch deRegistration] not found all registerInstance , serviceName：%s , groupName: %s",
                    serviceName, groupName));
        }
        
        Map<Instance, Instance> instanceMap = instances.stream()
                .collect(Collectors.toMap(Function.identity(), Function.identity()));
        List<Instance> retainInstances = new ArrayList<>();
        for (Instance instance : allInstance) {
            if (!instanceMap.containsKey(instance)) {
                retainInstances.add(instance);
            }
        }
        return retainInstances;
    }
    
    /**
     * Execute batch register operation.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   instances
     * @throws NacosException NacosException
     */
    public void doBatchRegisterService(String serviceName, String groupName, List<Instance> instances)
            throws NacosException {
        BatchInstanceRequest request = new BatchInstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.BATCH_REGISTER_INSTANCE, instances);
        requestToServer(request, BatchInstanceResponse.class);
        redoService.instanceRegistered(serviceName, groupName);
    }
    
    /**
     * Execute register operation.
     *
     * @param serviceName name of service
     * @param groupName   group of service
     * @param instance    instance to register
     * @throws NacosException nacos exception
     */
    public void doRegisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.REGISTER_INSTANCE, instance);
        requestToServer(request, Response.class);
        redoService.instanceRegistered(serviceName, groupName);
    }
    
    @Override
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER
                .info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}", namespaceId, serviceName,
                        instance);
        redoService.instanceDeregister(serviceName, groupName);
        doDeregisterService(serviceName, groupName, instance);
    }
    
    /**
     * Execute deregister operation.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    public void doDeregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        InstanceRequest request = new InstanceRequest(namespaceId, serviceName, groupName,
                NamingRemoteConstants.DE_REGISTER_INSTANCE, instance);
        requestToServer(request, Response.class);
        redoService.instanceDeregistered(serviceName, groupName);
    }
    
    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
    
    }
    
    @Override
    public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort,
            boolean healthyOnly) throws NacosException {
        ServiceQueryRequest request = new ServiceQueryRequest(namespaceId, serviceName, groupName);
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
        ListView<String> result = new ListView<>();
        result.setCount(response.getCount());
        result.setData(response.getServiceNames());
        return result;
    }
    
    @Override
    public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
        if (NAMING_LOGGER.isDebugEnabled()) {
            NAMING_LOGGER.debug("[GRPC-SUBSCRIBE] service:{}, group:{}, cluster:{} ", serviceName, groupName, clusters);
        }
        redoService.cacheSubscriberForRedo(serviceName, groupName, clusters);
        return doSubscribe(serviceName, groupName, clusters);
    }
    
    /**
     * Execute subscribe operation.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @return current service info of subscribe service
     * @throws NacosException nacos exception
     */
    public ServiceInfo doSubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        SubscribeServiceRequest request = new SubscribeServiceRequest(namespaceId, groupName, serviceName, clusters,
                true);
        SubscribeServiceResponse response = requestToServer(request, SubscribeServiceResponse.class);
        redoService.subscriberRegistered(serviceName, groupName, clusters);
        return response.getServiceInfo();
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        if (NAMING_LOGGER.isDebugEnabled()) {
            NAMING_LOGGER
                    .debug("[GRPC-UNSUBSCRIBE] service:{}, group:{}, cluster:{} ", serviceName, groupName, clusters);
        }
        redoService.subscriberDeregister(serviceName, groupName, clusters);
        doUnsubscribe(serviceName, groupName, clusters);
    }
    
    @Override
    public boolean isSubscribed(String serviceName, String groupName, String clusters) throws NacosException {
        return redoService.isSubscriberRegistered(serviceName, groupName, clusters);
    }
    
    /**
     * Execute unsubscribe operation.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @throws NacosException nacos exception
     */
    public void doUnsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        SubscribeServiceRequest request = new SubscribeServiceRequest(namespaceId, groupName, serviceName, clusters,
                false);
        requestToServer(request, SubscribeServiceResponse.class);
        redoService.removeSubscriberForRedo(serviceName, groupName, clusters);
    }
    
    @Override
    public boolean serverHealthy() {
        return rpcClient.isRunning();
    }
    
    private <T extends Response> T requestToServer(AbstractNamingRequest request, Class<T> responseClass)
            throws NacosException {
        try {
            request.putAllHeader(
                    getSecurityHeaders(request.getNamespace(), request.getGroupName(), request.getServiceName()));
            Response response =
                    requestTimeout < 0 ? rpcClient.request(request) : rpcClient.request(request, requestTimeout);
            if (ResponseCode.SUCCESS.getCode() != response.getResultCode()) {
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            if (responseClass.isAssignableFrom(response.getClass())) {
                return (T) response;
            }
            NAMING_LOGGER.error("Server return unexpected response '{}', expected response should be '{}'",
                    response.getClass().getName(), responseClass.getName());
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, "Request nacos server failed: ", e);
        }
        throw new NacosException(NacosException.SERVER_ERROR, "Server return invalid response");
    }
    
    @Override
    public void shutdown() throws NacosException {
        rpcClient.shutdown();
        redoService.shutdown();
    }
    
    public boolean isEnable() {
        return rpcClient.isRunning();
    }
}
