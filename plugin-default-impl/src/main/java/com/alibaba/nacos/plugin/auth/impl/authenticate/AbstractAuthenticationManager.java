/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.authenticate;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetails;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;

import javax.servlet.http.HttpServletRequest;

/**
 * AbstractAuthenticationManager.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/13 12:48
 */
public class AbstractAuthenticationManager implements IAuthenticationManager {
    
    protected NacosUserDetailsServiceImpl userDetailsService;
    
    protected TokenManagerDelegate jwtTokenManager;
    
    protected NacosRoleServiceImpl roleService;
    
    public AbstractAuthenticationManager(NacosUserDetailsServiceImpl userDetailsService,
            TokenManagerDelegate jwtTokenManager, NacosRoleServiceImpl roleService) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenManager = jwtTokenManager;
        this.roleService = roleService;
    }
    
    @Override
    public NacosUser authenticate(String username, String rawPassword) throws AccessException {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(rawPassword)) {
            throw new AccessException("user not found!");
        }
        
        NacosUserDetails nacosUserDetails = (NacosUserDetails) userDetailsService.loadUserByUsername(username);
        if (nacosUserDetails == null || !PasswordEncoderUtil.matches(rawPassword, nacosUserDetails.getPassword())) {
            throw new AccessException("user not found!");
        }
        return new NacosUser(nacosUserDetails.getUsername(), jwtTokenManager.createToken(username));
    }
    
    @Override
    public NacosUser authenticate(String token) throws AccessException {
        if (StringUtils.isBlank(token)) {
            throw new AccessException("user not found!");
        }
        return jwtTokenManager.parseToken(token);
    }
    
    @Override
    public NacosUser authenticate(HttpServletRequest httpServletRequest) throws AccessException {
        String token = resolveToken(httpServletRequest);
        
        NacosUser user;
        if (StringUtils.isNotBlank(token)) {
            user = authenticate(token);
        } else {
            String userName = httpServletRequest.getParameter(AuthConstants.PARAM_USERNAME);
            String password = httpServletRequest.getParameter(AuthConstants.PARAM_PASSWORD);
            user = authenticate(userName, password);
        }
        
        return user;
    }
    
    @Override
    public void authorize(Permission permission, NacosUser nacosUser) throws AccessException {
        if (Loggers.AUTH.isDebugEnabled()) {
            Loggers.AUTH.debug("auth permission: {}, nacosUser: {}", permission, nacosUser);
        }
        if (nacosUser.isGlobalAdmin()) {
            return;
        }
        if (hasGlobalAdminRole(nacosUser)) {
            return;
        }
        
        if (!roleService.hasPermission(nacosUser, permission)) {
            throw new AccessException("authorization failed!");
        }
    }
    
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(AuthConstants.TOKEN_PREFIX.length());
        }
        bearerToken = request.getParameter(Constants.ACCESS_TOKEN);
        
        return bearerToken;
    }
    
    @Override
    public boolean hasGlobalAdminRole(String username) {
        return roleService.hasGlobalAdminRole(username);
    }
    
    @Override
    public boolean hasGlobalAdminRole(NacosUser nacosUser) {
        if (nacosUser.isGlobalAdmin()) {
            return true;
        }
        nacosUser.setGlobalAdmin(hasGlobalAdminRole(nacosUser.getUserName()));
        return nacosUser.isGlobalAdmin();
    }
}
