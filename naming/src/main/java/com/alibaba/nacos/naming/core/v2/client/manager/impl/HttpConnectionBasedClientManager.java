/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientVerifyInfo;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.factory.ClientFactory;
import com.alibaba.nacos.naming.core.v2.client.factory.ClientFactoryHolder;
import com.alibaba.nacos.naming.core.v2.client.impl.HttpConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Manager for {@link HttpConnectionBasedClient}.
 *
 * @author Nacos
 */
@DependsOn("clientServiceIndexesManager")
@Component("httpConnectionBasedClientManager")
public class HttpConnectionBasedClientManager implements ClientManager {
    
    private final ConcurrentMap<String, HttpConnectionBasedClient> clients =
        new ConcurrentHashMap<>();
    
    private final DistroMapper distroMapper;
    
    private final ClientFactory<HttpConnectionBasedClient> clientFactory;
    
    @Autowired
    public HttpConnectionBasedClientManager(DistroMapper distroMapper) {
        this(distroMapper, true);
    }
    
    HttpConnectionBasedClientManager(DistroMapper distroMapper, boolean scheduleCleaner) {
        this.distroMapper = distroMapper;
        this.clientFactory = ClientFactoryHolder.getInstance()
            .findClientFactory(ClientConstants.HTTP_CONNECTION_BASED);
        if (scheduleCleaner) {
            GlobalExecutor.scheduleExpiredClientCleaner(this::cleanExpiredClients, 0,
                Constants.DEFAULT_HEART_BEAT_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }
    
    @Override
    public boolean clientConnected(String clientId, ClientAttributes attributes) {
        return clientConnected(clientFactory.newClient(clientId, attributes));
    }
    
    @Override
    public boolean clientConnected(Client client) {
        clients.computeIfAbsent(client.getClientId(), key -> {
            Loggers.SRV_LOG.info("HTTP client connection {} connect", client.getClientId());
            return (HttpConnectionBasedClient) client;
        });
        return true;
    }
    
    @Override
    public boolean syncClientConnected(String clientId, ClientAttributes attributes) {
        HttpConnectionBasedClient syncedClient =
            clientFactory.newSyncedClient(clientId, attributes);
        clients.compute(clientId, (key, current) -> {
            if (current == null) {
                Loggers.SRV_LOG.info("Synced HTTP client connection {} connect", clientId);
                return syncedClient;
            }
            current.synchronizeAttributes(attributes);
            return current;
        });
        return true;
    }
    
    @Override
    public boolean clientDisconnected(String clientId) {
        Loggers.SRV_LOG.info(
            "HTTP client connection {} disconnect, remove instances and subscribers", clientId);
        HttpConnectionBasedClient client = clients.remove(clientId);
        if (client == null) {
            return true;
        }
        boolean responsible = isResponsibleClient(client);
        NotifyCenter.publishEvent(new ClientEvent.ClientDisconnectEvent(client, responsible));
        client.release();
        NotifyCenter.publishEvent(
            new ClientOperationEvent.ClientReleaseEvent(client, responsible));
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
        return clients.keySet();
    }
    
    @Override
    public boolean isResponsibleClient(Client client) {
        return client instanceof HttpConnectionBasedClient
            && distroMapper.responsible(client.getClientId());
    }
    
    @Override
    public boolean verifyClient(DistroClientVerifyInfo verifyData) {
        HttpConnectionBasedClient client = clients.get(verifyData.getClientId());
        if (client == null) {
            return false;
        }
        if (verifyData.getRevision() != 0
            && client.getRevision() != verifyData.getRevision()) {
            Loggers.DISTRO.info(
                "[DISTRO-VERIFY-FAILED] HttpConnectionBasedClient[{}] revision local={}, remote={}",
                client.getClientId(), client.getRevision(), verifyData.getRevision());
            return false;
        }
        client.renewReplica();
        return true;
    }
    
    /**
     * Renew only an existing HTTP client.
     *
     * @param clientId Naming internal client id
     * @return {@code true} when the responsible client exists
     */
    public boolean renewClient(String clientId) {
        HttpConnectionBasedClient client = clients.get(clientId);
        if (client == null || !isResponsibleClient(client)) {
            return false;
        }
        client.renewClient();
        return true;
    }
    
    /**
     * Renew an existing HTTP publisher and its client.
     *
     * @param clientId Naming internal client id
     * @return {@code true} when the responsible publisher exists
     */
    public boolean renewPublisher(String clientId) {
        HttpConnectionBasedClient client = clients.get(clientId);
        if (client == null || !isResponsibleClient(client)
            || client.getAllPublishedService().isEmpty()) {
            return false;
        }
        if (client.renewPublisher()) {
            publishHealthChange(client);
        }
        return true;
    }
    
    /**
     * Remove an HTTP client when it no longer owns publishers or subscribers.
     *
     * @param clientId Naming internal client id
     * @return {@code true} when no client state remains
     */
    public boolean disconnectIfEmpty(String clientId) {
        HttpConnectionBasedClient client = clients.get(clientId);
        if (client == null) {
            return true;
        }
        if (!client.getAllPublishedService().isEmpty()
            || !client.getAllSubscribeService().isEmpty()) {
            return false;
        }
        return clientDisconnected(clientId);
    }
    
    void cleanExpiredClients() {
        cleanExpiredClients(System.currentTimeMillis());
    }
    
    void cleanExpiredClients(long currentTime) {
        for (String clientId : allClientId()) {
            HttpConnectionBasedClient client = clients.get(clientId);
            if (client == null) {
                continue;
            }
            if (client.isExpire(currentTime)) {
                clientDisconnected(clientId);
                continue;
            }
            if (!isResponsibleClient(client)) {
                continue;
            }
            cleanPublisher(client, currentTime);
        }
    }
    
    private void cleanPublisher(HttpConnectionBasedClient client, long currentTime) {
        if (client.getAllPublishedService().isEmpty()) {
            return;
        }
        long inactiveTime = currentTime
            - Math.max(client.getPublisherLastUpdatedTime(), client.getLastRenewTime());
        if (inactiveTime > Constants.DEFAULT_IP_DELETE_TIMEOUT) {
            removeAllPublishers(client);
            return;
        }
        if (inactiveTime > Constants.DEFAULT_HEART_BEAT_TIMEOUT
            && client.markPublisherUnhealthy()) {
            publishHealthChange(client);
        }
    }
    
    private void removeAllPublishers(HttpConnectionBasedClient client) {
        Collection<Service> publishedServices =
            new ArrayList<>(client.getAllPublishedService());
        for (Service service : publishedServices) {
            client.removeServiceInstance(service);
            NotifyCenter.publishEvent(
                new ClientOperationEvent.ClientDeregisterServiceEvent(service,
                    client.getClientId()));
        }
        client.resetPublisherLiveness();
        client.recalculateRevision();
        disconnectIfEmpty(client.getClientId());
    }
    
    private void publishHealthChange(HttpConnectionBasedClient client) {
        client.recalculateRevision();
        for (Service service : client.getAllPublishedService()) {
            NotifyCenter.publishEvent(new ServiceEvent.ServiceChangedEvent(service,
                Constants.ServiceChangedType.HEART_BEAT));
        }
        NotifyCenter.publishEvent(new ClientEvent.ClientChangedEvent(client));
    }
    
}
