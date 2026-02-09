/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.legacy.adapter.auth;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthSystemTypes;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordGeneratorUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Legacy v1 auth user API (user CRUD only; login remains in default-auth-plugin).
 * Loaded only when new default-auth-plugin is on classpath.
 *
 * @author wfnuser
 * @author nkorange
 * @deprecated Use v3 auth API instead.
 */
@RestController
@RequestMapping({"/v1/auth", "/v1/auth/users"})
@Deprecated
public class LegacyAuthUserController {

    private final NacosUserService userDetailsService;
    private final NacosRoleService roleService;
    private final AuthConfigs authConfigs;
    private final IAuthenticationManager iAuthenticationManager;

    public LegacyAuthUserController(NacosUserService userDetailsService, NacosRoleService roleService,
            AuthConfigs authConfigs, IAuthenticationManager iAuthenticationManager) {
        this.userDetailsService = userDetailsService;
        this.roleService = roleService;
        this.authConfigs = authConfigs;
        this.iAuthenticationManager = iAuthenticationManager;
    }

    /**
     * Create a new user (v1 API).
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "POST ${contextPath:nacos}/v3/auth/user")
    public Object createUser(@RequestParam String username, @RequestParam String password) {
        if (AuthConstants.DEFAULT_USER.equals(username)) {
            return RestResultUtils.failed(HttpStatus.CONFLICT.value(),
                    "User `nacos` is default admin user. Please use `/nacos/v1/auth/users/admin` API to init `nacos` users. "
                            + "Detail see `https://nacos.io/docs/latest/manual/admin/auth/#31-%E8%AE%BE%E7%BD%AE%E7%AE%A1%E7%90%86%E5%91%98%E5%AF%86%E7%A0%81`");
        }
        User user = userDetailsService.getUser(username);
        if (user != null) {
            throw new IllegalArgumentException("user '" + username + "' already exist!");
        }
        userDetailsService.createUser(username, password);
        return RestResultUtils.success("create user ok!");
    }

    /**
     * Create admin user when no admin exists (v1 API).
     */
    @PostMapping("/admin")
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "POST ${contextPath:nacos}/v3/auth/user/admin")
    public Object createAdminUser(@RequestParam(required = false) String password) {
        if (AuthSystemTypes.NACOS.name().equalsIgnoreCase(authConfigs.getNacosAuthSystemType())) {
            if (iAuthenticationManager.hasGlobalAdminRole()) {
                return RestResultUtils.failed(HttpStatus.CONFLICT.value(), "have admin user cannot use it");
            }
            if (StringUtils.isBlank(password)) {
                password = PasswordGeneratorUtil.generateRandomPassword();
            }
            String username = AuthConstants.DEFAULT_USER;
            userDetailsService.createUser(username, password);
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
     * Delete an existing user (v1 API).
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "DELETE ${contextPath:nacos}/v3/auth/user")
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
     * Update user password (v1 API).
     */
    @PutMapping
    @Secured(resource = AuthConstants.UPDATE_PASSWORD_ENTRY_POINT, action = ActionTypes.WRITE, tags = {
            com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY, AuthConstants.UPDATE_PASSWORD_ENTRY_POINT})
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "PUT ${contextPath:nacos}/v3/auth/user")
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
        User user = userDetailsService.getUser(username);
        if (user == null) {
            throw new IllegalArgumentException("user " + username + " not exist!");
        }
        userDetailsService.updateUserPassword(username, newPassword);
        return RestResultUtils.success("update user ok!");
    }

    /**
     * Check if current identity can act on the given username (admin or same user).
     */
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
            iAuthenticationManager.hasGlobalAdminRole(user);
        }
        if (user.isGlobalAdmin()) {
            return true;
        }
        return user.getUserName().equals(username);
    }

    /**
     * Get paged users (v1 API).
     */
    @GetMapping(params = "search=accurate")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/user/list")
    public Page<User> getUsers(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", required = false, defaultValue = "") String username) {
        return userDetailsService.getUsers(pageNo, pageSize, username);
    }

    /**
     * Fuzzy search users (v1 API).
     */
    @GetMapping(params = "search=blur")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.READ)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/user/list")
    public Page<User> fuzzySearchUser(@RequestParam int pageNo, @RequestParam int pageSize,
            @RequestParam(name = "username", required = false, defaultValue = "") String username) {
        return userDetailsService.findUsers(username, pageNo, pageSize);
    }

    /**
     * Search usernames by prefix (v1 API).
     */
    @GetMapping("/search")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "users", action = ActionTypes.WRITE)
    @Compatibility(apiType = ApiType.CONSOLE_API, alternatives = "GET ${contextPath:nacos}/v3/auth/user/search")
    public List<String> searchUsersLikeUsername(@RequestParam String username) {
        return userDetailsService.findUserNames(username);
    }
}
