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
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Request helpers for skill subscription APIs.
 *
 * @author nacos
 */
final class SkillSubscriptionRequestUtil {
    
    private static final String ANONYMOUS_SUBSCRIBER = "anonymous";
    
    private SkillSubscriptionRequestUtil() {
    }
    
    static String resolveNamespaceId(String namespaceId) {
        return StringUtils.isBlank(namespaceId) ? Constants.Skills.SKILL_DEFAULT_NAMESPACE
            : namespaceId;
    }
    
    static String resolveSubscriber() {
        String currentIdentity = VisibilityHelper.resolveCurrentIdentity();
        if (StringUtils.isNotBlank(currentIdentity)) {
            return currentIdentity;
        }
        String requestUsername = resolveRequestUsername();
        return StringUtils.isBlank(requestUsername) ? ANONYMOUS_SUBSCRIBER : requestUsername;
    }
    
    private static String resolveRequestUsername() {
        try {
            RequestAttributes attributes =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes)) {
                return StringUtils.EMPTY;
            }
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            String username = request.getParameter(com.alibaba.nacos.api.common.Constants.USERNAME);
            return StringUtils.isBlank(username)
                ? request.getHeader(com.alibaba.nacos.api.common.Constants.USERNAME) : username;
        } catch (Exception ignored) {
            return StringUtils.EMPTY;
        }
    }
}
