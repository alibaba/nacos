/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Client service.
 *
 * @author Nacos
 */

public interface ClientService {
    
    /**
     * Retrieves a list of all connected clients.
     *
     * @return A list of client identifiers.
     */
    List<String> getClientList();
    
    /**
     * Retrieves detailed information about a specific client.
     *
     * @param clientId The unique identifier of the client.
     * @return Detailed information about the client in JSON format.
     * @throws NacosApiException If an error occurs while retrieving the client information.
     */
    ClientSummaryInfo getClientDetail(String clientId) throws NacosApiException;
    
    /**
     * Retrieves a list of services published by a specific client. For 2.x http api.
     *
     * @param clientId The unique identifier of the client.
     * @return A list of published services in JSON format.
     * @deprecated with removing 2.x http api. use {@link #getPublishedServiceList(String)} replaced
     */
    @Deprecated
    List<ObjectNode> getPublishedServiceListAdapt(String clientId);
    
    /**
     * Retrieves a list of services published by a specific client. For 2.x http api.
     *
     * @param clientId The unique identifier of the client.
     * @return A list of {@link ClientServiceInfo} with {@link ClientPublisherInfo}
     */
    List<ClientServiceInfo> getPublishedServiceList(String clientId);
    
    /**
     * Retrieves a list of services subscribed to by a specific client. For 2.x http api.
     *
     * @param clientId The unique identifier of the client.
     * @return A list of subscribed services in JSON format.
     * @deprecated with removing 2.x http api. use {@link #getSubscribeServiceList(String)} replaced
     */
    @Deprecated
    List<ObjectNode> getSubscribeServiceListAdapt(String clientId);
    
    /**
     * Retrieves a list of services subscribed to by a specific client.
     *
     * @param clientId The unique identifier of the client.
     * @return A list of {@link ClientServiceInfo} with {@link ClientSubscriberInfo}
     */
    List<ClientServiceInfo> getSubscribeServiceList(String clientId);
    
    /**
     * Retrieves a list of clients that have published a specific service. For 2.x http api.
     *
     * @param namespaceId The namespace of the service.
     * @param groupName   The group name of the service.
     * @param serviceName The name of the service.
     * @param ephemeral   Whether the service is ephemeral (temporary).
     * @param ip          The IP address of the client (optional filter).
     * @param port        The port number of the client (optional filter).
     * @return A list of clients that published the service in JSON format.
     * @deprecated with removing 2.x http api. use {@link #getPublishedClientList(String, String, String, String, Integer)} replaced
     */
    @Deprecated
    List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            String ip, Integer port);
    
    /**
     * Retrieves a list of clients that have published a specific service.
     *
     * @param namespaceId The namespace of the service.
     * @param groupName   The group name of the service.
     * @param serviceName The name of the service.
     * @param ip          The IP address of the client (optional filter).
     * @param port        The port number of the client (optional filter).
     * @return A list of {@link ClientPublisherInfo} with clientId
     */
    List<ClientPublisherInfo> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            String ip, Integer port);
    
    /**
     * Retrieves a list of clients that have subscribed to a specific service. For 2.x http api.
     *
     * @param namespaceId The namespace of the service.
     * @param groupName   The group name of the service.
     * @param serviceName The name of the service.
     * @param ephemeral   Whether the service is ephemeral (temporary).
     * @param ip          The IP address of the client (optional filter).
     * @param port        The port number of the client (optional filter).
     * @return A list of clients that subscribed to the service in JSON format.
     * @deprecated @deprecated with removing 2.x http api. use {@link #getSubscribeClientList(String, String, String, String, Integer)} replaced
     */
    @Deprecated
    List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName, boolean ephemeral,
            String ip, Integer port);
    
    /**
     * Retrieves a list of clients that have subscribed to a specific service.
     *
     * @param namespaceId The namespace of the service.
     * @param groupName   The group name of the service.
     * @param serviceName The name of the service.
     * @param ip          The IP address of the client (optional filter).
     * @param port        The port number of the client (optional filter).
     * @return A list of {@link ClientSubscriberInfo} with clientId
     */
    List<ClientSubscriberInfo> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            String ip, Integer port);
    
    /**
     * Determines the responsible server for handling requests from a specific client based on its IP and port.
     *
     * @param ip   The IP address of the client.
     * @param port The port number of the client.
     * @return The responsible server information in JSON format.
     */
    ObjectNode getResponsibleServer4Client(String ip, String port);
}