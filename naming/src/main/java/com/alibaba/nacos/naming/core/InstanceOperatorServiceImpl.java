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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.ClientInfo;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link InstanceOperator} by service for v1.x.
 *
 * @author xiweng.yy
 */
@Component
public class InstanceOperatorServiceImpl implements InstanceOperator {
    
    private final ServiceManager serviceManager;
    
    private final SwitchDomain switchDomain;
    
    private final PushService pushService;
    
    private DataSource pushDataSource = new DataSource() {
        
        @Override
        public String getData(PushService.PushClient client) {
            ServiceInfo result = new ServiceInfo(client.getServiceName(), client.getClusters());
            try {
                Subscriber subscriber = new Subscriber(client.getAddrStr(), client.getAgent(), client.getApp(),
                        client.getIp(), client.getNamespaceId(), client.getServiceName(), client.getPort(),
                        client.getClusters());
                result = listInstance(client.getNamespaceId(), client.getServiceName(), subscriber,
                        client.getClusters(), false);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("PUSH-SERVICE: service is not modified", e);
            }
            
            // overdrive the cache millis to push mode
            result.setCacheMillis(switchDomain.getPushCacheMillis(client.getServiceName()));
            return JacksonUtils.toJson(result);
        }
    };
    
    public InstanceOperatorServiceImpl(ServiceManager serviceManager, SwitchDomain switchDomain,
            PushService pushService) {
        this.serviceManager = serviceManager;
        this.switchDomain = switchDomain;
        this.pushService = pushService;
    }
    
    @Override
    public void registerInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {
        com.alibaba.nacos.naming.core.Instance coreInstance = (com.alibaba.nacos.naming.core.Instance) instance;
        serviceManager.registerInstance(namespaceId, serviceName, coreInstance);
    }
    
    @Override
    public void removeInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {
        com.alibaba.nacos.naming.core.Instance coreInstance = (com.alibaba.nacos.naming.core.Instance) instance;
        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            Loggers.SRV_LOG.warn("remove instance from non-exist service: {}", serviceName);
            return;
        }
        serviceManager.removeInstance(namespaceId, serviceName, instance.isEphemeral(), coreInstance);
    }
    
    @Override
    public void updateInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {
        com.alibaba.nacos.naming.core.Instance coreInstance = (com.alibaba.nacos.naming.core.Instance) instance;
        serviceManager.updateInstance(namespaceId, serviceName, coreInstance);
    }
    
    @Override
    public ServiceInfo listInstance(String namespaceId, String serviceName, Subscriber subscriber, String cluster,
            boolean healthOnly) throws Exception {
        ClientInfo clientInfo = new ClientInfo(subscriber.getAgent());
        String clientIP = subscriber.getIp();
        ServiceInfo result = new ServiceInfo(serviceName, cluster);
        Service service = serviceManager.getService(namespaceId, serviceName);
        long cacheMillis = switchDomain.getDefaultCacheMillis();
        
        // now try to enable the push
        try {
            if (subscriber.getPort() > 0 && pushService.canEnablePush(subscriber.getAgent())) {
                pushService.addClient(namespaceId, serviceName, cluster, subscriber.getAgent(),
                        new InetSocketAddress(clientIP, subscriber.getPort()), pushDataSource, StringUtils.EMPTY,
                        StringUtils.EMPTY);
                cacheMillis = switchDomain.getPushCacheMillis(serviceName);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-API] failed to added push client {}, {}:{}", clientInfo, clientIP,
                    subscriber.getPort(), e);
            cacheMillis = switchDomain.getDefaultCacheMillis();
        }
        
        if (service == null) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("no instance to serve for service: {}", serviceName);
            }
            result.setCacheMillis(cacheMillis);
            return result;
        }
        
        checkIfDisabled(service);
        
        List<com.alibaba.nacos.naming.core.Instance> srvedIPs = service
                .srvIPs(Arrays.asList(StringUtils.split(cluster, ",")));
        
        // filter ips using selector:
        if (service.getSelector() != null && StringUtils.isNotBlank(clientIP)) {
            srvedIPs = service.getSelector().select(clientIP, srvedIPs);
        }
        
        if (CollectionUtils.isEmpty(srvedIPs)) {
            
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("no instance to serve for service: {}", serviceName);
            }
            
            result.setCacheMillis(cacheMillis);
            result.setLastRefTime(System.currentTimeMillis());
            result.setChecksum(service.getChecksum());
            return result;
        }
        
        Map<Boolean, List<com.alibaba.nacos.naming.core.Instance>> ipMap = new HashMap<>(2);
        ipMap.put(Boolean.TRUE, new ArrayList<>());
        ipMap.put(Boolean.FALSE, new ArrayList<>());
        
        for (com.alibaba.nacos.naming.core.Instance ip : srvedIPs) {
            ipMap.get(ip.isHealthy()).add(ip);
        }
        
        double threshold = service.getProtectThreshold();
        if ((float) ipMap.get(Boolean.TRUE).size() / srvedIPs.size() <= threshold) {
            
            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}", serviceName);
            
            ipMap.get(Boolean.TRUE).addAll(ipMap.get(Boolean.FALSE));
            ipMap.get(Boolean.FALSE).clear();
        }
        
        List<Instance> hosts = new LinkedList<>();
        
        for (Map.Entry<Boolean, List<com.alibaba.nacos.naming.core.Instance>> entry : ipMap.entrySet()) {
            List<com.alibaba.nacos.naming.core.Instance> ips = entry.getValue();
            
            if (healthOnly && !entry.getKey()) {
                continue;
            }
            
            for (com.alibaba.nacos.naming.core.Instance instance : ips) {
                
                // remove disabled instance:
                if (!instance.isEnabled()) {
                    continue;
                }
                hosts.add(instance);
            }
        }
        
        result.setHosts(hosts);
        result.setCacheMillis(cacheMillis);
        result.setLastRefTime(System.currentTimeMillis());
        result.setChecksum(service.getChecksum());
        return result;
    }
    
    private void checkIfDisabled(Service service) throws Exception {
        if (!service.getEnabled()) {
            throw new Exception("service is disabled now.");
        }
    }
    
    @Override
    public int handleBeat(String namespaceId, String serviceName, String ip, int port, String cluster,
            RsInfo clientBeat) throws NacosException {
        com.alibaba.nacos.naming.core.Instance instance = serviceManager
                .getInstance(namespaceId, serviceName, cluster, ip, port);
        
        if (instance == null) {
            if (clientBeat == null) {
                return NamingResponseCode.RESOURCE_NOT_FOUND;
            }
            
            Loggers.SRV_LOG.warn("[CLIENT-BEAT] The instance has been removed for health mechanism, "
                    + "perform data compensation operations, beat: {}, serviceName: {}", clientBeat, serviceName);
            
            instance = new com.alibaba.nacos.naming.core.Instance();
            instance.setPort(clientBeat.getPort());
            instance.setIp(clientBeat.getIp());
            instance.setWeight(clientBeat.getWeight());
            instance.setMetadata(clientBeat.getMetadata());
            instance.setClusterName(cluster);
            instance.setServiceName(serviceName);
            instance.setInstanceId(instance.getInstanceId());
            instance.setEphemeral(clientBeat.isEphemeral());
            
            serviceManager.registerInstance(namespaceId, serviceName, instance);
        }
        
        Service service = serviceManager.getService(namespaceId, serviceName);
        
        if (service == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                    "service not found: " + serviceName + "@" + namespaceId);
        }
        if (clientBeat == null) {
            clientBeat = new RsInfo();
            clientBeat.setIp(ip);
            clientBeat.setPort(port);
            clientBeat.setCluster(cluster);
        }
        service.processClientBeat(clientBeat);
        return NamingResponseCode.OK;
    }
    
    @Override
    public long getHeartBeatInterval(String namespaceId, String serviceName, String ip, int port, String cluster) {
        com.alibaba.nacos.naming.core.Instance instance = serviceManager
                .getInstance(namespaceId, serviceName, cluster, ip, port);
        if (instance.containsMetadata(PreservedMetadataKeys.HEART_BEAT_INTERVAL)) {
            return instance.getInstanceHeartBeatInterval();
        }
        return switchDomain.getClientBeatInterval();
    }
}
