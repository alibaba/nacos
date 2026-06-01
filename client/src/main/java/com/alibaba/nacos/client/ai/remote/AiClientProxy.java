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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.lifecycle.Closeable;

/**
 * AI client proxy interface for abstracting transport layer (gRPC / HTTP).
 *
 * <p>Defines AI operations that support switching between gRPC and HTTP transport.</p>
 *
 * @author nacos
 */
public interface AiClientProxy extends Closeable {
    
    /**
     * Query prompt by latest/version/label with optional md5 for conditional query.
     *
     * @param promptKey prompt key
     * @param version   prompt version, optional
     * @param label     prompt label, optional
     * @param md5       client md5 for conditional query, optional
     * @return prompt detail
     * @throws NacosException if request parameter is invalid or handle error
     */
    Prompt queryPrompt(String promptKey, String version, String label, String md5)
        throws NacosException;
    
    /**
     * Query skill by latest/version/label with optional md5 for conditional download.
     *
     * <p>When {@code md5} matches the server-published content fingerprint, the implementation
     * MUST throw {@link NacosException} with code {@link NacosException#NOT_MODIFIED} so the
     * caller can keep its local cache.
     *
     * @param skillName skill name
     * @param version   skill version, optional
     * @param label     skill label, optional
     * @param md5       client md5 for conditional query, optional
     * @return skill ZIP bytes plus the published content MD5 and resolved version headers
     * @throws NacosException if request parameter is invalid or handle error
     */
    SkillQueryResponse querySkill(String skillName, String version, String label, String md5)
        throws NacosException;
    
    /**
     * Query agentspec by latest/version/label with optional md5 for conditional query.
     *
     * <p>When {@code md5} matches the server-published content fingerprint, the implementation
     * MUST throw {@link NacosException} with code {@link NacosException#NOT_MODIFIED} so the
     * caller can keep its local cache.
     *
     * @param agentSpecName agentspec name
     * @param version       agentspec version, optional
     * @param label         agentspec label, optional
     * @param md5           client md5 for conditional query, optional
     * @return agentspec plus the published content MD5 and resolved version headers
     * @throws NacosException if request parameter is invalid or handle error
     */
    AgentSpecQueryResponse queryAgentSpec(String agentSpecName, String version, String label,
        String md5) throws NacosException;
}
