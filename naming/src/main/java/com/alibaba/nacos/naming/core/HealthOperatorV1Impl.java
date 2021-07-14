/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.google.common.collect.Lists;
import org.springframework.stereotype.Component;

/**
 * Health operator implementation for v1.x.
 *
 * @author xiweng.yy
 */
@Component
public class HealthOperatorV1Impl implements HealthOperator {
    
    private final ServiceManager serviceManager;
    
    private final UdpPushService pushService;
    
    public HealthOperatorV1Impl(ServiceManager serviceManager, UdpPushService pushService) {
        this.serviceManager = serviceManager;
        this.pushService = pushService;
    }
    
    @Override
    public void updateHealthStatusForPersistentInstance(String namespace, String fullServiceName, String clusterName,
            String ip, int port, boolean healthy) throws NacosException {
        Service service = serviceManager.getService(namespace, fullServiceName);
        // Only health check "none" need update health status with api
        if (HealthCheckType.NONE.name().equals(service.getClusterMap().get(clusterName).getHealthChecker().getType())) {
            for (Instance instance : service.allIPs(Lists.newArrayList(clusterName))) {
                if (instance.getIp().equals(ip) && instance.getPort() == port) {
                    instance.setHealthy(healthy);
                    Loggers.EVT_LOG
                            .info((healthy ? "[IP-ENABLED]" : "[IP-DISABLED]") + " ips: " + instance.getIp() + ":"
                                    + instance.getPort() + "@" + instance.getClusterName() + ", service: "
                                    + fullServiceName + ", msg: update thought HealthController api");
                    pushService.serviceChanged(service);
                    break;
                }
            }
        } else {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "health check is still working, service: " + fullServiceName);
        }
    }
}
