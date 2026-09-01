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

import com.alibaba.nacos.ai.service.runtime.AiHttpClientLifecycleService;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.springframework.stereotype.Service;

/**
 * Adapts Agent Runtime publication operations to the shared AI HTTP Client lifecycle.
 *
 * @author Nacos
 */
@Service
public class AgentHttpClientLifecycleService {
    
    private final AiHttpClientLifecycleService clientLifecycleService;
    
    private final AgentRuntimeRegistryService runtimeRegistryService;
    
    public AgentHttpClientLifecycleService(AiHttpClientLifecycleService clientLifecycleService,
        AgentRuntimeRegistryService runtimeRegistryService) {
        this.clientLifecycleService = clientLifecycleService;
        this.runtimeRegistryService = runtimeRegistryService;
    }
    
    /**
     * Refresh an existing HTTP Client without changing Publisher liveness.
     *
     * @param externalClientId optional external HTTP Client id
     * @param namespaceId request namespace
     * @throws NacosApiException when the id or existing binding is invalid
     */
    public void renewForQuery(String externalClientId, String namespaceId)
        throws NacosApiException {
        clientLifecycleService.renewForQuery(externalClientId, namespaceId);
    }
    
    /**
     * Validate the mandatory HTTP Watch headers and renew an existing bound client.
     *
     * <p>A Watch does not create a Naming HTTP client or publisher. When the same external id
     * already owns publications, its initial identity and namespace binding still apply.</p>
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @param namespaceId effective Watch namespace
     * @throws NacosApiException when the headers or an existing binding are invalid
     */
    public void renewForWatch(String externalClientId, String requestModule, String namespaceId)
        throws NacosApiException {
        clientLifecycleService.renewForWatch(externalClientId, requestModule, namespaceId);
    }
    
    /**
     * Replace one HTTP Publisher's complete Agent Endpoint batch.
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @param batch complete registration batch
     * @return server liveness intervals
     * @throws NacosException when validation or registration fails
     */
    public ClientLivenessInfo register(String externalClientId, String requestModule,
        AgentEndpointRegistrationBatch batch) throws NacosException {
        return clientLifecycleService.register(externalClientId, requestModule,
            batch.getNamespaceId(),
            internalClientId -> runtimeRegistryService.register(internalClientId, batch));
    }
    
    /**
     * Remove one HTTP Publisher's complete Agent Endpoint publication.
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @param namespaceId publication namespace
     * @param agentName publication Agent name
     * @param protocol publication protocol
     * @throws NacosException when validation or deregistration fails
     */
    public void deregister(String externalClientId, String requestModule, String namespaceId,
        String agentName, String protocol) throws NacosException {
        clientLifecycleService.deregister(externalClientId, requestModule, namespaceId,
            internalClientId -> runtimeRegistryService.deregisterPublisher(internalClientId,
                namespaceId, agentName, protocol));
    }
    
    /**
     * Refresh the shared HTTP Client and all Endpoint publications it owns.
     *
     * @param externalClientId external HTTP Client id
     * @param requestModule Request-Module header
     * @return server liveness intervals
     * @throws NacosApiException when the Client or publication does not exist
     */
    public ClientLivenessInfo heartbeat(String externalClientId, String requestModule)
        throws NacosApiException {
        return clientLifecycleService.heartbeat(externalClientId, requestModule);
    }
}
