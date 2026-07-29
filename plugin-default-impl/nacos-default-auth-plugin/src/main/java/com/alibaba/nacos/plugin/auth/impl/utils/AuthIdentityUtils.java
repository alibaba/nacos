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

package com.alibaba.nacos.plugin.auth.impl.utils;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;

/**
 * Utilities for resolving auth identity from request context.
 *
 * @author Zhengcy05
 */
public final class AuthIdentityUtils {
    
    private AuthIdentityUtils() {
    }
    
    /**
     * Resolve current username from auth context.
     *
     * @return username or {@code null}
     */
    public static String resolveCurrentUsername() {
        try {
            IdentityContext identityContext =
                RequestContextHolder.getContext().getAuthContext().getIdentityContext();
            if (identityContext == null) {
                return null;
            }
            Object nacosUser = identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
            if (nacosUser instanceof NacosUser user) {
                return user.getUserName();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check whether the current authenticated user is a global admin and matches the given identity.
     *
     * @param identity target identity
     * @return {@code true} if the current user matches and is a global admin
     */
    public static boolean isCurrentIdentityGlobalAdmin(String identity) {
        if (StringUtils.isBlank(identity)) {
            return false;
        }
        try {
            IdentityContext identityContext =
                RequestContextHolder.getContext().getAuthContext().getIdentityContext();
            Object nacosUser = identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
            if (!(nacosUser instanceof NacosUser user)) {
                return false;
            }
            return identity.equals(user.getUserName()) && user.isGlobalAdmin();
        } catch (Exception e) {
            return false;
        }
    }
}
