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

package com.alibaba.nacos.ai.service.agent.runtime;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Serializes Runtime Publication admission and replacement for one Naming Client.
 *
 * @author Nacos
 */
@Component
public class AgentRuntimePublicationCapacityGate {
    
    private final ClientManager clientManager;
    
    private final int maxPublicationsPerClient;
    
    private final ConcurrentMap<String, Map<String, Integer>> logicalPublications =
        new ConcurrentHashMap<String, Map<String, Integer>>();
    
    @Autowired
    public AgentRuntimePublicationCapacityGate(ClientManagerDelegate clientManager) {
        this(clientManager, resolveMaxPublicationsPerClient());
    }
    
    AgentRuntimePublicationCapacityGate(ClientManager clientManager,
        int maxPublicationsPerClient) {
        if (maxPublicationsPerClient < 1) {
            throw new IllegalArgumentException(
                Constants.Agent.MAX_PUBLICATIONS_PER_CLIENT_CONFIG_KEY
                    + " must be greater than 0");
        }
        this.clientManager = clientManager;
        this.maxPublicationsPerClient = maxPublicationsPerClient;
    }
    
    /**
     * Admit a complete Runtime publication batch atomically for one Client.
     *
     * @param clientId publisher Client id
     * @param service target Runtime Naming service
     * @param requestedPublicationCount number of Runtime Endpoint entries in the new batch
     * @param registration validated Naming registration operation
     * @throws NacosApiException when an at-capacity Client attempts to grow
     */
    public void register(String clientId, Service service, int requestedPublicationCount,
        Runnable registration) throws NacosApiException {
        Client client = clientManager.getClient(clientId);
        if (client == null) {
            registration.run();
            return;
        }
        synchronized (client) {
            int existingPublicationCount =
                countPublicationEntries(client.getInstancePublishInfo(service));
            ensureCapacity(client, countLogicalPublications(clientId),
                existingPublicationCount, requestedPublicationCount);
            registration.run();
        }
    }
    
    /**
     * Admit one logical Runtime publication materialized under internal child publishers.
     *
     * <p>The reservation is counted together with publications physically owned by the parent
     * Client, but any number of implementation-only physical projections still consumes the
     * requested logical count once.</p>
     *
     * @param clientId parent Client id
     * @param publicationKey stable logical publication identity within the Client
     * @param requestedPublicationCount number of Endpoint entries in the replacement batch
     * @param registration validated primary registration operation
     * @throws NacosException when capacity or the primary registration rejects the write
     */
    public void registerLogical(String clientId, String publicationKey,
        int requestedPublicationCount, PublicationOperation registration)
        throws NacosException {
        validateLogicalArguments(publicationKey, requestedPublicationCount, registration);
        Client client = clientManager.getClient(clientId);
        if (client == null) {
            registration.run();
            return;
        }
        synchronized (client) {
            Map<String, Integer> reservations = logicalPublications.get(clientId);
            int existingPublicationCount = reservations == null ? 0
                : reservations.getOrDefault(publicationKey, 0);
            ensureCapacity(client, countLogicalPublications(reservations),
                existingPublicationCount, requestedPublicationCount);
            registration.run();
            logicalPublications.computeIfAbsent(clientId,
                key -> new HashMap<String, Integer>()).put(publicationKey,
                    requestedPublicationCount);
        }
    }
    
    /**
     * Remove one logical Runtime reservation after its primary physical publication is removed.
     *
     * @param clientId parent Client id
     * @param publicationKey stable logical publication identity within the Client
     * @param deregistration primary deregistration operation
     * @throws NacosException when the primary deregistration rejects the write
     */
    public void deregisterLogical(String clientId, String publicationKey,
        PublicationOperation deregistration) throws NacosException {
        validateLogicalArguments(publicationKey, 0, deregistration);
        Client client = clientManager.getClient(clientId);
        if (client == null) {
            deregistration.run();
            clearLogicalPublication(clientId, publicationKey);
            return;
        }
        synchronized (client) {
            deregistration.run();
            clearLogicalPublication(clientId, publicationKey);
        }
    }
    
    /**
     * Release every logical reservation owned by a disconnected Client.
     *
     * @param clientId disconnected parent Client id
     */
    public void clearLogicalPublications(String clientId) {
        logicalPublications.remove(clientId);
    }
    
    int countLogicalPublicationsForTest(String clientId) {
        return countLogicalPublications(clientId);
    }
    
    private void ensureCapacity(Client client, int additionalPublicationCount,
        int existingPublicationCount, int requestedPublicationCount) throws NacosApiException {
        if (countAgentPublications(client) + additionalPublicationCount >= maxPublicationsPerClient
            && requestedPublicationCount > existingPublicationCount) {
            throw new NacosApiException(NacosException.OVER_THRESHOLD,
                ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT,
                "Agent Endpoint publication limit of " + maxPublicationsPerClient
                    + " reached for this Client.");
        }
    }
    
    private void validateLogicalArguments(String publicationKey, int requestedPublicationCount,
        PublicationOperation operation) {
        if (publicationKey == null || publicationKey.isEmpty()
            || requestedPublicationCount < 0 || operation == null) {
            throw new IllegalArgumentException(
                "Logical publication key, non-negative count, and operation are required");
        }
    }
    
    private int countLogicalPublications(String clientId) {
        return countLogicalPublications(logicalPublications.get(clientId));
    }
    
    private int countLogicalPublications(Map<String, Integer> reservations) {
        if (reservations == null) {
            return 0;
        }
        int result = 0;
        for (Integer count : reservations.values()) {
            result += count;
        }
        return result;
    }
    
    private void clearLogicalPublication(String clientId, String publicationKey) {
        Map<String, Integer> reservations = logicalPublications.get(clientId);
        if (reservations == null) {
            return;
        }
        reservations.remove(publicationKey);
        if (reservations.isEmpty()) {
            logicalPublications.remove(clientId, reservations);
        }
    }
    
    private int countAgentPublications(Client client) {
        int result = 0;
        for (Service service : client.getAllPublishedService()) {
            if (Constants.Agent.AGENT_ENDPOINT_GROUP.equals(service.getGroup())) {
                result += countPublicationEntries(client.getInstancePublishInfo(service));
            }
        }
        return result;
    }
    
    private int countPublicationEntries(InstancePublishInfo publishInfo) {
        if (publishInfo instanceof BatchInstancePublishInfo) {
            BatchInstancePublishInfo batch = (BatchInstancePublishInfo) publishInfo;
            return batch.getInstancePublishInfos() == null
                ? 0 : batch.getInstancePublishInfos().size();
        }
        return publishInfo == null ? 0 : 1;
    }
    
    private static int resolveMaxPublicationsPerClient() {
        return EnvUtil.getProperty(Constants.Agent.MAX_PUBLICATIONS_PER_CLIENT_CONFIG_KEY,
            Integer.class, Constants.Agent.DEFAULT_MAX_PUBLICATIONS_PER_CLIENT);
    }
    
    /**
     * One checked primary publication operation.
     */
    @FunctionalInterface
    public interface PublicationOperation {
        
        /**
         * Execute the operation.
         *
         * @throws NacosException when the primary publication fails
         */
        void run() throws NacosException;
    }
}
