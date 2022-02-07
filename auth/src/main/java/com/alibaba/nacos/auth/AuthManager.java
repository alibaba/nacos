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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.exception.AccessException;
import com.alibaba.nacos.auth.model.Permission;
import com.alibaba.nacos.auth.model.User;

/**
 * Access control entry. Can be extended by 3rd party implementations.
 *
 * @author nkorange
 * @author mai.jh
 * @since 1.2.0
 */
public interface AuthManager {
    
    /**
     * Authentication of request, identify the user who request the resource.
     *
     * @param request where we can find the user information
     * @return user related to this request, null if no user info is found.
     * @throws AccessException if authentication is failed
     */
    User login(Object request) throws AccessException;
    
    /**
     * Authentication of request, identify the user who request the resource.
     *
     * @param request where we can find the user information
     * @return user related to this request, null if no user info is found.
     * @throws AccessException if authentication is failed
     */
    User loginRemote(Object request) throws AccessException;
    
    /**
     * Authorization of request, constituted with resource and user.
     *
     * @param permission permission to auth
     * @param user       user who wants to access the resource.
     * @throws AccessException if authorization is failed
     */
    void auth(Permission permission, User user) throws AccessException;
}
