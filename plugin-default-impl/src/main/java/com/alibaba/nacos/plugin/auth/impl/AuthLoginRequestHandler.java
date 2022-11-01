/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.remote.AuthLoginRequest;
import com.alibaba.nacos.plugin.auth.impl.remote.AuthLoginResponse;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Handle the auth login from client.
 *
 * @author Nacos
 */
@Component
public class AuthLoginRequestHandler extends RequestHandler<AuthLoginRequest, AuthLoginResponse> {
    
    private final JwtTokenManager jwtTokenManager;
    
    private final AuthConfigs authConfigs;
    
    private final NacosAuthManager authManager;
    
    private final AuthenticationManager authenticationManager;
    
    @Autowired
    public AuthLoginRequestHandler(JwtTokenManager jwtTokenManager, AuthConfigs authConfigs,
            NacosAuthManager authManager, AuthenticationManager authenticationManager) {
        this.jwtTokenManager = jwtTokenManager;
        this.authConfigs = authConfigs;
        this.authManager = authManager;
        this.authenticationManager = authenticationManager;
    }
    
    /**
     * Handler request.
     *
     * @see com.alibaba.nacos.plugin.auth.impl.controller.UserController#login(String, String,
     * javax.servlet.http.HttpServletResponse, javax.servlet.http.HttpServletRequest)
     */
    @Override
    public AuthLoginResponse handle(AuthLoginRequest request, RequestMeta meta) {
        
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())
                || AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            try {
                IdentityContext identityContext = new IdentityContext();
                identityContext.setParameter(AuthConstants.PARAM_USERNAME, request.getUsername());
                identityContext.setParameter(AuthConstants.PARAM_PASSWORD, request.getPassword());
                NacosUser user = (NacosUser) authManager.login(identityContext);
                return new AuthLoginResponse(user.getToken(), jwtTokenManager.getTokenValidityInSeconds());
            } catch (AccessException e) {
                return AuthLoginResponse.denied(e.getErrMsg());
            }
        }
        
        // create Authentication class through username and password, the implement class is UsernamePasswordAuthenticationToken
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                request.getUsername(), request.getPassword());
        try {
            // use the method authenticate of AuthenticationManager(default implement is ProviderManager) to valid Authentication
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            // generate Token
            String token = jwtTokenManager.createToken(authentication);
            return new AuthLoginResponse(AuthConstants.TOKEN_PREFIX + token,
                    jwtTokenManager.getTokenValidityInSeconds());
        } catch (BadCredentialsException e) {
            return AuthLoginResponse.denied(e.getMessage());
        }
    }
}
