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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.A2aCanonicalDefinitionConverter;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aHistoricalDefinitionReconciler {
    
    private static final String DEFAULT_OWNER = "nacos";
    
    private final A2aCanonicalDefinitionConverter definitionConverter;
    
    private final A2aMigrationTargetStore targetStore;
    
    public A2aHistoricalDefinitionReconciler(
        A2aCanonicalDefinitionConverter definitionConverter,
        A2aMigrationTargetStore targetStore) {
        this.definitionConverter = definitionConverter;
        this.targetStore = targetStore;
    }
    
    /**
     * Convert and reconcile one immutable historical source snapshot.
     *
     * @param snapshot historical summary and every referenced Version
     * @param sourceCurrent source fence checked before and after target preparation
     * @return reconciliation outcome
     * @throws NacosException when target storage or persistence fails
     */
    public A2aMigrationTargetStore.Result reconcile(
        A2aHistoricalDefinitionSnapshot snapshot, BooleanSupplier sourceCurrent)
        throws NacosException {
        if (snapshot == null || sourceCurrent == null) {
            throw new IllegalArgumentException("Historical A2A snapshot and source fence required");
        }
        A2aMigrationDefinition definition = convert(snapshot);
        return targetStore.reconcile(definition, sourceCurrent);
    }
    
    /**
     * Verify the complete canonical target for one immutable source without repairing it.
     *
     * @param snapshot historical source snapshot
     * @return whether Resource, Versions, and Storage are completely current
     * @throws NacosException when source conversion fails
     */
    public boolean isCurrent(A2aHistoricalDefinitionSnapshot snapshot) throws NacosException {
        if (snapshot == null) {
            return false;
        }
        return targetStore.isCurrent(convert(snapshot));
    }
    
    A2aMigrationDefinition convert(A2aHistoricalDefinitionSnapshot snapshot)
        throws NacosException {
        String namespaceId = snapshot.getNamespaceId();
        String agentName = snapshot.getSummary().getName();
        String latest = snapshot.getSummary().getLatestPublishedVersion();
        AgentValidationUtils.validateNamespaceId(namespaceId);
        AgentValidationUtils.validateAgentName(agentName);
        AgentValidationUtils.validateVersion(latest);
        List<AgentVersionDetail> versions = new ArrayList<AgentVersionDetail>();
        AgentDraftCreateRequest latestRequest = null;
        for (A2aHistoricalDefinitionSnapshot.VersionSnapshot source : snapshot.getVersions()
            .values()) {
            boolean latestVersion = latest.equals(source.getAgentCard().getVersion());
            AgentDraftCreateRequest request = definitionConverter.convert(namespaceId,
                source.getAgentCard(), source.getAgentCard().getRegistrationType(), latestVersion);
            AgentVersionDetail version = new AgentVersionDetail();
            version.setNamespaceId(namespaceId);
            version.setAgentName(agentName);
            version.setVersion(request.getVersion());
            version.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
            version.setCallInterfaces(request.getCallInterfaces());
            version.setAuthor(DEFAULT_OWNER);
            version.setChangeDescription("");
            versions.add(version);
            if (latestVersion) {
                latestRequest = request;
            }
        }
        if (latestRequest == null) {
            throw new IllegalStateException("Historical A2A latest Version content is missing");
        }
        Agent agent = new Agent();
        agent.setNamespaceId(namespaceId);
        agent.setAgentName(agentName);
        agent.setDescription(latestRequest.getDescription());
        agent.setIconUrl(latestRequest.getIconUrl());
        agent.setProvider(latestRequest.getProvider());
        agent.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        agent.setOwner(DEFAULT_OWNER);
        agent.setScope(VisibilityConstants.SCOPE_PUBLIC);
        return new A2aMigrationDefinition(agent, versions, latest);
    }
}
