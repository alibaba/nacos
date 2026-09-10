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

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;

/** Prompt query routing shared by direct reads and the existing polling cache. */
public class PromptTransportRouter extends RequiredAiGrpcClientProxy {
    
    private final AgentTransportMode mode;
    
    private final AgentGrpcTransport grpcTransport;
    
    private final AiHttpClientProxy httpProxy;
    
    public PromptTransportRouter(AgentTransportMode mode, AgentGrpcTransport grpcTransport,
        AiHttpClientProxy httpProxy) {
        super(grpcTransport);
        this.mode = mode;
        this.grpcTransport = grpcTransport;
        this.httpProxy = httpProxy;
    }
    
    @Override
    public Prompt queryPrompt(String promptKey, String version, String label, String md5)
        throws NacosException {
        if (mode == AgentTransportMode.GRPC || mode == AgentTransportMode.AUTO
            && grpcTransport.isAvailable(AgentGrpcTransport.Resource.PROMPT)) {
            try {
                return grpcTransport.acquireProtocolNeutralClient()
                    .queryPrompt(promptKey, version, label, md5);
            } catch (NacosException | NacosRuntimeException e) {
                if (mode != AgentTransportMode.AUTO
                    || !AiTransportExceptionUtils.isConnectionFailure(e)) {
                    throw e;
                }
            }
        }
        try {
            Prompt result = httpProxy.queryPrompt(promptKey, version, label, md5);
            grpcTransport.recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
            return result;
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_MODIFIED) {
                grpcTransport.recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
            }
            throw e;
        }
    }
}
