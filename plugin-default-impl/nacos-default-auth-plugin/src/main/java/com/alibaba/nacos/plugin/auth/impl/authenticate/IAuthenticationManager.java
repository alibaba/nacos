/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.authenticate;

import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;

import javax.servlet.http.HttpServletRequest;

/**
 * Authentication interface.
 *
 * @author Weizhan▪Yun
 * @date 2023/1/12 23:31
 */
public interface IAuthenticationManager {
    
    /**
     * Authentication of user with password.
     *
     * @param username    username
     * @param rawPassword raw password
     * @return user related to this request, null if no user info is found.
     * @throws AccessException if authentication is failed
     */
    NacosUser authenticate(String username, String rawPassword) throws AccessException;
    
    /**
     * Authentication with jwt.
     *
     * @param jwtToken json web token
     * @return nacos user
     * @throws AccessException if authentication is failed
     */
    NacosUser authenticate(String jwtToken) throws AccessException;
    
    /**
     * Authentication of request, identify the user who request the resource.
     *
     * @param httpServletRequest http servlet request
     * @return nacos user
     * @throws AccessException if authentication is failed
     */
    NacosUser authenticate(HttpServletRequest httpServletRequest) throws AccessException;
    
    /**
     * Authorize if the nacosUser has the specified permission.
     *
     * @param permission permission to auth
     * @param nacosUser  nacosUser who wants to access the resource.
     * @throws AccessException if authorization is failed
     */
    void authorize(Permission permission, NacosUser nacosUser) throws AccessException;
    
    /**
     * Whether the user has the administrator role.
     *
     * @param username nacos user name
     * @return if the user has the administrator role.
     */
    boolean hasGlobalAdminRole(String username);
    
    /**
     * Whether the user has the administrator role.
     *
     * @param nacosUser nacos user name
     * @return if the user has the administrator role.
     */
    boolean hasGlobalAdminRole(NacosUser nacosUser);
}
