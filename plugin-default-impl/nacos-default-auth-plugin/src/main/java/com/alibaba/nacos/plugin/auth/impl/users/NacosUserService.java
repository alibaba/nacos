/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.users;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

/**
 * Nacos auth plugin user service interface.
 *
 * @author xiweng.yy
 */
public interface NacosUserService extends UserDetailsService {
    
    /**
     * Update user password.
     *
     * @param username username to be updated password
     * @param password new password
     */
    void updateUserPassword(String username, String password);
    
    /**
     * Get users by paged.
     *
     * @param pageNo       page number
     * @param pageSize     page size
     * @param username     username
     * @return user list
     */
    Page<User> getUsers(int pageNo, int pageSize, String username);
    
    /**
     * Find users with blur search by paged.
     *
     * @param username     username
     * @param pageNo       page number
     * @param pageSize     page size
     * @return user list
     */
    Page<User> findUsers(String username, int pageNo, int pageSize);
    
    /**
     * Get User info by username.
     *
     * @param username     username
     * @return {@link User} information
     */
    User getUser(String username);
    
    /**
     * Find usernames with blur search.
     *
     * @param username     username
     * @return usernames
     */
    List<String> findUserNames(String username);
    
    /**
     * Create user.
     *
     * @param username     username
     * @param password     password
     */
    default void createUser(String username, String password) {
        createUser(username, password, true);
    }
    
    /**
     * Create user.
     *
     * @param username     username
     * @param password     password
     * @param encode       {@code true} will encode password, {@code false} will not encode password
     */
    void createUser(String username, String password, boolean encode);
    
    /**
     * Delete user.
     *
     * @param username     username
     */
    void deleteUser(String username);
}
