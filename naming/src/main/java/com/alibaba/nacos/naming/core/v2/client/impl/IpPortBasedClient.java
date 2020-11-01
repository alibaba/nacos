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

import com.alibaba.nacos.naming.core.v2.client.AbstractClient;
import com.alibaba.nacos.naming.core.v2.pojo.HeartBeatInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatCheckTaskV2;
import com.alibaba.nacos.naming.healthcheck.HealthCheckReactor;

import java.util.Collection;

/**
 * Nacos naming client based ip and port.
 *
 * <p>The client is bind to the ip and port users registered. It's a abstract content to simulate the tcp session
 * client.
 *
 * @author xiweng.yy
 */
public class IpPortBasedClient extends AbstractClient {
    
    private final String clientId;
    
    private final boolean ephemeral;
    
    private final ClientBeatCheckTaskV2 beatCheckTask;
    
    public IpPortBasedClient(String clientId, boolean ephemeral) {
        this.ephemeral = ephemeral;
        this.clientId = clientId;
        beatCheckTask = new ClientBeatCheckTaskV2(this);
        scheduleCheckTask();
    }
    
    private void scheduleCheckTask() {
        HealthCheckReactor.scheduleCheck(beatCheckTask);
    }
    
    @Override
    public String getClientId() {
        return clientId;
    }
    
    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    @Override
    public boolean addServiceInstance(Service service, InstancePublishInfo instancePublishInfo) {
        return super.addServiceInstance(service, parseToHeartBeatInstance(instancePublishInfo));
    }
    
    public Collection<InstancePublishInfo> getAllInstancePublishInfo() {
        return publishers.values();
    }
    
    public void destroy() {
        HealthCheckReactor.cancelCheck(beatCheckTask);
    }
    
    private InstancePublishInfo parseToHeartBeatInstance(InstancePublishInfo instancePublishInfo) {
        if (instancePublishInfo instanceof HeartBeatInstancePublishInfo) {
            return instancePublishInfo;
        }
        HeartBeatInstancePublishInfo result = new HeartBeatInstancePublishInfo();
        result.setIp(instancePublishInfo.getIp());
        result.setPort(instancePublishInfo.getPort());
        result.setHealthy(instancePublishInfo.isHealthy());
        result.setExtendDatum(instancePublishInfo.getExtendDatum());
        return result;
    }
}
