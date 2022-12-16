/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * ClientInfoControllerV2.
 *
 * @author dongyafei
 * @date 2022/9/20
 */

@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_CLIENT_CONTEXT)
public class ClientInfoControllerV2 {
    
    private final ClientManager clientManager;
    
    private final ConnectionManager connectionManager;
    
    private final ClientServiceIndexesManager clientServiceIndexesManager;
    
    public ClientInfoControllerV2(ClientManager clientManager, ConnectionManager connectionManager,
            ClientServiceIndexesManager clientServiceIndexesManager) {
        this.clientManager = clientManager;
        this.connectionManager = connectionManager;
        this.clientServiceIndexesManager = clientServiceIndexesManager;
    }
    
    /**
     * Query all clients.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin")
    public Result<List<String>> getClientList() {
        return Result.success(new ArrayList<>(clientManager.allClientId()));
    }
    
    /**
     * Query client by clientId.
     *
     * @param clientId clientId
     */
    @GetMapping()
    @Secured(action = ActionTypes.READ, resource = "nacos/admin")
    public Result<ObjectNode> getClientDetail(@RequestParam("clientId") String clientId) throws NacosApiException {
        checkClientId(clientId);
        Client client = clientManager.getClient(clientId);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("clientId", client.getClientId());
        result.put("ephemeral", client.isEphemeral());
        result.put("lastUpdatedTime", client.getLastUpdatedTime());
        
        if (client instanceof ConnectionBasedClient) {
            // 2.x client
            result.put("clientType", "connection");
            Connection connection = connectionManager.getConnection(clientId);
            ConnectionMeta connectionMetaInfo = connection.getMetaInfo();
            result.put("connectType", connectionMetaInfo.getConnectType());
            result.put("appName", connectionMetaInfo.getAppName());
            result.put("version", connectionMetaInfo.getVersion());
            result.put("clientIp", connectionMetaInfo.getClientIp());
            result.put("clientPort", clientId.substring(clientId.lastIndexOf('_') + 1));
        } else if (client instanceof IpPortBasedClient) {
            // 1.x client
            result.put("clientType", "ipPort");
            IpPortBasedClient ipPortBasedClient = (IpPortBasedClient) client;
            String responsibleId = ipPortBasedClient.getResponsibleId();
            int idx = responsibleId.lastIndexOf(':');
            result.put("clientIp", responsibleId.substring(0, idx));
            result.put("clientPort", responsibleId.substring(idx + 1));
        }
        return Result.success(result);
    }
    
    /**
     * Query the services registered by the specified client.
     *
     * @param clientId clientId
     */
    @GetMapping("/publish/list")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin")
    public Result<List<ObjectNode>> getPublishedServiceList(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        Client client = clientManager.getClient(clientId);
        Collection<Service> allPublishedService = client.getAllPublishedService();
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (Service service : allPublishedService) {
            ObjectNode item = JacksonUtils.createEmptyJsonNode();
            item.put("namespace", service.getNamespace());
            item.put("group", service.getGroup());
            item.put("serviceName", service.getName());
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(service);
            ObjectNode instanceInfo = JacksonUtils.createEmptyJsonNode();
            instanceInfo.put("ip", instancePublishInfo.getIp());
            instanceInfo.put("port", instancePublishInfo.getPort());
            instanceInfo.put("cluster", instancePublishInfo.getCluster());
            item.set("registeredInstance", instanceInfo);
            res.add(item);
        }
        return Result.success(res);
    }
    
    /**
     * Query the services to which the specified client subscribes.
     *
     * @param clientId clientId.
     */
    @GetMapping("/subscribe/list")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin")
    public Result<List<ObjectNode>> getSubscribeServiceList(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        Client client = clientManager.getClient(clientId);
        Collection<Service> allSubscribeService = client.getAllSubscribeService();
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (Service service : allSubscribeService) {
            ObjectNode item = JacksonUtils.createEmptyJsonNode();
            item.put("namespace", service.getNamespace());
            item.put("group", service.getGroup());
            item.put("serviceName", service.getName());
            Subscriber subscriber = client.getSubscriber(service);
            ObjectNode subscriberInfo = JacksonUtils.createEmptyJsonNode();
            subscriberInfo.put("app", subscriber.getApp());
            subscriberInfo.put("agent", subscriber.getAgent());
            subscriberInfo.put("addr", subscriber.getAddrStr());
            item.set("subscriberInfo", subscriberInfo);
            res.add(item);
        }
        return Result.success(res);
    }
    
    /**
     * Query the clients that have registered the specified service.
     *
     * @param namespaceId namespaceId
     * @param groupName   groupName
     * @param ephemeral   ephemeral
     * @param serviceName serviceName
     * @param ip          ip
     * @param port        port
     * @return client info
     */
    @GetMapping("/service/publisher/list")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin")
    public Result<List<ObjectNode>> getPublishedClientList(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", required = false, defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(value = "ephemeral", required = false, defaultValue = "true") Boolean ephemeral,
            @RequestParam("serviceName") String serviceName, @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "port", required = false) Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName, ephemeral);
        Collection<String> allClientsRegisteredService = clientServiceIndexesManager
                .getAllClientsRegisteredService(service);
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (String clientId : allClientsRegisteredService) {
            Client client = clientManager.getClient(clientId);
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(service);
            if (!Objects.equals(instancePublishInfo.getIp(), ip) || !Objects
                    .equals(port, instancePublishInfo.getPort())) {
                continue;
            }
            ObjectNode item = JacksonUtils.createEmptyJsonNode();
            item.put("clientId", clientId);
            item.put("ip", instancePublishInfo.getIp());
            item.put("port", instancePublishInfo.getPort());
            res.add(item);
        }
        return Result.success(res);
    }
    
    /**
     * Query the clients that are subscribed to the specified service.
     *
     * @param namespaceId namespaceId
     * @param groupName   groupName
     * @param ephemeral   ephemeral
     * @param serviceName serviceName
     * @param ip          ip
     * @param port        port
     * @return client info
     */
    @GetMapping("/service/subscriber/list")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin")
    public Result<List<ObjectNode>> getSubscribeClientList(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", required = false, defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(value = "ephemeral", required = false, defaultValue = "true") Boolean ephemeral,
            @RequestParam("serviceName") String serviceName, @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "port", required = false) Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName, ephemeral);
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsSubscribeService(service);
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (String clientId : allClientsSubscribeService) {
            Client client = clientManager.getClient(clientId);
            Subscriber subscriber = client.getSubscriber(service);
            if (!Objects.equals(subscriber.getIp(), ip) || !Objects.equals(port, subscriber.getPort())) {
                continue;
            }
            ObjectNode item = JacksonUtils.createEmptyJsonNode();
            item.put("clientId", clientId);
            item.put("ip", subscriber.getIp());
            item.put("port", subscriber.getPort());
            res.add(item);
        }
        return Result.success(res);
    }
    
    private void checkClientId(String clientId) throws NacosApiException {
        if (!clientManager.contains(clientId)) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "clientId [ " + clientId + " ] not exist");
        }
    }
}
