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

import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Client controller.
 *
 * @author Nacos
 */

@Component
public class ClientServiceV2Impl implements ClientService {
    
    @Resource
    private ClientManager clientManager;
    
    @Resource
    private ConnectionManager connectionManager;
    
    @Resource
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    @Resource
    private DistroMapper distroMapper;
    
    @Override
    public List<String> getClientList() {
        return new ArrayList<>(clientManager.allClientId());
    }
    
    @Override
    public ObjectNode getClientDetail(String clientId) {
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
        }
        
        return result;
    }
    
    @Override
    public List<ObjectNode> getPublishedServiceList(String clientId) {
        Client client = clientManager.getClient(clientId);
        Collection<Service> allPublishedService = client.getAllPublishedService();
        List<ObjectNode> res = new ArrayList<>();
        for (Service service : allPublishedService) {
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(service);
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                List<InstancePublishInfo> instancePublishInfos = ((BatchInstancePublishInfo) instancePublishInfo).getInstancePublishInfos();
                for (InstancePublishInfo publishInfo : instancePublishInfos) {
                    res.add(wrapSingleInstanceNode(publishInfo, service));
                }
            } else {
                res.add(wrapSingleInstanceNode(instancePublishInfo, service));
            }
        }
        
        return res;
    }
    
    private ObjectNode wrapSingleInstanceNode(InstancePublishInfo instancePublishInfo, Service service) {
        ObjectNode item = JacksonUtils.createEmptyJsonNode();
        item.put("namespace", service.getNamespace());
        item.put("group", service.getGroup());
        item.put("serviceName", service.getName());
        item.set("registeredInstance", wrapSingleInstance(instancePublishInfo));
        return item;
    }
    
    private ObjectNode wrapSingleInstance(InstancePublishInfo instancePublishInfo) {
        ObjectNode instanceInfo = JacksonUtils.createEmptyJsonNode();
        instanceInfo.put("ip", instancePublishInfo.getIp());
        instanceInfo.put("port", instancePublishInfo.getPort());
        instanceInfo.put("cluster", instancePublishInfo.getCluster());
        return instanceInfo;
    }
    
    @Override
    public List<ObjectNode> getSubscribeServiceList(String clientId) {
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
        
        return res;
    }
    
    @Override
    public List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName, ephemeral);
        Collection<String> allClientsRegisteredService = clientServiceIndexesManager.getAllClientsRegisteredService(service);
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (String clientId : allClientsRegisteredService) {
            Client client = clientManager.getClient(clientId);
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(service);
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                List<InstancePublishInfo> list = ((BatchInstancePublishInfo) instancePublishInfo).getInstancePublishInfos();
                for (InstancePublishInfo info : list) {
                    if (!Objects.equals(info.getIp(), ip) || !Objects
                            .equals(port, info.getPort())) {
                        continue;
                    }
                    res.add(wrapSingleInstance(info).put("clientId", clientId));
                }
            } else {
                if (!Objects.equals(instancePublishInfo.getIp(), ip) || !Objects
                        .equals(port, instancePublishInfo.getPort())) {
                    continue;
                }
                res.add(wrapSingleInstance(instancePublishInfo).put("clientId", clientId));
            }
        }
        
        return res;
    }
    
    @Override
    public List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName, ephemeral);
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager
                .getAllClientsSubscribeService(service);
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (String clientId : allClientsSubscribeService) {
            Client client = clientManager.getClient(clientId);
            Subscriber subscriber = client.getSubscriber(service);
            boolean ipMatch = Objects.isNull(ip) || Objects.equals(ip, subscriber.getIp());
            boolean portMatch = Objects.isNull(port) || Objects.equals(port, subscriber.getPort());
            if (!ipMatch || !portMatch) {
                continue;
            }
            ObjectNode item = JacksonUtils.createEmptyJsonNode();
            item.put("clientId", clientId);
            item.put("ip", subscriber.getIp());
            item.put("port", subscriber.getPort());
            res.add(item);
        }
        
        return res;
    }
    
    @Override
    public ObjectNode getResponsibleServer4Client(String ip, String port) {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        String tag = ip + InternetAddressUtil.IP_PORT_SPLITER + port;
        result.put("responsibleServer", distroMapper.mapSrv(tag));
        
        return result;
    }
}