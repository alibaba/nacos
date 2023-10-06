/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.monitor.naming;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.remote.gprc.redo.data.InstanceRedoData;
import com.alibaba.nacos.common.lifecycle.Closeable;

import java.util.List;

/**
 * {@link com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService} interface for dynamic proxy to trace
 * the NamingGrpcRedoService by OpenTelemetry.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public interface NamingGrpcRedoServiceTraceProxy extends Closeable {
    
    /**
     * Cache registered instance for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instance    registered instance
     */
    void cacheInstanceForRedo(String serviceName, String groupName, Instance instance);
    
    /**
     * Cache registered instance for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   batch registered instance
     */
    void cacheInstanceForRedo(String serviceName, String groupName, List<Instance> instances);
    
    /**
     * Instance register successfully, mark registered status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     */
    void instanceRegistered(String serviceName, String groupName);
    
    /**
     * Instance deregister, mark unregistering status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     */
    void instanceDeregister(String serviceName, String groupName);
    
    /**
     * Instance deregister finished, mark unregistered status.
     *
     * @param serviceName service name
     * @param groupName   group name
     */
    void instanceDeregistered(String serviceName, String groupName);
    
    /**
     * Cache subscriber for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    void cacheSubscriberForRedo(String serviceName, String groupName, String cluster);
    
    /**
     * Subscriber register successfully, mark registered status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    void subscriberRegistered(String serviceName, String groupName, String cluster);
    
    /**
     * Subscriber deregister, mark unregistering status as {@code true}.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    void subscriberDeregister(String serviceName, String groupName, String cluster);
    
    /**
     * Judge subscriber has registered to server.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     * @return {@code true} if subscribed, otherwise {@code false}
     */
    boolean isSubscriberRegistered(String serviceName, String groupName, String cluster);
    
    /**
     * Remove subscriber for redo.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param cluster     cluster
     */
    void removeSubscriberForRedo(String serviceName, String groupName, String cluster);
    
    /**
     * get Cache service.
     *
     * @return cache service
     */
    InstanceRedoData getRegisteredInstancesByKey(String combinedServiceName);
    
    /**
     * get service namespace.
     *
     * @return namespace
     */
    String getNamespace();
}
