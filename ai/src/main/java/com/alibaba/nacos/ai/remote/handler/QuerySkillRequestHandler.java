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

import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.remote.request.QuerySkillRequest;
import com.alibaba.nacos.api.ai.remote.response.QuerySkillResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.filter.NamespaceValidation;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.SkillRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nacos AI module query skill request handler.
 *
 * @author nacos
 */
@Component
public class QuerySkillRequestHandler extends RequestHandler<QuerySkillRequest, QuerySkillResponse> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(QuerySkillRequestHandler.class);
    
    private final SkillOperationService skillOperationService;
    
    public QuerySkillRequestHandler(SkillOperationService skillOperationService) {
        this.skillOperationService = skillOperationService;
    }
    
    @Override
    @NamespaceValidation
    @ExtractorManager.Extractor(rpcExtractor = SkillRequestParamExtractor.class)
    @Secured(action = ActionTypes.READ, signType = SignType.AI)
    public QuerySkillResponse handle(QuerySkillRequest request, RequestMeta meta) {
        request.setNamespaceId(NamespaceUtil.processNamespaceParameter(request.getNamespaceId()));
        if (StringUtils.isBlank(request.getSkillName())) {
            QuerySkillResponse errorResponse = new QuerySkillResponse();
            errorResponse.setErrorInfo(NacosException.INVALID_PARAM, "parameter `skillName` can't be empty or null");
            return errorResponse;
        }
        QuerySkillResponse response = new QuerySkillResponse();
        try {
            Skill skill = skillOperationService.querySkill(request.getNamespaceId(), request.getSkillName(),
                    request.getVersion(), request.getLabel());
            response.setSkill(skill);
        } catch (NacosException e) {
            LOGGER.error("Query skill {} error: {}", request.getSkillName(), e.getErrMsg());
            response.setErrorInfo(e.getErrCode(), e.getErrMsg());
        }
        return response;
    }
}
