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

package com.alibaba.nacos.ai.service.agent.identity;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

/**
 * Composer for the {@code rad-service-name-v1} Naming service identity.
 *
 * @author Nacos
 */
public final class RadServiceNameComposer {
    
    /**
     * Stable composer identifier recorded by the Agent storage contract.
     */
    public static final String COMPOSER_ID = "rad-service-name-v1";
    
    private static final String PREFIX = "rad-";
    
    private RadServiceNameComposer() {
    }
    
    /**
     * Compose a Naming service name from the original Agent name and a protocol token.
     *
     * <p>Version 1 favors a concise, readable physical name and accepts the rare tuple ambiguity
     * caused by hyphens in both components. For example, {@code (A, B-C)} and {@code (A-B, C)}
     * both compose to {@code rad-A-B-C}. Agent Version is intentionally excluded.</p>
     *
     * @param agentName original public Agent name
     * @param protocol canonical Agent protocol token
     * @return service name containing only {@code [A-Za-z0-9-]}
     * @throws IllegalArgumentException when either input violates the Agent contract
     */
    public static String compose(String agentName, String protocol) {
        AgentValidationUtils.validateProtocol(protocol);
        String encodedAgentId = RadAsciiAgentIdCodec.encode(agentName);
        return PREFIX + encodedAgentId + '-' + protocol;
    }
}
