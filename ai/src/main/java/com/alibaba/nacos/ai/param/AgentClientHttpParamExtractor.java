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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.agent.client.AgentWatchBatchForm;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;

/**
 * Agent Client HTTP API parameter extractor.
 *
 * @author Nacos
 */
public class AgentClientHttpParamExtractor extends AbstractHttpParamExtractor {
    
    @Override
    public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
        ParamInfo result = new ParamInfo();
        String requestUri = request.getRequestURI();
        if (requestUri != null
            && requestUri.endsWith(Constants.Agent.CLIENT_PATH + "/watch")) {
            try {
                result.setNamespaceId(
                    AgentWatchBatchForm.extractNamespaceId(request.getParameter("watches")));
            } catch (NacosApiException ignored) {
                // Leave malformed Watch payload validation to the controller form. Throwing from
                // an HTTP parameter extractor is wrapped by the shared parameter-check filter and
                // would prevent the API exception handler from returning the canonical 400 body.
            }
        } else {
            result.setNamespaceId(request.getParameter("namespaceId"));
            result.setAgentName(request.getParameter("agentName"));
        }
        return Collections.singletonList(result);
    }
}
