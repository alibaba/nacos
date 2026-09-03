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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Temporary generic-Agent write guard for Nacos 3.0-3.2 A2A migration projections.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationAgentMutationGuard {
    
    private final A2aMigrationStateService stateService;
    
    public A2aMigrationAgentMutationGuard(A2aMigrationStateService stateService) {
        this.stateService = stateService;
    }
    
    /**
     * Reject a generic Agent mutation while the target remains migration-owned.
     *
     * @param resource writable Agent resource already authorized for the caller
     * @throws NacosApiException when the historical source is still authoritative
     */
    public void checkMutable(AiResource resource) throws NacosApiException {
        if (resource == null || !A2aMigrationTargetStore.MIGRATION_RESOURCE_SOURCE.equals(
            resource.getFrom())) {
            return;
        }
        A2aMigrationState state = stateService.resolveConfigured();
        if (A2aMigrationState.CANONICAL != state) {
            throw new NacosApiException(NacosException.CONFLICT,
                ErrorCode.AGENT_MIGRATION_IN_PROGRESS,
                "Agent migration is in progress; historical A2A remains authoritative for Agent: "
                    + resource.getName());
        }
    }
}
