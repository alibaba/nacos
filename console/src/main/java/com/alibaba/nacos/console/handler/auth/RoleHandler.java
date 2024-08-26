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
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;

import java.util.List;

/**
 * Interface for handling role-related operations.
 *
 * @author zhangyukun on:2024/8/16
 */
public interface RoleHandler {
    
    /**
     * Add a role to a user or create a role and bind it to GLOBAL_ADMIN.
     *
     * @param role     the role name
     * @param username the username
     * @return true if the operation is successful
     */
    boolean createRole(String role, String username);
    
    /**
     * Delete a role or delete all users under this role if no username is specified.
     *
     * @param role     the role name
     * @param username the username (optional)
     * @return true if the operation is successful
     */
    boolean deleteRole(String role, String username);
    
    /**
     * Get a paginated list of roles with the option for accurate or fuzzy search.
     *
     * @param pageNo   the page number
     * @param pageSize the size of the page
     * @param username the username (optional)
     * @param role     the role name (optional)
     * @param search   the type of search: "accurate" or "blur"
     * @return a paginated list of roles
     */
    Page<RoleInfo> getRoleList(int pageNo, int pageSize, String username, String role, String search);
    
    /**
     * Fuzzy match a role name.
     *
     * @param role the role name
     * @return a list of matching roles
     */
    List<String> getRoleListByRoleName(String role);
}

