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

import com.alibaba.nacos.auth.common.AuthConfigs;
import com.alibaba.nacos.auth.exception.AuthConfigsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Grpc request Identify context builder.
 *
 * @author wuyfee
 */
@Service
public class HttpIdentityContextBuilder implements IdentityContextBuilder<HttpServletRequest> {
    
    @Autowired
    AuthConfigs authConfigs;
    
    private final IdentityContext identityContext;
    
    public HttpIdentityContextBuilder() {
        authConfigs = new AuthConfigs();
        identityContext = new IdentityContext();
    }
    
    public HttpIdentityContextBuilder(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
        identityContext = new IdentityContext();
    }
    
    /**
     * get identity context from http.
     *
     * @param request user request
     * @return IdentityContext from request context
     */
    @Override
    public IdentityContext build(HttpServletRequest request) throws AuthConfigsException {
        switch (authConfigs.getIdentifyPositionTypes()) {
            case HEADER:
                Enumeration<String> headerEnu = request.getHeaderNames();
                setIdentityContext(headerEnu, request);
                break;
            case PARAMETER:
                Enumeration<String> paramEnu = request.getParameterNames();
                setIdentityContext(paramEnu, request);
                break;
            case HEADER_AND_PARAMETER:
                Enumeration<String> headerBothEnu = request.getHeaderNames();
                Enumeration<String> paramBothEnu = request.getParameterNames();
                setIdentityContext(headerBothEnu, request);
                setIdentityContext(paramBothEnu, request);
                break;
            default:
                throw new AuthConfigsException(
                        "AuthConfigs.identifyPosition error! Check application.properties nacos.core.auth.identifyPosition.");
        }
        return identityContext;
    }
    
    public void setIdentityContext(Enumeration<String> enu, HttpServletRequest request) {
        Set<String> keySet = new HashSet<>(Arrays.asList(authConfigs.getAuthorityKey()));
        while (enu.hasMoreElements()) {
            String paraName = enu.nextElement();
            if (keySet.contains(paraName)) {
                identityContext.setParameter(paraName, request.getParameter(paraName));
            }
        }
    }
    
}