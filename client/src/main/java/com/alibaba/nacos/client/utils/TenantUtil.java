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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Tenant Util.
 *
 * @author Nacos
 */
public class TenantUtil {
    
    private static final String USER_TENANT;
    
    static {
        USER_TENANT = System.getProperty("tenant.id", "");
    }
    
    /**
     * Adapt the way ACM gets tenant on the cloud.
     * <p>
     * Note the difference between getting and getting ANS. Since the processing logic on the server side is different,
     * the default value returns differently.
     * </p>
     *
     * @return user tenant for acm
     */
    public static String getUserTenantForAcm() {
        String tmp = USER_TENANT;
        
        if (StringUtils.isBlank(USER_TENANT)) {
            tmp = System.getProperty("acm.namespace", "");
        }
        
        return tmp;
    }
    
    /**
     * Adapt the way ANS gets tenant on the cloud.
     *
     * @return user tenant for ans
     */
    public static String getUserTenantForAns() {
        String tmp = USER_TENANT;
        
        if (StringUtils.isBlank(USER_TENANT)) {
            tmp = System.getProperty("ans.namespace");
        }
        return tmp;
    }
}
