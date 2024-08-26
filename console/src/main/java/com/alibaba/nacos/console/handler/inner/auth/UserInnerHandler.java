/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.handler.inner.auth;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.handler.auth.UserHandler;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.token.TokenManagerDelegate;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.HttpSessionRequiredException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of UserHandler that handles user-related operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@Service
public class UserInnerHandler implements UserHandler {
    
    private final NacosUserDetailsServiceImpl userDetailsService;
    
    private final NacosRoleServiceImpl roleService;
    
    private final AuthConfigs authConfigs;
    
    private final IAuthenticationManager iAuthenticationManager;
    
    private final TokenManagerDelegate jwtTokenManager;
    
    private final AuthenticationManager authenticationManager;
    
    /**
     * Constructs a new UserInnerHandler with the provided dependencies.
     *
     * @param userDetailsService     the service for user details operations
     * @param roleService            the service for role operations
     * @param authConfigs            the authentication configuration
     * @param iAuthenticationManager the authentication manager interface
     * @param jwtTokenManager        the JWT token manager
     * @param authenticationManager  the authentication manager
     */
    public UserInnerHandler(NacosUserDetailsServiceImpl userDetailsService, NacosRoleServiceImpl roleService,
            AuthConfigs authConfigs, IAuthenticationManager iAuthenticationManager,
            TokenManagerDelegate jwtTokenManager, @Deprecated AuthenticationManager authenticationManager) {
        this.userDetailsService = userDetailsService;
        this.roleService = roleService;
        this.authConfigs = authConfigs;
        this.iAuthenticationManager = iAuthenticationManager;
        this.jwtTokenManager = jwtTokenManager;
        this.authenticationManager = authenticationManager;
    }
    
    @Override
    public boolean createUser(String username, String password) {
        User user = userDetailsService.getUserFromDatabase(username);
        if (user != null) {
            throw new IllegalArgumentException("user '" + username + "' already exist!");
        }
        userDetailsService.createUser(username, PasswordEncoderUtil.encode(password));
        return true;
    }
    
    @Override
    public Object createAdminUser(String password) {
        
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            if (iAuthenticationManager.hasGlobalAdminRole()) {
                return Result.failure(ErrorCode.CONFLICT, "have admin user cannot use it");
            }
            String username = AuthConstants.DEFAULT_USER;
            userDetailsService.createUser(username, PasswordEncoderUtil.encode(password));
            roleService.addAdminRole(username);
            ObjectNode result = JacksonUtils.createEmptyJsonNode();
            result.put(AuthConstants.PARAM_USERNAME, username);
            result.put(AuthConstants.PARAM_PASSWORD, password);
            return Result.success(result);
        } else {
            return Result.failure(ErrorCode.NOT_IMPLEMENTED, "not support");
        }
    }
    
    @Override
    public boolean deleteUser(String username) {
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleInfo.getRole())) {
                    throw new IllegalArgumentException("cannot delete admin: " + username);
                }
            }
        }
        userDetailsService.deleteUser(username);
        return true;
    }
    
    @Override
    public Object updateUser(String username, String newPassword, HttpServletResponse response,
            HttpServletRequest request) throws IOException {
        try {
            if (!hasPermission(username, request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "authorization failed!");
                return null;
            }
        } catch (HttpSessionRequiredException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "session expired!");
            return null;
        } catch (AccessException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "authorization failed!");
            return null;
        }
        
        User user = userDetailsService.getUserFromDatabase(username);
        if (user == null) {
            throw new IllegalArgumentException("user " + username + " not exist!");
        }
        
        userDetailsService.updateUserPassword(username, PasswordEncoderUtil.encode(newPassword));
        return "update user ok!";
    }
    
    private boolean hasPermission(String username, HttpServletRequest request)
            throws HttpSessionRequiredException, AccessException {
        if (!authConfigs.isAuthEnabled()) {
            return true;
        }
        IdentityContext identityContext = (IdentityContext) request.getSession()
                .getAttribute(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_CONTEXT);
        if (identityContext == null) {
            throw new HttpSessionRequiredException("session expired!");
        }
        NacosUser user = (NacosUser) identityContext.getParameter(AuthConstants.NACOS_USER_KEY);
        if (user == null) {
            user = iAuthenticationManager.authenticate(request);
            if (user == null) {
                throw new HttpSessionRequiredException("session expired!");
            }
            //get user form jwt need check permission
            iAuthenticationManager.hasGlobalAdminRole(user);
        }
        // admin
        if (user.isGlobalAdmin()) {
            return true;
        }
        // same user
        return user.getUserName().equals(username);
    }
    
    @Override
    public Page<User> getUserList(int pageNo, int pageSize, String username, String search) {
        if ("blur".equalsIgnoreCase(search)) {
            return userDetailsService.findUsersLike4Page(username, pageNo, pageSize);
        } else {
            return userDetailsService.getUsersFromDatabase(pageNo, pageSize, username);
        }
    }
    
    @Override
    public List<String> getUserListByUsername(String username) {
        return userDetailsService.findUserLikeUsername(username);
    }
    
    @Override
    public Object login(String username, String password, HttpServletResponse response, HttpServletRequest request)
            throws AccessException, IOException {
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())
                || AuthSystemTypes.LDAP.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            
            NacosUser user = iAuthenticationManager.authenticate(request);
            
            response.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.TOKEN_PREFIX + user.getToken());
            
            ObjectNode result = JacksonUtils.createEmptyJsonNode();
            result.put(Constants.ACCESS_TOKEN, user.getToken());
            result.put(Constants.TOKEN_TTL, jwtTokenManager.getTokenTtlInSeconds(user.getToken()));
            result.put(Constants.GLOBAL_ADMIN, iAuthenticationManager.hasGlobalAdminRole(user));
            result.put(Constants.USERNAME, user.getUserName());
            return result;
        }
        
        // create Authentication class through username and password, the implement class is UsernamePasswordAuthenticationToken
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username,
                password);
        
        try {
            // use the method authenticate of AuthenticationManager(default implement is ProviderManager) to valid Authentication
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            // bind SecurityContext to Authentication
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // generate Token
            String token = jwtTokenManager.createToken(authentication);
            // write Token to Http header
            response.addHeader(AuthConstants.AUTHORIZATION_HEADER, "Bearer " + token);
            return Result.success("Bearer " + token);
        } catch (BadCredentialsException authentication) {
            return Result.failure(ErrorCode.UNAUTHORIZED, "Login failed");
        }
    }
}

