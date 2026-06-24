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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.springframework.stereotype.Service;

/**
 * Runtime service implementation for querying skill subscriptions from config dump/cache.
 *
 * @author nacos
 */
@Service
public class SkillClientSubscriptionServiceImpl implements SkillClientSubscriptionService {
    
    private final ConfigQueryChainService configQueryChainService;
    
    public SkillClientSubscriptionServiceImpl(ConfigQueryChainService configQueryChainService) {
        this.configQueryChainService = configQueryChainService;
    }
    
    @Override
    public SkillSubscriptionDocument listSubscriptions(String namespaceId)
        throws NacosException {
        String resolvedNamespaceId = SkillSubscriptionRequestUtil.resolveNamespaceId(namespaceId);
        String resolvedSubscriber = SkillSubscriptionRequestUtil.resolveSubscriber();
        String dataId = ConfigSkillSubscriptionStorage.buildDataId(resolvedSubscriber);
        ConfigQueryChainRequest request =
            ConfigQueryChainRequest.buildConfigQueryChainRequest(dataId,
                Constants.Skills.SKILL_SUBSCRIPTION_GROUP, resolvedNamespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        if (ResponseCode.FAIL.getCode() == response.getResultCode()) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                response.getMessage());
        }
        if (response
            .getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Skill subscription config is being modified, please retry later.");
        }
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
            || response.getContent() == null) {
            return emptyDocument(resolvedNamespaceId, resolvedSubscriber);
        }
        SkillSubscriptionDocument document = JacksonUtils.toObj(response.getContent(),
            SkillSubscriptionDocument.class);
        return document == null ? emptyDocument(resolvedNamespaceId, resolvedSubscriber)
            : normalizeDocument(resolvedNamespaceId, resolvedSubscriber, document);
    }
    
    private SkillSubscriptionDocument normalizeDocument(String namespaceId, String subscriber,
        SkillSubscriptionDocument document) {
        document.setNamespaceId(namespaceId);
        document.setSubscriber(subscriber);
        document.setGroupId(Constants.Skills.SKILL_SUBSCRIPTION_GROUP);
        document.setDataId(ConfigSkillSubscriptionStorage.buildDataId(subscriber));
        return document;
    }
    
    private SkillSubscriptionDocument emptyDocument(String namespaceId, String subscriber) {
        return normalizeDocument(namespaceId, subscriber, new SkillSubscriptionDocument());
    }
}
