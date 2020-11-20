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
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.ClientOperationService;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatProcessorV2;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.utils.ServiceUtil;

import java.util.Optional;

/**
 * Instance service.
 *
 * @author xiweng.yy
 */
@org.springframework.stereotype.Service
public class InstanceOperatorClientImpl implements InstanceOperator {
    
    private final ClientManagerDelegate clientManager;
    
    private final ClientOperationService clientOperationService;
    
    private final ServiceStorage serviceStorage;
    
    private final NamingMetadataOperateService metadataOperateService;
    
    private final NamingMetadataManager metadataManager;
    
    private final SwitchDomain switchDomain;
    
    public InstanceOperatorClientImpl(ClientManagerDelegate clientManager,
            ClientOperationService clientOperationService, ServiceStorage serviceStorage,
            NamingMetadataOperateService metadataOperateService, NamingMetadataManager metadataManager, SwitchDomain switchDomain) {
        this.clientManager = clientManager;
        this.clientOperationService = clientOperationService;
        this.serviceStorage = serviceStorage;
        this.metadataOperateService = metadataOperateService;
        this.metadataManager = metadataManager;
        this.switchDomain = switchDomain;
    }
    
    /**
     * This method creates {@code IpPortBasedClient} if it don't exist.
     */
    @Override
    public void registerInstance(String namespaceId, String serviceName, Instance instance) {
        String clientId = instance.toInetAddr();
        createIpPortClientIfAbsent(clientId, instance.isEphemeral());
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped, instance.isEphemeral());
        clientOperationService.registerInstance(service, instance, clientId);
    }
    
    @Override
    public void removeInstance(String namespaceId, String serviceName, Instance instance) {
        String clientId = instance.toInetAddr();
        if (!clientManager.allClientId().contains(clientId)) {
            Loggers.SRV_LOG.warn("remove instance from non-exist client: {}", clientId);
            return;
        }
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped, instance.isEphemeral());
        clientOperationService.deregisterInstance(service, instance, clientId);
    }
    
    @Override
    public void updateInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException {
        Service service = Service.newService(namespaceId, groupName, serviceName, instance.isEphemeral());
        if (!ServiceManager.getInstance().containSingleton(service)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "service not found, namespace: " + namespaceId + ", service: " + service);
        }
        metadataOperateService.updateInstanceMetadata(service, instance.getIp(), buildMetadata(instance));
    }
    
    private InstanceMetadata buildMetadata(Instance instance) {
        InstanceMetadata result = new InstanceMetadata();
        result.setEnabled(instance.isEnabled());
        result.setWeight(instance.getWeight());
        result.getExtendData().putAll(instance.getMetadata());
        return result;
    }
    
    @Override
    public ServiceInfo listInstance(String namespaceId, String serviceName, Subscriber subscriber, String cluster,
            boolean healthOnly) {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped);
        if (null != subscriber) {
            createIpPortClientIfAbsent(subscriber.getAddrStr(), true);
            clientOperationService.subscribeService(service, subscriber, subscriber.getAddrStr());
        }
        ServiceInfo serviceInfo = serviceStorage.getData(service);
        ServiceInfo result = ServiceUtil.selectInstances(serviceInfo, cluster, healthOnly, true);
        // adapt for v1.x sdk
        result.setName(NamingUtils.getGroupedName(result.getName(), result.getGroupName()));
        return result;
    }
    
    @Override
    public int handleBeat(String namespaceId, String serviceName, String ip, int port, String cluster,
            RsInfo clientBeat) throws NacosException {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        String clientId = ip + ":" + port;
        IpPortBasedClient client = (IpPortBasedClient) clientManager.getClient(clientId);
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
            client = (IpPortBasedClient) clientManager.getClient(clientId);
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
    
    @Override
    public long getHeartBeatInterval(String namespaceId, String serviceName, String ip, int port, String cluster) {
        String groupName = NamingUtils.getGroupName(serviceName);
        String serviceNameNoGrouped = NamingUtils.getServiceName(serviceName);
        Service service = Service.newService(namespaceId, groupName, serviceNameNoGrouped);
        Optional<InstanceMetadata> metadata = metadataManager.getInstanceMetadata(service, ip);
        if (metadata.isPresent() && metadata.get().getExtendData().containsKey(PreservedMetadataKeys.HEART_BEAT_INTERVAL)) {
            return ConvertUtils.toLong(metadata.get().getExtendData().get(PreservedMetadataKeys.HEART_BEAT_INTERVAL));
        }
        String clientId = ip + ":" + port;
        Client client = clientManager.getClient(clientId);
        InstancePublishInfo instance = null != client ? client.getInstancePublishInfo(service) : null;
        if (null != instance && instance.getExtendDatum().containsKey(PreservedMetadataKeys.HEART_BEAT_INTERVAL)) {
            return ConvertUtils.toLong(instance.getExtendDatum().get(PreservedMetadataKeys.HEART_BEAT_INTERVAL));
        }
        return switchDomain.getClientBeatInterval();
    }
    
    private void createIpPortClientIfAbsent(String clientId, boolean ephemeral) {
        if (!clientManager.allClientId().contains(clientId)) {
            clientManager.clientConnected(new IpPortBasedClient(clientId, ephemeral));
        }
    }
}
