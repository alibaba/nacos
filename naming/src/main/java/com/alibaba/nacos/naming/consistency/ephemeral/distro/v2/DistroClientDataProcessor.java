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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataProcessor;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncData;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncDatumSnapshot;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Distro processor for v2.
 *
 * @author xiweng.yy
 */
public class DistroClientDataProcessor extends SmartSubscriber implements DistroDataStorage, DistroDataProcessor {
    
    public static final String TYPE = "Nacos:Naming:v2:ClientData";
    
    private final ClientManager clientManager;
    
    private final DistroProtocol distroProtocol;
    
    public DistroClientDataProcessor(ClientManager clientManager, DistroProtocol distroProtocol) {
        this.clientManager = clientManager;
        this.distroProtocol = distroProtocol;
        NotifyCenter.registerSubscriber(this);
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ClientEvent.ClientChangedEvent.class);
        result.add(ClientEvent.ClientDisconnectEvent.class);
        return result;
    }
    
    @Override
    public void onEvent(Event event) {
        if (ApplicationUtils.getStandaloneMode()) {
            return;
        }
        ClientEvent clientEvent = (ClientEvent) event;
        Client client = clientEvent.getClient();
        if (!clientManager.isResponsibleClient(client)) {
            return;
        }
        if (event instanceof ClientEvent.ClientDisconnectEvent) {
            DistroKey distroKey = new DistroKey(client.getClientId(), TYPE);
            distroProtocol.sync(distroKey, DataOperation.DELETE);
        } else if (event instanceof ClientEvent.ClientChangedEvent) {
            DistroKey distroKey = new DistroKey(client.getClientId(), TYPE);
            distroProtocol.sync(distroKey, DataOperation.CHANGE);
        }
    }
    
    @Override
    public String processType() {
        return TYPE;
    }
    
    @Override
    public boolean processData(DistroData distroData) {
        switch (distroData.getType()) {
            case ADD:
            case CHANGE:
                ClientSyncData clientSyncData = ApplicationUtils.getBean(Serializer.class)
                        .deserialize(distroData.getContent(), ClientSyncData.class);
                handlerClientSyncData(clientSyncData);
                return true;
            case DELETE:
                clientManager.clientDisconnected(distroData.getDistroKey().getResourceKey());
                return true;
            default:
                return false;
        }
    }
    
    private void handlerClientSyncData(ClientSyncData clientSyncData) {
        clientManager.syncClientConnected(clientSyncData.getClientId());
        Client client = clientManager.getClient(clientSyncData.getClientId());
        upgradeClient(client, clientSyncData);
    }
    
    private void upgradeClient(Client client, ClientSyncData clientSyncData) {
        List<String> namespaces = clientSyncData.getNamespaces();
        List<String> groupNames = clientSyncData.getGroupNames();
        List<String> serviceNames = clientSyncData.getServiceNames();
        List<InstancePublishInfo> instances = clientSyncData.getInstancePublishInfos();
        Set<Service> syncedService = new HashSet<>();
        for (int i = 0; i < namespaces.size(); i++) {
            Service service = Service.newService(namespaces.get(i), groupNames.get(i), serviceNames.get(i));
            Service singleton = ServiceManager.getInstance().getSingleton(service);
            syncedService.add(singleton);
            InstancePublishInfo instancePublishInfo = instances.get(i);
            if (!instancePublishInfo.equals(client.getInstancePublishInfo(singleton))) {
                client.addServiceInstance(singleton, instancePublishInfo);
                NotifyCenter.publishEvent(
                        new ClientOperationEvent.ClientRegisterServiceEvent(singleton, client.getClientId()));
            }
        }
        for (Service each : client.getAllPublishedService()) {
            if (!syncedService.contains(each)) {
                client.removeServiceInstance(each);
                NotifyCenter.publishEvent(
                        new ClientOperationEvent.ClientDeregisterServiceEvent(each, client.getClientId()));
            }
        }
    }
    
    @Override
    public boolean processVerifyData(DistroData distroData) {
        List<String> verifyData = ApplicationUtils.getBean(Serializer.class)
                .deserialize(distroData.getContent(), List.class);
        List<String> invalidData = new LinkedList<>();
        for (String each : verifyData) {
            if (!clientManager.verifyClient(each)) {
                invalidData.add(each);
            }
        }
        String sourceServer = distroData.getDistroKey().getTargetServer();
        for (String each : invalidData) {
            Loggers.DISTRO.info("client {} is invalid, get new client from {}", each, sourceServer);
            DistroData data = distroProtocol.queryFromRemote(new DistroKey(each, TYPE, sourceServer));
            data.setType(DataOperation.ADD);
            processData(data);
        }
        return true;
    }
    
    @Override
    public boolean processSnapshot(DistroData distroData) {
        ClientSyncDatumSnapshot snapshot = ApplicationUtils.getBean(Serializer.class)
                .deserialize(distroData.getContent(), ClientSyncDatumSnapshot.class);
        for (ClientSyncData each : snapshot.getClientSyncDataList()) {
            handlerClientSyncData(each);
        }
        return true;
    }
    
    @Override
    public DistroData getDistroData(DistroKey distroKey) {
        Client client = clientManager.getClient(distroKey.getResourceKey());
        if (null == client) {
            return null;
        }
        byte[] data = ApplicationUtils.getBean(Serializer.class).serialize(client.generateSyncData());
        return new DistroData(distroKey, data);
    }
    
    @Override
    public DistroData getDatumSnapshot() {
        List<ClientSyncData> datum = new LinkedList<>();
        for (String each : clientManager.allClientId()) {
            Client client = clientManager.getClient(each);
            if (null == client) {
                continue;
            }
            datum.add(client.generateSyncData());
        }
        ClientSyncDatumSnapshot snapshot = new ClientSyncDatumSnapshot();
        snapshot.setClientSyncDataList(datum);
        byte[] data = ApplicationUtils.getBean(Serializer.class).serialize(snapshot);
        return new DistroData(new DistroKey(DataOperation.SNAPSHOT.name(), TYPE), data);
    }
    
    @Override
    public DistroData getVerifyData() {
        List<String> verifyData = new LinkedList<>();
        for (String each : clientManager.allClientId()) {
            Client client = clientManager.getClient(each);
            if (null == client) {
                continue;
            }
            if (clientManager.isResponsibleClient(client)) {
                verifyData.add(each);
            }
        }
        byte[] data = ApplicationUtils.getBean(Serializer.class).serialize(verifyData);
        return new DistroData(new DistroKey(DataOperation.VERIFY.name(), TYPE), data);
    }
}
