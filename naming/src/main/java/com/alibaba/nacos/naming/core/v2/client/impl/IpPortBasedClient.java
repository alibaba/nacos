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

package com.alibaba.nacos.naming.core.v2.client.impl;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.AbstractClient;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncData;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatCheckTaskV2;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;

import java.util.Collection;
import java.util.List;

/**
 * Nacos naming client based ip and port.
 *
 * <p>The client is bind to the ip and port users registered. It's a abstract content to simulate the tcp session
 * client.
 *
 * @author xiweng.yy
 */
public class IpPortBasedClient extends AbstractClient {
    
    public static final String ID_DELIMITER = "#";
    
    private final String clientId;
    
    private final boolean ephemeral;
    
    private final String responsibleId;
    
    private ClientBeatCheckTaskV2 beatCheckTask;
    
    private HealthCheckTaskV2 healthCheckTaskV2;
    
    public IpPortBasedClient(String clientId, boolean ephemeral) {
        this.ephemeral = ephemeral;
        this.clientId = clientId;
        this.responsibleId = getResponsibleTagFromId();
        if (ephemeral) {
            beatCheckTask = new ClientBeatCheckTaskV2(this);
            HealthCheckReactor.scheduleCheck(beatCheckTask);
        } else {
            healthCheckTaskV2 = new HealthCheckTaskV2(this);
            HealthCheckReactor.scheduleCheck(healthCheckTaskV2);
        }
    }
    
    private String getResponsibleTagFromId() {
        int index = clientId.indexOf(IpPortBasedClient.ID_DELIMITER);
        return clientId.substring(0, index);
    }
    
    public static String getClientId(String address, boolean ephemeral) {
        return address + ID_DELIMITER + ephemeral;
    }
    
    @Override
    public String getClientId() {
        return clientId;
    }
    
    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    public String getResponsibleId() {
        return responsibleId;
    }
    
    @Override
    public boolean addServiceInstance(Service service, InstancePublishInfo instancePublishInfo) {
        return super.addServiceInstance(service, parseToHealthCheckInstance(instancePublishInfo));
    }
    
    @Override
    public boolean isExpire(long currentTime) {
        return isEphemeral() && getAllPublishedService().isEmpty()
                && currentTime - getLastUpdatedTime() > Constants.DEFAULT_IP_DELETE_TIMEOUT;
    }
    
    public Collection<InstancePublishInfo> getAllInstancePublishInfo() {
        return publishers.values();
    }
    
    @Override
    public void release() {
        super.release();
        if (ephemeral) {
            HealthCheckReactor.cancelCheck(beatCheckTask);
        } else {
            healthCheckTaskV2.setCancelled(true);
        }
    }
    
    private HealthCheckInstancePublishInfo parseToHealthCheckInstance(InstancePublishInfo instancePublishInfo) {
        if (instancePublishInfo instanceof HealthCheckInstancePublishInfo) {
            return (HealthCheckInstancePublishInfo) instancePublishInfo;
        }
        HealthCheckInstancePublishInfo result = new HealthCheckInstancePublishInfo();
        result.setIp(instancePublishInfo.getIp());
        result.setPort(instancePublishInfo.getPort());
        result.setHealthy(instancePublishInfo.isHealthy());
        result.setCluster(instancePublishInfo.getCluster());
        result.setExtendDatum(instancePublishInfo.getExtendDatum());
        if (!ephemeral) {
            result.initHealthCheck();
        }
        return result;
    }
    
    /**
     * Load {@code ClientSyncData} and update current client.
     *
     * @param client client sync data
     */
    public void loadClientSyncData(ClientSyncData client) {
        List<String> namespaces = client.getNamespaces();
        List<String> groupNames = client.getGroupNames();
        List<String> serviceNames = client.getServiceNames();
        List<InstancePublishInfo> instances = client.getInstancePublishInfos();
        for (int i = 0; i < namespaces.size(); i++) {
            Service service = Service.newService(namespaces.get(i), groupNames.get(i), serviceNames.get(i), ephemeral);
            Service singleton = ServiceManager.getInstance().getSingleton(service);
            HealthCheckInstancePublishInfo instance = parseToHealthCheckInstance(instances.get(i));
            instance.initHealthCheck();
            publishers.put(singleton, instance);
            MetricsMonitor.incrementInstanceCount();
        }
    }
}
