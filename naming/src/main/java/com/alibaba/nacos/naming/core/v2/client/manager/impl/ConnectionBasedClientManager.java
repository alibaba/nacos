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

import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * The manager of {@code ConnectionBasedClient}.
 *
 * @author xiweng.yy
 */
@Component("connectionBasedClientManager")
public class ConnectionBasedClientManager extends BaseClientManager<ConnectionBasedClient> implements ClientManager {
    
    public ConnectionBasedClientManager() {
        new InnerClientConnectionEventListener(this);
    }
    
    @Override
    public boolean clientConnected(Client client) {
        Loggers.SRV_LOG.info("Client connection {} connect", client.getClientId());
        if (!clients.containsKey(client.getClientId())) {
            clients.put(client.getClientId(), (ConnectionBasedClient) client);
        }
        return true;
    }
    
    @Override
    public boolean syncClientConnected(String clientId) {
        return clientConnected(new ConnectionBasedClient(clientId, false));
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        Loggers.SRV_LOG.info("Client connection {} disconnect, remove instances and subscribers", clientId);
        ConnectionBasedClient client = clients.remove(clientId);
        if (null == client) {
            return true;
        }
        NotifyCenter.publishEvent(new ClientEvent.ClientDisconnectEvent(client));
        return true;
    }
    
    @Override
    public Client getClient(String clientId) {
        return clients.get(clientId);
    }
    
    @Override
    public Collection<String> allClientId() {
        return clients.keySet();
    }
    
    @Override
    public void forEach(BiConsumer<String, Client> consumer) {
        clients.forEach(consumer);
    }
    
    @Override
    public boolean isResponsibleClient(Client client) {
        return (client instanceof ConnectionBasedClient) && ((ConnectionBasedClient) client).isNative();
    }
    
    @Override
    public boolean verifyClient(String clientId) {
        ConnectionBasedClient client = clients.get(clientId);
        if (null != client) {
            client.setLastRenewTime();
            return true;
        }
        return false;
    }
    
    protected class InnerClientConnectionEventListener extends ClientConnectionEventListener {
        
        private final ConnectionBasedClientManager clientManager;
    
        private InnerClientConnectionEventListener(ConnectionBasedClientManager clientManager) {
            this.clientManager = clientManager;
        }
    
        @Override
        public void clientConnected(Connection connect) {
            if (!RemoteConstants.LABEL_MODULE_NAMING.equals(connect.getMetaInfo().getLabel(RemoteConstants.LABEL_MODULE))) {
                return;
            }
            clientManager.clientConnected(new ConnectionBasedClient(connect.getMetaInfo().getConnectionId(), true));
        }
    
        @Override
        public void clientDisConnected(Connection connect) {
            clientDisconnected(connect.getMetaInfo().getConnectionId());
        }
    }
    
}
