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
import com.alibaba.nacos.api.ai.converter.AgentConverter;
import com.alibaba.nacos.api.ai.converter.AgentConverters;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
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
     * Obtain the offline A2A converter for explicit application use.
     *
     * <p>Equivalent to {@link AgentConverters#a2a()}; no connection or capability query is
     * performed. The converter remains usable after this service is closed. Discovery and
     * listeners do not invoke it automatically.
     *
     * @return reusable converter producing independent AgentCard objects
     * @since 3.3.0
     */
    default AgentConverter<AgentCard> a2aConverter() {
        return AgentConverters.a2a();
    }
    
    /**
     * Publish one exact Agent Version from application code.
     *
     * <p>The AiService namespace is used automatically. A newly created first Version is submitted
     * even when {@link AgentPublishRequest#isAutoSubmit()} is false. "First" means the Agent had
     * no Versions before this creation. Other drafts follow that flag; existing non-drafts remain
     * unchanged. Submission uses the ordinary pipeline: without a Pipeline it publishes and sets
     * latest, while a configured Pipeline may delay publication. It never force-publishes.</p>
     *
     * @param request Agent definition publication request
     * @return resulting exact Version detail
     * @throws NacosException when validation, publication, or submit fails
     */
    @Since("3.3.0")
    default AgentVersionDetail publishAgent(AgentPublishRequest request)
        throws NacosException {
        throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
            "Agent publication is not implemented by this AgentService.");
    }
}
