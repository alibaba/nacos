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
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * AgentService operations obtained from {@link AiService#agent()}.
 *
 * @author Nacos
 * @since 3.3.0
 */
public interface AgentService extends A2aService, AgentDiscoveryService {
    
    /**
     * Publish one exact Agent Version from application code.
     *
     * <p>The AiService namespace is used automatically. By default this creates a draft;
     * {@link AgentPublishRequest#isAutoSubmit()} requests the ordinary submit pipeline and never
     * force-publishes a Version.</p>
     *
     * @param request Agent definition publication request
     * @return resulting exact Version detail
     * @throws NacosException when validation, publication, or submit fails
     */
    @Since("3.3.0")
    default AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent publication is not implemented by this AgentService.");
    }
}
