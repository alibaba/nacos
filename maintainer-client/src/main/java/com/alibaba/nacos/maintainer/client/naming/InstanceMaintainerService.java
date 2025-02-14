/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;

/**
 * Nacos naming module instance maintainer API.
 *
 * @author xiweng.yy
 */
public interface InstanceMaintainerService {
    
    /**
     * Register a new persistent default cluster instance to target service with default namespace id and default group name.
     *
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String serviceName, String ip, int port) throws NacosException {
        return registerInstance(Constants.DEFAULT_GROUP, serviceName, ip, port);
    }
    
    /**
     * Register a new persistent default cluster instance to target service with default namespace id.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String groupName, String serviceName, String ip, int port) throws NacosException {
        return registerInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, ip, port);
    }
    
    /**
     * Register a new persistent default cluster instance to target service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String namespaceId, String groupName, String serviceName, String ip, int port)
            throws NacosException {
        return registerInstance(namespaceId, groupName, serviceName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    /**
     * Register a new persistent instance to target service with default namespace id and default group name.
     *
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param clusterName the cluster name of instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        return registerInstance(Constants.DEFAULT_GROUP, serviceName, ip, port, clusterName);
    }
    
    /**
     * Register a new persistent instance to target service with default namespace id.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param clusterName the cluster name of instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String groupName, String serviceName, String ip, int port, String clusterName)
            throws NacosException {
        return registerInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, ip, port, clusterName);
    }
    
    /**
     * Register a new persistent instance to target service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param clusterName the cluster name of instance
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String namespaceId, String groupName, String serviceName, String ip, int port,
            String clusterName) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setClusterName(clusterName);
        instance.setEphemeral(false);
        return registerInstance(namespaceId, groupName, serviceName, instance);
    }
    
    /**
     * Register a new instance to target service with default namespace id and default group name.
     *
     * @param serviceName   the service name
     * @param instance      instance to be registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String serviceName, Instance instance) throws NacosException {
        return registerInstance(Constants.DEFAULT_GROUP, serviceName, instance);
    }
    
    /**
     * Register a new instance to target service with default namespace id.
     *
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param instance      instance to be registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String groupName, String serviceName, Instance instance) throws NacosException {
        return registerInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, instance);
    }
    
    /**
     * Register a new instance to target service.
     *
     * @param namespaceId   the namespace ID
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param instance      instance to be registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String registerInstance(String namespaceId, String groupName, String serviceName, Instance instance)
            throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(instance.isEphemeral());
        return registerInstance(service, instance);
    }
    
    /**
     * Register a new instance to target service.
     *
     * <p>
     *     the ephemeral parameter in {@link Instance} is higher than {@link Service},
     *     if conflict, the actual value is {@link Instance#isEphemeral()}
     * </p>
     *
     * @param service   target service to register instance
     * @param instance  instance to be registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String registerInstance(Service service, Instance instance) throws NacosException;
    
}
