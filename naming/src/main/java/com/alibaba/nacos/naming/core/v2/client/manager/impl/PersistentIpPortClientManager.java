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
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * The manager of {@code IpPortBasedClient} and persistence.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author xiweng.yy
 */
@Component("persistentIpPortClientManager")
public class PersistentIpPortClientManager implements ClientManager {
    
    private ConcurrentMap<String, IpPortBasedClient> clients = new ConcurrentHashMap<>();
    
    @Override
    public boolean clientConnected(Client client) {
        Loggers.SRV_LOG.info("Client connection {} connect", client.getClientId());
        if (!clients.containsKey(client.getClientId())) {
            clients.putIfAbsent(client.getClientId(), (IpPortBasedClient) client);
        }
        return true;
    }
    
    @Override
    public boolean syncClientConnected(String clientId, ClientSyncAttributes attributes) {
        throw new UnsupportedOperationException("");
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        Loggers.SRV_LOG.info("Persistent client connection {} disconnect", clientId);
        IpPortBasedClient client = clients.remove(clientId);
        if (null == client) {
            return true;
        }
        NotifyCenter.publishEvent(new ClientEvent.ClientDisconnectEvent(client));
        client.release();
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
    
    /**
     * Compute and do operation new client when client not exist.
     *
     * @param clientId clientId
     * @param supplier operation
     * @return Client saved in manager
     */
    public Client computeIfAbsent(final String clientId, final Supplier<IpPortBasedClient> supplier) {
        clients.computeIfAbsent(clientId, s -> supplier.get());
        Loggers.SRV_LOG.info("Client connection {} connect", clientId);
        return getClient(clientId);
    }
    
    @Override
    public Collection<String> allClientId() {
        Collection<String> clientIds = new ArrayList<>(clients.size());
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
    public boolean verifyClient(String clientId) {
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
}
