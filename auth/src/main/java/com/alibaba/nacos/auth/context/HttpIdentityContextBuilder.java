/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.context;

import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.auth.config.AuthConfigs;

import javax.servlet.http.HttpServletRequest;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Identity context builder for HTTP.
 *
 * @author Nacos
 */
public class HttpIdentityContextBuilder implements IdentityContextBuilder<HttpServletRequest> {
    
    private final AuthConfigs authConfigs;
    
    public HttpIdentityContextBuilder(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    /**
     * get identity context from http.
     *
     * @param request user request
     * @return IdentityContext from request context
     */
    @Override
    public IdentityContext build(HttpServletRequest request) {
        IdentityContext result = new IdentityContext();
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        if (!authPluginService.isPresent()) {
            return result;
        }
        Set<String> identityNames = new HashSet<>(authPluginService.get().identityNames());
        getIdentityFromHeader(request, result, identityNames);
        getIdentityFromParameter(request, result, identityNames);
        return result;
    }
    
    private void getIdentityFromHeader(HttpServletRequest request, IdentityContext result, Set<String> identityNames) {
        Enumeration<String> headerEnu = request.getHeaderNames();
        while (headerEnu.hasMoreElements()) {
            String paraName = headerEnu.nextElement();
            if (identityNames.contains(paraName)) {
                result.setParameter(paraName, request.getHeader(paraName));
            }
        }
    }
    
    private void getIdentityFromParameter(HttpServletRequest request, IdentityContext result, Set<String> identityNames) {
        Enumeration<String> paramEnu = request.getParameterNames();
        while (paramEnu.hasMoreElements()) {
            String paraName = paramEnu.nextElement();
            if (identityNames.contains(paraName)) {
                result.setParameter(paraName, request.getParameter(paraName));
            }
        }
    }
}
