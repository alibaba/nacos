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

package com.alibaba.nacos.ai.remote.handler;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.service.prompt.PromptClientOperationService;
import com.alibaba.nacos.ai.utils.PromptConvertUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryPromptRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryPromptResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.PromptRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nacos AI module query prompt request handler.
 *
 * @author nacos
 */
@Since("3.2.0")
@Component
public class QueryPromptRequestHandler
    extends RequestHandler<QueryPromptRequest, QueryPromptResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryPromptRequestHandler.class);
    
    private final PromptClientOperationService promptOperationService;
    
    public QueryPromptRequestHandler(PromptClientOperationService promptOperationService) {
        this.promptOperationService = promptOperationService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = PromptRequestParamExtractor.class)
    @Secured(action = ActionTypes.READ, signType = SignType.AI)
    public QueryPromptResponse handle(QueryPromptRequest request, RequestMeta meta) {
        request.setNamespaceId(NamespaceUtil.processNamespaceParameter(request.getNamespaceId()));
        if (StringUtils.isBlank(request.getPromptKey())) {
            QueryPromptResponse errorResponse = new QueryPromptResponse();
            errorResponse.setErrorInfo(NacosException.INVALID_PARAM,
                "parameters `promptKey` can't be empty or null");
            return errorResponse;
        }
        QueryPromptResponse response = new QueryPromptResponse();
        try {
            PromptVersionInfo result = promptOperationService.queryPrompt(
                request.getNamespaceId(), request.getPromptKey(), request.getVersion(),
                request.getLabel(), request.getMd5());
            response.setPromptInfo(PromptConvertUtils.toClientPrompt(result));
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_MODIFIED) {
                response.setErrorInfo(NacosException.NOT_MODIFIED, "prompt data is up to date");
                return response;
            }
            LOGGER.error("Query prompt {} error: {}", request.getPromptKey(), e.getErrMsg());
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
        }
        return response;
    }
}
