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
import com.alibaba.nacos.auth.common.AuthSystemTypes;
import com.alibaba.nacos.auth.context.IdentityContext;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.roles.NacosAuthRoleServiceImpl;
import com.alibaba.nacos.auth.roles.RoleInfo;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Builtin access control entry of Nacos.
 *
 * @author wuyfee
 */
public class NacosAuthServiceImpl implements AuthService {
    
    private final JwtTokenManager jwtTokenManager;
    
    private final AuthenticationManager authenticationManager;
    
    private final NacosAuthRoleServiceImpl roleService;
    
    public NacosAuthServiceImpl() {
        jwtTokenManager = ApplicationUtils.getBean(JwtTokenManager.class);
        authenticationManager = ApplicationUtils.getBean(AuthenticationManager.class);
        roleService = ApplicationUtils.getBean(NacosAuthRoleServiceImpl.class);
    }
    
    @Override
    public IdentityContext login(IdentityContext identityContext) throws AccessException {
        String username = (String) identityContext.getParameter(Constants.USERNAME);
        String password = (String) identityContext.getParameter(
                com.alibaba.nacos.auth.constant.Constants.Auth.PARAM_PASSWORD);
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
        
        String token = jwtTokenManager.createToken(finalName);
        SecurityContextHolder.getContext().setAuthentication(jwtTokenManager.getAuthentication(token));
        
        IdentityContext authResult = new IdentityContext();
        setIdentityContext(finalName, token, identityContext);
        setIdentityContext(finalName, token, authResult);
        return authResult;
    }
    
    @Override
    public Boolean authorityAccess(IdentityContext identityContext, Permission permission) throws AccessException {
        String token;
        String bearerToken = (String) identityContext.getParameter(NacosAuthConfig.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(
                com.alibaba.nacos.auth.constant.Constants.Auth.TOKEN_PREFIX)) {
            token = bearerToken.substring(7);
        } else {
            token = (String) identityContext.getParameter(Constants.ACCESS_TOKEN);
        }
        
        String username;
        if (StringUtils.isBlank(token)) {
            username = (String) login(identityContext).getParameter(Constants.USERNAME);
        } else {
            username = getUsernameFromToken(token);
            setIdentityContext(username, token, identityContext);
        }
        
        if (!roleService.hasPermission(username, permission)) {
            throw new AccessException("authorization failed!");
        }
        return true;
    }
    
    @Override
    public String getAuthServiceName() {
        return AuthSystemTypes.USERNAME_PASSWORD.name();
    }
    
    /**
     * get username from token.
     */
    private String getUsernameFromToken(String token) throws AccessException {
        try {
            jwtTokenManager.validateToken(token);
        } catch (ExpiredJwtException e) {
            throw new AccessException("token expired!");
        } catch (Exception e) {
            throw new AccessException("token invalid!");
        }
        
        Authentication authentication = jwtTokenManager.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        return authentication.getName();
    }
    
    /**
     * set NacosUser.
     *
     * @param username username.
     * @param token    user access token.
     */
    public void setIdentityContext(String username, String token, IdentityContext identityContext) {
        identityContext.setParameter(Constants.USERNAME, username);
        identityContext.setParameter(Constants.ACCESS_TOKEN, token);
        identityContext.setParameter(Constants.GLOBAL_ADMIN, false);
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (roleInfo.getRole().equals(com.alibaba.nacos.auth.constant.Constants.Auth.GLOBAL_ADMIN_ROLE)) {
                    identityContext.setParameter(Constants.GLOBAL_ADMIN, true);
                    break;
                }
            }
        }
    }
    
}
