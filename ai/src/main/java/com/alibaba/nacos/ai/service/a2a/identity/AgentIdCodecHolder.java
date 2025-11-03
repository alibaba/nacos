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

package com.alibaba.nacos.ai.service.a2a.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The Holder of {@link AgentIdCodec}.
 *
 * @author xiweng.yy
 */
@Component
public class AgentIdCodecHolder {
    
    private final AgentIdCodec agentIdCodec;
    
    public AgentIdCodecHolder(ObjectProvider<AgentIdCodec> agentIdCodecsProvider) {
        this.agentIdCodec = agentIdCodecsProvider.getIfAvailable(AsciiAgentIdCodec::new);
    }
    
    /**
     * Encode agent name to identity.
     *
     * @param agentName agent name
     * @return identity encoded from agent name
     */
    public String encode(String agentName) {
        return agentIdCodec.encode(agentName);
    }
    
    /**
     * Encode agent name to identity for search, which means only do encode value without any prefix and suffix, used to do blur search.
     *
     * @param agentName agent name
     * @return identity encoded from agent name
     */
    public String encodeForSearch(String agentName) {
        return agentIdCodec.encodeForSearch(agentName);
    }
    
    /**
     * Decode agent id to agent name.
     *
     * @param agentId agent identity
     * @return agent name
     */
    public String decode(String agentId) {
        return agentIdCodec.decode(agentId);
    }
}
