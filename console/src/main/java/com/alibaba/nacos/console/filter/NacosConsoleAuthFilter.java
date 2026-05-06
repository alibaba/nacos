/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.filter;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.serveridentity.ServerIdentityResult;
import com.alibaba.nacos.core.auth.AbstractWebAuthFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Nacos Console web auth filter.
 *
 * @author xiweng.yy
 */
public class NacosConsoleAuthFilter extends AbstractWebAuthFilter {
    
    private final NacosAuthConfig authConfig;
    
    public NacosConsoleAuthFilter(NacosAuthConfig authConfig, ControllerMethodsCache methodsCache) {
        super(authConfig, methodsCache);
        this.authConfig = authConfig;
    }
    
    @Override
    protected boolean isAuthEnabled() {
        return authConfig.isAuthEnabled();
    }
    
    @Override
    protected ServerIdentityResult checkServerIdentity(HttpServletRequest request, Secured secured) {
        return ServerIdentityResult.noMatched();
    }
}
