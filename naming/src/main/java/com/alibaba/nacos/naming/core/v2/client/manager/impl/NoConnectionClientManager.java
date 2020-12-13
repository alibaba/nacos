/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.client.manager.impl;

import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Non-active client management.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component("noConnectionClientManager")
public class NoConnectionClientManager extends BaseClientManager<IpPortBasedClient> {
    
    private static final AtomicReferenceFieldUpdater<BaseClientManager, ConcurrentMap> CLIENTS_FIELD_UPDATER = AtomicReferenceFieldUpdater
            .newUpdater(BaseClientManager.class, ConcurrentMap.class, "clients");
    
    @Override
    protected void init() {
        // DO Nothing
    }
    
    @Override
    public boolean clientConnected(Client client) {
        Loggers.SRV_LOG.info("Client connection {} connect", client.getClientId());
        if (!clients.containsKey(client.getClientId())) {
            clients.put(client.getClientId(), (IpPortBasedClient) client);
        }
        return true;
    }
    
    @Override
    public boolean syncClientConnected(String clientId) {
        throw new UnsupportedOperationException("");
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        throw new UnsupportedOperationException("");
    }
    
    @Override
    public Client getClient(String clientId) {
        return clients.get(clientId);
    }
    
    public Client computeIfAbsent(final String clientId, final Supplier<IpPortBasedClient> supplier) {
        clients.computeIfAbsent(clientId, s -> supplier.get());
        return getClient(clientId);
    }
    
    @Override
    public Collection<String> allClientId() {
        Collection<String> clientIds = new ArrayList<>(clients.size());
        clientIds.addAll(clients.keySet());
        return clientIds;
    }
    
    @Override
    public void forEach(BiConsumer<String, Client> consumer) {
        clients.forEach(consumer);
    }
    
    /**
     * Because the persistence instance relies on the Raft algorithm, any node can process the request.
     *
     * @param client client
     * @return
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
    
    public void loadFromRemote(ConcurrentMap<String, IpPortBasedClient> clients) {
        CLIENTS_FIELD_UPDATER.set(this, clients);
    }
}
