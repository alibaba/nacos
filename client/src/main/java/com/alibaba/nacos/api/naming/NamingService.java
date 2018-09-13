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
package com.alibaba.nacos.api.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

import java.util.List;

/**
 * @author dungu.zpf
 */
public interface NamingService {

    /**
     * Register a instance to service
     *
     * @param serviceName name of service
     * @param ip          instance ip
     * @param port        instance port
     * @throws NacosException
     */
    void registerInstance(String serviceName, String ip, int port) throws NacosException;

    /**
     * Register a instance to service with specified cluster name
     *
     * @param serviceName name of service
     * @param ip          instance ip
     * @param port        instance port
     * @param clusterName instance cluster name
     * @throws NacosException
     */
    void registerInstance(String serviceName, String ip, int port, String clusterName) throws NacosException;

    /**
     * Register a instance to service with specified instance properties
     *
     * @param serviceName name of service
     * @param instance    instance to register
     * @throws NacosException
     */
    void registerInstance(String serviceName, Instance instance) throws NacosException;

    /**
     * Deregister instance from a service
     *
     * @param serviceName name of service
     * @param ip          instance ip
     * @param port        instance port
     * @throws NacosException
     */
    void deregisterInstance(String serviceName, String ip, int port) throws NacosException;

    /**
     * Deregister instance with specified cluster name from a service
     *
     * @param serviceName name of service
     * @param ip          instance ip
     * @param port        instance port
     * @param clusterName instance cluster name
     * @throws NacosException
     */
    void deregisterInstance(String serviceName, String ip, int port, String clusterName) throws NacosException;

    /**
     * Get all instances of a service
     *
     * @param serviceName name of service
     * @return A list of instance
     * @throws NacosException
     */
    List<Instance> getAllInstances(String serviceName) throws NacosException;

    /**
     * Get all instances within specified clusters of a service
     *
     * @param serviceName name of service
     * @param clusters    list of cluster
     * @return A list of qualified instance
     * @throws NacosException
     */
    List<Instance> getAllInstances(String serviceName, List<String> clusters) throws NacosException;

    /**
     * Get qualified instances of service
     *
     * @param serviceName name of service
     * @param healthy     a flag to indicate returning healthy or unhealthy instances
     * @return A qualified list of instance
     * @throws NacosException
     */
    List<Instance> selectInstances(String serviceName, boolean healthy) throws NacosException;

    /**
     * Get qualified instances within specified clusters of service
     *
     * @param serviceName name of service
     * @param clusters    list of cluster
     * @param healthy     a flag to indicate returning healthy or unhealthy instances
     * @return A qualified list of instance
     * @throws NacosException
     */
    List<Instance> selectInstances(String serviceName, List<String> clusters, boolean healthy) throws NacosException;

    /**
     * Select one healthy instance of service using predefined load balance strategy
     *
     * @param serviceName name of service
     * @return qualified instance
     * @throws NacosException
     */
    Instance selectOneHealthyInstance(String serviceName) throws NacosException;

    /**
     * Select one healthy instance of service using predefined load balance strategy
     *
     * @param serviceName name of service
     * @param clusters    a list of clusters should the instance belongs to
     * @return qualified instance
     * @throws NacosException
     */
    Instance selectOneHealthyInstance(String serviceName, List<String> clusters) throws NacosException;

    /**
     * Subscribe service to receive events of instances alteration
     *
     * @param serviceName name of service
     * @param listener    event listener
     * @throws NacosException
     */
    void subscribe(String serviceName, EventListener listener) throws NacosException;

    /**
     * Subscribe service to receive events of instances alteration
     *
     * @param serviceName name of service
     * @param clusters    list of cluster
     * @param listener    event listener
     * @throws NacosException
     */
    void subscribe(String serviceName, List<String> clusters, EventListener listener) throws NacosException;

    /**
     * Unsubscribe event listener of service
     *
     * @param serviceName name of service
     * @param listener    event listener
     * @throws NacosException
     */
    void unsubscribe(String serviceName, EventListener listener) throws NacosException;

    /**
     * Unsubscribe event listener of service
     *
     * @param serviceName name of service
     * @param clusters    list of cluster
     * @param listener    event listener
     * @throws NacosException
     */
    void unsubscribe(String serviceName, List<String> clusters, EventListener listener) throws NacosException;

    /**
     * Get all service names from server
     *
     * @param pageNo   page index
     * @param pageSize page size
     * @return list of service names
     * @throws NacosException
     */
    ListView<String> getServicesOfServer(int pageNo, int pageSize) throws NacosException;

    /**
     * Get all subscribed services of current client
     *
     * @return subscribed services
     * @throws NacosException
     */
    List<ServiceInfo> getSubscribeServices() throws NacosException;

    /**
     * Get server health status
     *
     * @return is server healthy
     */
    String getServerStatus();
}
