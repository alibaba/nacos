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

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationDefinitionWriteAfterHook;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationLegacyMutationGuard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Routes every historical A2A definition operation to one complete implementation.
 *
 * @author Nacos
 */
@Component
public class A2aCompatibilityOperationService implements A2aOperationService {
    
    private final A2aCompatibilityModeResolver modeResolver;
    
    private final A2aServerOperationService canonicalService;
    
    private final LegacyA2aOperationService legacyService;
    
    // TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.
    // Keep canonical behavior independent from this branch.
    private final A2aMigrationDefinitionWriteAfterHook migrationWriteAfterHook;
    
    // TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.
    // Keep canonical behavior independent from this branch.
    private final A2aMigrationLegacyMutationGuard migrationMutationGuard;
    
    public A2aCompatibilityOperationService(A2aCompatibilityModeResolver modeResolver,
        A2aServerOperationService canonicalService, LegacyA2aOperationService legacyService,
        A2aMigrationDefinitionWriteAfterHook migrationWriteAfterHook,
        A2aMigrationLegacyMutationGuard migrationMutationGuard) {
        this.modeResolver = modeResolver;
        this.canonicalService = canonicalService;
        this.legacyService = legacyService;
        this.migrationWriteAfterHook = migrationWriteAfterHook;
        this.migrationMutationGuard = migrationMutationGuard;
    }
    
    @Override
    public void registerAgent(AgentCard agentCard, String namespaceId, String registrationType)
        throws NacosException {
        migrationMutationGuard.checkMutable();
        A2aOperationService selected = current();
        selected.registerAgent(agentCard, namespaceId, registrationType);
        notifyLegacyMutation(selected, namespaceId, agentCard.getName());
    }
    
    @Override
    public void releaseAgent(AgentCard agentCard, String namespaceId, String registrationType,
        boolean setAsLatest) throws NacosException {
        migrationMutationGuard.checkMutable();
        A2aOperationService selected = current();
        selected.releaseAgent(agentCard, namespaceId, registrationType, setAsLatest);
        notifyLegacyMutation(selected, namespaceId, agentCard.getName());
    }
    
    @Override
    public void updateAgentCard(AgentCard agentCard, String namespaceId, String registrationType,
        boolean setAsLatest) throws NacosException {
        migrationMutationGuard.checkMutable();
        A2aOperationService selected = current();
        selected.updateAgentCard(agentCard, namespaceId, registrationType, setAsLatest);
        notifyLegacyMutation(selected, namespaceId, agentCard.getName());
    }
    
    @Override
    public void deleteAgent(String namespaceId, String agentName, String version)
        throws NacosException {
        migrationMutationGuard.checkMutable();
        A2aOperationService selected = current();
        selected.deleteAgent(namespaceId, agentName, version);
        notifyLegacyMutation(selected, namespaceId, agentName);
    }
    
    @Override
    public AgentCardDetailInfo getAgentCard(String namespaceId, String agentName, String version,
        String registrationType) throws NacosException {
        return current().getAgentCard(namespaceId, agentName, version, registrationType);
    }
    
    @Override
    public AgentCardDetailInfo getAgentCardForClient(String namespaceId, String agentName,
        String version, String registrationType) throws NacosException {
        return current().getAgentCardForClient(namespaceId, agentName, version, registrationType);
    }
    
    @Override
    public Page<AgentCardVersionInfo> listAgents(String namespaceId, String agentName,
        String search, int pageNo, int pageSize) throws NacosException {
        return current().listAgents(namespaceId, agentName, search, pageNo, pageSize);
    }
    
    @Override
    public List<AgentVersionDetail> listAgentVersions(String namespaceId, String name)
        throws NacosException {
        return current().listAgentVersions(namespaceId, name);
    }
    
    private A2aOperationService current() {
        return A2aCompatibilityMode.CANONICAL == modeResolver.resolve() ? canonicalService
            : legacyService;
    }
    
    private void notifyLegacyMutation(A2aOperationService selected, String namespaceId,
        String agentName) {
        if (selected == legacyService) {
            migrationWriteAfterHook.afterSuccessfulMutation(namespaceId, agentName);
        }
    }
}
