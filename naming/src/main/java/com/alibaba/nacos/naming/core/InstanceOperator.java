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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.pojo.Subscriber;

/**
 * Instance operator.
 *
 * @author xiweng.yy
 */
public interface InstanceOperator {
    
    /**
     * Register an instance to a service in AP mode.
     *
     * @param namespaceId id of namespace
     * @param serviceName service name
     * @param instance    instance to register
     * @throws NacosException nacos exception when register failed
     */
    void registerInstance(String namespaceId, String serviceName, Instance instance) throws NacosException;
    
    /**
     * Remove instance from service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param instance    instance
     * @throws NacosException nacos exception when remove failed
     */
    void removeInstance(String namespaceId, String serviceName, Instance instance) throws NacosException;
    
    /**
     * Update instance information. Due to the basic information can't be changed, so this update should only update
     * metadata.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    instance
     * @throws NacosException nacos exception when update failed
     */
    void updateInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException;
    
    /**
     * Get all instance of input service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param subscriber  subscriber info
     * @param cluster     cluster of instances
     * @param healthOnly  whether only return health instances
     * @return service info
     * @throws Exception exception when list instance failed
     */
    ServiceInfo listInstance(String namespaceId, String serviceName, Subscriber subscriber, String cluster,
            boolean healthOnly) throws Exception;
    
    /**
     * Handle beat request.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param ip          ip of instance
     * @param port        port of instance
     * @param cluster     cluster of instance
     * @param clientBeat  client beat info
     * @return result code
     * @throws NacosException nacos exception when service non-exist and client beat info is null
     */
    int handleBeat(String namespaceId, String serviceName, String ip, int port, String cluster, RsInfo clientBeat)
            throws NacosException;
    
    /**
     * Get heart beat interval for specified instance.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param ip          ip of instance
     * @param port        port of instance
     * @param cluster     cluster of instance
     * @return heart beat interval
     */
    long getHeartBeatInterval(String namespaceId, String serviceName, String ip, int port, String cluster);
}
