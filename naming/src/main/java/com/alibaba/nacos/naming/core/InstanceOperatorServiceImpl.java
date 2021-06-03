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
import com.alibaba.nacos.naming.pojo.InstanceOperationContext;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.naming.push.v1.ClientInfo;
import com.alibaba.nacos.naming.push.v1.DataSource;
import com.alibaba.nacos.naming.push.v1.NamingSubscriberServiceV1Impl;
import com.alibaba.nacos.naming.push.v1.PushClient;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.EPHEMERAL;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.PERSIST;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.UPDATE_INSTANCE_METADATA_ACTION_REMOVE;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.UPDATE_INSTANCE_METADATA_ACTION_UPDATE;

/**
 * Implementation of {@link InstanceOperator} by service for v1.x.
 *
 * @author xiweng.yy
 */
@Component
public class InstanceOperatorServiceImpl implements InstanceOperator {
    
    private final ServiceManager serviceManager;
    
    private final SwitchDomain switchDomain;
    
    private final UdpPushService pushService;
    
    private final NamingSubscriberServiceV1Impl subscriberServiceV1;
    
    private DataSource pushDataSource = new DataSource() {
        
        @Override
        public String getData(PushClient client) {
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
            UdpPushService pushService, NamingSubscriberServiceV1Impl subscriberServiceV1) {
        this.serviceManager = serviceManager;
        this.switchDomain = switchDomain;
        this.pushService = pushService;
        this.subscriberServiceV1 = subscriberServiceV1;
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
    public void patchInstance(String namespaceId, String serviceName, InstancePatchObject patchObject)
            throws NacosException {
        com.alibaba.nacos.naming.core.Instance instance = serviceManager
                .getInstance(namespaceId, serviceName, patchObject.getCluster(), patchObject.getIp(),
                        patchObject.getPort());
        if (instance == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "instance not found");
        }
        if (null != patchObject.getMetadata()) {
            instance.setMetadata(patchObject.getMetadata());
        }
        if (null != patchObject.getApp()) {
            instance.setApp(patchObject.getApp());
        }
        if (null != patchObject.getEnabled()) {
            instance.setEnabled(patchObject.getEnabled());
        }
        if (null != patchObject.getHealthy()) {
            instance.setHealthy(patchObject.getHealthy());
        }
        if (null != patchObject.getApp()) {
            instance.setApp(patchObject.getApp());
        }
        instance.setLastBeat(System.currentTimeMillis());
        instance.validate();
        serviceManager.updateInstance(namespaceId, serviceName, instance);
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
                subscriberServiceV1.addClient(namespaceId, serviceName, cluster, subscriber.getAgent(),
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
        
        List<com.alibaba.nacos.naming.core.Instance> srvedIps = service
                .srvIPs(Arrays.asList(StringUtils.split(cluster, ",")));
        
        // filter ips using selector:
        if (service.getSelector() != null && StringUtils.isNotBlank(clientIP)) {
            srvedIps = service.getSelector().select(clientIP, srvedIps);
        }
        
        if (CollectionUtils.isEmpty(srvedIps)) {
            
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("no instance to serve for service: {}", serviceName);
            }
            
            result.setCacheMillis(cacheMillis);
            result.setLastRefTime(System.currentTimeMillis());
            result.setChecksum(service.getChecksum());
            return result;
        }
        
        long total = 0;
        Map<Boolean, List<com.alibaba.nacos.naming.core.Instance>> ipMap = new HashMap<>(2);
        ipMap.put(Boolean.TRUE, new ArrayList<>());
        ipMap.put(Boolean.FALSE, new ArrayList<>());
        
        for (com.alibaba.nacos.naming.core.Instance ip : srvedIps) {
            // remove disabled instance:
            if (!ip.isEnabled()) {
                continue;
            }
            ipMap.get(ip.isHealthy()).add(ip);
            total += 1;
        }
        
        double threshold = service.getProtectThreshold();
        List<Instance> hosts;
        if ((float) ipMap.get(Boolean.TRUE).size() / total <= threshold) {
            
            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}", result.getName());
            result.setReachProtectionThreshold(true);
            hosts = Stream.of(Boolean.TRUE, Boolean.FALSE).map(ipMap::get).flatMap(Collection::stream)
                    .map(InstanceUtil::deepCopy)
                    // set all to `healthy` state to protect
                    .peek(instance -> instance.setHealthy(true)).collect(Collectors.toCollection(LinkedList::new));
        } else {
            result.setReachProtectionThreshold(false);
            hosts = new LinkedList<>(ipMap.get(Boolean.TRUE));
            if (!healthOnly) {
                hosts.addAll(ipMap.get(Boolean.FALSE));
            }
        }
        
        result.setHosts(hosts);
        result.setCacheMillis(cacheMillis);
        result.setLastRefTime(System.currentTimeMillis());
        result.setChecksum(service.getChecksum());
        return result;
    }
    
    @Override
    public Instance getInstance(String namespaceId, String serviceName, String cluster, String ip, int port)
            throws NacosException {
        Service service = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);
        List<com.alibaba.nacos.naming.core.Instance> ips = service.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            throw new NacosException(NacosException.NOT_FOUND,
                    "no ips found for cluster " + cluster + " in service " + serviceName);
        }
        for (com.alibaba.nacos.naming.core.Instance each : ips) {
            if (each.getIp().equals(ip) && each.getPort() == port) {
                return each;
            }
        }
        throw new NacosException(NacosException.NOT_FOUND, "no matched ip found!");
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
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
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
        if (null != instance && instance.containsMetadata(PreservedMetadataKeys.HEART_BEAT_INTERVAL)) {
            return instance.getInstanceHeartBeatInterval();
        }
        return switchDomain.getClientBeatInterval();
    }
    
    @Override
    public List<? extends Instance> listAllInstances(String namespaceId, String serviceName) throws NacosException {
        Service service = serviceManager.getService(namespaceId, serviceName);
        
        serviceManager.checkServiceIsNull(service, namespaceId, serviceName);
        
        return service.allIPs();
    }
    
    @Override
    public List<String> batchUpdateMetadata(String namespaceId, InstanceOperationInfo instanceOperationInfo,
            Map<String, String> metadata) {
        return batchOperate(namespaceId, instanceOperationInfo, metadata, UPDATE_INSTANCE_METADATA_ACTION_UPDATE);
    }
    
    @Override
    public List<String> batchDeleteMetadata(String namespaceId, InstanceOperationInfo instanceOperationInfo,
            Map<String, String> metadata) throws NacosException {
        return batchOperate(namespaceId, instanceOperationInfo, metadata, UPDATE_INSTANCE_METADATA_ACTION_REMOVE);
    }
    
    private List<String> batchOperate(String namespaceId, InstanceOperationInfo instanceOperationInfo,
            Map<String, String> metadata, String updateInstanceMetadataAction) {
        List<String> result = new LinkedList<>();
        for (com.alibaba.nacos.naming.core.Instance each : batchOperateMetadata(namespaceId, instanceOperationInfo,
                metadata, updateInstanceMetadataAction)) {
            result.add(each.getDatumKey() + ":" + (each.isEphemeral() ? EPHEMERAL : PERSIST));
        }
        return result;
    }
    
    private List<com.alibaba.nacos.naming.core.Instance> batchOperateMetadata(String namespace,
            InstanceOperationInfo instanceOperationInfo, Map<String, String> metadata, String action) {
        Function<InstanceOperationContext, List<com.alibaba.nacos.naming.core.Instance>> operateFunction = instanceOperationContext -> {
            try {
                return serviceManager.updateMetadata(instanceOperationContext.getNamespace(),
                        instanceOperationContext.getServiceName(), instanceOperationContext.getEphemeral(), action,
                        instanceOperationContext.getAll(), instanceOperationContext.getInstances(), metadata);
            } catch (NacosException e) {
                Loggers.SRV_LOG.warn("UPDATE-METADATA: updateMetadata failed", e);
            }
            return new ArrayList<>();
        };
        return serviceManager.batchOperate(namespace, instanceOperationInfo, operateFunction);
    }
}
