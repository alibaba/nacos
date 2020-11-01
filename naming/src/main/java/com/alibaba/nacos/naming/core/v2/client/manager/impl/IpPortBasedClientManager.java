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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.healthcheck.heartbeat.ClientBeatUpdateTask;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * The manager of {@code IpPortBasedClient} and ephemeral.
 *
 * @author xiweng.yy
 */
@Component("ipPortBasedClientManager")
public class IpPortBasedClientManager implements ClientManager {
    
    private final ConcurrentMap<String, IpPortBasedClient> clients = new ConcurrentHashMap<>();
    
    private final DistroMapper distroMapper;
    
    public IpPortBasedClientManager(DistroMapper distroMapper) {
        this.distroMapper = distroMapper;
        GlobalExecutor
                .scheduleExpiredClientCleaner(new ExpiredClientCleaner(this), 0, Constants.DEFAULT_HEART_BEAT_INTERVAL,
                        TimeUnit.MILLISECONDS);
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
        return clientConnected(new IpPortBasedClient(clientId, true));
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        Loggers.SRV_LOG.info("Client connection {} disconnect, remove instances and subscribers", clientId);
        IpPortBasedClient client = clients.remove(clientId);
        if (null == client) {
            return true;
        }
        NotifyCenter.publishEvent(new ClientEvent.ClientDisconnectEvent(client));
        client.destroy();
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
    
    private static class ExpiredClientCleaner implements Runnable {
        
        private final IpPortBasedClientManager clientManager;
        
        public ExpiredClientCleaner(IpPortBasedClientManager clientManager) {
            this.clientManager = clientManager;
        }
        
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            for (String each : clientManager.allClientId()) {
                IpPortBasedClient client = (IpPortBasedClient) clientManager.getClient(each);
                if (null != client && isExpireClient(currentTime, client)) {
                    clientManager.clientDisconnected(each);
                }
            }
        }
        
        private boolean isExpireClient(long currentTime, IpPortBasedClient client) {
            return client.isEphemeral() && client.getAllPublishedService().isEmpty()
                    && currentTime - client.getLastUpdatedTime() > Constants.DEFAULT_IP_DELETE_TIMEOUT;
        }
    }
}
