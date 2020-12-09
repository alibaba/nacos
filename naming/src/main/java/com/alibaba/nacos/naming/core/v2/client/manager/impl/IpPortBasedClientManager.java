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
import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatUpdateTask;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The manager of {@code IpPortBasedClient} and ephemeral.
 *
 * @author xiweng.yy
 */
@Component("ipPortBasedClientManager")
public class IpPortBasedClientManager extends BaseClientManager<IpPortBasedClient> implements ClientManager {
    
    private final DistroMapper distroMapper;
    
    private final Map<String, IpPortBasedClient> persistentClientRepository = new ConcurrentHashMap<>(512);
    
    public IpPortBasedClientManager(DistroMapper distroMapper) {
        this.distroMapper = distroMapper;
    }
    
    @Override
    public boolean clientConnected(Client client) {
        Loggers.SRV_LOG.info("Client connection {} connect", client.getClientId());
        if (!clients.containsKey(client.getClientId())) {
            switchOneRepository(client.isEphemeral()).put(client.getClientId(), (IpPortBasedClient) client);
        }
        return true;
    }
    
    @Override
    public boolean syncClientConnected(String clientId) {
        return clientConnected(new IpPortBasedClient(clientId, true));
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        Loggers.SRV_LOG.info("Client connection {} disconnect, remove instances and subscribers", clientId);
        IpPortBasedClient eClient = clients.remove(clientId);
        IpPortBasedClient pClient = persistentClientRepository.remove(clientId);
        if (null == eClient && pClient == null) {
            return true;
        }
        IpPortBasedClient client = Objects.isNull(eClient) ? pClient : eClient;
        NotifyCenter.publishEvent(new ClientEvent.ClientDisconnectEvent(client));
        client.destroy();
        return true;
    }
    
    @Override
    public Client getClient(String clientId) {
        return clients.getOrDefault(clientId, persistentClientRepository.get(clientId));
    }
    
    @Override
    public Collection<String> allClientId() {
        Collection<String> clientIds = new ArrayList<>(clients.size() + persistentClientRepository.size());
        clientIds.addAll(clients.keySet());
        clientIds.addAll(persistentClientRepository.keySet());
        return clientIds;
    }
    
    @Override
    public void forEach(BiConsumer<String, Client> consumer) {
        clients.forEach(consumer);
        persistentClientRepository.forEach(consumer);
    }
    
    @Override
    public boolean isResponsibleClient(Client client) {
        return distroMapper.responsible(client.getClientId());
    }
    
    @Override
    public boolean verifyClient(String clientId) {
        IpPortBasedClient client = clients.get(clientId);
        if (null != client) {
            NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(clientId, new ClientBeatUpdateTask(client));
            return true;
        }
        return false;
    }
    
    private Map<String, IpPortBasedClient> switchOneRepository(boolean ephemeral) {
        return ephemeral ? clients : persistentClientRepository;
    }
    
}
