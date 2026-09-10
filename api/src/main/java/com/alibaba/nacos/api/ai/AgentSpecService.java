/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * AgentSpecService operations obtained from {@link AiService#agentSpec()}.
 *
 * @author Nacos
 * @since 3.3.0
 */
public interface AgentSpecService {
    
    /**
     * Load agent spec by agent spec name.
     *
     * <p>
     * This method will query the agent spec main configuration and all resource configurations,
     * then assemble them into a complete AgentSpec object.
     * </p>
     *
     * @param agentSpecName agent spec name (unique identifier)
     * @return complete AgentSpec object with all resources
     * @throws NacosException if agent spec not found or query error
     */
    @Since("3.2.0")
    AgentSpec loadAgentSpec(String agentSpecName) throws NacosException;
    
    /**
     * Subscribe agent spec.
     *
     * @param agentSpecName       name of agent spec
     * @param agentSpecListener   listener of agent spec, callback when agent spec configuration is changed
     * @return The agent spec object at current time, nullable if agent spec not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    AgentSpec subscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException;
    
    /**
     * Un-subscribe agent spec.
     *
     * @param agentSpecName       name of agent spec
     * @param agentSpecListener   listener of agent spec
     * @throws NacosException if request parameter is invalid or handle error
     */
    @Since("3.2.0")
    void unsubscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException;
}
