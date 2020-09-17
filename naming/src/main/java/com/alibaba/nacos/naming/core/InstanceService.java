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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingResponseCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.impl.IpPortBasedClientManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationService;
import com.alibaba.nacos.naming.healthcheck.ClientBeatProcessorV2;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.utils.ServiceUtil;

/**
 * Instance service.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class InstanceService {
    
    private final IpPortBasedClientManager ipPortBasedClientManager;
    
    private final ClientOperationService clientOperationService;
    
    private final ServiceStorage serviceStorage;
    
    public InstanceService(IpPortBasedClientManager ipPortBasedClientManager,
            ClientOperationService clientOperationService, ServiceStorage serviceStorage) {
        this.ipPortBasedClientManager = ipPortBasedClientManager;
        this.clientOperationService = clientOperationService;
        this.serviceStorage = serviceStorage;
    }
    
    /**
     * Register an instance to a service in AP mode.
     *
     * <p>This method creates {@code IpPortBasedClient} if it don't exist.
     *
     * @param namespaceId id of namespace
     * @param serviceName service name
     * @param instance    instance to register
     */
    public void registerInstance(String namespaceId, String serviceName, Instance instance) {
        String clientId = instance.toInetAddr();
        createIpPortClientIfAbsent(clientId, instance.isEphemeral());
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped, instance.isEphemeral());
        clientOperationService.registerInstance(service, instance, clientId);
    }
    
    /**
     * Remove instance from service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param instance    instance
     */
    public void removeInstance(String namespaceId, String serviceName, Instance instance) {
        String clientId = instance.toInetAddr();
        if (!ipPortBasedClientManager.allClientId().contains(clientId)) {
            Loggers.SRV_LOG.warn("remove instance from non-exist client: {}", clientId);
            return;
        }
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped, instance.isEphemeral());
        clientOperationService.deregisterInstance(service, instance, clientId);
    }
    
    /**
     * Get all instance of input service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param subscriber  subscriber info
     * @param cluster     cluster of instances
     * @param healthOnly  whether only return health instances
     * @return service info
     */
    public ServiceInfo listInstance(String namespaceId, String serviceName, Subscriber subscriber, String cluster,
            boolean healthOnly) {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped, true);
        if (null != subscriber) {
            createIpPortClientIfAbsent(subscriber.getAddrStr(), true);
            clientOperationService.subscribeService(service, subscriber, subscriber.getAddrStr());
        }
        ServiceInfo serviceInfo = serviceStorage.getData(service);
        return ServiceUtil.filterInstances(serviceInfo, cluster, healthOnly);
    }
    
    /**
     * Handle beat request.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param ip          ip of instance
     * @param port        port of instance
     * @param cluster     cluster of instance
     * @param clientBeat  client beat info
     * @return result code
     * @throws NacosException nacos exception when service non-exist and client beat info is null
     */
    public int handleBeat(String namespaceId, String serviceName, String ip, int port, String cluster,
            RsInfo clientBeat) throws NacosException {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        String clientId = ip + ":" + port;
        IpPortBasedClient client = (IpPortBasedClient) ipPortBasedClientManager.getClient(clientId);
        if (null == client) {
            if (null == clientBeat) {
                return NamingResponseCode.RESOURCE_NOT_FOUND;
            }
            Instance instance = new Instance();
            instance.setPort(clientBeat.getPort());
            instance.setIp(clientBeat.getIp());
            instance.setWeight(clientBeat.getWeight());
            instance.setMetadata(clientBeat.getMetadata());
            instance.setClusterName(clientBeat.getCluster());
            instance.setServiceName(serviceName);
            instance.setInstanceId(instance.getInstanceId());
            instance.setEphemeral(clientBeat.isEphemeral());
            registerInstance(namespaceId, serviceName, instance);
            client = (IpPortBasedClient) ipPortBasedClientManager.getClient(clientId);
        }
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped);
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    "service not found: " + serviceName + "@" + namespaceId);
        }
        if (null == clientBeat) {
            clientBeat = new RsInfo();
            clientBeat.setIp(ip);
            clientBeat.setPort(port);
            clientBeat.setCluster(cluster);
            clientBeat.setServiceName(serviceName);
        }
        ClientBeatProcessorV2 beatProcessor = new ClientBeatProcessorV2(namespaceId, clientBeat, client);
        HealthCheckReactor.scheduleNow(beatProcessor);
        client.setLastUpdatedTime();
        return NamingResponseCode.OK;
    }
    
    public long getHeartBeatInterval(String namespaceId, String serviceName, String ip, int port, String cluster) {
        // TODO Get heart beat interval from CP metadata
        return 5000L;
    }
    
    private void createIpPortClientIfAbsent(String clientId, boolean ephemeral) {
        if (!ipPortBasedClientManager.allClientId().contains(clientId)) {
            ipPortBasedClientManager.clientConnected(new IpPortBasedClient(clientId, ephemeral));
        }
    }
}
