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
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.common.lifecycle.Closeable;

import java.util.Set;

/**
 * Naming Client Proxy.
 *
 * @author xiweng.yy
 */
public interface NamingClientProxy extends Closeable {
    
    /**
     * Register a instance to service with specified instance properties.
     *
     * @param serviceName name of service
     * @param groupName   group of service
     * @param instance    instance to register
     * @throws NacosException nacos exception
     */
    void registerService(String serviceName, String groupName, Instance instance) throws NacosException;
    
    /**
     * Deregister instance from a service.
     *
     * @param serviceName name of service
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException;
    
    /**
     * Update instance to service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException;
    
    /**
     * Query instance list.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters
     * @param udpPort     udp port
     * @param healthyOnly healthy only
     * @return service info
     * @throws NacosException nacos exception
     */
    ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort, boolean healthyOnly)
            throws NacosException;
    
    /**
     * Query Service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @return service
     * @throws NacosException nacos exception
     */
    Service queryService(String serviceName, String groupName) throws NacosException;
    
    /**
     * Create service.
     *
     * @param service  service
     * @param selector selector
     * @throws NacosException nacos exception
     */
    void createService(Service service, AbstractSelector selector) throws NacosException;
    
    /**
     * Delete service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @return true if delete ok
     * @throws NacosException nacos exception
     */
    boolean deleteService(String serviceName, String groupName) throws NacosException;
    
    /**
     * Update service.
     *
     * @param service  service
     * @param selector selector
     * @throws NacosException nacos exception
     */
    void updateService(Service service, AbstractSelector selector) throws NacosException;
    
    /**
     * Get service list.
     *
     * @param pageNo    page number
     * @param pageSize  size per page
     * @param groupName group name of service
     * @param selector  selector
     * @return list of service
     * @throws NacosException nacos exception
     */
    ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
            throws NacosException;
    
    /**
     * Subscribe service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @return current service info of subscribe service
     * @throws NacosException nacos exception
     */
    ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException;
    
    /**
     * Unsubscribe service.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param clusters    clusters, current only support subscribe all clusters, maybe deprecated
     * @throws NacosException nacos exception
     */
    void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException;
    
    /**
     * Update beat info.
     *
     * @param modifiedInstances modified instances
     */
    void updateBeatInfo(Set<Instance> modifiedInstances);
    
    /**
     * Check Server healthy.
     *
     * @return true if server is healthy
     */
    boolean serverHealthy();
}
