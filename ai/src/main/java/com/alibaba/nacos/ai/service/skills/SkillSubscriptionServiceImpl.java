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
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscription;
import com.alibaba.nacos.api.ai.model.skills.SkillSubscriptionDocument;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill subscription service implementation.
 *
 * @author nacos
 */
@Service
public class SkillSubscriptionServiceImpl implements SkillSubscriptionService {
    
    private static final String ANONYMOUS_SUBSCRIBER = "anonymous";
    
    private final SkillSubscriptionStorage storage;
    
    private final SkillOperationService skillOperationService;
    
    public SkillSubscriptionServiceImpl(SkillSubscriptionStorage storage,
        SkillOperationService skillOperationService) {
        this.storage = storage;
        this.skillOperationService = skillOperationService;
    }
    
    @Override
    public SkillSubscriptionDocument listSubscriptions(String namespaceId) throws NacosException {
        String resolvedNamespaceId = resolveNamespaceId(namespaceId);
        String subscriber = resolveSubscriber();
        SkillSubscriptionDocument document = storage.get(resolvedNamespaceId, subscriber);
        return normalizeDocument(resolvedNamespaceId, subscriber, document);
    }
    
    @Override
    public SkillSubscriptionDocument subscribe(String namespaceId,
        List<SkillSubscription> subscriptions) throws NacosException {
        String resolvedNamespaceId = resolveNamespaceId(namespaceId);
        String subscriber = resolveSubscriber();
        SkillSubscriptionDocument document = storage.get(resolvedNamespaceId, subscriber);
        SkillSubscriptionDocument normalizedDocument = normalizeDocument(resolvedNamespaceId,
            subscriber, document);
        Map<String, SkillSubscription> merged = toSubscriptionMap(
            normalizedDocument.getSubscriptions());
        if (subscriptions != null) {
            for (SkillSubscription item : subscriptions) {
                SkillSubscription normalized = normalizeSubscription(resolvedNamespaceId, item);
                merged.put(normalized.getName(), normalized);
            }
        }
        SkillSubscriptionDocument updated = buildDocument(resolvedNamespaceId, subscriber,
            normalizedDocument, merged);
        storage.save(resolvedNamespaceId, subscriber, updated);
        return updated;
    }
    
    @Override
    public SkillSubscriptionDocument unsubscribe(String namespaceId, List<String> names)
        throws NacosException {
        String resolvedNamespaceId = resolveNamespaceId(namespaceId);
        String subscriber = resolveSubscriber();
        List<String> normalizedNames = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (StringUtils.isNotBlank(name)) {
                    normalizedNames.add(name.trim());
                }
            }
        }
        if (normalizedNames.isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING, "Skill subscription name is required");
        }
        SkillSubscriptionDocument document = storage.get(resolvedNamespaceId, subscriber);
        SkillSubscriptionDocument normalizedDocument = normalizeDocument(resolvedNamespaceId,
            subscriber, document);
        Map<String, SkillSubscription> remaining = toSubscriptionMap(
            normalizedDocument.getSubscriptions());
        for (String name : normalizedNames) {
            remaining.remove(name);
        }
        SkillSubscriptionDocument updated = buildDocument(resolvedNamespaceId, subscriber,
            normalizedDocument, remaining);
        storage.save(resolvedNamespaceId, subscriber, updated);
        return updated;
    }
    
    private SkillSubscription normalizeSubscription(String namespaceId, SkillSubscription item)
        throws NacosException {
        if (item == null || StringUtils.isBlank(item.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING, "Skill subscription name is required");
        }
        String name = item.getName().trim();
        skillOperationService.getSkillDetail(namespaceId, name);
        SkillSubscription normalized = new SkillSubscription();
        normalized.setName(name);
        return normalized;
    }
    
    private SkillSubscriptionDocument normalizeDocument(String namespaceId, String subscriber,
        SkillSubscriptionDocument document) {
        Map<String, SkillSubscription> map = document == null ? new LinkedHashMap<>()
            : toSubscriptionMap(document.getSubscriptions());
        return buildDocument(namespaceId, subscriber, document, map);
    }
    
    private SkillSubscriptionDocument buildDocument(String namespaceId, String subscriber,
        SkillSubscriptionDocument source, Map<String, SkillSubscription> subscriptions) {
        SkillSubscriptionDocument document = new SkillSubscriptionDocument();
        document.setNamespaceId(namespaceId);
        document.setSubscriber(subscriber);
        if (source != null) {
            document.setGroupId(source.getGroupId());
            document.setDataId(source.getDataId());
        }
        List<SkillSubscription> items = new ArrayList<>(subscriptions.values());
        items.sort(Comparator.comparing(SkillSubscription::getName));
        document.setSubscriptions(items);
        return document;
    }
    
    private Map<String, SkillSubscription> toSubscriptionMap(
        List<SkillSubscription> subscriptions) {
        Map<String, SkillSubscription> result = new LinkedHashMap<>();
        if (subscriptions == null) {
            return result;
        }
        for (SkillSubscription item : subscriptions) {
            if (item == null || StringUtils.isBlank(item.getName())) {
                continue;
            }
            SkillSubscription normalized = new SkillSubscription();
            normalized.setName(item.getName().trim());
            result.put(normalized.getName(), normalized);
        }
        return result;
    }
    
    private String resolveNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.Skills.SKILL_DEFAULT_NAMESPACE
            : namespaceId;
    }
    
    private String resolveSubscriber() {
        String currentIdentity = VisibilityHelper.resolveCurrentIdentity();
        if (StringUtils.isNotBlank(currentIdentity)) {
            return currentIdentity;
        }
        String requestUsername = resolveRequestUsername();
        return StringUtils.isBlank(requestUsername) ? ANONYMOUS_SUBSCRIBER : requestUsername;
    }
    
    private String resolveRequestUsername() {
        try {
            RequestAttributes attributes =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes)) {
                return StringUtils.EMPTY;
            }
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            String username = request.getParameter(
                com.alibaba.nacos.api.common.Constants.USERNAME);
            return StringUtils.isBlank(username) ? request.getHeader(
                com.alibaba.nacos.api.common.Constants.USERNAME) : username;
        } catch (Exception ignored) {
            return StringUtils.EMPTY;
        }
    }
}
