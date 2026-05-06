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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;

import java.util.List;

/**
 * Nacos naming module client information maintainer API.
 *
 * @author xiweng.yy
 */
public interface NamingClientMaintainerService {
    
    /**
     * Get the list of all clients.
     *
     * @return the list of client IDs
     * @throws NacosException if an error occurs
     */
    List<String> getClientList() throws NacosException;
    
    /**
     * Get detailed information of a client.
     *
     * @param clientId the client ID
     * @return the client detail information
     * @throws NacosException if an error occurs
     */
    ClientSummaryInfo getClientDetail(String clientId) throws NacosException;
    
    /**
     * Get the list of services published by a client.
     *
     * @param clientId the client ID
     * @return the list of published services
     * @throws NacosException if an error occurs
     */
    List<ClientServiceInfo> getPublishedServiceList(String clientId) throws NacosException;
    
    /**
     * Get the list of services subscribed by a client.
     *
     * @param clientId the client ID
     * @return the list of subscribed services
     * @throws NacosException if an error occurs
     */
    List<ClientServiceInfo> getSubscribeServiceList(String clientId) throws NacosException;
    
    /**
     * Get the list of clients that published a specific service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the client
     * @param port        the port of the client
     * @return the list of clients
     * @throws NacosException if an error occurs
     */
    List<ClientPublisherInfo> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            String ip, Integer port) throws NacosException;
    
    /**
     * Get the list of clients that subscribed to a specific service.
     *
     * @param namespaceId the namespace ID
     * @param groupName   the group name
     * @param serviceName the service name
     * @param ip          the IP address of the client
     * @param port        the port of the client
     * @return the list of clients
     * @throws NacosException if an error occurs
     */
    List<ClientSubscriberInfo> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            String ip, Integer port) throws NacosException;
    
}
