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

package com.alibaba.nacos.console.proxy.auth;

import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.auth.UserHandler;
import com.alibaba.nacos.console.handler.inner.auth.UserInnerHandler;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * roxy class for handling user-related operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@Service
public class UserProxy {
    
    private final Map<String, UserHandler> userHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    /**
     * Constructs a new UserProxy with the given UserInnerHandler and ConsoleConfig.
     *
     * @param userInnerHandler the default implementation of UserHandler
     * @param consoleConfig    the console configuration used to determine the deployment type
     */
    @Autowired
    public UserProxy(UserInnerHandler userInnerHandler, ConsoleConfig consoleConfig) {
        this.userHandlerMap.put("merged", userInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Create a new user.
     *
     * @param username the username
     * @param password the password
     * @return a success message if the user is created
     * @throws IllegalArgumentException if the user already exists
     */
    public Object createUser(String username, String password) {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.createUser(username, password);
    }
    
    /**
     * Create an admin user. This operation can only be used if no admin user exists.
     *
     * @param password the password for the admin user
     * @return the result of the operation
     */
    public Object createAdminUser(String password) {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.createAdminUser(password);
    }
    
    /**
     * Delete an existing user.
     *
     * @param username the username of the user to be deleted
     * @return a success message if the user is deleted, otherwise a silent response if the user does not exist
     * @throws IllegalArgumentException if trying to delete an admin user
     */
    public Object deleteUser(String username) {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.deleteUser(username);
    }
    
    /**
     * Update a user's password.
     *
     * @param username    the username of the user
     * @param newPassword the new password
     * @param response    the HTTP response
     * @param request     the HTTP request
     * @return a success message if the password is updated
     * @throws IOException if an I/O error occurs
     */
    public Object updateUser(String username, String newPassword, HttpServletResponse response,
            HttpServletRequest request) throws IOException {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.updateUser(username, newPassword, response, request);
    }
    
    /**
     * Get a list of users with pagination and optional accurate or fuzzy search.
     *
     * @param pageNo   the page number
     * @param pageSize the size of the page
     * @param username the username to search for
     * @param search   the type of search: "accurate" for exact match, "blur" for fuzzy match
     * @return a paginated list of users
     */
    public Page<User> getUserList(int pageNo, int pageSize, String username, String search) {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.getUserList(pageNo, pageSize, username, search);
    }
    
    /**
     * Fuzzy match a username.
     *
     * @param username the username to match
     * @return a list of matched usernames
     */
    public List<String> getUserListByUsername(String username) {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.getUserListByUsername(username);
    }
    
    /**
     * Login to Nacos.
     *
     * @param username the username
     * @param password the password
     * @param response the HTTP response
     * @param request  the HTTP request
     * @return a new token if the login is successful
     * @throws AccessException if user credentials are incorrect
     * @throws IOException     if an I/O error occurs
     */
    public Object login(String username, String password, HttpServletResponse response, HttpServletRequest request)
            throws AccessException, IOException {
        UserHandler userHandler = userHandlerMap.get(consoleConfig.getType());
        if (userHandler == null) {
            throw new IllegalArgumentException("Invalid deployment type");
        }
        return userHandler.login(username, password, response, request);
    }
}

