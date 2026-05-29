/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.controller;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * V1 auth user API - login only. Other v1 user/role/permission APIs have been moved to nacos-api-legacy-adapter and are
 * loaded when this plugin (new version) is present.
 *
 * @author wfnuser
 * @author nkorange
 */
@RestController
@RequestMapping({"/v1/auth", "/v1/auth/users"})
public class UserController {
    
    private final TokenManagerDelegate jwtTokenManager;
    
    private final AuthConfigs authConfigs;
    
    private final IAuthenticationManager iAuthenticationManager;
    
    @Deprecated
    private final AuthenticationManager authenticationManager;
    
    public UserController(TokenManagerDelegate jwtTokenManager, AuthConfigs authConfigs,
        IAuthenticationManager iAuthenticationManager,
        AuthenticationManager authenticationManager) {
        this.jwtTokenManager = jwtTokenManager;
        this.authConfigs = authConfigs;
        this.iAuthenticationManager = iAuthenticationManager;
        this.authenticationManager = authenticationManager;
    }
    
    /**
     * Login to Nacos (v1 API, kept for old clients).
     *
     * @param username username of user
     * @param password password
     * @param response http response
     * @param request  http request
     * @return new token of the user
     * @throws AccessException if user info is incorrect
     */
    @Since("2.3.0")
    @PostMapping("/login")
    @Compatibility(apiType = ApiType.OPEN_API,
        alternatives = "POST ${contextPath:nacos}/v3/auth/user/login")
    public Object login(@RequestParam String username, @RequestParam String password,
        HttpServletResponse response,
        HttpServletRequest request) throws AccessException, IOException {
        
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())
            || AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            
            NacosUser user = iAuthenticationManager.authenticate(request);
            
            response.addHeader(AuthConstants.AUTHORIZATION_HEADER,
                AuthConstants.TOKEN_PREFIX + user.getToken());
            
            Map<String, Object> result = new HashMap<>();
            result.put(Constants.ACCESS_TOKEN, user.getToken());
            result.put(Constants.TOKEN_TTL, jwtTokenManager.getTokenTtlInSeconds(user.getToken()));
            result.put(Constants.GLOBAL_ADMIN, iAuthenticationManager.hasGlobalAdminRole(user));
            result.put(Constants.USERNAME, user.getUserName());
            return result;
        }
        
        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(username,
                password);
        
        try {
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtTokenManager.createToken(authentication);
            response.addHeader(AuthConstants.AUTHORIZATION_HEADER, "Bearer " + token);
            return RestResultUtils.success("Bearer " + token);
        } catch (BadCredentialsException authentication) {
            return RestResultUtils.failed(HttpStatus.UNAUTHORIZED.value(), null, "Login failed");
        }
    }
}
