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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.utils.A2aRadConverter;

/**
 * Single-attempt RAD query and publication binding for the legacy Card facade.
 *
 * @author Nacos
 */
public class A2aRadClientAdapter {
    
    private final String namespaceId;
    
    private final AgentClientProxy proxy;
    
    public A2aRadClientAdapter(String namespaceId, AgentClientProxy proxy) {
        this.namespaceId = namespaceId;
        this.proxy = proxy;
    }
    
    /**
     * Discover and project a complete Card using one RAD read.
     * @param name Agent name
     * @param version exact Version, or blank for latest
     * @param registrationType optional projection override
     * @return projected Card
     * @throws NacosException when discovery or projection fails
     */
    public AgentCardDetailInfo getAgentCard(String name, String version,
        String registrationType) throws NacosException {
        A2aRadConverter.normalizeRegistrationType(registrationType, null);
        AgentDiscoveryRequest request =
            A2aRadConverter.discoveryRequest(namespaceId, name, version);
        return A2aRadConverter.project(proxy.discoverAgent(request), request, registrationType);
    }
    
    /**
     * Publish a complete Card through the Client publication state machine once.
     * @param card caller-owned Card
     * @param registrationType definition preference
     * @param setAsLatest ordinary autoSubmit flag
     * @throws NacosException when conversion or publication fails
     */
    public void releaseAgentCard(AgentCard card, String registrationType, boolean setAsLatest)
        throws NacosException {
        proxy.publishAgent(A2aRadConverter.publishRequest(namespaceId, card,
            registrationType, setAsLatest));
    }
}
