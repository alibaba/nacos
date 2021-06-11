/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.security.nacos;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.AuthManager;
import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.model.User;
import com.alibaba.nacos.config.server.auth.RoleInfo;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.security.nacos.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.console.security.nacos.users.NacosUser;
import com.alibaba.nacos.core.utils.Loggers;
import io.jsonwebtoken.ExpiredJwtException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Builtin access control entry of Nacos.
 *
 * @author nkorange
 * @since 1.2.0
 */
@Component
public class NacosAuthManager implements AuthManager {
    
    private static final String TOKEN_PREFIX = "Bearer ";
    
    private static final String PARAM_USERNAME = "username";
    
    private static final String PARAM_PASSWORD = "password";
    
    @Autowired
    private JwtTokenManager tokenManager;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private NacosRoleServiceImpl roleService;
    
    @Override
    public User login(Object request) throws AccessException {
        HttpServletRequest req = (HttpServletRequest) request;
        String token = resolveToken(req);
        if (StringUtils.isBlank(token)) {
            throw new AccessException("user not found!");
        }
        
        try {
            tokenManager.validateToken(token);
        } catch (ExpiredJwtException e) {
            throw new AccessException("token expired!");
        } catch (Exception e) {
            throw new AccessException("token invalid!");
        }
        
        Authentication authentication = tokenManager.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String username = authentication.getName();
        NacosUser user = new NacosUser();
        user.setUserName(username);
        user.setToken(token);
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (roleInfo.getRole().equals(NacosRoleServiceImpl.GLOBAL_ADMIN_ROLE)) {
                    user.setGlobalAdmin(true);
                    break;
                }
            }
        }
        req.setAttribute(RequestUtil.NACOS_USER_KEY, user);
        return user;
    }
    
    @Override
    public User loginRemote(Object request) throws AccessException {
        Request req = (Request) request;
        String token = resolveToken(req);
        if (StringUtils.isBlank(token)) {
            throw new AccessException("user not found!");
        }
        
        try {
            tokenManager.validateToken(token);
        } catch (ExpiredJwtException e) {
            throw new AccessException("token expired!");
        } catch (Exception e) {
            throw new AccessException("token invalid!");
        }
        
        Authentication authentication = tokenManager.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String username = authentication.getName();
        NacosUser user = new NacosUser();
        user.setUserName(username);
        user.setToken(token);
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (roleInfo.getRole().equals(NacosRoleServiceImpl.GLOBAL_ADMIN_ROLE)) {
                    user.setGlobalAdmin(true);
                    break;
                }
            }
        }
        return user;
    }
    
    @Override
    public void auth(Permission permission, User user) throws AccessException {
        if (Loggers.AUTH.isDebugEnabled()) {
            Loggers.AUTH.debug("auth permission: {}, user: {}", permission, user);
        }
        
        if (!roleService.hasPermission(user.getUserName(), permission)) {
            throw new AccessException("authorization failed!");
        }
    }
    
    /**
     * Get token from header.
     */
    private String resolveToken(HttpServletRequest request) throws AccessException {
        String bearerToken = request.getHeader(NacosAuthConfig.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(7);
        }
        bearerToken = request.getParameter(Constants.ACCESS_TOKEN);
        if (StringUtils.isBlank(bearerToken)) {
            String userName = request.getParameter(PARAM_USERNAME);
            String password = request.getParameter(PARAM_PASSWORD);
            bearerToken = resolveTokenFromUser(userName, password);
        }
        
        return bearerToken;
    }
    
    /**
     * Get token from header.
     */
    private String resolveToken(Request request) throws AccessException {
        String bearerToken = request.getHeader(NacosAuthConfig.AUTHORIZATION_HEADER);
        if (StringUtils.isNotBlank(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(7);
        }
        bearerToken = request.getHeader(Constants.ACCESS_TOKEN);
        if (StringUtils.isBlank(bearerToken)) {
            String userName = request.getHeader(PARAM_USERNAME);
            String password = request.getHeader(PARAM_PASSWORD);
            bearerToken = resolveTokenFromUser(userName, password);
        }
        
        return bearerToken;
    }
    
    private String resolveTokenFromUser(String userName, String rawPassword) throws AccessException {
        String finalName;
        Authentication authenticate;
        try {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userName,
                    rawPassword);
            authenticate = authenticationManager.authenticate(authenticationToken);
        } catch (AuthenticationException e) {
            throw new AccessException("unknown user!");
        }
        
        if (null == authenticate || StringUtils.isBlank(authenticate.getName())) {
            finalName = userName;
        } else {
            finalName = authenticate.getName();
        }
        
        return tokenManager.createToken(finalName);
    }
}
