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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import org.springframework.stereotype.Component;

/**
 * Visibility-based owner recheck that deliberately stores and replays no credential.
 *
 * @author Nacos
 */
@Component
public class DefaultAgentWatchOwnerEligibilityChecker
    implements AgentWatchOwnerEligibilityChecker {
    
    private final AgentPersistenceService persistenceService;
    
    public DefaultAgentWatchOwnerEligibilityChecker(
        AgentPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }
    
    @Override
    public AgentWatchOwnerEligibility evaluate(AgentWatchOwnerContext owner,
        AgentProjectionKey key) {
        try {
            Agent agent = persistenceService.getAgent(key.getNamespaceId(), key.getAgentName());
            AgentVisibilityResource resource = new AgentVisibilityResource(agent);
            return VisibilityHelper.canReadResource(owner.getIdentity(), owner.getApiType(),
                resource) ? AgentWatchOwnerEligibility.ALLOWED
                    : AgentWatchOwnerEligibility.DENIED;
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND
                || e.getErrCode() == NacosException.RESOURCE_NOT_FOUND) {
                return AgentWatchOwnerEligibility.DENIED;
            }
            return AgentWatchOwnerEligibility.UNCERTAIN;
        } catch (RuntimeException e) {
            return AgentWatchOwnerEligibility.UNCERTAIN;
        }
    }
    
    private static class AgentVisibilityResource extends VisibilityResource {
        
        private final String namespaceId;
        
        private final String agentName;
        
        AgentVisibilityResource(Agent agent) {
            namespaceId = agent.getNamespaceId();
            agentName = agent.getAgentName();
            setOwner(agent.getOwner());
            setScope(agent.getScope());
        }
        
        @Override
        public String getNamespaceId() {
            return namespaceId;
        }
        
        @Override
        public String getResourceName() {
            return agentName;
        }
        
        @Override
        public String getResourceType() {
            return Constants.Agent.RESOURCE_TYPE_AGENT;
        }
    }
}
