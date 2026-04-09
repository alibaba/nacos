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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper for ai_resource_version persistence trace logging.
 */
final class AiResourceVersionTraceHelper {
    
    static final String TRACE_PERSIST_SUCCESS_ENABLED_KEY = "nacos.ai.resource.trace.persist.success.enabled";
    
    private AiResourceVersionTraceHelper() {
    }
    
    static boolean isPersistSuccessTraceEnabled() {
        return EnvUtil.getProperty(TRACE_PERSIST_SUCCESS_ENABLED_KEY, Boolean.class, false);
    }
    
    static void traceSuccess(String resourceType, String resourceName, String version, String sqlOperation, int rowsAffected) {
        if (!isPersistSuccessTraceEnabled()) {
            return;
        }
        AiResourceTraceService.logSuccess(resourceType, resourceName, version, AiResourceTraceService.OP_VERSION_ROW_PERSIST,
                "-", "-", buildExt(sqlOperation, rowsAffected, null));
    }
    
    static void traceFailure(String resourceType, String resourceName, String version, String sqlOperation, String errorMsg) {
        AiResourceTraceService.logFailure(resourceType, resourceName, version, AiResourceTraceService.OP_VERSION_ROW_PERSIST,
                "-", "-", buildExt(sqlOperation, null, errorMsg));
    }
    
    static String buildExt(String sqlOperation, Integer rowsAffected, String errorMsg) {
        Map<String, Object> ext = new LinkedHashMap<>(4);
        ext.put("sqlOperation", StringUtils.defaultIfBlank(sqlOperation, "-"));
        if (rowsAffected != null) {
            ext.put("rowsAffected", rowsAffected);
        }
        if (StringUtils.isNotBlank(errorMsg)) {
            ext.put("error", errorMsg);
        }
        return JacksonUtils.toJson(ext);
    }
}
