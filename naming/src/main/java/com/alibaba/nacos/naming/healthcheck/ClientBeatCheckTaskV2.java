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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NacosNamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.HeartBeatInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collection;
import java.util.Optional;

/**
 * Client beat check task of service for version 2.x.
 *
 * @author nkorange
 */
public class ClientBeatCheckTaskV2 implements BeatCheckTask {
    
    private final IpPortBasedClient client;
    
    public ClientBeatCheckTaskV2(IpPortBasedClient client) {
        this.client = client;
    }
    
    @JsonIgnore
    public DistroMapper getDistroMapper() {
        return ApplicationUtils.getBean(DistroMapper.class);
    }
    
    public GlobalConfig getGlobalConfig() {
        return ApplicationUtils.getBean(GlobalConfig.class);
    }
    
    public SwitchDomain getSwitchDomain() {
        return ApplicationUtils.getBean(SwitchDomain.class);
    }
    
    public NacosNamingMetadataManager getMetadataManager() {
        return ApplicationUtils.getBean(NacosNamingMetadataManager.class);
    }
    
    @Override
    public String taskKey() {
        return KeyBuilder.buildServiceMetaKey(client.getClientId(), String.valueOf(client.isEphemeral()));
    }
    
    @Override
    public void run() {
        // TODO add white list like v1 {@code marked}
        try {
            if (!getSwitchDomain().isHealthCheckEnabled()) {
                return;
            }
            if (!getDistroMapper().responsible(client.getClientId())) {
                return;
            }
            boolean expireInstance = getGlobalConfig().isExpireInstance();
            Collection<Service> services = client.getAllPublishedService();
            for (Service each : services) {
                HeartBeatInstancePublishInfo instance = (HeartBeatInstancePublishInfo) client.getInstancePublishInfo(each);
                long lastBeatTime = instance.getLastHeartBeatTime();
                if (instance.isHealthy() && isUnhealthy(each, instance, lastBeatTime)) {
                    changeHealthyStatus(each, instance, lastBeatTime);
                }
                if (expireInstance && isExpireInstance(each, instance, lastBeatTime)) {
                    deleteIp(each, instance);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("Exception while processing client beat time out.", e);
        }
        
    }
    
    private boolean isUnhealthy(Service service, InstancePublishInfo instance, long lastBeatTime) {
        long beatTimeout = getTimeout(service, instance, PreservedMetadataKeys.HEART_BEAT_TIMEOUT,
                Constants.DEFAULT_HEART_BEAT_TIMEOUT);
        return System.currentTimeMillis() - lastBeatTime > beatTimeout;
    }
    
    private void changeHealthyStatus(Service service, InstancePublishInfo instance, long lastBeatTime) {
        instance.setHealthy(false);
        Object cluster = instance.getExtendDatum().get(CommonParams.CLUSTER_NAME);
        Loggers.EVT_LOG
                .info("{POS} {IP-DISABLED} valid: {}:{}@{}@{}, region: {}, msg: client last beat: {}", instance.getIp(),
                        instance.getPort(), cluster, service.getName(), UtilsAndCommons.LOCALHOST_SITE, lastBeatTime);
        NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service));
        NotifyCenter.publishEvent(new ClientEvent.ClientChangedEvent(client));
    }
    
    private boolean isExpireInstance(Service service, InstancePublishInfo instance, long lastBeatTime) {
        long deleteTimeout = getTimeout(service, instance, PreservedMetadataKeys.IP_DELETE_TIMEOUT,
                Constants.DEFAULT_IP_DELETE_TIMEOUT);
        return System.currentTimeMillis() - lastBeatTime > deleteTimeout;
    }
    
    private void deleteIp(Service service, InstancePublishInfo instance) {
        Loggers.SRV_LOG.info("[AUTO-DELETE-IP] service: {}, ip: {}", service.toString(), JacksonUtils.toJson(instance));
        client.removeServiceInstance(service);
        NotifyCenter.publishEvent(new ClientOperationEvent.ClientDeregisterServiceEvent(service, client.getClientId()));
    }
    
    private long getTimeout(Service service, InstancePublishInfo instance, String timeoutKey, long defaultValue) {
        Optional<InstanceMetadata> instanceMetadata = getMetadataManager().getInstanceMetadata(service, instance.getIp());
        Object object = instance.getExtendDatum().get(timeoutKey);
        if (instanceMetadata.isPresent() && instanceMetadata.get().getExtendData().containsKey(timeoutKey)) {
            object = instanceMetadata.get().getExtendData().get(timeoutKey);
        }
        if (null == object) {
            return defaultValue;
        }
        return Long.parseLong(object.toString());
    }
}
