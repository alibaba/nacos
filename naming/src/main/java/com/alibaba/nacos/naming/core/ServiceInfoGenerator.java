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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Service information generator.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceInfoGenerator {
    
    private final ServiceManager serviceManager;
    
    private final SwitchDomain switchDomain;
    
    public ServiceInfoGenerator(ServiceManager serviceManager, SwitchDomain switchDomain) {
        this.serviceManager = serviceManager;
        this.switchDomain = switchDomain;
    }
    
    public ServiceInfo generateEmptyServiceInfo(String serviceName, String clusters) {
        return new ServiceInfo(serviceName, clusters);
    }
    
    /**
     * Generate {@link ServiceInfo} for service and clusters.
     *
     * @param namespaceId namespace id of service
     * @param serviceName service name
     * @param clusters    clusters of instances
     * @param healthyOnly only healthy instances
     * @param clientIp    source client ip
     * @return service information
     * @throws NacosException when service is disabled
     */
    public ServiceInfo generateServiceInfo(String namespaceId, String serviceName, String clusters, boolean healthyOnly,
            String clientIp) throws NacosException {
        if (!serviceManager.containService(namespaceId, serviceName)) {
            return generateEmptyServiceInfo(serviceName, clusters);
        }
        Service service = serviceManager.getService(namespaceId, serviceName);
        if (!service.getEnabled()) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    String.format("Service %s : %s is disable now", namespaceId, serviceName));
        }
        return generateServiceInfo(service, clusters, healthyOnly, clientIp);
    }
    
    /**
     * Generate {@link ServiceInfo} for service and clusters.
     *
     * @param service     service
     * @param clusters    clusters of instances
     * @param healthyOnly only healthy instances
     * @param clientIp    source client ip
     * @return service information
     */
    public ServiceInfo generateServiceInfo(Service service, String clusters, boolean healthyOnly, String clientIp) {
        // TODO the origin logic in {@link InstanceController#doSrvIpxt will try to add push.
        ServiceInfo result = new ServiceInfo(service.getName(), clusters);
        List<Instance> instances = getInstanceFromService(service, clusters, healthyOnly, clientIp);
        result.addAllHosts(instances);
        result.setName(service.getName());
        result.setCacheMillis(switchDomain.getDefaultCacheMillis());
        result.setLastRefTime(System.currentTimeMillis());
        result.setChecksum(service.getChecksum());
        result.setClusters(clusters);
        // TODO there are some parameters do not include in service info, but added to return in origin logic
        return result;
    }
    
    private List<Instance> getInstanceFromService(Service service, String clusters, boolean healthyOnly,
            String clientIp) {
        List<Instance> result = service.srvIPs(Arrays.asList(StringUtils.split(clusters, ",")));
        if (service.getSelector() != null && StringUtils.isNotBlank(clientIp)) {
            result = service.getSelector().select(clientIp, result);
        }
        return result.isEmpty() ? result : healthyOnly ? doProtectThreshold(service, result) : result;
    }
    
    private List<Instance> doProtectThreshold(Service service, List<Instance> instances) {
        Map<Boolean, List<Instance>> healthyInstancesMap = new HashMap<>(2);
        healthyInstancesMap.put(Boolean.TRUE, new LinkedList<>());
        healthyInstancesMap.put(Boolean.FALSE, new LinkedList<>());
        for (Instance each : instances) {
            healthyInstancesMap.get(each.isHealthy()).add(each);
        }
        if ((float) healthyInstancesMap.get(Boolean.TRUE).size() / instances.size() <= service.getProtectThreshold()) {
            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}", service.getName());
            healthyInstancesMap.get(Boolean.TRUE).addAll(healthyInstancesMap.get(Boolean.FALSE));
            healthyInstancesMap.get(Boolean.FALSE).clear();
        }
        return healthyInstancesMap.get(Boolean.TRUE);
    }
}
