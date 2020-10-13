/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * Tenant(namespace) Util.
 * Because config and naming treat tenant(namespace) differently,
 * this tool class can only be used by the config module.
 * @author klw(213539@qq.com)
 * @date 2020/10/12 17:56
 */
public class TenantUtil {
    
    private static final String NAMESPACE_PUBLIC_KEY = "public";
    
    private static final String NAMESPACE_NULL_KEY = "null";
    
    /**
     * Treat the tenant parameters with values of "public" and "null" as an empty string.
     * @param tenant tenant(namespace) id
     * @return java.lang.String A tenant(namespace) string processed
     */
    public static String processTenantParameter(String tenant) {
        if (StringUtils.isBlank(tenant) || NAMESPACE_PUBLIC_KEY.equalsIgnoreCase(tenant) || NAMESPACE_NULL_KEY
                .equalsIgnoreCase(tenant)) {
            return "";
        }
        return tenant.trim();
    }

}
