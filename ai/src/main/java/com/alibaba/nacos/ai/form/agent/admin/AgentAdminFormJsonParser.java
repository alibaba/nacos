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

package com.alibaba.nacos.ai.form.agent.admin;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * JSON field parser shared by Agent Admin forms.
 *
 * @author Nacos
 */
final class AgentAdminFormJsonParser {
    
    private AgentAdminFormJsonParser() {
    }
    
    static <T> T parseOptional(String fieldName, String value, Class<T> targetType)
        throws NacosApiException {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(value, targetType);
        } catch (NacosDeserializationException e) {
            throw invalidJson(fieldName);
        }
    }
    
    static <T> T parseOptional(String fieldName, String value,
        TypeReference<T> targetType) throws NacosApiException {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(value, targetType);
        } catch (NacosDeserializationException e) {
            throw invalidJson(fieldName);
        }
    }
    
    private static NacosApiException invalidJson(String fieldName) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Request parameter `" + fieldName + "` is not valid JSON.");
    }
}
