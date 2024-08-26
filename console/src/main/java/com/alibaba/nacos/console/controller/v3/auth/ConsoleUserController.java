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

package com.alibaba.nacos.console.controller.v3.auth;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.auth.UserProxy;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordGeneratorUtil;
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
 * .
 *
 * @author zhangyukun on:2024/8/16
 */
@RestController
@RequestMapping("/v3/console/auth/user")
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConsoleUserController {
    
    private final UserProxy userProxy;
    
    public ConsoleUserController(UserProxy userProxy) {
        this.userProxy = userProxy;
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
        userProxy.createUser(username, password);
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
        return userProxy.createAdminUser(password);
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
        userProxy.deleteUser(username);
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
        userProxy.updateUser(username, newPassword, response, request);
        return Result.success("update user ok!");
        
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
        Page<User> userPage = userProxy.getUserList(pageNo, pageSize, username, search);
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
        List<String> userList = userProxy.getUserListByUsername(username);
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
        Object loginResult = userProxy.login(username, password, response, request);
        return Result.success(loginResult);
    }
    
}
