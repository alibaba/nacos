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

package com.alibaba.nacos.plugin.auth.impl.controller.v3;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
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
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordGeneratorUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Controller for handling HTTP requests related to user operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@RestController
@RequestMapping("/v3/auth/user")
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class UserControllerV3 {
    
    private final NacosUserDetailsServiceImpl userDetailsService;
    
    private final NacosRoleServiceImpl roleService;
    
    private final AuthConfigs authConfigs;
    
    private final IAuthenticationManager iAuthenticationManager;
    
    private final TokenManagerDelegate jwtTokenManager;
    
    private final AuthenticationManager authenticationManager;
    
    private static final String SEARCH_TYPE_BLUR = "blur";
    
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
    public UserControllerV3(NacosUserDetailsServiceImpl userDetailsService, NacosRoleServiceImpl roleService,
            AuthConfigs authConfigs, IAuthenticationManager iAuthenticationManager,
            TokenManagerDelegate jwtTokenManager, @Deprecated AuthenticationManager authenticationManager) {
        this.userDetailsService = userDetailsService;
        this.roleService = roleService;
        this.authConfigs = authConfigs;
        this.iAuthenticationManager = iAuthenticationManager;
        this.jwtTokenManager = jwtTokenManager;
        this.authenticationManager = authenticationManager;
    }
    
    /**
     * Create a new user.
     *
     * @param username username
     * @param password password
     * @return ok if create succeed
     * @throws IllegalArgumentException if user already exist
     * @since 1.2.0
     */
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    @PostMapping
    public Object createUser(@RequestParam String username, @RequestParam String password) {
        User user = userDetailsService.getUserFromDatabase(username);
        if (user != null) {
            throw new IllegalArgumentException("user '" + username + "' already exist!");
        }
        userDetailsService.createUser(username, PasswordEncoderUtil.encode(password));
        return Result.success("create user ok!");
    }
    
    /**
     * Create a admin user only not exist admin user can use.
     */
    @PostMapping("/admin")
    public Object createAdminUser(@RequestParam(required = false) String password) {
        
        if (StringUtils.isBlank(password)) {
            password = PasswordGeneratorUtil.generateRandomPassword();
        }
    
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
    
    /**
     * Delete an existed user.
     *
     * @param username username of user
     * @return ok if deleted succeed, keep silent if user not exist
     * @since 1.2.0
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    public Object deleteUser(@RequestParam String username) {
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleInfo.getRole())) {
                    throw new IllegalArgumentException("cannot delete admin: " + username);
                }
            }
        }
        userDetailsService.deleteUser(username);
        return Result.success("delete user ok!");
    }
    
    /**
     * Update an user.
     *
     * @param username    username of user
     * @param newPassword new password of user
     * @param response    http response
     * @param request     http request
     * @return ok if update succeed
     * @throws IllegalArgumentException if user not exist or oldPassword is incorrect
     * @since 1.2.0
     */
    @PutMapping
    @Secured(resource = AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, action = ActionTypes.WRITE)
    public Object updateUser(@RequestParam String username, @RequestParam String newPassword,
            HttpServletResponse response, HttpServletRequest request) throws IOException {
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
        return Result.success("update user ok!");
        
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
    
    
    /**
     * Get paged users with the option for accurate or fuzzy search.
     *
     * @param pageNo   number index of page
     * @param pageSize size of page
     * @param username the username to search for, can be an empty string
     * @param search   the type of search: "accurate" for exact match, "blur" for fuzzy match
     * @return A collection of users, empty set if no user is found
     * @since 1.2.0
     */
    @GetMapping("/list")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.READ)
    public Result<Page<User>> getUserList(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", required = false, defaultValue = "") String username,
            @RequestParam(name = "search", required = false, defaultValue = "accurate") String search) {
        Page<User> userPage;
        if (SEARCH_TYPE_BLUR.equalsIgnoreCase(search)) {
            userPage = userDetailsService.findUsersLike4Page(username, pageNo, pageSize);
        } else {
            userPage = userDetailsService.getUsersFromDatabase(pageNo, pageSize, username);
        }
        return Result.success(userPage);
    }
    
    /**
     * Fuzzy matching username.
     *
     * @param username username
     * @return Matched username
     */
    @GetMapping("/search")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    public Result<List<String>> getUserListByUsername(@RequestParam String username) {
        List<String> userList = userDetailsService.findUserLikeUsername(username);
        return Result.success(userList);
    }
    
    /**
     * Login to Nacos
     *
     * <p>This methods uses username and password to require a new token.
     *
     * @param username username of user
     * @param password password
     * @param response http response
     * @param request  http request
     * @return new token of the user
     * @throws AccessException if user info is incorrect
     */
    @PostMapping("/login")
    public Object login(@RequestParam String username, @RequestParam String password, HttpServletResponse response,
            HttpServletRequest request) throws AccessException, IOException {
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

