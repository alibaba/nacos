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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.http.HttpStatus;

/**
 * Compatibility gate for a small number of deprecated APIs pending removal.
 *
 * @author xiweng.yy
 */
public final class CompatibilityHelper {
    
    public static final String API_COMPATIBILITY_ENABLED_KEY =
        "nacos.core.api.compatibility.enabled";
    
    private static final String DEPRECATED_API_MESSAGE =
        "Current API is deprecated. Please use API(s) `%s` instead, or set `%s=true` "
            + "in application.properties during migration.";
    
    private CompatibilityHelper() {
    }
    
    /**
     * Check whether deprecated API compatibility is enabled.
     *
     * @param alternatives replacement APIs
     * @throws NacosApiException if deprecated APIs are disabled
     */
    public static void check(String alternatives) throws NacosApiException {
        if (EnvUtil.getProperty(API_COMPATIBILITY_ENABLED_KEY, Boolean.class, false)) {
            return;
        }
        throw new NacosApiException(HttpStatus.GONE.value(), ErrorCode.API_DEPRECATED,
            String.format(DEPRECATED_API_MESSAGE, alternatives,
                API_COMPATIBILITY_ENABLED_KEY));
    }
}
