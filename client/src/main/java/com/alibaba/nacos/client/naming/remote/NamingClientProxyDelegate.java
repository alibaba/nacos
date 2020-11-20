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

package com.alibaba.nacos.client.naming.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.naming.core.ServiceInfoUpdateService;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;

import java.util.Properties;
import java.util.Set;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Delegate of naming client proxy.
 *
 * @author xiweng.yy
 */
public class NamingClientProxyDelegate implements NamingClientProxy {
    
    private final ServerListManager serverListManager;
    
    private final ServiceInfoUpdateService serviceInfoUpdateService;
    
    private final ServiceInfoHolder serviceInfoHolder;
    
    private final NamingHttpClientProxy httpClientProxy;
    
    private final NamingGrpcClientProxy grpcClientProxy;
    
    public NamingClientProxyDelegate(String namespace, ServiceInfoHolder serviceInfoHolder, Properties properties,
            InstancesChangeNotifier changeNotifier) throws NacosException {
        this.serviceInfoUpdateService = new ServiceInfoUpdateService(properties, serviceInfoHolder, this,
                changeNotifier);
        this.serverListManager = new ServerListManager(properties);
        this.serviceInfoHolder = serviceInfoHolder;
        this.httpClientProxy = new NamingHttpClientProxy(namespace, serverListManager, properties, serviceInfoHolder);
        this.grpcClientProxy = new NamingGrpcClientProxy(namespace, serverListManager, properties, serviceInfoHolder);
    }
    
    @Override
    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        getExecuteClientProxy().registerService(serviceName, groupName, instance);
    }
    
    @Override
    public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        getExecuteClientProxy().deregisterService(serviceName, groupName, instance);
    }
    
    @Override
    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
    
    }
    
    @Override
    public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort,
            boolean healthyOnly) throws NacosException {
        return getExecuteClientProxy().queryInstancesOfService(serviceName, groupName, clusters, udpPort, healthyOnly);
    }
    
    @Override
    public Service queryService(String serviceName, String groupName) throws NacosException {
        return null;
    }
    
    @Override
    public void createService(Service service, AbstractSelector selector) throws NacosException {
    
    }
    
    @Override
    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        return false;
    }
    
    @Override
    public void updateService(Service service, AbstractSelector selector) throws NacosException {
    
    }
    
    @Override
    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException {
        return getExecuteClientProxy().getServiceList(pageNo, pageSize, groupName, selector);
    }
    
    @Override
    public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
        String serviceNameWithGroup = NamingUtils.getGroupedName(serviceName, groupName);
        String serviceKey = ServiceInfo.getKey(serviceNameWithGroup, clusters);
        ServiceInfo result = serviceInfoHolder.getServiceInfoMap().get(serviceKey);
        if (null == result) {
            result = getExecuteClientProxy().subscribe(serviceName, groupName, clusters);
        }
        serviceInfoUpdateService.scheduleUpdateIfAbsent(serviceName, groupName, clusters);
        serviceInfoHolder.processServiceInfo(result);
        return result;
    }
    
    @Override
    public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
        serviceInfoUpdateService.stopUpdateIfContain(serviceName, groupName, clusters);
        getExecuteClientProxy().unsubscribe(serviceName, groupName, clusters);
    }
    
    @Override
    public void updateBeatInfo(Set<Instance> modifiedInstances) {
        httpClientProxy.updateBeatInfo(modifiedInstances);
    }
    
    @Override
    public boolean serverHealthy() {
        return grpcClientProxy.serverHealthy() || httpClientProxy.serverHealthy();
    }
    
    private NamingClientProxy getExecuteClientProxy() {
        return grpcClientProxy;
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        serviceInfoUpdateService.shutdown();
        serverListManager.shutdown();
        httpClientProxy.shutdown();
        grpcClientProxy.shutdown();
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }
}
