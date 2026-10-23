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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityMode;
import com.alibaba.nacos.ai.service.a2a.A2aCompatibilityModeResolver;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Admission guard for external RAD business requests while historical A2A is authoritative.
 *
 * <p>Cleanup and shared domain services deliberately do not use this guard.</p>
 *
 * @author Nacos
 */
@Component
public class AgentClientMigrationGuard {
    
    private final A2aCompatibilityModeResolver modeResolver;
    
    public AgentClientMigrationGuard(A2aCompatibilityModeResolver modeResolver) {
        this.modeResolver = modeResolver;
    }
    
    /**
     * Require canonical authority after binding authorization and before RAD admission.
     *
     * @throws NacosApiException when this node still uses historical A2A authority
     */
    public void checkReady() throws NacosApiException {
        if (modeResolver.resolve() != A2aCompatibilityMode.CANONICAL) {
            throw new NacosApiException(NacosException.CONFLICT,
                ErrorCode.AGENT_MIGRATION_IN_PROGRESS,
                "RAD is unavailable while historical A2A remains authoritative; "
                    + "retry explicitly after migration completes.");
        }
    }
}
