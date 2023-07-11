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

package com.alibaba.nacos.naming.core.v2.client.manager.impl;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.factory.ClientFactory;
import com.alibaba.nacos.naming.core.v2.client.factory.ClientFactoryHolder;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The manager of {@code IpPortBasedClient} and persistence.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author xiweng.yy
 */
@Component("persistentIpPortClientManager")
public class PersistentIpPortClientManager implements ClientManager {
    
    private final ClientFactory<IpPortBasedClient> clientFactory;
    
    private ConcurrentMap<String, IpPortBasedClient> clients = new ConcurrentHashMap<>();
    
    public PersistentIpPortClientManager() {
        clientFactory = ClientFactoryHolder.getInstance().findClientFactory(ClientConstants.PERSISTENT_IP_PORT);
    }
    
    @Override
    public boolean clientConnected(String clientId, ClientAttributes attributes) {
        return clientConnected(clientFactory.newClient(clientId, attributes));
    }
    
    @Override
    public boolean clientConnected(final Client client) {
        clients.computeIfAbsent(client.getClientId(), s -> {
            Loggers.SRV_LOG.info("Client connection {} connect", client.getClientId());
            IpPortBasedClient ipPortBasedClient = (IpPortBasedClient) client;
            ipPortBasedClient.init();
            return ipPortBasedClient;
        });
        return true;
    }
    
    @Override
    public boolean syncClientConnected(String clientId, ClientAttributes attributes) {
        throw new UnsupportedOperationException("");
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        Loggers.SRV_LOG.info("Persistent client connection {} disconnect", clientId);
        IpPortBasedClient client = clients.remove(clientId);
        if (null == client) {
            return true;
        }
        boolean isResponsible = isResponsibleClient(client);
        NotifyCenter.publishEvent(new ClientEvent.ClientDisconnectEvent(client, isResponsible));
        client.release();
        NotifyCenter.publishEvent(new ClientOperationEvent.ClientReleaseEvent(client, isResponsible));
        return true;
    }
    
    @Override
    public Client getClient(String clientId) {
        return clients.get(clientId);
    }
    
    @Override
    public boolean contains(String clientId) {
        return clients.containsKey(clientId);
    }
    
    @Override
    public Collection<String> allClientId() {
        // client id is unique in the application
        // use set to replace array list
        // it will improve the performance
        Collection<String> clientIds = new HashSet<>(clients.size());
        clientIds.addAll(clients.keySet());
        return clientIds;
    }
    
    /**
     * Because the persistence instance relies on the Raft algorithm, any node can process the request.
     *
     * @param client client
     * @return true
     */
    @Override
    public boolean isResponsibleClient(Client client) {
        return true;
    }
    
    @Override
    public boolean verifyClient(DistroClientVerifyInfo verifyData) {
        throw new UnsupportedOperationException("");
    }
    
    public Map<String, IpPortBasedClient> showClients() {
        return Collections.unmodifiableMap(clients);
    }
    
    /**
     * Load persistent clients from snapshot.
     *
     * @param clients clients snapshot
     */
    public void loadFromSnapshot(ConcurrentMap<String, IpPortBasedClient> clients) {
        ConcurrentMap<String, IpPortBasedClient> oldClients = this.clients;
        this.clients = clients;
        oldClients.clear();
    }

    /**
     * add client directly.
     *
     * @param client client
     */
    public void addSyncClient(IpPortBasedClient client) {
        clients.put(client.getClientId(), client);
    }

    /**
     * remove client.
     *
     * @param clientId client id
     */
    public void removeAndRelease(String clientId) {
        IpPortBasedClient client = clients.remove(clientId);
        if (client != null) {
            client.release();
        }
    }
}
