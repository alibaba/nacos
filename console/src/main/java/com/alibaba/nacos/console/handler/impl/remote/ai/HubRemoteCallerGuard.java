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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.context.addition.AuthContext;
import com.alibaba.nacos.plugin.auth.api.AuthResult;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.springframework.stereotype.Component;

/**
 * Prevent credential-less Console->Admin calls from using the maintainer client's
 * fallback server identity. The Admin endpoint repeats authorization against its
 * own freshly validated user principal.
 */
@Component
public class HubRemoteCallerGuard {
    
    /**
     * Require a verified Console user with original request credentials.
     *
     * @throws NacosException if forwarding would require fallback credentials
     */
    public void requireForwardableUser() throws NacosException {
        AuthContext context = RequestContextHolder.getContext().getAuthContext();
        IdentityContext identity = context.getIdentityContext();
        AuthResult<?> result = context.getAuthResult();
        if (!ApiType.CONSOLE_API.name().equals(context.getApiType()) || identity == null
            || result == null || !result.isSuccess()
            || Boolean.TRUE.equals(identity.getParameter(Constants.Identity.SERVER_IDENTITY))) {
            throw denied();
        }
        Object principal = identity.getParameter(Constants.Identity.IDENTITY_ID);
        if (!(principal instanceof String) || StringUtils.isBlank((String) principal)
            || "anonymous".equalsIgnoreCase((String) principal)) {
            throw denied();
        }
        boolean hasCredential = false;
        for (String name : identity.getRequestIdentityNames()) {
            if (StringUtils.isNotBlank(identity.getParameter(name, ""))) {
                hasCredential = true;
                break;
            }
        }
        if (!hasCredential) {
            throw denied();
        }
    }
    
    private NacosApiException denied() {
        return new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
            "Hub remote mutations require an authenticated user and forwardable credentials");
    }
}
