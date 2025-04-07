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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
 * User related methods entry.
 *
 * @author wfnuser
 * @author nkorange
 */
@RestController("user")
@RequestMapping({"/v1/auth", "/v1/auth/users"})
public class UserController {
    
    @Autowired
    private TokenManagerDelegate jwtTokenManager;
    
    @Autowired
    @Deprecated
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private NacosRoleServiceImpl roleService;
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Autowired
    private IAuthenticationManager iAuthenticationManager;
    
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
        if (AuthConstants.DEFAULT_USER.equals(username)) {
            return RestResultUtils.failed(HttpStatus.CONFLICT.value(),
                    "User `nacos` is default admin user. Please use `/nacos/v1/auth/users/admin` API to init `nacos` users. "
                            + "Detail see `https://nacos.io/docs/latest/manual/admin/auth/#31-%E8%AE%BE%E7%BD%AE%E7%AE%A1%E7%90%86%E5%91%98%E5%AF%86%E7%A0%81`");
        }
        User user = userDetailsService.getUserFromDatabase(username);
        if (user != null) {
            throw new IllegalArgumentException("user '" + username + "' already exist!");
        }
        userDetailsService.createUser(username, PasswordEncoderUtil.encode(password));
        return RestResultUtils.success("create user ok!");
    }
    
    /**
     * Create a admin user only not exist admin user can use.
     */
    @PostMapping("/admin")
    public Object createAdminUser(@RequestParam(required = false) String password) {
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            if (iAuthenticationManager.hasGlobalAdminRole()) {
                return RestResultUtils.failed(HttpStatus.CONFLICT.value(), "have admin user cannot use it");
            }
            if (StringUtils.isBlank(password)) {
                password = PasswordGeneratorUtil.generateRandomPassword();
            }
            
            String username = AuthConstants.DEFAULT_USER;
            userDetailsService.createUser(username, PasswordEncoderUtil.encode(password));
            roleService.addAdminRole(username);
            ObjectNode result = JacksonUtils.createEmptyJsonNode();
            result.put(AuthConstants.PARAM_USERNAME, username);
            result.put(AuthConstants.PARAM_PASSWORD, password);
            return result;
        } else {
            return RestResultUtils.failed(HttpStatus.NOT_IMPLEMENTED.value(), "not support");
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
        return RestResultUtils.success("delete user ok!");
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
    @Secured(resource = AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, action = ActionTypes.WRITE, tags = {
            AuthConstants.UPDATE_PASSWORD_ENTRY_POINT})
    public Object updateUser(@RequestParam String username, @RequestParam String newPassword,
            HttpServletResponse response, HttpServletRequest request) throws IOException {
        // admin or same user
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
        
        return RestResultUtils.success("update user ok!");
    }
    
    private boolean hasPermission(String username, HttpServletRequest request)
            throws HttpSessionRequiredException, AccessException {
        if (!authConfigs.isAuthEnabled()) {
            return true;
        }
        IdentityContext identityContext = RequestContextHolder.getContext().getAuthContext().getIdentityContext();
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
     * Get paged users.
     *
     * @param pageNo   number index of page
     * @param pageSize size of page
     * @return A collection of users, empty set if no user is found
     * @since 1.2.0
     */
    @GetMapping(params = "search=accurate")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.READ)
    public Page<User> getUsers(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", required = false, defaultValue = "") String username) {
        return userDetailsService.getUsersFromDatabase(pageNo, pageSize, username);
    }
    
    @GetMapping(params = "search=blur")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.READ)
    public Page<User> fuzzySearchUser(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", required = false, defaultValue = "") String username) {
        return userDetailsService.findUsersLike4Page(username, pageNo, pageSize);
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
            return RestResultUtils.success("Bearer " + token);
        } catch (BadCredentialsException authentication) {
            return RestResultUtils.failed(HttpStatus.UNAUTHORIZED.value(), null, "Login failed");
        }
    }
    
    /**
     * Update password.
     *
     * @param oldPassword old password
     * @param newPassword new password
     * @return Code 200 if update successfully, Code 401 if old password invalid, otherwise 500
     */
    @PutMapping("/password")
    @Deprecated
    public RestResult<String> updatePassword(@RequestParam(value = "oldPassword") String oldPassword,
            @RequestParam(value = "newPassword") String newPassword) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = ((UserDetails) principal).getUsername();
        User user = userDetailsService.getUserFromDatabase(username);
        String password = user.getPassword();
        
        // TODO: throw out more fine grained exceptions
        try {
            if (PasswordEncoderUtil.matches(oldPassword, password)) {
                userDetailsService.updateUserPassword(username, PasswordEncoderUtil.encode(newPassword));
                return RestResultUtils.success("Update password success");
            }
            return RestResultUtils.failed(HttpStatus.UNAUTHORIZED.value(), "Old password is invalid");
        } catch (Exception e) {
            return RestResultUtils.failed(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Update userpassword failed");
        }
    }
    
    /**
     * Fuzzy matching username.
     *
     * @param username username
     * @return Matched username
     */
    @GetMapping("/search")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    public List<String> searchUsersLikeUsername(@RequestParam String username) {
        return userDetailsService.findUserLikeUsername(username);
    }
}
