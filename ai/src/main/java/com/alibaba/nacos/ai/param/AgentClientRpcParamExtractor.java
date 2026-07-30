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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.api.ai.remote.request.AbstractAgentClientRpcRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.Collections;
import java.util.List;

/**
 * Parameter extractor for Agent Client gRPC bindings.
 *
 * <p>This extractor projects the common request identity into the generic parameter checker.
 * Complete RAD semantic validation remains at the domain-service boundary.</p>
 *
 * @author Nacos
 */
public class AgentClientRpcParamExtractor extends AbstractRpcParamExtractor {
    
    @Override
    public List<ParamInfo> extractParam(Request request) throws NacosException {
        ParamInfo result = new ParamInfo();
        if (request instanceof AbstractAgentClientRpcRequest) {
            AbstractAgentClientRpcRequest agentRequest =
                (AbstractAgentClientRpcRequest) request;
            result.setNamespaceId(agentRequest.extractNamespaceId());
            result.setAgentName(agentRequest.extractAgentName());
        }
        return Collections.singletonList(result);
    }
}
