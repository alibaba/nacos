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
package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.client.utils.StringUtils;

/**
 * Tenant Util
 *
 * @author Nacos
 */
public class TenantUtil {

    static String userTenant = "";

    static {
        userTenant = System.getProperty("tenant.id", "");
        if (StringUtils.isBlank(userTenant)) {
            userTenant = System.getProperty("acm.namespace", "");
        }
    }

    public static String getUserTenant() {
        return userTenant;
    }

    public static void setUserTenant(String userTenant) {
        TenantUtil.userTenant = userTenant;
    }
}
