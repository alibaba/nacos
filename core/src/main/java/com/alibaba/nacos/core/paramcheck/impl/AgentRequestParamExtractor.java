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

package com.alibaba.nacos.core.paramcheck.impl;

import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.List;

/**
 * Nacos A2A(Agent & Agent Card) grpc request param extractor.
 *
 * @author xiweng.yy
 */
public class AgentRequestParamExtractor extends AbstractRpcParamExtractor {
    
    @Override
    public List<ParamInfo> extractParam(Request request) throws NacosException {
        AbstractAgentRequest agentRequest = (AbstractAgentRequest) request;
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId(agentRequest.getNamespaceId());
        paramInfo.setAgentName(agentRequest.getAgentName());
        if (agentRequest instanceof ReleaseAgentCardRequest) {
            ReleaseAgentCardRequest releaseAgentCardRequest = (ReleaseAgentCardRequest) agentRequest;
            if (null != releaseAgentCardRequest.getAgentCard()) {
                paramInfo.setAgentName(releaseAgentCardRequest.getAgentCard().getName());
            }
        }
        return List.of(paramInfo);
    }
}
