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

package com.alibaba.nacos.naming.healthcheck.heartbeat;

import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

/**
 * Thread to update ephemeral instance triggered by client beat for v2.x.
 *
 * @author nkorange
 */
public class ClientBeatProcessorV2 implements BeatProcessor {
    
    private final String namespace;
    
    private final RsInfo rsInfo;
    
    private final IpPortBasedClient client;
    
    public ClientBeatProcessorV2(String namespace, RsInfo rsInfo, IpPortBasedClient ipPortBasedClient) {
        this.namespace = namespace;
        this.rsInfo = rsInfo;
        this.client = ipPortBasedClient;
    }
    
    @Override
    public void run() {
        if (Loggers.EVT_LOG.isDebugEnabled()) {
            Loggers.EVT_LOG.debug("[CLIENT-BEAT] processing beat: {}", rsInfo.toString());
        }
        String ip = rsInfo.getIp();
        int port = rsInfo.getPort();
        String serviceName = NamingUtils.getServiceName(rsInfo.getServiceName());
        String groupName = NamingUtils.getGroupName(rsInfo.getServiceName());
        Service service = Service.newService(namespace, groupName, serviceName, rsInfo.isEphemeral());
        HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) client.getInstancePublishInfo(service);
        if (instance.getIp().equals(ip) && instance.getPort() == port) {
            if (Loggers.EVT_LOG.isDebugEnabled()) {
                Loggers.EVT_LOG.debug("[CLIENT-BEAT] refresh beat: {}", rsInfo);
            }
            instance.setLastHeartBeatTime(System.currentTimeMillis());
            if (!instance.isHealthy()) {
                instance.setHealthy(true);
                Loggers.EVT_LOG.info("service: {} {POS} {IP-ENABLED} valid: {}:{}@{}, region: {}, msg: client beat ok",
                        rsInfo.getServiceName(), ip, port, rsInfo.getCluster(), UtilsAndCommons.LOCALHOST_SITE);
                NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service));
                NotifyCenter.publishEvent(new ClientEvent.ClientChangedEvent(client));
            }
        }
    }
}
