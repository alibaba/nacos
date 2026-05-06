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
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;

import java.util.List;
import java.util.Map;

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
    
    /**
     * De-register an exist instance from target service with default namespace id and default group name.
     *
     * @param serviceName the service name
     * @param ip          the IP address of the de-registered
     * @param port        the port of the de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String serviceName, String ip, int port) throws NacosException {
        return deregisterInstance(Constants.DEFAULT_GROUP, serviceName, ip, port);
    }
    
    /**
     * De-register an exist instance from target service with default namespace id.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the de-registered
     * @param port        the port of the de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String groupName, String serviceName, String ip, int port) throws NacosException {
        return deregisterInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, ip, port);
    }
    
    /**
     * De-register an exist instance from target service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the de-registered
     * @param port        the port of the de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String namespaceId, String groupName, String serviceName, String ip, int port)
            throws NacosException {
        return deregisterInstance(namespaceId, groupName, serviceName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    /**
     * De-register an exist instance from target service with default namespace id and default group name.
     *
     * @param serviceName the service name
     * @param ip          the IP address of the de-registered
     * @param port        the port of the de-registered
     * @param clusterName the cluster name of de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String serviceName, String ip, int port, String clusterName)
            throws NacosException {
        return deregisterInstance(Constants.DEFAULT_GROUP, serviceName, ip, port, clusterName);
    }
    
    /**
     * De-register an exist instance from target service with default namespace id.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the de-registered
     * @param port        the port of the de-registered
     * @param clusterName the cluster name of de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String groupName, String serviceName, String ip, int port, String clusterName)
            throws NacosException {
        return deregisterInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, ip, port, clusterName);
    }
    
    /**
     * De-register an exist instance from target service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the de-registered
     * @param port        the port of the de-registered
     * @param clusterName the cluster name of de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String namespaceId, String groupName, String serviceName, String ip, int port,
            String clusterName) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setClusterName(clusterName);
        instance.setEphemeral(false);
        return deregisterInstance(namespaceId, groupName, serviceName, instance);
    }
    
    /**
     * De-register an exist instance from target service with default namespace id and default group name.
     *
     * @param serviceName   the service name
     * @param instance      instance to be de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String serviceName, Instance instance) throws NacosException {
        return deregisterInstance(Constants.DEFAULT_GROUP, serviceName, instance);
    }
    
    /**
     * De-register an exist instance from target service with default namespace id.
     *
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param instance      instance to be de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String groupName, String serviceName, Instance instance) throws NacosException {
        return deregisterInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, instance);
    }
    
    /**
     * De-register an exist instance from target service.
     *
     * @param namespaceId   the namespace ID
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param instance      instance to be de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String deregisterInstance(String namespaceId, String groupName, String serviceName, Instance instance)
            throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(instance.isEphemeral());
        return deregisterInstance(service, instance);
    }
    
    /**
     * De-register an exist instance from target service.
     *
     * <p>
     *     the ephemeral parameter in {@link Instance} is higher than {@link Service},
     *     if conflict, the actual value is {@link Instance#isEphemeral()}
     * </p>
     * <p>
     *     if {@link Instance#isEphemeral()} is true, the instance may deregister failed due to the nacos-client keep the connection.
     * </p>
     *
     * @param service   target service to de-register instance
     * @param instance  instance to be de-registered
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String deregisterInstance(Service service, Instance instance) throws NacosException;
    
    /**
     * Update an exist instance in target service with default namespace id and default group name.
     *
     * @param serviceName   the service name
     * @param instance      instance to be updated
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateInstance(String serviceName, Instance instance) throws NacosException {
        return updateInstance(Constants.DEFAULT_GROUP, serviceName, instance);
    }
    
    /**
     * Update an exist instance in target service with default namespace id.
     *
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param instance      instance to be updated
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateInstance(String groupName, String serviceName, Instance instance) throws NacosException {
        return updateInstance(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, instance);
    }
    
    /**
     * Update an exist instance in target service.
     *
     * @param namespaceId   the namespace ID
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param instance      instance to be updated
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default String updateInstance(String namespaceId, String groupName, String serviceName, Instance instance)
            throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(instance.isEphemeral());
        return updateInstance(service, instance);
    }
    
    /**
     * Update an exist instance in target service.
     *
     * <p>
     *     the ephemeral parameter in {@link Instance} is higher than {@link Service},
     *     if conflict, the actual value is {@link Instance#isEphemeral()}
     * </p>
     * <p>
     *     {@link Instance#getIp()}, {@link Instance#getPort()}, {@link Instance#getClusterName()} and {@link Instance#isEphemeral()}
     *     can't be updated, these four parameters are used to locate which instance will be updated.
     *     {@link Instance#isHealthy()} will be changed immediately, but it will be changed after the next health check.
     *     other parameters like {@link Instance#getWeight()}, {@link Instance#getMetadata()} and {@link Instance#isEnabled()} can be changed
     *     and has higher priority than nacos-client registered metadata.
     *     e.g.
     *     nacos-client registered metadata with k1=v1, and updateInstance with k1=v2, the actual value is v2;
     *     then nacos-client registered metadata with k1=v3, the actual value is still v2.
     * </p>
     * <p>
     *     The metadata be update will keep in server for a while after instance has been de-registered,
     *     if redo the register for same instance during the keep time, the metadata will be re-used.
     * </p>
     * <p>
     *     The metadata be update will full replace the metadata in server.
     *     e.g.
     *     The first time update metadata with k1=v1, the second time update metadata with k2=v2, the actual value is k2=v2 and no k1;
     * </p>
     *
     * @param service   target service to update instance
     * @param instance  instance to be updated
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String updateInstance(Service service, Instance instance) throws NacosException;
    
    /**
     * Batch update all instances metadata in target service.
     *
     * <p>
     *     the ephemeral parameter in {@link Instance} is higher than {@link Service},
     *     if conflict, the actual value is {@link Instance#isEphemeral()}
     *     {@link Instance#isEphemeral()} will use the first one instance in {@param instances}
     * </p>
     * <p>
     *     The metadata be update will full replace the metadata in {@param instances}.
     *     e.g.
     *     The first time update metadata with k1=v1, the second time update metadata with k2=v2, the actual value is k2=v2 and no k1;
     * </p>
     *
     * @param service       target service to update instance
     * @param instances     all instances to be updated, {@link Instance#getIp()}, {@link Instance#getPort()}
     *                      and {@link Instance#getClusterName()} is working.
     * @param newMetadata   the new metadata to be updated
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    InstanceMetadataBatchResult batchUpdateInstanceMetadata(Service service, List<Instance> instances,
            Map<String, String> newMetadata) throws NacosException;
    
    /**
     * Batch remove all instances metadata in target service.
     *
     * <p>
     *     the ephemeral parameter in {@link Instance} is higher than {@link Service},
     *     if conflict, the actual value is {@link Instance#isEphemeral()}
     *     {@link Instance#isEphemeral()} will use the first one instance in {@param instances}
     * </p>
     * <p>
     *     The metadata be removed will remove all keys in the {@param newMetadata} for {@param instances}.
     *     e.g.
     *     The metadata is k1=v1, k2=v2, k3=v3, the newMetadata is k1=v1, k2=v2, the actual value k3=v3;
     * </p>
     *
     * @param service       target service to removed instance
     * @param instances     all instances to be removed, {@link Instance#getIp()}, {@link Instance#getPort()}
     *                      and {@link Instance#getClusterName()} is working.
     * @param newMetadata   the new metadata to be removed
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    InstanceMetadataBatchResult batchDeleteInstanceMetadata(Service service, List<Instance> instances,
            Map<String, String> newMetadata) throws NacosException;
    
    /**
     * Partially update an instance in target service.
     *
     * <p>
     *     Different with {@link #updateInstance(Service, Instance)}, This method will only replace the information
     *     and metadata key-value in {@link Instance#getMetadata()}, other information will not be changed.
     *     e.g.
     *     The current instance metadata is k1=v1, k2=v2, k3=v3, the new metadata in {@param instance} is k1=v11, k2=v22,
     *     the actual value is k1=v11, k2=v22, k3=v3;
     *     But for {@link #updateInstance(Service, Instance)} the result is k1=v11, k2=v22, no k3.
     * </p>
     *
     * @param service   target service to update instance
     * @param instance  instance to be updated
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    String partialUpdateInstance(Service service, Instance instance) throws NacosException;
    
    /**
     * List all instances in target service with default namespace id and default group name.
     *
     * @param serviceName   the service name
     * @param clusterName   cluster name of instances, default is {@link Constants#DEFAULT_CLUSTER_NAME}
     * @param healthyOnly   {@code true} if only return healthy instances, {@code false} if return all instances
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default List<Instance> listInstances(String serviceName, String clusterName, boolean healthyOnly)
            throws NacosException {
        return listInstances(Constants.DEFAULT_GROUP, serviceName, clusterName, healthyOnly);
    }
    
    /**
     * List all instances in target service with default namespace id.
     *
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param clusterName   cluster name of instances, default is {@link Constants#DEFAULT_CLUSTER_NAME}
     * @param healthyOnly   {@code true} if only return healthy instances, {@code false} if return all instances
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default List<Instance> listInstances(String groupName, String serviceName, String clusterName, boolean healthyOnly)
            throws NacosException {
        return listInstances(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, clusterName, healthyOnly);
    }
    
    /**
     * List all instances in target service.
     *
     * @param namespaceId   the namespace ID
     * @param groupName     the group name
     * @param serviceName   the service name
     * @param clusterName   cluster name of instances, default is {@link Constants#DEFAULT_CLUSTER_NAME}
     * @param healthyOnly   {@code true} if only return healthy instances, {@code false} if return all instances
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    default List<Instance> listInstances(String namespaceId, String groupName, String serviceName, String clusterName,
            boolean healthyOnly) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        return listInstances(service, clusterName, healthyOnly);
    }
    
    /**
     * List all instances in target service.
     *
     * @param service       target service
     * @param clusterName   cluster name of instances, default is {@link Constants#DEFAULT_CLUSTER_NAME}
     * @param healthyOnly   {@code true} if only return healthy instances, {@code false} if return all instances
     * @return the result of the operation
     * @throws NacosException if an error occurs
     */
    List<Instance> listInstances(Service service, String clusterName, boolean healthyOnly) throws NacosException;
    
    /**
     * Get detailed information of an instance.
     *
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    default Instance getInstanceDetail(String serviceName, String ip, int port) throws NacosException {
        return getInstanceDetail(Constants.DEFAULT_GROUP, serviceName, ip, port);
    }
    
    /**
     * Get detailed information of an instance.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    default Instance getInstanceDetail(String groupName, String serviceName, String ip, int port)
            throws NacosException {
        return getInstanceDetail(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, ip, port);
    }
    
    /**
     * Get detailed information of an instance.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    default Instance getInstanceDetail(String namespaceId, String groupName, String serviceName, String ip, int port)
            throws NacosException {
        return getInstanceDetail(namespaceId, groupName, serviceName, ip, port, Constants.DEFAULT_CLUSTER_NAME);
    }
    
    /**
     * Get detailed information of an instance.
     *
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param clusterName the cluster name
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    default Instance getInstanceDetail(String serviceName, String ip, int port, String clusterName)
            throws NacosException {
        return getInstanceDetail(Constants.DEFAULT_GROUP, serviceName, ip, port, clusterName);
    }
    
    /**
     * Get detailed information of an instance.
     *
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param clusterName the cluster name
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    default Instance getInstanceDetail(String groupName, String serviceName, String ip, int port, String clusterName)
            throws NacosException {
        return getInstanceDetail(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, ip, port, clusterName);
    }
    
    /**
     * Get detailed information of an instance.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the instance
     * @param port        the port of the instance
     * @param clusterName the cluster name
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    default Instance getInstanceDetail(String namespaceId, String groupName, String serviceName, String ip, int port,
            String clusterName) throws NacosException {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setClusterName(clusterName);
        return getInstanceDetail(service, instance);
    }
    
    /**
     * Get detailed information of an instance.
     *
     * @param service   target service
     * @param instance  instance basic information, {@link Instance#getIp()}, {@link Instance#getPort()} is required
     *                  and {@link Instance#getClusterName()} is optional. Others useless.
     * @return the instance detail information
     * @throws NacosException if an error occurs
     */
    Instance getInstanceDetail(Service service, Instance instance) throws NacosException;
}
