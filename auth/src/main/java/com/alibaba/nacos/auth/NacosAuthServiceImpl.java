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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.context.IdentityContext;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.NacosUser;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.roles.AuthNacosRoleServiceImpl;
import com.alibaba.nacos.auth.roles.RoleInfo;
import com.alibaba.nacos.common.utils.StringUtils;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builtin access control entry of Nacos.
 *
 * @author wuyfee
 */
@Component
public class NacosAuthServiceImpl implements AuthService {
    
    private static final String TOKEN_PREFIX = "Bearer ";
    
    private static final String PARAM_USERNAME = "username";
    
    private static final String PARAM_PASSWORD = "password";
    
    @Autowired
    private AuthJwtTokenManager authJwtTokenManager;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private AuthNacosRoleServiceImpl roleService;
    
    @Override
    public NacosUser login(IdentityContext identityContext) throws AccessException {
        String username = (String) identityContext.getParameter(PARAM_USERNAME);
        String password = (String) identityContext.getParameter(PARAM_PASSWORD);
        String finalName;
        Authentication authenticate;
        try {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username,
                    password);
            authenticate = authenticationManager.authenticate(authenticationToken);
        } catch (AuthenticationException e) {
            throw new AccessException("unknown user!");
        }
        
        if (null == authenticate || StringUtils.isBlank(authenticate.getName())) {
            finalName = username;
        } else {
            finalName = authenticate.getName();
        }
        
        String token = authJwtTokenManager.createToken(finalName);
        SecurityContextHolder.getContext().setAuthentication(authJwtTokenManager.getAuthentication(token));
        
        return setUser(finalName, token);
    }
    
    @Override
    public Boolean authorityAccess(IdentityContext identityContext, Permission permission) throws AccessException {
        String token;
        String bearerToken = (String) identityContext.getParameter(AuthNacosAuthConfig.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            token = bearerToken.substring(7);
        } else {
            token = (String) identityContext.getParameter(Constants.ACCESS_TOKEN);
        }
        
        String username;
        NacosUser nacosUser;
        if (StringUtils.isBlank(token)) {
            nacosUser = login(identityContext);
            username = nacosUser.getUserName();
        } else {
            username = getUsernameFromToken(token);
            nacosUser = setUser(username, token);
        }
        
        identityContext.setParameter(AuthNacosAuthConfig.NACOS_USER_KEY, nacosUser);
        if (!roleService.hasPermission(username, permission)) {
            throw new AccessException("authorization failed!");
        }
        return true;
    }
    
    @Override
    public String getAuthServiceName() {
        return "NacosAuthServiceImpl";
    }
    
    /**
     * get username from token.
     */
    private String getUsernameFromToken(String token) throws AccessException {
        try {
            authJwtTokenManager.validateToken(token);
        } catch (ExpiredJwtException e) {
            throw new AccessException("token expired!");
        } catch (Exception e) {
            throw new AccessException("token invalid!");
        }
        
        Authentication authentication = authJwtTokenManager.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        return authentication.getName();
    }
    
    public NacosUser setUser(String username, String token) {
        NacosUser user = new NacosUser();
        user.setUserName(username);
        user.setToken(token);
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (roleInfo.getRole().equals(AuthNacosRoleServiceImpl.GLOBAL_ADMIN_ROLE)) {
                    user.setGlobalAdmin(true);
                    break;
                }
            }
        }
        return user;
    }
}
