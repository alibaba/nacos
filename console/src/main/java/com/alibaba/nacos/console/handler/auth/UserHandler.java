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

package com.alibaba.nacos.console.handler.auth;

import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Interface for handling user-related operations.
 *
 * @author zhangyukun on:2024/8/16
 */
public interface UserHandler {
    
    /**
     * Create a new user.
     *
     * @param username the username
     * @param password the password
     * @return true if the user is created successfully
     * @throws IllegalArgumentException if the user already exists
     */
    boolean createUser(String username, String password);
    
    /**
     * Create an admin user. This operation can only be used if no admin user exists.
     *
     * @param password the password for the admin user
     * @return the result of the operation as a boolean or other data structure
     */
    Object createAdminUser(String password);
    
    /**
     * Delete an existing user.
     *
     * @param username the username of the user to be deleted
     * @return true if the user is deleted successfully
     * @throws IllegalArgumentException if trying to delete an admin user
     */
    boolean deleteUser(String username);
    
    /**
     * Update a user's password.
     *
     * @param username    the username of the user
     * @param newPassword the new password
     * @param response    the HTTP response
     * @param request     the HTTP request
     * @return true if the password is updated successfully
     * @throws IOException if an I/O error occurs
     */
    Object updateUser(String username, String newPassword, HttpServletResponse response, HttpServletRequest request) throws IOException;
    
    /**
     * Get a list of users with pagination and optional accurate or fuzzy search.
     *
     * @param pageNo   the page number
     * @param pageSize the size of the page
     * @param username the username to search for
     * @param search   the type of search: "accurate" for exact match, "blur" for fuzzy match
     * @return a paginated list of users
     */
    Page<User> getUserList(int pageNo, int pageSize, String username, String search);
    
    /**
     * Fuzzy match a username.
     *
     * @param username the username to match
     * @return a list of matched usernames
     */
    List<String> getUserListByUsername(String username);
    
    /**
     * Login to Nacos.
     *
     * @param username the username
     * @param password the password
     * @param response the HTTP response
     * @param request  the HTTP request
     * @return a result object containing login information
     * @throws AccessException if user credentials are incorrect
     * @throws IOException     if an I/O error occurs
     */
    Object login(String username, String password, HttpServletResponse response, HttpServletRequest request) throws AccessException, IOException;
}
