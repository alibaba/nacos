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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client controller.
 *
 * @author Nacos
 */

@Component
public class ClientServiceImpl implements ClientService {
    
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
    public ClientSummaryInfo getClientDetail(String clientId) throws NacosApiException {
        Client client = clientManager.getClient(clientId);
        if (null == client) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("Client id %s not exist.", clientId));
        }
        ClientSummaryInfo result = new ClientSummaryInfo();
        result.setClientId(client.getClientId());
        result.setEphemeral(client.isEphemeral());
        result.setLastUpdatedTime(client.getLastUpdatedTime());
        result.setClientType("ipPort");
        if (client instanceof ConnectionBasedClient) {
            // upper 2.x client
            result.setClientType("connection");
            Connection connection = connectionManager.getConnection(clientId);
            ConnectionMeta connectionMetaInfo = connection.getMetaInfo();
            result.setConnectType(connectionMetaInfo.getConnectType());
            result.setAppName(connectionMetaInfo.getAppName());
            result.setVersion(connectionMetaInfo.getVersion());
            result.setClientIp(connectionMetaInfo.getClientIp());
            result.setClientPort(connectionMetaInfo.getRemotePort());
        }
        return result;
    }
    
    @Override
    public List<ObjectNode> getPublishedServiceListAdapt(String clientId) {
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
    
    @Override
    public List<ClientServiceInfo> getPublishedServiceList(String clientId) {
        Client client = clientManager.getClient(clientId);
        Collection<Service> allPublishedService = client.getAllPublishedService();
        List<ClientServiceInfo> result = new LinkedList<>();
        for (Service each : allPublishedService) {
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(each);
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                for (InstancePublishInfo eachPub : ((BatchInstancePublishInfo) instancePublishInfo).getInstancePublishInfos()) {
                    result.add(buildClientServiceInfo(each, eachPub, null));
                }
            } else {
                result.add(buildClientServiceInfo(each, instancePublishInfo, null));
            }
        }
        return result;
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
    public List<ObjectNode> getSubscribeServiceListAdapt(String clientId) {
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
    public List<ClientServiceInfo> getSubscribeServiceList(String clientId) {
        Client client = clientManager.getClient(clientId);
        Collection<Service> allSubscribeService = client.getAllSubscribeService();
        List<ClientServiceInfo> result = new LinkedList<>();
        for (Service each : allSubscribeService) {
            Subscriber subscriber = client.getSubscriber(each);
            result.add(buildClientServiceInfo(each, null, subscriber));
        }
        return result;
    }
    
    @Override
    public List<ObjectNode> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName, ephemeral);
        Collection<String> allClientsRegisteredService = clientServiceIndexesManager.getAllClientsRegisteredService(
                service);
        ArrayList<ObjectNode> res = new ArrayList<>();
        for (String clientId : allClientsRegisteredService) {
            Client client = clientManager.getClient(clientId);
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(service);
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                List<InstancePublishInfo> list = ((BatchInstancePublishInfo) instancePublishInfo).getInstancePublishInfos();
                for (InstancePublishInfo info : list) {
                    if (!Objects.equals(info.getIp(), ip) || !Objects.equals(port, info.getPort())) {
                        continue;
                    }
                    res.add(wrapSingleInstance(info).put("clientId", clientId));
                }
            } else {
                if (!Objects.equals(instancePublishInfo.getIp(), ip) || !Objects.equals(port,
                        instancePublishInfo.getPort())) {
                    continue;
                }
                res.add(wrapSingleInstance(instancePublishInfo).put("clientId", clientId));
            }
        }
        
        return res;
    }
    
    @Override
    public List<ClientPublisherInfo> getPublishedClientList(String namespaceId, String groupName, String serviceName,
            String ip, Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName);
        Collection<String> allClientsRegisteredService = clientServiceIndexesManager.getAllClientsRegisteredService(
                service);
        List<ClientPublisherInfo> result = new LinkedList<>();
        for (String clientId : allClientsRegisteredService) {
            Client client = clientManager.getClient(clientId);
            InstancePublishInfo instancePublishInfo = client.getInstancePublishInfo(service);
            if (instancePublishInfo instanceof BatchInstancePublishInfo) {
                for (InstancePublishInfo info : ((BatchInstancePublishInfo) instancePublishInfo).getInstancePublishInfos()) {
                    filterInstancePublishInfo(info, ip, port, clientId).ifPresent(result::add);
                }
            } else {
                filterInstancePublishInfo(instancePublishInfo, ip, port, clientId).ifPresent(result::add);
            }
        }
        return result;
    }
    
    private Optional<ClientPublisherInfo> filterInstancePublishInfo(InstancePublishInfo instancePublishInfo,
            String expectedIp, Integer expectedPort, String clientId) {
        boolean ipMatch = Objects.isNull(expectedIp) || Objects.equals(expectedIp, instancePublishInfo.getIp());
        boolean portMatch = Objects.isNull(expectedPort) || Objects.equals(expectedPort, instancePublishInfo.getPort());
        if (!ipMatch || !portMatch) {
            return Optional.empty();
        }
        ClientPublisherInfo clientPublisherInfo = buildClientPublisherInfo(instancePublishInfo);
        clientPublisherInfo.setClientId(clientId);
        return Optional.of(clientPublisherInfo);
    }
    
    @Override
    public List<ObjectNode> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            boolean ephemeral, String ip, Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName, ephemeral);
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager.getAllClientsSubscribeService(
                service);
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
    public List<ClientSubscriberInfo> getSubscribeClientList(String namespaceId, String groupName, String serviceName,
            String ip, Integer port) {
        Service service = Service.newService(namespaceId, groupName, serviceName);
        Collection<String> allClientsSubscribeService = clientServiceIndexesManager.getAllClientsSubscribeService(
                service);
        List<ClientSubscriberInfo> result = new LinkedList<>();
        for (String clientId : allClientsSubscribeService) {
            Client client = clientManager.getClient(clientId);
            Subscriber subscriber = client.getSubscriber(service);
            boolean ipMatch = Objects.isNull(ip) || Objects.equals(ip, subscriber.getIp());
            boolean portMatch = Objects.isNull(port) || Objects.equals(port, subscriber.getPort());
            if (!ipMatch || !portMatch) {
                continue;
            }
            ClientSubscriberInfo item = new ClientSubscriberInfo();
            item.setClientId(clientId);
            item.setAddress(subscriber.getAddrStr());
            item.setAgent(subscriber.getAgent());
            item.setAppName(subscriber.getApp());
            result.add(item);
        }
        return result;
    }
    
    @Override
    public ObjectNode getResponsibleServer4Client(String ip, String port) {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        String tag = ip + InternetAddressUtil.IP_PORT_SPLITER + port;
        result.put("responsibleServer", distroMapper.mapSrv(tag));
        
        return result;
    }
    
    private ClientServiceInfo buildClientServiceInfo(Service service, InstancePublishInfo instancePublishInfo,
            Subscriber subscriber) {
        ClientServiceInfo result = new ClientServiceInfo();
        result.setNamespaceId(service.getNamespace());
        result.setGroupName(service.getGroup());
        result.setServiceName(service.getName());
        if (null != instancePublishInfo) {
            result.setPublisherInfo(buildClientPublisherInfo(instancePublishInfo));
        }
        if (null != subscriber) {
            result.setSubscriberInfo(buildClientSubscriberInfo(subscriber));
        }
        return result;
    }
    
    private ClientPublisherInfo buildClientPublisherInfo(InstancePublishInfo instancePublishInfo) {
        ClientPublisherInfo result = new ClientPublisherInfo();
        result.setIp(instancePublishInfo.getIp());
        result.setPort(instancePublishInfo.getPort());
        result.setClusterName(instancePublishInfo.getCluster());
        return result;
    }
    
    private ClientSubscriberInfo buildClientSubscriberInfo(Subscriber subscriber) {
        ClientSubscriberInfo result = new ClientSubscriberInfo();
        result.setAddress(subscriber.getAddrStr());
        result.setAgent(subscriber.getAgent());
        result.setAppName(subscriber.getApp());
        return result;
    }
}