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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.a2a.A2aEndpointChildPublisherManager.ChildPublisher;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.ai.utils.AgentEndpointUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.naming.BatchRegisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Physical historical exact-Version A2A Naming operations.
 *
 * <p>Explicit LEGACY mode continues to pass the original connection id. Upgrade migration may
 * reuse the same mapping with a deterministic child publisher without changing the historical
 * service or instance layout.</p>
 *
 * @author Nacos
 */
@Component
public class LegacyA2aEndpointOperationService {
    
    static final String CHILD_LAYOUT = "legacy";
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    private final AgentIdCodecHolder agentIdCodecHolder;
    
    private final A2aEndpointChildPublisherManager childPublisherManager;
    
    public LegacyA2aEndpointOperationService(
        EphemeralClientOperationServiceImpl clientOperationService,
        AgentIdCodecHolder agentIdCodecHolder,
        A2aEndpointChildPublisherManager childPublisherManager) {
        this.clientOperationService = clientOperationService;
        this.agentIdCodecHolder = agentIdCodecHolder;
        this.childPublisherManager = childPublisherManager;
    }
    
    /**
     * Validate and map one Endpoint without changing Naming state.
     *
     * @param endpoint legacy Endpoint
     * @throws NacosException when the legacy mapping is invalid
     */
    public void validate(AgentEndpoint endpoint) throws NacosException {
        AgentEndpointUtil.transferToInstance(endpoint);
    }
    
    /**
     * Validate and map one complete Batch without changing Naming state.
     *
     * @param endpoints legacy Endpoint Batch
     * @throws NacosException when the legacy mapping is invalid
     */
    public void validate(Collection<AgentEndpoint> endpoints) throws NacosException {
        AgentEndpointUtil.transferToInstances(endpoints);
    }
    
    /**
     * Register one Endpoint with the supplied physical publisher.
     *
     * @param publisherId Naming publisher id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param endpoint legacy Endpoint
     * @param sourceIp request source IP used only by trace events
     * @throws NacosException when Naming rejects the write
     */
    public void register(String publisherId, String namespaceId, String agentName,
        AgentEndpoint endpoint, String sourceIp) throws NacosException {
        Instance instance = AgentEndpointUtil.transferToInstance(endpoint);
        Service service = composeService(namespaceId, agentName, endpoint.getVersion());
        clientOperationService.registerInstance(service, instance, publisherId);
        NotifyCenter.publishEvent(new RegisterInstanceTraceEvent(System.currentTimeMillis(),
            sourceIp, true, service.getNamespace(), service.getGroup(), service.getName(),
            instance.getIp(), instance.getPort()));
    }
    
    /**
     * Replace one complete Endpoint Batch with the supplied physical publisher.
     *
     * @param publisherId Naming publisher id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param endpoints complete legacy Endpoint Batch
     * @param sourceIp request source IP used only by trace events
     * @throws NacosException when mapping fails
     */
    public void register(String publisherId, String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints, String sourceIp) throws NacosException {
        List<Instance> instances = AgentEndpointUtil.transferToInstances(endpoints);
        String version = endpoints.iterator().next().getVersion();
        Service service = composeService(namespaceId, agentName, version);
        clientOperationService.batchRegisterInstance(service, instances, publisherId);
        long eventTime = System.currentTimeMillis();
        for (Instance instance : instances) {
            NotifyCenter.publishEvent(new BatchRegisterInstanceTraceEvent(eventTime, sourceIp,
                true, service.getNamespace(), service.getGroup(), service.getName(),
                instance.getIp(), instance.getPort()));
        }
    }
    
    /**
     * Deregister one exact-Version historical publication.
     *
     * @param publisherId Naming publisher id
     * @param namespaceId namespace identifier
     * @param agentName Agent name
     * @param endpoint legacy Endpoint identity
     * @param sourceIp request source IP used only by trace events
     * @throws NacosException when mapping fails
     */
    public void deregister(String publisherId, String namespaceId, String agentName,
        AgentEndpoint endpoint, String sourceIp) throws NacosException {
        Instance instance = AgentEndpointUtil.transferToInstance(endpoint);
        Service service = composeService(namespaceId, agentName, endpoint.getVersion());
        clientOperationService.deregisterInstance(service, instance, publisherId);
        NotifyCenter.publishEvent(new DeregisterInstanceTraceEvent(System.currentTimeMillis(),
            sourceIp, true, DeregisterInstanceReason.REQUEST, service.getNamespace(),
            service.getGroup(), service.getName(), instance.getIp(), instance.getPort()));
    }
    
    /**
     * Register one migration-owned child publication.
     */
    public void registerChild(String parentClientId, String namespaceId, String agentName,
        AgentEndpoint endpoint, String sourceIp) throws NacosException {
        ChildPublisher child = childPublisherManager.ensureChild(parentClientId, namespaceId,
            agentName, endpoint.getVersion(), CHILD_LAYOUT);
        try {
            register(child.getClientId(), namespaceId, agentName, endpoint, sourceIp);
        } catch (NacosException | RuntimeException e) {
            cleanupNewChild(parentClientId, child);
            throw e;
        }
    }
    
    /**
     * Register one migration-owned child Batch publication.
     */
    public void registerChild(String parentClientId, String namespaceId, String agentName,
        Collection<AgentEndpoint> endpoints, String sourceIp) throws NacosException {
        String version = endpoints.iterator().next().getVersion();
        ChildPublisher child = childPublisherManager.ensureChild(parentClientId, namespaceId,
            agentName, version, CHILD_LAYOUT);
        try {
            register(child.getClientId(), namespaceId, agentName, endpoints, sourceIp);
        } catch (NacosException | RuntimeException e) {
            cleanupNewChild(parentClientId, child);
            throw e;
        }
    }
    
    /**
     * Deregister one migration-owned child publication.
     */
    public void deregisterChild(String parentClientId, String namespaceId, String agentName,
        AgentEndpoint endpoint, String sourceIp) throws NacosException {
        String childClientId = childPublisherManager.findChild(parentClientId, namespaceId,
            agentName, endpoint.getVersion(), CHILD_LAYOUT);
        if (childClientId == null) {
            return;
        }
        deregister(childClientId, namespaceId, agentName, endpoint, sourceIp);
        childPublisherManager.disconnectChild(parentClientId, childClientId);
    }
    
    private Service composeService(String namespaceId, String agentName, String version) {
        String serviceName = agentIdCodecHolder.encode(agentName) + "::" + version;
        return Service.newService(namespaceId, Constants.Agent.AGENT_ENDPOINT_GROUP, serviceName);
    }
    
    private void cleanupNewChild(String parentClientId, ChildPublisher child) {
        if (child.isCreated()) {
            childPublisherManager.disconnectChild(parentClientId, child.getClientId());
        }
    }
}
